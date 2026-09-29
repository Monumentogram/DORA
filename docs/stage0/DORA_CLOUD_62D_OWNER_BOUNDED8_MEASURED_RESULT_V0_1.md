# Owner eight-clip measurement: English quality failed

Outcome: **FAIL**. Eight owner primary jobs completed and were measured; technical evaluation was not run because the frozen composite recipe hash check failed before Put.
Stage 6.2D: BLOCKED / MEASURED_PROVIDER_QUALITY_FAIL.
Owner-only and full provider admission: NOT_ESTABLISHED. Stage 6.3: NOT_RUN / NOT_ELIGIBLE.

Quality uses RU total 20% and EN read 18% thresholds. RU class micros are descriptive.

| Group | Raw S/D/I/N | Normalized S/D/I/N | Normalized WER | Role |
| --- | --- | --- | --- | --- |
| RU_READ | 15/0/0/121 | 7/0/0/121 | 5.7851% | DESCRIPTIVE_CLASS_MICRO |
| RU_SPONTANEOUS | 26/0/0/58 | 10/0/0/58 | 17.2414% | DESCRIPTIVE_CLASS_MICRO |
| RU_TOTAL | 41/0/0/179 | 17/0/0/179 | 9.4972% | LANGUAGE_THRESHOLD |
| EN_READ | 21/4/18/181 | 15/1/21/181 | 20.4420% | LANGUAGE_THRESHOLD |

Latency is a four-clip-per-language small-sample diagnostic.

| Language | p50 seconds | p95 seconds | p50 ratio | p95 ratio | Bounded |
| --- | ---: | ---: | ---: | ---: | --- |
| RU_TOTAL | 11.361393 | 35.250682 | 5715663/19460000 | 17625341/21350000 | PASS |
| EN_READ | 2.579270 | 11.403957 | 2575991/34480000 | 11403957/26920000 | PASS |

Eight owner clips (counts exclude transcript text):

| Case | Duration s | Turnaround s | Raw S/D/I/N | Raw WER | Normalized S/D/I/N | Normalized WER |
| --- | ---: | ---: | --- | ---: | --- | ---: |
| ru-read-01 | 38.920000 | 11.431326 | 5/0/0/57 | 8.7719% | 2/0/0/57 | 3.5088% |
| ru-read-02 | 42.700000 | 35.250682 | 10/0/0/64 | 15.6250% | 5/0/0/64 | 7.8125% |
| ru-spontaneous-01 | 34.900000 | 9.097038 | 10/0/0/30 | 33.3333% | 3/0/0/30 | 10.0000% |
| ru-spontaneous-02 | 34.440000 | 11.361393 | 16/0/0/28 | 57.1429% | 7/0/0/28 | 25.0000% |
| en-read-01 | 26.920000 | 11.403957 | 3/1/0/46 | 8.6957% | 3/0/0/46 | 6.5217% |
| en-read-02 | 34.480000 | 2.575991 | 4/2/6/45 | 26.6667% | 3/1/6/45 | 22.2222% |
| en-read-03 | 35.260000 | 2.579270 | 6/0/11/45 | 37.7778% | 4/0/12/45 | 35.5556% |
| en-read-04 | 32.900000 | 2.608192 | 8/1/1/45 | 22.2222% | 5/0/3/45 | 17.7778% |

Technical starts: **0**. No technical fixture outcome is claimed.
Timestamp structure was checked; timestamp accuracy was not evaluated.
Target data and jobs were removed; the task stack was deleted and both CMKs are pending deletion.
Nine CloudShell WAV transport copies and eleven CloudShell output/evidence-transfer copies were removed after private evidence was preserved locally.
Actual AWS charges are not measured; the reservation is not an invoice.
Blockers: EN_READ_NORMALIZED_WER_EXCEEDS_18_PERCENT, TECHNICAL_EVALUATION_BLOCKED_HARNESS_DEFECT.

The JSON below is the normative record for this Markdown rendering.

```json
{
  "attempt_accounting": {
    "automatic_retries": 0,
    "historical_failed_primary_starts": 4,
    "new_primary_starts": 8,
    "new_technical_starts": 0,
    "prior_diagnostic_starts": 8
  },
  "blockers": [
    "EN_READ_NORMALIZED_WER_EXCEEDS_18_PERCENT",
    "TECHNICAL_EVALUATION_BLOCKED_HARNESS_DEFECT"
  ],
  "bounded8": "FAIL",
  "broad_provider_admission": "NOT_ESTABLISHED",
  "budget": {
    "actual_charges": "NOT_MEASURED",
    "reservation_is_not_invoice": true,
    "reserved_asr_usd": "0.5370000000",
    "reserved_total_usd": "9.4370000000"
  },
  "cleanup": {
    "all_task_stack_resources_delete_complete": 13,
    "cloudshell_output_copies_absent": 11,
    "cloudshell_task_copies_absent": 9,
    "customer_keys_pending_deletion": true,
    "physical_key_deletion": "PENDING_NOT_CLAIMED",
    "stack_deleted": true,
    "task_roles_functions_buckets_absent": true,
    "whole_target_data_and_jobs_empty": true
  },
  "config_sha256": "7f16bc11e74961fa89106a8e65a11397748f41a472c09ba1a7f921c227814f17",
  "effective_gate_statuses": {
    "CLD-ADM-ADAPTER-001": "NOT_RUN",
    "CLD-ADM-ADMISSION-001": "BLOCKED",
    "CLD-ADM-API-001": "OPEN",
    "CLD-ADM-ARCH-001": "SATISFIED",
    "CLD-ADM-AUTH-001": "NOT_RUN",
    "CLD-ADM-BACKGROUND-001": "NOT_RUN",
    "CLD-ADM-CONSENT-001": "SATISFIED",
    "CLD-ADM-CONSENT-RUNTIME-001": "NOT_RUN",
    "CLD-ADM-CONTROL-001": "SATISFIED_FOR_CLOSED_INTERNAL_ALPHA",
    "CLD-ADM-COST-001": "NOT_RUN",
    "CLD-ADM-CRYPTO-001": "NOT_RUN",
    "CLD-ADM-DATA-001": "SATISFIED",
    "CLD-ADM-DATA-RUNTIME-001": "NOT_RUN",
    "CLD-ADM-DELETE-001": "NOT_RUN",
    "CLD-ADM-EVALUATION-001": "OPEN",
    "CLD-ADM-EXIT-001": "NOT_RUN",
    "CLD-ADM-EXPORT-001": "NOT_RUN",
    "CLD-ADM-FAILURE-001": "NOT_RUN",
    "CLD-ADM-GAPS-001": "SATISFIED",
    "CLD-ADM-HARNESS-001": "NOT_RUN",
    "CLD-ADM-HISTORY-001": "NOT_RUN",
    "CLD-ADM-LOCAL-001": "NOT_RUN",
    "CLD-ADM-MERGE-001": "NOT_RUN",
    "CLD-ADM-OBSERVABILITY-001": "NOT_RUN",
    "CLD-ADM-OFFLINE-001": "NOT_RUN",
    "CLD-ADM-OPERATIONS-001": "NOT_RUN",
    "CLD-ADM-PRIVACY-001": "SATISFIED_FOR_CLOSED_INTERNAL_ALPHA",
    "CLD-ADM-PROVIDER-001": "OPEN",
    "CLD-ADM-QUEUE-001": "NOT_RUN",
    "CLD-ADM-RELEASE-001": "OPEN",
    "CLD-ADM-RESULT-001": "NOT_RUN",
    "CLD-ADM-RETENTION-001": "SATISFIED",
    "CLD-ADM-RETENTION-RUNTIME-001": "NOT_RUN",
    "CLD-ADM-SCOPE-001": "SATISFIED",
    "CLD-ADM-SECRETS-001": "NOT_RUN",
    "CLD-ADM-SECURITY-001": "NOT_RUN",
    "CLD-ADM-SUPPLY-001": "OPEN",
    "CLD-ADM-UPLOAD-001": "NOT_RUN",
    "CLD-ADM-UX-001": "NOT_RUN"
  },
  "effective_status_counts": {
    "BLOCKED": 1,
    "NOT_RUN": 25,
    "OPEN": 5,
    "SATISFIED": 6,
    "SATISFIED_FOR_CLOSED_INTERNAL_ALPHA": 2
  },
  "failure_domain": "MEASURED_QUALITY",
  "failure_domains": [
    "MEASURED_QUALITY",
    "HARNESS_DEFECT"
  ],
  "frozen_gate_count": 39,
  "latency_inference": "SMALL_SAMPLE_DESCRIPTIVE_ONLY",
  "manifest_sha256": "27ea1593a46c9c17de0a568df6537dbe55df56c4def59559d758cb9f07ecbe65",
  "non_evaluated": [
    "TECHNICAL_FIXTURES",
    "EN_SPONTANEOUS",
    "NOISE_SPEAKERPHONE",
    "ARBITRARY_SPEAKERS",
    "TIMESTAMP_ACCURACY"
  ],
  "owner_only_provider_admission": "NOT_ESTABLISHED",
  "primary_records": [
    {
      "case_id": "ru-read-01",
      "duration_seconds": "38.920000",
      "language": "ru",
      "normalized": {
        "D": 0,
        "I": 0,
        "N": 57,
        "S": 2,
        "errors": 2,
        "wer_percent": "3.5088"
      },
      "raw": {
        "D": 0,
        "I": 0,
        "N": 57,
        "S": 5,
        "errors": 5,
        "wer_percent": "8.7719"
      },
      "service_turnaround_ratio": "5715663/19460000",
      "service_turnaround_seconds": "11.431326",
      "speech_class": "READ",
      "timestamp_structure": {
        "malformed": 0,
        "missing": 0,
        "non_monotonic": 0,
        "pronunciation_items": 57
      }
    },
    {
      "case_id": "ru-read-02",
      "duration_seconds": "42.700000",
      "language": "ru",
      "normalized": {
        "D": 0,
        "I": 0,
        "N": 64,
        "S": 5,
        "errors": 5,
        "wer_percent": "7.8125"
      },
      "raw": {
        "D": 0,
        "I": 0,
        "N": 64,
        "S": 10,
        "errors": 10,
        "wer_percent": "15.6250"
      },
      "service_turnaround_ratio": "17625341/21350000",
      "service_turnaround_seconds": "35.250682",
      "speech_class": "READ",
      "timestamp_structure": {
        "malformed": 0,
        "missing": 0,
        "non_monotonic": 0,
        "pronunciation_items": 64
      }
    },
    {
      "case_id": "ru-spontaneous-01",
      "duration_seconds": "34.900000",
      "language": "ru",
      "normalized": {
        "D": 0,
        "I": 0,
        "N": 30,
        "S": 3,
        "errors": 3,
        "wer_percent": "10.0000"
      },
      "raw": {
        "D": 0,
        "I": 0,
        "N": 30,
        "S": 10,
        "errors": 10,
        "wer_percent": "33.3333"
      },
      "service_turnaround_ratio": "4548519/17450000",
      "service_turnaround_seconds": "9.097038",
      "speech_class": "SPONTANEOUS",
      "timestamp_structure": {
        "malformed": 0,
        "missing": 0,
        "non_monotonic": 0,
        "pronunciation_items": 30
      }
    },
    {
      "case_id": "ru-spontaneous-02",
      "duration_seconds": "34.440000",
      "language": "ru",
      "normalized": {
        "D": 0,
        "I": 0,
        "N": 28,
        "S": 7,
        "errors": 7,
        "wer_percent": "25.0000"
      },
      "raw": {
        "D": 0,
        "I": 0,
        "N": 28,
        "S": 16,
        "errors": 16,
        "wer_percent": "57.1429"
      },
      "service_turnaround_ratio": "3787131/11480000",
      "service_turnaround_seconds": "11.361393",
      "speech_class": "SPONTANEOUS",
      "timestamp_structure": {
        "malformed": 0,
        "missing": 0,
        "non_monotonic": 0,
        "pronunciation_items": 28
      }
    },
    {
      "case_id": "en-read-01",
      "duration_seconds": "26.920000",
      "language": "en",
      "normalized": {
        "D": 0,
        "I": 0,
        "N": 46,
        "S": 3,
        "errors": 3,
        "wer_percent": "6.5217"
      },
      "raw": {
        "D": 1,
        "I": 0,
        "N": 46,
        "S": 3,
        "errors": 4,
        "wer_percent": "8.6957"
      },
      "service_turnaround_ratio": "11403957/26920000",
      "service_turnaround_seconds": "11.403957",
      "speech_class": "READ",
      "timestamp_structure": {
        "malformed": 0,
        "missing": 0,
        "non_monotonic": 0,
        "pronunciation_items": 45
      }
    },
    {
      "case_id": "en-read-02",
      "duration_seconds": "34.480000",
      "language": "en",
      "normalized": {
        "D": 1,
        "I": 6,
        "N": 45,
        "S": 3,
        "errors": 10,
        "wer_percent": "22.2222"
      },
      "raw": {
        "D": 2,
        "I": 6,
        "N": 45,
        "S": 4,
        "errors": 12,
        "wer_percent": "26.6667"
      },
      "service_turnaround_ratio": "2575991/34480000",
      "service_turnaround_seconds": "2.575991",
      "speech_class": "READ",
      "timestamp_structure": {
        "malformed": 0,
        "missing": 0,
        "non_monotonic": 0,
        "pronunciation_items": 49
      }
    },
    {
      "case_id": "en-read-03",
      "duration_seconds": "35.260000",
      "language": "en",
      "normalized": {
        "D": 0,
        "I": 12,
        "N": 45,
        "S": 4,
        "errors": 16,
        "wer_percent": "35.5556"
      },
      "raw": {
        "D": 0,
        "I": 11,
        "N": 45,
        "S": 6,
        "errors": 17,
        "wer_percent": "37.7778"
      },
      "service_turnaround_ratio": "257927/3526000",
      "service_turnaround_seconds": "2.579270",
      "speech_class": "READ",
      "timestamp_structure": {
        "malformed": 0,
        "missing": 0,
        "non_monotonic": 0,
        "pronunciation_items": 56
      }
    },
    {
      "case_id": "en-read-04",
      "duration_seconds": "32.900000",
      "language": "en",
      "normalized": {
        "D": 0,
        "I": 3,
        "N": 45,
        "S": 5,
        "errors": 8,
        "wer_percent": "17.7778"
      },
      "raw": {
        "D": 1,
        "I": 1,
        "N": 45,
        "S": 8,
        "errors": 10,
        "wer_percent": "22.2222"
      },
      "service_turnaround_ratio": "81506/1028125",
      "service_turnaround_seconds": "2.608192",
      "speech_class": "READ",
      "timestamp_structure": {
        "malformed": 0,
        "missing": 0,
        "non_monotonic": 0,
        "pronunciation_items": 45
      }
    }
  ],
  "private_evidence_sha256": {
    "abort_cleanup": "23a8515f961c43a3353195597dae9ceca6d2cb96bdba52d4ad31ac3958701940",
    "abort_intent": "2facaaf59039b879a0592226283229a9277f4430e4f6412b9d80e2b04d9a47fa",
    "b_summary": "2f8c0a09d0fcaca3dde44b1c13699ca60e7d4c8956a33ee5fab0a6c0dbc50c68",
    "c_summary": "dc161b0240623deaa3d56e6a90fd681281c86bb6d629ec49910eff58b820ac55",
    "copy_purge_proof": "b5e22e2acb8fd591fe5b6af9c5bb1cf1bb6240f3b3f1f3a8f870348d5e717681",
    "independent_score_proof": "0b4237849415c612996a18d03cc038a8da6b9e8c595db49fb7ff98caa4b572ff",
    "offline_recipe_proof": "ba8d6fcb0d58caf90a08a25a2cad9887daae4ae5b4f9cc932c96225075b6a8d5",
    "output_copy_purge_proof": "a9f002a684f00e5ee91623766a56f4babed0434e97ba97ba4b601d7364049a27",
    "preserved_private_export": "e0bebc00f84f548662f8e60124a0b8524af8a6c7ba49826e2399270b0797fff2",
    "retirement_intent": "54e3bc74fc6929099a884bcee5be78122c9d6fc1682a8dc579a5f3016399f576",
    "retirement_proof": "0b15adce5056b0d44357fd824d599e7bd7b6498e2e3c07902e8c7fa91cb87ba7",
    "retirement_supplement": "b22c461f10d5d17a88f976ae55ca82c46e62d74f3166f5904daa536522230605",
    "score": "84f09b18cb8fb92c1b8fb7c92fe159ddae0422a1bb6e430c57bd446b8cc2f4b1"
  },
  "provider_admission": "NOT_ESTABLISHED",
  "public_privacy": "No audio, references, transcript text, per-record content hashes, account/resource/job identifiers or raw provider errors.",
  "publication": {
    "phase_a_commit": "0c865f5822cc5e76b60b1da067f30657de3b923f",
    "phase_a_sha256": "f5a498c7e77bfd21c070257c834d53accffdb3460f813ab2388123a8bcde2e97",
    "zero_resumed_owner_audio_before_publication": true
  },
  "quality_groups": {
    "EN_READ": {
      "normalized": {
        "D": 1,
        "I": 21,
        "N": 181,
        "S": 15,
        "errors": 37,
        "wer_percent": "20.4420"
      },
      "normalized_threshold_pass": false,
      "normalized_threshold_percent": 18,
      "quality_scope": "LANGUAGE_THRESHOLD",
      "raw": {
        "D": 4,
        "I": 18,
        "N": 181,
        "S": 21,
        "errors": 43,
        "wer_percent": "23.7569"
      },
      "records": 4,
      "service_latency": {
        "bounded_diagnostic": "PASS",
        "n": 4,
        "p50_ratio": "2575991/34480000",
        "p50_seconds": "2.579270",
        "p95_ratio": "11403957/26920000",
        "p95_seconds": "11.403957",
        "sample_status": "SMALL_SAMPLE_DESCRIPTIVE_ONLY"
      }
    },
    "RU_READ": {
      "normalized": {
        "D": 0,
        "I": 0,
        "N": 121,
        "S": 7,
        "errors": 7,
        "wer_percent": "5.7851"
      },
      "quality_scope": "DESCRIPTIVE_CLASS_MICRO",
      "raw": {
        "D": 0,
        "I": 0,
        "N": 121,
        "S": 15,
        "errors": 15,
        "wer_percent": "12.3967"
      },
      "records": 2
    },
    "RU_SPONTANEOUS": {
      "normalized": {
        "D": 0,
        "I": 0,
        "N": 58,
        "S": 10,
        "errors": 10,
        "wer_percent": "17.2414"
      },
      "quality_scope": "DESCRIPTIVE_CLASS_MICRO",
      "raw": {
        "D": 0,
        "I": 0,
        "N": 58,
        "S": 26,
        "errors": 26,
        "wer_percent": "44.8276"
      },
      "records": 2
    },
    "RU_TOTAL": {
      "normalized": {
        "D": 0,
        "I": 0,
        "N": 179,
        "S": 17,
        "errors": 17,
        "wer_percent": "9.4972"
      },
      "normalized_threshold_pass": true,
      "normalized_threshold_percent": 20,
      "quality_scope": "LANGUAGE_THRESHOLD",
      "raw": {
        "D": 0,
        "I": 0,
        "N": 179,
        "S": 41,
        "errors": 41,
        "wer_percent": "22.9050"
      },
      "records": 4,
      "service_latency": {
        "bounded_diagnostic": "PASS",
        "n": 4,
        "p50_ratio": "5715663/19460000",
        "p50_seconds": "11.361393",
        "p95_ratio": "17625341/21350000",
        "p95_seconds": "35.250682",
        "sample_status": "SMALL_SAMPLE_DESCRIPTIVE_ONLY"
      }
    }
  },
  "schema_version": "dora-62d-owner-bounded8-abort-v1",
  "scope": "PROJECT_OWNER_ONLY_BOUNDED8",
  "stage_6_2D": "BLOCKED",
  "stage_6_2D_reason": "MEASURED_PROVIDER_QUALITY_FAIL",
  "stage_6_3": "NOT_RUN / NOT_ELIGIBLE",
  "technical_cases": [
    {
      "case_id": "tech-empty",
      "status": "NOT_RUN_HARNESS_DEFECT"
    },
    {
      "case_id": "tech-near_empty",
      "status": "NOT_RUN_HARNESS_DEFECT"
    },
    {
      "case_id": "tech-malformed",
      "status": "NOT_RUN_HARNESS_DEFECT"
    },
    {
      "case_id": "tech-truncated",
      "status": "NOT_RUN_HARNESS_DEFECT"
    },
    {
      "case_id": "tech-wrong_format",
      "status": "NOT_RUN_HARNESS_DEFECT"
    },
    {
      "case_id": "tech-missing_s3",
      "status": "NOT_RUN_HARNESS_DEFECT"
    },
    {
      "case_id": "tech-duplicate_name",
      "status": "NOT_RUN_HARNESS_DEFECT"
    },
    {
      "case_id": "tech-permission_denial",
      "status": "NOT_RUN_HARNESS_DEFECT"
    },
    {
      "case_id": "tech-ru_300",
      "status": "NOT_RUN_HARNESS_DEFECT"
    },
    {
      "case_id": "tech-ru_600",
      "status": "NOT_RUN_HARNESS_DEFECT"
    },
    {
      "case_id": "tech-en_300",
      "status": "NOT_RUN_HARNESS_DEFECT"
    },
    {
      "case_id": "tech-en_600",
      "status": "NOT_RUN_HARNESS_DEFECT"
    }
  ],
  "technical_evaluation": "INCOMPLETE_HARNESS_DEFECT",
  "timestamp_accuracy": "NOT_EVALUATED",
  "timestamp_structure": {
    "malformed": 0,
    "missing": 0,
    "non_monotonic": 0,
    "pronunciation_items": 374
  }
}
```
