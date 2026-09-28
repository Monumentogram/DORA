"""Task-only server expiry backstop. No audio/transcript read, no content logging.

After one immutable run deadline, fence writes by bucket/IAM policy and repeatedly
delete objects/versions/parts and terminal jobs. Immediate post-ingest deletion is
the live runner's duty. Running jobs cannot be hard cancelled.
"""
from datetime import datetime, timezone
import os


def handler(event, context):
    import boto3
    probe = isinstance(event, dict) and event.get("probe") == "non-speech-v1"
    if not probe and datetime.now(timezone.utc) < datetime.fromisoformat(os.environ["DEADLINE"]):
        return {"status": "BEFORE_DEADLINE"}
    s3 = boto3.client("s3", region_name="eu-central-1")
    failures = []
    def attempt(fn, **kwargs):
        try:
            return fn(**kwargs)
        except Exception:
            failures.append("FAILED_REDACTED")
            return None
    for bucket, prefix in ((os.environ["INPUT_BUCKET"], "input/"), (os.environ["OUTPUT_BUCKET"], "output/")):
        # Fixed non-speech namespace only; caller cannot change buckets/prefixes.
        if probe:
            prefix += "__retention_probe__/"
        try:
            for page in s3.get_paginator("list_object_versions").paginate(Bucket=bucket, Prefix=prefix):
                for obj in page.get("Versions", []) + page.get("DeleteMarkers", []):
                    if obj["Key"].startswith(prefix):
                        attempt(s3.delete_object, Bucket=bucket, Key=obj["Key"], VersionId=obj["VersionId"])
        except Exception:
            failures.append("VERSIONS_LIST_FAILED")
        try:
            for page in s3.get_paginator("list_multipart_uploads").paginate(Bucket=bucket, Prefix=prefix):
                for obj in page.get("Uploads", []):
                    if obj["Key"].startswith(prefix):
                        attempt(s3.abort_multipart_upload, Bucket=bucket, Key=obj["Key"], UploadId=obj["UploadId"])
        except Exception:
            failures.append("PARTS_LIST_FAILED")
    if probe:
        if failures:
            raise RuntimeError("PROBE_CLEANUP_INCOMPLETE:" + str(len(failures)))
        return {"status": "PROBE_DELETION_ATTEMPTED_REQUIRES_READBACK"}
    transcribe = boto3.client("transcribe", region_name="eu-central-1")
    pending = 0
    try:
        for page in transcribe.get_paginator("list_transcription_jobs").paginate(JobNameContains=os.environ["JOB_PREFIX"]):
            for job in page.get("TranscriptionJobSummaries", []):
                if not job["TranscriptionJobName"].startswith(os.environ["JOB_PREFIX"]):
                    continue
                if job["TranscriptionJobStatus"] in ("COMPLETED", "FAILED"):
                    attempt(transcribe.delete_transcription_job, TranscriptionJobName=job["TranscriptionJobName"])
                else:
                    pending += 1
    except Exception:
        failures.append("JOBS_LIST_FAILED")
    if failures:
        raise RuntimeError("CLEANUP_INCOMPLETE:" + str(len(failures)))
    return {"status": "PENDING" if pending else "DELETION_ATTEMPTED_REQUIRES_READBACK", "pending": pending}
