"""Bounded Amazon standard Batch adapter, fixed S3 keys, no arbitrary URL fetch."""
import base64
import hashlib
import json
from decimal import Decimal, InvalidOperation

from botocore.exceptions import BotoCoreError, ClientError, ReadTimeoutError, ConnectTimeoutError


class ProviderError(Exception):
    def __init__(self, code, uncertain=False):
        super().__init__(code)
        self.code, self.uncertain = code, uncertain


def aws_call(call, *, uncertain=False, **kwargs):
    try:
        return call(**kwargs)
    except ClientError as exc:
        code = exc.response.get("Error", {}).get("Code")
        status = exc.response.get("ResponseMetadata", {}).get("HTTPStatusCode", 0)
        if code == "ConflictException":
            safe = "START_UNCERTAIN"
        elif status == 429 or code in ("LimitExceededException", "ThrottlingException", "TooManyRequestsException"):
            safe = "PROVIDER_RATE_LIMITED"
        elif status >= 500:
            safe = "PROVIDER_UNAVAILABLE"
        else:
            safe = "PROVIDER_REJECTED"
        raise ProviderError(safe, uncertain) from None
    except (ReadTimeoutError, ConnectTimeoutError):
        raise ProviderError("PROVIDER_TIMEOUT", uncertain) from None
    except (BotoCoreError, OSError):
        raise ProviderError("PROVIDER_UNAVAILABLE", uncertain) from None


class AmazonProvider:
    def __init__(self, config, s3, transcribe, admission):
        self.config, self.s3, self.transcribe, self.admission = config, s3, transcribe, admission

    def _input(self, attempt):
        return "input/" + attempt["name"] + ".wav"

    def _output(self, attempt):
        return "output/" + attempt["name"] + ".json"

    def put(self, attempt, audio):
        self.admission()
        aws_call(self.s3.put_object, Bucket=self.config.input_bucket, Key=self._input(attempt), Body=audio,
                 ContentType="audio/wav", ServerSideEncryption="aws:kms", SSEKMSKeyId=self.config.input_kms_arn,
                 ExpectedBucketOwner=self.config.account_id,
                 ChecksumSHA256=base64.b64encode(hashlib.sha256(audio).digest()).decode())

    def start(self, attempt, job):
        self.admission()
        response = aws_call(self.transcribe.start_transcription_job, uncertain=True,
            TranscriptionJobName=attempt["name"], LanguageCode=job["creation"]["language"],
            MediaSampleRateHertz=16000, MediaFormat="wav",
            Media={"MediaFileUri": f"s3://{self.config.input_bucket}/{self._input(attempt)}"},
            OutputBucketName=self.config.output_bucket, OutputKey=self._output(attempt),
            OutputEncryptionKMSKeyId=self.config.output_kms_arn)
        return self._observation(response, attempt, job)

    def status(self, attempt, job):
        response = aws_call(self.transcribe.get_transcription_job, TranscriptionJobName=attempt["name"])
        return self._observation(response, attempt, job)

    def _observation(self, response, attempt, job):
        observed = response.get("TranscriptionJob", {})
        required = {"TranscriptionJobName": attempt["name"], "LanguageCode": job["creation"]["language"],
                    "MediaFormat": "wav", "MediaSampleRateHertz": 16000,
                    "Media": {"MediaFileUri": f"s3://{self.config.input_bucket}/{self._input(attempt)}"}}
        # GetTranscriptionJob materializes these disabled defaults for standard Batch.
        # Accept only literal false; enabled features, extra settings and malformed values fail closed.
        settings = observed.get("Settings", {})
        if (not isinstance(settings, dict) or set(settings) - {"ChannelIdentification", "ShowAlternatives"}
                or any(value is not False for value in settings.values())):
            raise ProviderError("PROVIDER_IDENTITY_MISMATCH", uncertain=True)
        forbidden = ("ModelSettings", "JobExecutionSettings", "ContentRedaction", "IdentifyLanguage",
                     "IdentifyMultipleLanguages", "LanguageOptions", "Subtitles", "LanguageIdSettings", "ToxicityDetection")
        if any(observed.get(k) != v for k, v in required.items()) or any(observed.get(k) for k in forbidden):
            raise ProviderError("PROVIDER_IDENTITY_MISMATCH", uncertain=True)
        state = observed.get("TranscriptionJobStatus")
        if state not in ("QUEUED", "IN_PROGRESS", "COMPLETED", "FAILED"):
            raise ProviderError("PROVIDER_INVALID_RESULT")
        return state

    def result(self, attempt, job):
        response = aws_call(self.s3.get_object, Bucket=self.config.output_bucket, Key=self._output(attempt),
                            ExpectedBucketOwner=self.config.account_id)
        stream = response["Body"]
        try:
            if (response.get("ServerSideEncryption") != "aws:kms" or response.get("SSEKMSKeyId") != self.config.output_kms_arn or
                    not 0 < response.get("ContentLength", 0) <= 1048576):
                raise ProviderError("PROVIDER_INVALID_RESULT")
            data = stream.read(1048577)
            if len(data) > 1048576:
                raise ProviderError("PROVIDER_INVALID_RESULT")
            document = json.loads(data)
            if (document["jobName"] != attempt["name"] or document["accountId"] != self.config.account_id or
                    document["status"] != "COMPLETED"):
                raise ProviderError("PROVIDER_IDENTITY_MISMATCH")
            results = document["results"]
            if "language_code" in results and results["language_code"] != job["creation"]["language"]:
                raise ProviderError("PROVIDER_IDENTITY_MISMATCH")
            transcripts = results["transcripts"]
            if len(transcripts) != 1 or not isinstance(transcripts[0]["transcript"], str):
                raise ValueError()
            segments, previous = [], 0
            for item in results["items"]:
                if item["type"] not in ("pronunciation", "punctuation"):
                    raise ValueError()
                alt = item["alternatives"][0]
                if not isinstance(alt["content"], str):
                    raise ValueError()
                start, end = (self._time(item.get(k), job["binding"]["frames"]) for k in ("start_time", "end_time"))
                if (start is not None and start < previous) or (start is not None and end is not None and end < start):
                    raise ValueError()
                if start is not None:
                    previous = start
                confidence = None
                if alt.get("confidence") is not None:
                    numeric = Decimal(str(alt["confidence"]))
                    if not numeric.is_finite() or not 0 <= numeric <= 1:
                        raise ValueError()
                    confidence = float(numeric)
                segments.append({"text": alt["content"], "start_ms": start, "end_ms": end, "confidence": confidence})
            return {"text": transcripts[0]["transcript"], "segments": segments}
        except ProviderError:
            raise
        except (KeyError, ValueError, TypeError, IndexError, InvalidOperation, BotoCoreError, OSError):
            raise ProviderError("PROVIDER_INVALID_RESULT") from None
        finally:
            stream.close()

    @staticmethod
    def _time(value, frames):
        if value is None:
            return None
        milliseconds = Decimal(str(value))*1000
        if (not milliseconds.is_finite() or not 0 <= milliseconds <= Decimal(frames)/16 or
                milliseconds != milliseconds.to_integral_value()):
            raise ValueError()
        return int(milliseconds)

    def delete_input(self, attempt):
        aws_call(self.s3.delete_object, Bucket=self.config.input_bucket, Key=self._input(attempt), ExpectedBucketOwner=self.config.account_id)

    def delete_output(self, attempt):
        aws_call(self.s3.delete_object, Bucket=self.config.output_bucket, Key=self._output(attempt), ExpectedBucketOwner=self.config.account_id)

    def delete_job(self, attempt):
        # Only called for a durably known terminal job. A lost successful deletion
        # response is reconciled against an exact-name bounded list, never messages.
        try:
            self.transcribe.delete_transcription_job(TranscriptionJobName=attempt["name"])
        except ClientError as exc:
            if exc.response.get("Error", {}).get("Code") != "BadRequestException":
                raise ProviderError("PROVIDER_CLEANUP_PENDING") from None
            response = aws_call(self.transcribe.list_transcription_jobs, JobNameContains=attempt["name"], MaxResults=100)
            if response.get("NextToken") or any(j.get("TranscriptionJobName") == attempt["name"] for j in response.get("TranscriptionJobSummaries", [])):
                raise ProviderError("PROVIDER_CLEANUP_PENDING")
        except (BotoCoreError, OSError):
            raise ProviderError("PROVIDER_CLEANUP_PENDING") from None
