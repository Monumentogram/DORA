# DORA 6.2D — v4 direct-S3 mandatory stop result v0.1

**6.2D = BLOCKED / LIVE_PROVIDER_EVALUATION_NOT_EXECUTED.**
Exact blocker: **V4_DEFAULT_KMS_PATH_FAILED**. BOUNDED8 remains INCOMPLETE.

The sole approved no-SSE-header PutObject was dispatched by the bounded operator
after both publications and exact v4 deployment. S3 returned AccessDenied with an
explicit deny in a resource-based policy. No object could be read to establish its
CMK. The specific matching Deny and actual authorization context remain unresolved;
no successful integration, cryptographic path or historical cause is inferred from
static policy tests. The mandatory STOP was honored: D1/D2/D3, Phase A v0.3 and all
eight recovery primaries were NOT_RUN. No second Put and no new Start were sent.

## Publications and verification

- 39aabea652de9b983c4f0e8d8ba0d7e55fb5bf91: reviewed v4 and complete prospective diagnostic v0.2.
- 1a12f96456da24ac3ed90eeefb033af8c172e884: narrow verifier correction and additive diagnostic publication v0.3 (machine schema 0.2).
- Both were pushed/refetched with clean equal local/remote HEAD before their observations.
- AWS suite 95 then 97 PASS; Stage00 7/7; preparation13, corpus51 and provider-independent AWS/NonAWSFake26 PASS; independent reviews and parity/diff checks PASS.

The first verifier invocation performed only reads: AWS's documented SSE-C blocking
metadata was rejected by an overly strict response comparison. This was separately
preserved and repaired before the sole actual Put. It was not an additional Put or
Transcribe attempt. Live deployment reached UPDATE_COMPLETE at 20:20:48.570Z.

AWS confirms [missing-key Deny semantics](https://docs.aws.amazon.com/IAM/latest/UserGuide/reference_policies_elements_condition_operators.html#Conditions_IfExists),
[GetBucketEncryption metadata](https://docs.aws.amazon.com/AmazonS3/latest/API/API_GetBucketEncryption.html), and
[SSE-C blocking](https://docs.aws.amazon.com/AmazonS3/latest/API/API_BlockedEncryptionTypes.html).
V3 can deny absent SSE keys. V4's static truth table passes the requested cases,
but its actual no-header authorization failed and is not waived.

## All eight records remain visible

| Case | Normalized reference N | Historical state | Recovery state |
|---|---:|---|---|
| ru-read-01 | 57 | REJECTED_BEFORE_JOB_CREATION | NOT_RUN |
| ru-read-02 | 64 | REJECTED_BEFORE_JOB_CREATION | NOT_RUN |
| ru-spontaneous-01 | 30 | REJECTED_BEFORE_JOB_CREATION | NOT_RUN |
| ru-spontaneous-02 | 28 | REJECTED_BEFORE_JOB_CREATION | NOT_RUN |
| en-read-01 | 46 | NOT_RUN | NOT_RUN |
| en-read-02 | 45 | NOT_RUN | NOT_RUN |
| en-read-03 | 45 | NOT_RUN | NOT_RUN |
| en-read-04 | 45 | NOT_RUN | NOT_RUN |

Manifest, audio and references are unchanged. RU READ N=121, SPONTANEOUS N=58,
total179; EN READ N=181. Scored N=0; raw/normalized S/D/I and WER are unavailable,
not zero-error results. No RU+EN averaging. Latency p50/p95 and timestamp structure
are NOT_EVALUATED. TIMESTAMP_ACCURACY=NOT_EVALUATED; no accurate-seek claim.
EN spontaneous, noise/speakerphone and other speakers remain NOT_EVALUATED.

All subsequent technical cases were NOT_RUN at this stop. Throttle was not induced.
The offline shared provider contract remains PASS; no SDK transcript type was added
to the core domain contract. This does not establish live provider quality.

## Cleanup and cost

The failed Put was followed by exact-key deletion, Head absence and operator plus
independent empty prefix readbacks. A separate independent whole-task readback
confirmed no current objects, versions, markers, multipart uploads or jobs.
The stack is DELETE_COMPLETE; both task buckets, three roles, Lambda and schedule
are absent. Both CMKs are PendingDeletion on 2026-10-05; physical deletion is not
claimed. Exactly three previous task configuration templates were deleted from
the shared support bucket, with empty object/version/multipart readback. The shared
bucket and unrelated contents were preserved.

New ASR jobs/seconds=0. Actual invoice is unavailable; failed requests and read
operations are not assumed free. The preserved USD8.4069 prospective reservation
is not an invoice; approved ceilings remain ASR2/total10 USD.

## Gate disposition and next step

EVALUATION and PROVIDER remain OPEN; ADMISSION remains BLOCKED. Frozen39 remains
6 SATISFIED +2 SATISFIED_FOR_CLOSED_INTERNAL_ALPHA +0 PARTIALLY_SATISFIED +5 OPEN
+1 BLOCKED +25 NOT_RUN=39. Scoped privacy/control statuses and external Legal gate
are unchanged. Owner-only admission and its additive population overlay were not
activated. The future second-speaker revalidation trigger remains recorded.

Next requires a new prospective S3 authorization diagnostic and fresh bound AWS
environment, preserving this consumed one-Put failure. Determine the matching Deny
and actual authorization context before any further write or Transcribe attempt.
6.3 remains NOT_RUN / NOT_ELIGIBLE. PR86, main and Stage5 were not touched.

## Machine-readable record

```json
{
  "schema_version": "1.0",
  "scope": "PROJECT_OWNER_ONLY",
  "status": "BLOCKED_DIRECT_S3_PROOF_FAILED",
  "published_commit": "1a12f96456da24ac3ed90eeefb033af8c172e884",
  "observed_at": "2026-09-28T20:39:56.873896+00:00",
  "independently_audited_at": "2026-09-28T20:42:33.356649+00:00",
  "direct_s3_proof": {
    "status": "V4_DEFAULT_KMS_PATH_FAILED",
    "actual_put_dispatches": 1,
    "maximum_put_dispatches": 1,
    "put_error_code": "AccessDenied",
    "attribution": "EXPLICIT_DENY_IN_RESOURCE_BASED_POLICY",
    "no_sse_headers": true,
    "output_cmk_head": "NOT_REACHED_AFTER_FAILED_PUT",
    "probe_prefix_cleanup": "VERIFIED_VISIBLE_EMPTY_OPERATOR_AND_INDEPENDENT_READBACK",
    "budget_consumed": true,
    "resubmission_authorized": false
  },
  "attempt_accounting": {
    "historical_primary_start_dispatches": 4,
    "historical_synthetic_start_dispatches": 1,
    "new_diagnostic_start_dispatches": 0,
    "new_real_start_dispatches": 0,
    "all_campaign_start_dispatches": 5,
    "accepted_jobs": 0,
    "completed_jobs": 0,
    "transcripts": 0,
    "scored_primaries": 0
  },
  "records": [
    {
      "case_id": "ru-read-01",
      "language": "ru",
      "speech_class": "READ",
      "historical_status": "REJECTED_BEFORE_JOB_CREATION",
      "historical_error_code": "BadRequestException",
      "recovery_status": "NOT_RUN",
      "raw": {
        "status": "NOT_EVALUATED",
        "S": null,
        "D": null,
        "I": null,
        "N_scored": 0,
        "wer_percent": null
      },
      "normalized": {
        "status": "NOT_EVALUATED",
        "S": null,
        "D": null,
        "I": null,
        "N_scored": 0,
        "wer_percent": null
      },
      "timestamp_structure": "NOT_EVALUATED",
      "timestamp_accuracy": "NOT_EVALUATED",
      "expected_reference_tokens": {
        "raw": 57,
        "normalized": 57
      },
      "duration_us": 38920000
    },
    {
      "case_id": "ru-read-02",
      "language": "ru",
      "speech_class": "READ",
      "historical_status": "REJECTED_BEFORE_JOB_CREATION",
      "historical_error_code": "BadRequestException",
      "recovery_status": "NOT_RUN",
      "raw": {
        "status": "NOT_EVALUATED",
        "S": null,
        "D": null,
        "I": null,
        "N_scored": 0,
        "wer_percent": null
      },
      "normalized": {
        "status": "NOT_EVALUATED",
        "S": null,
        "D": null,
        "I": null,
        "N_scored": 0,
        "wer_percent": null
      },
      "timestamp_structure": "NOT_EVALUATED",
      "timestamp_accuracy": "NOT_EVALUATED",
      "expected_reference_tokens": {
        "raw": 64,
        "normalized": 64
      },
      "duration_us": 42700000
    },
    {
      "case_id": "ru-spontaneous-01",
      "language": "ru",
      "speech_class": "SPONTANEOUS",
      "historical_status": "REJECTED_BEFORE_JOB_CREATION",
      "historical_error_code": "BadRequestException",
      "recovery_status": "NOT_RUN",
      "raw": {
        "status": "NOT_EVALUATED",
        "S": null,
        "D": null,
        "I": null,
        "N_scored": 0,
        "wer_percent": null
      },
      "normalized": {
        "status": "NOT_EVALUATED",
        "S": null,
        "D": null,
        "I": null,
        "N_scored": 0,
        "wer_percent": null
      },
      "timestamp_structure": "NOT_EVALUATED",
      "timestamp_accuracy": "NOT_EVALUATED",
      "expected_reference_tokens": {
        "raw": 30,
        "normalized": 30
      },
      "duration_us": 34900000
    },
    {
      "case_id": "ru-spontaneous-02",
      "language": "ru",
      "speech_class": "SPONTANEOUS",
      "historical_status": "REJECTED_BEFORE_JOB_CREATION",
      "historical_error_code": "BadRequestException",
      "recovery_status": "NOT_RUN",
      "raw": {
        "status": "NOT_EVALUATED",
        "S": null,
        "D": null,
        "I": null,
        "N_scored": 0,
        "wer_percent": null
      },
      "normalized": {
        "status": "NOT_EVALUATED",
        "S": null,
        "D": null,
        "I": null,
        "N_scored": 0,
        "wer_percent": null
      },
      "timestamp_structure": "NOT_EVALUATED",
      "timestamp_accuracy": "NOT_EVALUATED",
      "expected_reference_tokens": {
        "raw": 28,
        "normalized": 28
      },
      "duration_us": 34440000
    },
    {
      "case_id": "en-read-01",
      "language": "en",
      "speech_class": "READ",
      "historical_status": "NOT_RUN",
      "historical_error_code": null,
      "recovery_status": "NOT_RUN",
      "raw": {
        "status": "NOT_EVALUATED",
        "S": null,
        "D": null,
        "I": null,
        "N_scored": 0,
        "wer_percent": null
      },
      "normalized": {
        "status": "NOT_EVALUATED",
        "S": null,
        "D": null,
        "I": null,
        "N_scored": 0,
        "wer_percent": null
      },
      "timestamp_structure": "NOT_EVALUATED",
      "timestamp_accuracy": "NOT_EVALUATED",
      "expected_reference_tokens": {
        "raw": 46,
        "normalized": 46
      },
      "duration_us": 26920000
    },
    {
      "case_id": "en-read-02",
      "language": "en",
      "speech_class": "READ",
      "historical_status": "NOT_RUN",
      "historical_error_code": null,
      "recovery_status": "NOT_RUN",
      "raw": {
        "status": "NOT_EVALUATED",
        "S": null,
        "D": null,
        "I": null,
        "N_scored": 0,
        "wer_percent": null
      },
      "normalized": {
        "status": "NOT_EVALUATED",
        "S": null,
        "D": null,
        "I": null,
        "N_scored": 0,
        "wer_percent": null
      },
      "timestamp_structure": "NOT_EVALUATED",
      "timestamp_accuracy": "NOT_EVALUATED",
      "expected_reference_tokens": {
        "raw": 45,
        "normalized": 45
      },
      "duration_us": 34480000
    },
    {
      "case_id": "en-read-03",
      "language": "en",
      "speech_class": "READ",
      "historical_status": "NOT_RUN",
      "historical_error_code": null,
      "recovery_status": "NOT_RUN",
      "raw": {
        "status": "NOT_EVALUATED",
        "S": null,
        "D": null,
        "I": null,
        "N_scored": 0,
        "wer_percent": null
      },
      "normalized": {
        "status": "NOT_EVALUATED",
        "S": null,
        "D": null,
        "I": null,
        "N_scored": 0,
        "wer_percent": null
      },
      "timestamp_structure": "NOT_EVALUATED",
      "timestamp_accuracy": "NOT_EVALUATED",
      "expected_reference_tokens": {
        "raw": 45,
        "normalized": 45
      },
      "duration_us": 35260000
    },
    {
      "case_id": "en-read-04",
      "language": "en",
      "speech_class": "READ",
      "historical_status": "NOT_RUN",
      "historical_error_code": null,
      "recovery_status": "NOT_RUN",
      "raw": {
        "status": "NOT_EVALUATED",
        "S": null,
        "D": null,
        "I": null,
        "N_scored": 0,
        "wer_percent": null
      },
      "normalized": {
        "status": "NOT_EVALUATED",
        "S": null,
        "D": null,
        "I": null,
        "N_scored": 0,
        "wer_percent": null
      },
      "timestamp_structure": "NOT_EVALUATED",
      "timestamp_accuracy": "NOT_EVALUATED",
      "expected_reference_tokens": {
        "raw": 45,
        "normalized": 45
      },
      "duration_us": 32900000
    }
  ],
  "quality": "NOT_EVALUATED",
  "latency": "NOT_EVALUATED",
  "timestamp_structure": "NOT_EVALUATED",
  "timestamp_accuracy": "NOT_EVALUATED",
  "corpus_and_references_revalidated_unchanged": true,
  "historical_primary_journal_unchanged": true,
  "historical_synthetic_journal_unchanged": true,
  "bounded8": "INCOMPLETE",
  "provider_admission": "NOT_ESTABLISHED",
  "stage_6_2D": "BLOCKED / LIVE_PROVIDER_EVALUATION_NOT_EXECUTED",
  "stage_6_3": "NOT_RUN",
  "effective_gate_statuses": {
    "CLD-ADM-ARCH-001": "SATISFIED",
    "CLD-ADM-CONSENT-001": "SATISFIED",
    "CLD-ADM-DATA-001": "SATISFIED",
    "CLD-ADM-SCOPE-001": "SATISFIED",
    "CLD-ADM-GAPS-001": "SATISFIED",
    "CLD-ADM-PRIVACY-001": "SATISFIED_FOR_CLOSED_INTERNAL_ALPHA",
    "CLD-ADM-RETENTION-001": "SATISFIED",
    "CLD-ADM-CONTROL-001": "SATISFIED_FOR_CLOSED_INTERNAL_ALPHA",
    "CLD-ADM-EVALUATION-001": "OPEN",
    "CLD-ADM-PROVIDER-001": "OPEN",
    "CLD-ADM-ADMISSION-001": "BLOCKED",
    "CLD-ADM-API-001": "OPEN",
    "CLD-ADM-AUTH-001": "NOT_RUN",
    "CLD-ADM-CONSENT-RUNTIME-001": "NOT_RUN",
    "CLD-ADM-SECRETS-001": "NOT_RUN",
    "CLD-ADM-UPLOAD-001": "NOT_RUN",
    "CLD-ADM-CRYPTO-001": "NOT_RUN",
    "CLD-ADM-RETENTION-RUNTIME-001": "NOT_RUN",
    "CLD-ADM-DATA-RUNTIME-001": "NOT_RUN",
    "CLD-ADM-FAILURE-001": "NOT_RUN",
    "CLD-ADM-QUEUE-001": "NOT_RUN",
    "CLD-ADM-BACKGROUND-001": "NOT_RUN",
    "CLD-ADM-ADAPTER-001": "NOT_RUN",
    "CLD-ADM-COST-001": "NOT_RUN",
    "CLD-ADM-OBSERVABILITY-001": "NOT_RUN",
    "CLD-ADM-SECURITY-001": "NOT_RUN",
    "CLD-ADM-RESULT-001": "NOT_RUN",
    "CLD-ADM-MERGE-001": "NOT_RUN",
    "CLD-ADM-LOCAL-001": "NOT_RUN",
    "CLD-ADM-OFFLINE-001": "NOT_RUN",
    "CLD-ADM-DELETE-001": "NOT_RUN",
    "CLD-ADM-HARNESS-001": "NOT_RUN",
    "CLD-ADM-UX-001": "NOT_RUN",
    "CLD-ADM-HISTORY-001": "NOT_RUN",
    "CLD-ADM-EXPORT-001": "NOT_RUN",
    "CLD-ADM-OPERATIONS-001": "NOT_RUN",
    "CLD-ADM-SUPPLY-001": "OPEN",
    "CLD-ADM-EXIT-001": "NOT_RUN",
    "CLD-ADM-RELEASE-001": "OPEN"
  },
  "effective_status_counts": {
    "SATISFIED": 6,
    "SATISFIED_FOR_CLOSED_INTERNAL_ALPHA": 2,
    "OPEN": 5,
    "BLOCKED": 1,
    "NOT_RUN": 25,
    "PARTIALLY_SATISFIED": 0
  },
  "frozen_gate_count": 39,
  "technical_cases": {
    "short_valid": "NOT_RUN_DIRECT_S3_STOP",
    "empty": "NOT_RUN_DIRECT_S3_STOP",
    "near_empty": "NOT_RUN_DIRECT_S3_STOP",
    "malformed": "NOT_RUN_DIRECT_S3_STOP",
    "incorrect_declaration": "NOT_RUN_DIRECT_S3_STOP",
    "truncated": "NOT_RUN_DIRECT_S3_STOP",
    "missing_s3": "NOT_RUN_DIRECT_S3_STOP",
    "duplicate_conflict": "NOT_RUN_DIRECT_S3_STOP",
    "300s_composite": "NOT_RUN_DIRECT_S3_STOP",
    "600s_composite": "NOT_RUN_DIRECT_S3_STOP",
    "output_result_identity": "NOT_RUN_DIRECT_S3_STOP",
    "isolated_permission_denial": "NOT_RUN_NO_SAFE_ISOLATED_FIXTURE",
    "throttle": "DOCUMENTED_NOT_INDUCED"
  },
  "replacement_feasibility": {
    "status": "OFFLINE_PASS",
    "shared_contract_tests": 26,
    "adapters": [
      "AWS",
      "NonAWSFake"
    ],
    "aws_sdk_domain_contract": false,
    "live_quality_equivalence": "NOT_EVALUATED"
  },
  "cleanup_scope_limit": "WHOLE_TASK_BUCKETS_VERSIONS_MARKERS_MULTIPART_JOBS_VERIFIED_EMPTY_BEFORE_RETIREMENT",
  "infrastructure_retirement": {
    "status": "RETIRED_VERIFIED",
    "checks": {
      "stack_deleted": true,
      "Input_bucket_absent": true,
      "Output_bucket_absent": true,
      "DataRole_absent": true,
      "OperatorRole_absent": true,
      "WatchdogRole_absent": true,
      "Watchdog_absent": true,
      "Schedule_absent": true,
      "keys_pending_deletion": true
    },
    "kms_keys": [
      {
        "state": "PendingDeletion",
        "deletion_date": "2026-10-05T23:43:24.135000+03:00"
      },
      {
        "state": "PendingDeletion",
        "deletion_date": "2026-10-05T23:43:24.205000+03:00"
      }
    ],
    "physical_key_deletion": "PENDING_NOT_CLAIMED",
    "support_templates_deleted": 3,
    "shared_support_bucket_deleted": false,
    "unrelated_contents_touched": false
  },
  "actual_spend": "NOT_MEASURED; failed requests are not assumed free",
  "public_privacy": "No audio, references, transcripts, per-record content hashes, account/resource/job identifiers, credentials or raw provider errors.",
  "evidence_sha256": {
    "receipt.json": "247992e76f46087b8c01425433a8bc1cf418fea64800b9314513283dcc1b7767",
    "reservation.json": "9c827d54ee597ae44f59745e42734379c9c21b3b284c9efebf74888be3ef8b00",
    "cleanup.json": "f9b9824e78b0d8be3c45e466142349db5fc6d5699bc7a91fe9d91a563760305b",
    "publication.json": "28105c42a09405afb36d7901f406b720df8f628e3af811ac8c06424c6d57e6ad",
    "calls/000/intent.json": "e08202f4619d928400e5d8c4be4f2b7425bc0f61287511a4675e7b38795bbe6c",
    "calls/000/error.json": "3fe609e6d718f103bc2b607da2f58e36f14e27466c6c1e961c2cfa01a9c8d823",
    "final-pre-retirement-cleanup.json": "ffb89fe244971bd302f0c87cee9e3654b3a121d3b23c019bb7c8bb313ffb5b46",
    "retirement-01.json": "40704592f2a6b094a706fc9d769f448b2f4e385c1e426d9093b87a465b0c70f1",
    "support-cleanup.json": "b9ace2cd8b397b7e43680c72371677cb4aebabc02cb6f36038dd608e0851729a",
    "preflight-03/v4-preflight.json": "49b4c387bf565ccbba0ef684d9abea8147f789ce79d1f9133a1406c230e4d397",
    "preflight-02/v4-retention.json": "82cc7125c79f8a154855868e722f291aedafb86fa69c20ef38f4c5801ad3c06c",
    "initial-sha256.json": "50d182fff7462c04c711f0cc579bcd9add99ecfe6e718edc426833db50d18328"
  },
  "package": "DORA_CLOUD_62D_V4_DEFAULT_KMS_STOP_RESULT_V0_1",
  "blocker": "V4_DEFAULT_KMS_PATH_FAILED",
  "result_type": "STOP_BEFORE_TRANSCRIBE_NOT_PROVIDER_EVALUATION",
  "baseline": {
    "required_remote_head": "37201eee0533acab032824a4a7b7050a394ea5b4",
    "fetched_exact": true,
    "initial_dirty_only": [
      "tools/cloud62d_aws/aws_prepare.py",
      "tools/cloud62d_aws/test_aws.py"
    ],
    "initial_file_sha256": {
      "aws_prepare.py": "98d5456e8bdf2813e11a30ab942c3995da1205031c87be8d0bf583bc515d9807",
      "test_aws.py": "b2350628a5cdfcdd0630bde1f131da4375315bf7be6209782186a4d8219944a0"
    },
    "initial_diff_sha256": "bf2bf95877e877ab49de659c8424398d5d2f40c15e4164415810aa3737687651"
  },
  "root_cause": {
    "proven": "ONE_OPERATOR_PUT_WITHOUT_EXPLICIT_SSE_HEADERS_REJECTED_BY_EXPLICIT_RESOURCE_BASED_POLICY_DENY",
    "remaining_unresolved": "MATCHING_DENY_STATEMENT_AND_ACTUAL_S3_AUTHORIZATION_CONDITION_CONTEXT_NOT_EXPOSED",
    "historical_v3": "StringNotEqualsIfExists in explicit Deny rejects absent condition keys; whether this caused each historical Transcribe rejection remains unproven",
    "v4": "Static desired truth table PASS does not prove live default-encryption authorization; actual mandatory proof FAILED",
    "verifier_repair": "Documented restrictive SSE-C metadata caused initial READ_ONLY precondition stop; repaired prospectively before the sole Put; original evidence retained"
  },
  "publications": {
    "v4_and_diagnostic_v02": "39aabea652de9b983c4f0e8d8ba0d7e55fb5bf91",
    "verifier_and_diagnostic_v03": "1a12f96456da24ac3ed90eeefb033af8c172e884",
    "both_pushed_refetched_before_respective_observations": true
  },
  "v4": {
    "deployed_status": "UPDATE_COMPLETE",
    "last_updated_utc": "2026-09-28T20:20:48.570000Z",
    "template_sha256": "805bc62223d47a448ea0d1b9437c805abfe5be97c8269f9af182202a48226ce3",
    "config_sha256": "da8ffa54edb13be5bf62e95a6f02a163a42d4c00c5622375d690732ad49df27f",
    "policy_semantics": "Allow neither-header or exact full pair for otherwise authorized caller; deny incorrect/partial pairs and SSE-C; TLS/deadline/prefix/roles/keys/retention preserved",
    "both_output_kms_principals": "Exact live identity and key policies cover Encrypt/Decrypt/GenerateDataKey; cryptographic execution through DORA target path UNPROVEN",
    "successful_preflight_publication": "39aabea652de9b983c4f0e8d8ba0d7e55fb5bf91",
    "fresh_preflight_for_last_publication": "NOT_RUN_BECAUSE_DIRECT_S3_STOP"
  },
  "synthetic_diagnostic": {
    "D1": "NOT_RUN_DIRECT_S3_STOP",
    "D2": "NOT_RUN_DIRECT_S3_STOP",
    "D3": "NOT_RUN_DIRECT_S3_STOP"
  },
  "phase_a_v03": {
    "published": false,
    "executed": false,
    "reason": "D1_TARGET_PATH_NOT_PROVEN",
    "real_recovery_start_attempts": 0,
    "v02_unchanged": true
  },
  "whole_manifest_sha256": "27ea1593a46c9c17de0a568df6537dbe55df56c4def59559d758cb9f07ecbe65",
  "corpus": {
    "clips": 8,
    "ru": {
      "READ": 2,
      "SPONTANEOUS": 2,
      "normalized_reference_words": 179,
      "duration_seconds": "150.960"
    },
    "en": {
      "READ": 4,
      "SPONTANEOUS": 0,
      "normalized_reference_words": 181,
      "duration_seconds": "129.560"
    },
    "total_words": 360,
    "total_duration_seconds": "280.520"
  },
  "quality_groups": {
    "RU_READ": {
      "reference_words": 121,
      "scored_words": 0,
      "raw_wer": "NOT_EVALUATED",
      "normalized_wer": "NOT_EVALUATED",
      "S": null,
      "D": null,
      "I": null
    },
    "RU_SPONTANEOUS": {
      "reference_words": 58,
      "scored_words": 0,
      "raw_wer": "NOT_EVALUATED",
      "normalized_wer": "NOT_EVALUATED",
      "S": null,
      "D": null,
      "I": null
    },
    "RU_TOTAL": {
      "reference_words": 179,
      "scored_words": 0,
      "raw_wer": "NOT_EVALUATED",
      "normalized_wer": "NOT_EVALUATED",
      "S": null,
      "D": null,
      "I": null
    },
    "EN_READ_TOTAL": {
      "reference_words": 181,
      "scored_words": 0,
      "raw_wer": "NOT_EVALUATED",
      "normalized_wer": "NOT_EVALUATED",
      "S": null,
      "D": null,
      "I": null
    }
  },
  "en_spontaneous": "NOT_EVALUATED",
  "noise_speakerphone": "NOT_EVALUATED",
  "other_speakers": "NOT_EVALUATED",
  "metadata_promise": "NO_WORD_LEVEL_ACCURACY_PROMISE",
  "validation": {
    "aws_suite_before_v4": 95,
    "aws_suite_after_verifier_repair": 97,
    "stage00": 7,
    "preparation_tests": 13,
    "owned_corpus_tests": 51,
    "shared_contract_tests": 26,
    "policy_truth_table": "PASS",
    "json_md_parity": "PASS",
    "git_diff_check": "PASS",
    "independent_adversarial_review": "PASS"
  },
  "cost": {
    "actual_invoice": "NOT_AVAILABLE",
    "actual_charged_usd": null,
    "new_transcribe_start_dispatches": 0,
    "new_accepted_asr_jobs": 0,
    "new_asr_audio_seconds": 0,
    "prior_prospective_total_reservation_usd": "8.4069",
    "reservation_is_not_invoice": true,
    "approved_total_cap_usd": "10",
    "approved_asr_cap_usd": "2",
    "no_free_tier_assumption": true
  },
  "owner_only_scope": {
    "admission": "NOT_ESTABLISHED",
    "population_if_later_admitted": "PROJECT_OWNER_ONLY",
    "scope_overlay": "NOT_CREATED_NO_ADMISSION",
    "future_trigger": "PRE_ADDITIONAL_INTERNAL_TESTER_PROVIDER_REVALIDATION",
    "trigger_status": "DEFERRED_MANDATORY_BEFORE_SECOND_REAL_SPEAKER",
    "external_legal_gate": "UNCHANGED_OUTSIDE_FROZEN39"
  },
  "non_execution": {
    "6.3": "NOT_RUN",
    "PR86": "NOT_TOUCHED",
    "main": "NOT_CHANGED",
    "Stage5": "NOT_TOUCHED"
  },
  "next_step": "NEW_PROSPECTIVE_S3_AUTHORIZATION_DIAGNOSTIC_VERSION_AND_FRESH_BOUND_ENVIRONMENT_REQUIRED; no retry under consumed ONE-Put protocol; 6.3 NOT_ELIGIBLE"
}
```
