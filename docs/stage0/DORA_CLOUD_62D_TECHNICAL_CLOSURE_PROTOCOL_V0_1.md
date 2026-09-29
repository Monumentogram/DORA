# Cloud 6.2D prospective technical closure protocol v0.1

This freezes local media validation and one valid duplicate-name retest. No owner quality recording or WER is rerun. Historical raw evidence is immutable. The new budget upper bound retains the original reservation and separately tightens only proven unused capacity after retirement. Publication is not provider admission.

The [product boundary decision](../adr/ADR-0014-alpha-media-preflight-boundary.md) requires the same canonical preflight in the controlled dispatch path. The following JSON is normative and identical to the companion contract.

```json
{
  "admission_condition": "ALL_CURRENT_TECHNICAL_CASES_SATISFIED_WITH_CLEANUP_AND_COST; historical EN limitation preserved; no automatic admission from protocol publication",
  "admission_gate": "BLOCKED",
  "authority": "PROJECT_OWNER_EXPLICIT_TECHNICAL_CLOSURE_REQUEST_2026_09_29",
  "baseline": "a3da33da6a0af6e6e56f54ad6dddcc3071961e7b",
  "budget": {
    "actual_aws_charges": "NOT_OBSERVED",
    "ceiling_result": {
      "asr_ceiling_usd": "2.000000",
      "combined_asr_upper_usd": "0.541500",
      "combined_maximum_rounded_up_usd": "9.996384",
      "new_asr_reserve_usd": "0.003000",
      "new_incremental_maximum_usd": "0.240875",
      "previous_asr_upper_usd": "0.538500",
      "remaining_headroom_usd": "0.003616",
      "revised_prior_upper_rounded_up_usd": "9.755509",
      "total_ceiling_usd": "10.000000"
    },
    "free_tier_assumed": false,
    "limits_and_required_readbacks": [
      "Budget result is an upper-bound reservation, not an AWS bill, refund, account quota or free-tier claim.",
      "Original historical USD 9.4370 and all other retired technical-run reserve lines remain intact; only provably unused operator, watchdog and pre-deletion KMS key-time capacity was tightened.",
      "Before any AWS action, independently review an immutable fresh task-only source and budget guard that caps operator Invoke at 20 before dispatch, StartTranscriptionJob at two and diagnostic total at 12.",
      "Freeze and read back 4-hour deadline, rate(5 minutes) watchdog, EventBridge MaximumRetryAttempts=2/MaximumEventAgeInSeconds=300, Lambda asynchronous retry defaults, 512 MiB/120 s operator and 128 MiB/60 s watchdog.",
      "Retain previous 2000 S3 and 3000 KMS service request reservations as conservative estimates; they are not mechanically enforced account quotas. Stop if readback or usage threatens the budget envelope.",
      "No owner recordings, no quality reruns, no AdministratorAccess, private task resources and TLS throughout."
    ],
    "official_sources": {
      "kms_deletion_wait": "https://docs.aws.amazon.com/kms/latest/developerguide/deleting-keys.html",
      "kms_pricing_and_pending_deletion": "https://aws.amazon.com/kms/pricing/",
      "lambda_pricing": "https://aws.amazon.com/lambda/pricing/",
      "lambda_synchronous_retry_behavior": "https://docs.aws.amazon.com/lambda/latest/dg/invocation-retries.html",
      "transcribe_pricing": "https://aws.amazon.com/transcribe/pricing/",
      "transcribe_start_same_name_conflict": "https://docs.aws.amazon.com/transcribe/latest/APIReference/API_StartTranscriptionJob.html"
    },
    "previous_reservation_preserved": {
      "original_historical_reserve_released_usd": "0.000000",
      "original_historical_reserve_usd": "9.437000",
      "other_technical_run_lines_released_usd": "0.000000",
      "previous_cumulative_upper_usd": "9.968500",
      "retired_run_repricing_is_unused_upper_bound_capacity_not_refund": true,
      "technical_run_incremental_ceiling_as_originally_reserved_usd": "0.530000"
    },
    "prospective_fresh_run_reserve": {
      "cloudwatch_10mb_reserve_usd": "0.010000",
      "conservative_billable_seconds_each_start": 15,
      "diagnostic_dispatch_cap": 12,
      "eventbridge_and_s3_storage_reserve_usd": "0.001000",
      "external_transfer_25mb_reserve_usd": "0.005000",
      "frankfurt_transcribe_sku": "9BDG889E5Y3GVM5S",
      "kms_3000_symmetric_request_reserve_usd": "0.009000",
      "lambda_750_request_reserve_usd": "0.000200",
      "local_full_price_offer_sha256": "f0ffd6e40cbaafd7099ffd16b38e30b52171f261a39a511c99ea5c4f52996b02",
      "max_diagnostic_dispatches_after": 11,
      "max_new_start_transcription_job_dispatches": 2,
      "new_incremental_maximum_usd": "0.240875",
      "no_automatic_start_retry": true,
      "operator_hard_preinvoke_cap_required": 20,
      "operator_max_duration_seconds": 120,
      "operator_max_memory_gb": "0.5",
      "operator_reserve_usd": "0.020400",
      "previous_diagnostic_dispatches": 9,
      "s3_2000_request_reserve_usd": "0.020000",
      "same_valid_accessible_input_and_job_name_for_both_requests": true,
      "synthetic_input_only": true,
      "transcribe_usd_per_second": "0.0001000000",
      "two_fresh_cmk_five_hour_bucket_reserve_usd": "0.014900",
      "two_start_asr_reserve_usd": "0.003000",
      "vat_and_other_control_plane_contingency_usd": "0.100000",
      "verified_price_quote_sha256": "5633d4ce3dce090613f5913e29aee7380cb957688708ea8f9f53323fd4155a6a",
      "watchdog_four_hour_450_execution_reserve_usd": "0.057375"
    },
    "region": "eu-central-1",
    "retired_run_proof": {
      "archive_manifest_sha256": "c0662454073029cf7568fb30e968e25bc7524c354aef1dd4730f59ce558513a1",
      "customer_managed_kms_keys": {
        "both_pending_deletion_by_utc": "2026-09-29T13:40:27.605000+00:00",
        "conservative_minimum_month_hours": 672,
        "creation_after_provision_intent_utc": "2026-09-29T13:15:18.628448+00:00",
        "exact_fraction_retired_upper_usd": "2/672",
        "key_count": 2,
        "max_billable_hour_buckets_per_key": 1,
        "pending_deletion_key_storage_charge_usd": "0.000000",
        "pending_deletion_wait_days": 7,
        "previous_reserve_usd": "0.014900",
        "retired_run_upper_usd_rounded_up_to_6dp": "0.002977",
        "same_utc_billing_hour": "2026-09-29T13:00:00+00:00",
        "unused_capacity_exact_fraction_usd": "0.014900-2/672",
        "usd_per_key_month": "1.00"
      },
      "operator": {
        "invoke_type": "SYNCHRONOUS_REQUEST_RESPONSE",
        "ledger_filenames_contiguous": "0001.json THROUGH 0151.json",
        "max_duration_seconds_per_invoke": 120,
        "memory_gb": "0.5",
        "previous_max_invokes": 300,
        "previous_reserve_usd": "0.306000",
        "reserved_rate_usd_per_gb_second": "0.000017",
        "retired_run_upper_usd": "0.154020",
        "saved_preinvoke_ledger_count": 151,
        "saved_source_writes_ledger_before_every_operator_invoke": true,
        "sdk_total_max_attempts": 1,
        "unused_capacity_usd": "0.151980"
      },
      "previous_budget_proof_sha256": "205c8014affaff16ca70bd6213508679ae5eee912a6763e0bf8ecc592df725ec",
      "private_replay_archive_sha256": "1b0000de804903e0090ef4f8fa9e8aa2d3a56ebd81af9ccac664983a13180f90",
      "provision_intent_sha256": "3b30f872a534585e0fc7a7d90c4c9bcc873c3049539bfc68519df61df7545d4b",
      "provision_response_sha256": "d215cbc0e34eb488b3a838894686972580ab918f582c46281f9cee249bc34222",
      "retirement_intent_sha256": "65e241880258f3ffbbc8111194708e96573c18de4a33a14a85446f822c694d42",
      "retirement_proof_sha256": "78a2ad3d3e7d59005ace441b06bb4988861c005982a3c4d78fb394cc1a5c8713",
      "retirement_status": "DELETE_COMPLETE_RESOURCES_ABSENT_TWO_CMKS_PENDING_DELETION",
      "revised_prior_cumulative_upper_rounded_up_to_6dp_usd": "9.755509",
      "unused_capacity_floor_to_6dp_usd": "0.212991",
      "watchdog": {
        "conservative_elapsed_minutes_ceiling": 26,
        "conservative_max_ticks_including_boundary": 7,
        "eventbridge_deliveries_per_tick_max": 3,
        "lambda_async_executions_per_delivered_event_max": 3,
        "manual_invocations_max_retained": 2,
        "max_duration_seconds_per_invoke": 60,
        "memory_gb": "0.125",
        "previous_executions_reserved": 450,
        "previous_reserve_usd": "0.057375",
        "provision_intent_utc": "2026-09-29T13:15:18.628448+00:00",
        "reserved_rate_usd_per_gb_second": "0.000017",
        "retired_run_executions_upper": 65,
        "retired_run_upper_usd": "0.0082875",
        "retirement_delete_complete_utc": "2026-09-29T13:41:18.078002+00:00",
        "schedule_interval_minutes": 5,
        "unused_capacity_usd": "0.0490875"
      }
    },
    "schema_version": "dora-62d-duplicate-name-budget-proof-v1",
    "scope": "FRESH_PRIVATE_SYNTHETIC_SAME_VALID_INPUT_SAME_JOB_NAME_TWO_STARTS_MAX",
    "status": "BUDGET_BOUND_PASS_EXECUTION_GATES_PENDING"
  },
  "current_62d": "BLOCKED / REQUIRED_TECHNICAL_CASES_NOT_SATISFIED",
  "duplicate_protocol": {
    "automatic_start_retries": 0,
    "cleanup": "delete exact input/output/markers and terminal job; verify versions, multipart, buckets empty; retire stack DELETE_COMPLETE; verify CMKs PendingDeletion and resources absent; preserve shared resources",
    "expected_second_error": "ConflictException",
    "first": "JOB_X deterministic unique name; valid accessible exact synthetic input; require job creation",
    "fresh_environment_delta": "new task identifiers; one synthetic b1 catalog; remove prior isolated access-denial fixture policy; same private CMK/TLS/least-privilege target controls",
    "fresh_snapshot_rule": "freeze exact fresh live snapshot SHA before Start1; both Starts must match it; prior full physical snapshot hash is not claimed equal",
    "language": "ru-RU",
    "maximum_lifetime_hours": 4,
    "maximum_new_starts": 2,
    "maximum_operator_invokes": 20,
    "oracle": "typed documented collision attributable to existing JOB_X; exact prose not required; BadRequest/missing input never PASS",
    "prior_proven_environment_config_sha256": "99f9daac98f990e02892e739047d2719b4e64f57eb9defa22aa35d32f835bcd3",
    "region": "eu-central-1",
    "required_controls": [
      "PRIVATE_INPUT_OUTPUT",
      "INPUT_CMK",
      "EXPLICIT_OUTPUT_CMK",
      "BPA",
      "BUCKET_OWNER_ENFORCED",
      "TLS",
      "TEMPORARY_CREDENTIALS",
      "AI_OPT_OUT",
      "TASK_ONLY_PERMISSIONS",
      "NO_DATA_ACCESS_ROLE_REQUEST",
      "NO_JOB_EXECUTION_SETTINGS",
      "AUTOMATIC_CLEANUP"
    ],
    "second": "same JOB_X and byte-identical Start request, same verified accessible input, while first job exists; do not delete or replace either",
    "service": "AMAZON_TRANSCRIBE_STANDARD_BATCH_FILE_ASR",
    "synthetic_fixture_sha256": "3197cdd90229d945aefa33f0429fbe0849387df2f80291d761e16406139457d2",
    "uncertain_start": "PERSIST_INTENT_RECONCILE_WITH_GET_NEVER_BLIND_RETRY"
  },
  "historical_quality": {
    "en_read": "37/181 = 20.4420% HISTORICAL_18_PERCENT_BENCHMARK_FAIL",
    "new_primary_starts": 0,
    "owner_decision": "OD-62D-EN-01 ACCEPTED_WITH_MEASURED_QUALITY_LIMITATION OWNER_ONLY_CLOSED_INTERNAL_ALPHA",
    "ru": "17/179 = 9.4972% PASS_MEASURED",
    "wer_rescoring": 0
  },
  "limitations": [
    "OWNER_VOICE_ONLY",
    "EN_SPONTANEOUS_NOT_EVALUATED",
    "NOISE_NOT_EVALUATED",
    "TIMESTAMP_ACCURACY_NOT_EVALUATED",
    "NO_EXTERNAL_CUSTOMER_PUBLIC_ADMISSION"
  ],
  "local_controls": {
    "empty_near_empty_expected": "LOCAL_REJECTION_BEFORE_UPLOAD_ZERO_STARTS",
    "private_controls_proof_sha256": "880e24f061bdc9dbad5755c3250ed9a56646ae9ee1279b3f90178860abf22189",
    "private_long_composites_valid": 4,
    "private_owner_valid": 8,
    "synthetic_valid": 1,
    "truncated_expected": "TYPED_REJECTION_BEFORE_UPLOAD_ZERO_STARTS",
    "truncated_variants": [
      "RIFF_EXCEEDS_FILE",
      "DATA_EXCEEDS_FILE",
      "PARTIAL_FINAL_FRAME"
    ],
    "whole_manifest_sha256": "27ea1593a46c9c17de0a568df6537dbe55df56c4def59559d758cb9f07ecbe65",
    "wrong_format_expected": "MEDIA_FORMAT_MISMATCH_BEFORE_UPLOAD_ZERO_STARTS"
  },
  "official_sources": {
    "duplicate_name": "https://docs.aws.amazon.com/transcribe/latest/APIReference/API_StartTranscriptionJob.html",
    "minimum_duration": "https://docs.aws.amazon.com/general/latest/gr/transcribe.html"
  },
  "phase": "PROSPECTIVE_BEFORE_ANY_NEW_AWS_START",
  "preflight": {
    "ancillary_chunks": [
      "JUNK"
    ],
    "bits_per_sample": 16,
    "channels": 1,
    "checks": [
      "RIFF_WAVE_MAGIC",
      "EXACT_RIFF_PHYSICAL_LENGTH",
      "CHUNK_BOUNDS_AND_PADDING",
      "SINGLE_FMT_BEFORE_SINGLE_DATA",
      "PCM_FIELDS_BYTE_RATE_BLOCK_ALIGN",
      "DATA_EXTENT_AND_WHOLE_FRAMES",
      "NO_AMBIGUOUS_EMBEDDED_EXTENT",
      "DECLARATION_AGREES_WITH_BYTES",
      "EXACT_DURATION_BOUND",
      "BOUND_BYTES_SHA_PROFILE_BEFORE_UPLOAD_AND_START"
    ],
    "codec": "PCM_SIGNED_16_LE",
    "container": "RIFF_WAVE",
    "duration_us_max": 600000000,
    "duration_us_min": 500000,
    "fmt18_cbSize": 0,
    "fmt_bytes": [
      16,
      18
    ],
    "production_backend": "NOT_IMPLEMENTED_NOT_ADMITTED",
    "prospective_stage0_gateway": "tools/cloud62d_preflight_dispatch.py",
    "sample_rate_hz": 16000,
    "version": "dora-alpha-media-preflight-v0.1"
  },
  "private_bundle_bindings": {
    "budget_proof_sha256": "b9c728c3fea807182a6b4eae449cf8b625f52c519052458a894151f10839c498",
    "catalog_sha256": "53917d828009044415befdc6a38be518319729db269716cda33b9d93d682e92e",
    "fixture_sha256": "3197cdd90229d945aefa33f0429fbe0849387df2f80291d761e16406139457d2",
    "run_id": "c62d0929",
    "source_archive": "source-5c36d27289d2f4f48a99aee7f64bdf1b3f143e4a0d6b0f4385f48226d313d5a9.zip",
    "source_sha256": "5c36d27289d2f4f48a99aee7f64bdf1b3f143e4a0d6b0f4385f48226d313d5a9",
    "sources": {
      "authored-synthetic-ru.wav": "3197cdd90229d945aefa33f0429fbe0849387df2f80291d761e16406139457d2",
      "aws_prepare.py": "c365c50dd8332fb37d798dce1cd196db7cdec3634883b3031b0787dce6d764c6",
      "budget-proof.json": "b9c728c3fea807182a6b4eae449cf8b625f52c519052458a894151f10839c498",
      "closure_controller.py": "1afca1551b0536ada82548548b8642eca725f866905f2c670f7c0467ca711b06",
      "closure_env.py": "76edcc190e1b957ebcd202cebec6098a31ffe3fcb2ad2e82d9072266158d1cdc",
      "closure_template.py": "4546028c954d7b3b86164fc27c98fd6e0158b263ca95719d347d697baf4e1ef3",
      "composite_recipe.py": "6bfd6dd40e2fb924dc692d7c8fd9c6cf330ad710a84b190d2d4f6439d49b289d",
      "engineering_ledger.py": "14617d6406cb2a6b0459783dc690385fe828b5a9bfd6df34aed616e093ef18be",
      "fresh_b_catalog.py": "e199048fa496420b6e8620150728c82a7ccd0adbf741acdf2767e0e5badb3062",
      "fresh_b_driver.py": "6833373827345bedb8505ebcaa9c46504d7122c6ad89808cbb35dabc284b17e2",
      "fresh_b_runtime.py": "237faff79c309f726a82616385e10a345f522451aaeff457efd8f1eccf0ac16c",
      "fresh_b_runtime_technical.py": "f0ebfb53e90226c47052ad50ddb4b69fe245fa13aeb31310467249b8bc854394",
      "fresh_b_template.py": "204090489260c6fb931a5ced7252278860bfadf599524155afe5cf76133ed61e",
      "tools/cloud62d_aws/composite_recipe.py": "6bfd6dd40e2fb924dc692d7c8fd9c6cf330ad710a84b190d2d4f6439d49b289d",
      "tools/cloud62d_media.py": "1a328ce06bef5fea9fc9f6a223d3f9ea951d0351fb61ef45a839aed63ab334a3",
      "tools/cloud62d_preflight_dispatch.py": "389f5e9d55dbe484f45fcc3b32512ec9873225693c9ca9ad3e6e4e3b2b0c0754",
      "watchdog.py": "34ace6878f70be9d995e8423d97414a95b7c9f0eab2c1de24ccb8e216e3fba56"
    },
    "template_sha256": "5aaf607aa05ec19707a93e12b19fe4004f5b531fe4cffa05c02b12e2ca186f85"
  },
  "private_bundle_manifest_sha256": "0488b63902f93a7629cfec776cd3cce493e21e6a1c0a9f530f388a3a81ce7d8d",
  "readjudication_rules": {
    "duplicate_identity": "NEW_LIVE_VALID_SAME_NAME_CONFLICT_PROOF_REQUIRED",
    "empty_near_empty": "historical raw INCOMPLETE preserved; authoritative local duration guard may satisfy current cases",
    "generic_bad_request": "invalid_request unless independent contextual evidence exists",
    "historical_raw_rows": "IMMUTABLE",
    "malformed_long_result_identity": "REUSE_ACCEPTED_EVIDENCE_NO_RERUN",
    "missing_input": "SATISFIED_BY_TYPED_ADJUDICATION_AND_FROZEN_NORMALIZED_MAPPING",
    "permission_denial": "SATISFIED_BY_TYPED_ADJUDICATION_AND_FROZEN_NORMALIZED_MAPPING",
    "throttle": "DOCUMENTED_NOT_INDUCED",
    "truncated": "COMPLETED_EXTENT_UNPROVED; local structural rejection may satisfy current case",
    "wrong_format": "PROVIDER_ACCEPTS_MISMATCHED_DECLARATION_OBSERVED; local product rejection may satisfy current case"
  },
  "schema_version": "dora-62d-technical-closure-v0.1",
  "second_speaker": "PRE_ADDITIONAL_INTERNAL_TESTER_PROVIDER_REVALIDATION_MANDATORY",
  "source_sha256": {
    "tools/cloud62d_media.py": "1a328ce06bef5fea9fc9f6a223d3f9ea951d0351fb61ef45a839aed63ab334a3",
    "tools/cloud62d_owned/corpus.py": "e8d2eec76e345e486506ce273618ac2b92ba6bbf8a792b72e9fe34b7b4923cea",
    "tools/cloud62d_preflight_dispatch.py": "389f5e9d55dbe484f45fcc3b32512ec9873225693c9ca9ad3e6e4e3b2b0c0754",
    "tools/test_cloud62d_error_adjudication.py": "a643ab44a69c4ea42a6383648bd2bb5318696f9093dd7eebaa9177ae247db368",
    "tools/test_cloud62d_media.py": "8bd787b88c8036978d59c72e3f74fe63722bc337e8855d61f2a2665fb77696d6",
    "tools/test_cloud62d_preflight_dispatch.py": "2bf33146cbe26ba4b2c0dabddb665fb2b21f47ff603fd5adbdc958cb87e7dbd4"
  },
  "stage63": "NOT_RUN"
}
```
