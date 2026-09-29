# DORA 6.2D — engineering S0 result v0.1

**S0 CORE PASS:** one synthetic `ru-RU` standard batch job was accepted and completed with a valid, nonempty transcript. This proves the minimal private SSE-S3 input to service-managed output path under the task-scoped Lambda caller. It does not prove the ADR-0011 customer-S3/CMK/data-role target, provider quality, real-speech admission, or the cause of five historical failed Starts.

The published harness commit is `21fde28ac8bad2b6defd1e4f4939e5b917837dfb` (parent `72c300fab52e6e072d4ef4039dfda5c2d130aedd`). S0 used only `TranscriptionJobName`, `Media.MediaFileUri`, and `LanguageCode=ru-RU`; it omitted every `Output*` field and `JobExecutionSettings`. The input was the committed 3.320-second synthetic WAV. Root provisioned the task sandbox; the limited Lambda role made the Start call. CloudShell Python, boto3, botocore and CLI versions below describe the inspection client, **not** the Lambda built-in SDK.

The request was submitted at 06:59:56.846844 UTC and `COMPLETED` was first observed at 07:00:21.711338 UTC, 24.864494 seconds later. This is polling-observed terminal latency, not an exact service completion timestamp or a quality measurement. One new Start was dispatched; campaign accounting is now six total Starts, one accepted and completed. No real-audio Starts occurred.

The bounded sandbox is preserved for the immediate one-delta S1 → S2 → S3 → fresh B ladder; cleanup is **NOT_YET_DUE**, not PASS. The four-hour Start/write deadline remains in force. No optional no-header Put gate precedes the actual target. The 0.01 USD diagnostic ASR reservation is 1/12; prospective total reservation is 9.3069 USD against the approved 10 USD ceiling. Actual charges are unavailable and are not reported as zero.

**Stage 6.2D remains BLOCKED / LIVE_PROVIDER_EVALUATION_NOT_EXECUTED; 6.3 remains NOT_RUN.** Phase A v0.3 is unpublished, all eight resumed real primaries remain unrun, and the frozen 39-gate counts are unchanged. Public evidence contains only synthetic artifact hashes and sanitized metadata; the transcript text, presigned URL, account/resource/job identifiers and private voices remain out of Git.

## Machine-readable record

```json
{
  "schema_version": "0.1",
  "package": "DORA_CLOUD_62D_ENGINEERING_S0_RESULT_V0_1",
  "scope": "PROJECT_OWNER_ONLY_SYNTHETIC_ENGINEERING_DIAGNOSTIC",
  "status": "S0_CORE_PASS_TARGET_PATH_NOT_YET_PROVEN",
  "published_harness_commit": "21fde28ac8bad2b6defd1e4f4939e5b917837dfb",
  "harness_parent_commit": "72c300fab52e6e072d4ef4039dfda5c2d130aedd",
  "observation": {
    "service": "Amazon Transcribe",
    "mode": "STANDARD_BATCH_FILE_ASR",
    "region": "eu-central-1",
    "input": "PRIVATE_S3_SSE_S3_SYNTHETIC_WAV",
    "output": "TRANSCRIBE_SERVICE_MANAGED",
    "caller": "TASK_SCOPED_LAMBDA_EXECUTION_ROLE",
    "provisioner": "OWNER_CONSOLE_CLOUDSHELL_ROOT",
    "request_fields": [
      "TranscriptionJobName",
      "Media.MediaFileUri",
      "LanguageCode=ru-RU"
    ],
    "request_fields_absent": [
      "OutputBucketName",
      "OutputKey",
      "OutputEncryptionKMSKeyId",
      "JobExecutionSettings"
    ],
    "data_access_role_arn": "ABSENT",
    "synthetic_audio_duration_seconds": "3.320",
    "synthetic_wav_sha256": "3197cdd90229d945aefa33f0429fbe0849387df2f80291d761e16406139457d2",
    "submitted_utc": "2026-09-29T06:59:56.846844+00:00",
    "terminal_observed_utc": "2026-09-29T07:00:21.711338+00:00",
    "submit_to_first_terminal_observation_seconds": "24.864494",
    "observed_latency_to_audio_duration_ratio": "7.489305421686748",
    "new_start_dispatches": 1,
    "accepted_jobs": 1,
    "completed_jobs": 1,
    "valid_nonempty_synthetic_transcripts": 1,
    "ledger_attempts": 1,
    "ledger_created": true,
    "ledger_completed": true,
    "terminal_status": "COMPLETED"
  },
  "evidence_sha256": {
    "request": "49f39029b7fd748dbd8a89f4c931dc36ef880baa3138072f29753e894b4d19cc",
    "actual_config": "cbe9e00fae6eab918839d0c0775c4bb09fefc8f77ab5d10ff95a6e95799e4cf8",
    "terminal": "9f9702370818fce83ed624ed3afbe4861c8d196283f7b5c6ae1da928685071fe",
    "synthetic_transcript": "d189ba077f02a8ca07497ed1e31f0ffdd80e365e9e6f3550a00e4775db9a7253",
    "input_proof": "e613fa9197155520e860805e33c86389310557ba07cba22025532b3b320afb28",
    "start_reply": "dd9aa56357601f4a5693797aa6fa0aa36e18448df76d76c3f62d3cc6fce533ac"
  },
  "software_observed_in_cloudshell_not_lambda_runtime": {
    "aws_cli": "aws-cli/2.37.1 Python/3.14.6 Linux/6.1.186-228.376.amzn2023.x86_64 exec-env/CloudShell exe/x86_64.amzn.2023",
    "python": "3.13.15",
    "boto3": "1.43.38",
    "botocore": "1.43.57"
  },
  "lambda_builtin_sdk_versions": "NOT_RECORDED",
  "lambda_zip_code_sha256_base64": "ys1JdzcA2DmbSh7JFYYi6KSG4Ylz34g7G73D4KIDmaI=",
  "attempt_accounting": {
    "historical_failed_start_dispatches_preserved": 5,
    "new_synthetic_start_dispatches": 1,
    "all_campaign_start_dispatches": 6,
    "all_campaign_accepted_jobs": 1,
    "remaining_new_diagnostic_start_cap": 11,
    "new_real_start_dispatches": 0
  },
  "budget": {
    "currency": "USD",
    "approved_asr_ceiling": "2",
    "approved_total_ceiling": "10",
    "prospective_total_reservation": "9.3069",
    "diagnostic_asr_reservation": "0.01",
    "diagnostic_asr_reservation_dispatches": "1/12",
    "actual_charges": "UNAVAILABLE_NOT_ZERO"
  },
  "cleanup": {
    "status": "NOT_YET_DUE_NOT_PASS",
    "sandbox": "PRESERVED_FOR_IMMEDIATE_INCREMENTAL_LADDER",
    "write_start_deadline": "FOUR_HOUR_BOUND_REMAINS_APPLICABLE"
  },
  "interpretation": {
    "proven": "S0_CORE_PATH_ACCEPTED_COMPLETED_WITH_NONEMPTY_SYNTHETIC_TRANSCRIPT",
    "historical_failure_cause": "UNRESOLVED",
    "adr0011_customer_s3_cmk_data_role_target": "NOT_YET_PROVEN",
    "provider_quality_admission": "NOT_ESTABLISHED",
    "real_speech_admission": "NOT_ESTABLISHED"
  },
  "next": {
    "ladder": ["S1_CUSTOM_SSE_S3_OUTPUT", "S2_EXPLICIT_OUTPUT_CMK", "S3_INPUT_CMK", "FRESH_B_ADR0011_TARGET"],
    "rule": "ONE_DELTA_PER_PASS; NO_OPTIONAL_NO_HEADER_PUT_GATE; FREEZE_FULL_TARGET_ONLY_AFTER_PASS_BEFORE_PRIVATE_VOICE",
    "phase_a_v0_3": "NOT_PUBLISHED",
    "real_audio_starts": 0,
    "6.2D": "BLOCKED / LIVE_PROVIDER_EVALUATION_NOT_EXECUTED",
    "6.3": "NOT_RUN"
  },
  "frozen_39_gate_counts_unchanged": {
    "SATISFIED": 6,
    "SATISFIED_FOR_CLOSED_INTERNAL_ALPHA": 2,
    "PARTIALLY_SATISFIED": 0,
    "OPEN": 5,
    "BLOCKED": 1,
    "NOT_RUN": 25
  },
  "validation_reported_for_harness_publication": {
    "new_tests_passed": 27,
    "legacy_aws_tests_passed": 97,
    "stage00_tests_passed": 7,
    "preparation_tests_passed": 13,
    "corpus_tests_passed": 51,
    "shared_provider_contract_tests_passed": 26
  },
  "privacy": "No account/resource/job identifiers, transcript text, presigned URLs, private voice, credentials, or raw provider response are published."
}
```
