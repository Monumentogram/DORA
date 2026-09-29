# Cloud 6.2D owner technical result v0.1

The synthetic smoke passed, but required technical cases did not all pass. Stage 6.2D remains **BLOCKED**; Stage 6.3 is **NOT RUN / NOT ELIGIBLE**.

Nonpassing raw technical oracles: tech-empty, tech-near_empty, tech-truncated, tech-wrong_format, tech-missing_s3, tech-duplicate_name, tech-permission_denial. The measured English result remains 37/181 (20.4420%), above the historical 18% benchmark. OD-62D-EN-01 accepts that limitation only for the owner-only closed internal Alpha; it does not satisfy the technical gate. Exact CloudShell task-content copies were independently purged after private evidence preservation.

The wrong-format WAV was marked COMPLETED and fails its frozen oracle. The truncated WAV completed without proof of bounded processed extent. The duplicate Start returned BadRequest for a missing URI, so the required ConflictException was not observed. These outcomes keep provider admission open.

The following JSON is normative and identical to the companion file.

```json
{
  "blocking_case_explanations": {
    "tech-duplicate_name": "Second same-name Start returned BadRequest for missing URI; ConflictException was not observed and original identity does not prove this oracle.",
    "tech-truncated": "COMPLETED, but bounded processed extent was not proved; raw oracle INCOMPLETE.",
    "tech-wrong_format": "COMPLETED despite frozen WAV bytes declared mp3; raw oracle FAIL."
  },
  "budget": {
    "actual_charges": "NOT_OBSERVED",
    "additional_smoke_asr_reserved_usd": "0.0015",
    "asr_ceiling_usd": "2.0000",
    "historical_total_reserved_usd": "9.4370",
    "incremental_ancillary_reserved_usd": "0.5300",
    "maximum_asr_reserved_usd": "0.5385",
    "maximum_total_reserved_usd": "9.9685",
    "total_ceiling_usd": "10.0000"
  },
  "cleanup": {
    "actual_invoiced_charges": "NOT_OBSERVED",
    "cloudshell_deleted_exact_files": 21,
    "cloudshell_task_content_purge": "PASS_EXACT_21_TASK_CONTENT_COPIES_ABSENT",
    "cmk_deletion_dates_utc": {
      "input": "2026-10-06T13:40:27.605000+00:00",
      "output": "2026-10-06T13:40:27.557000+00:00"
    },
    "operator_invocation_cap": 300,
    "operator_invocation_cap_respected": true,
    "operator_invocations": 151,
    "physical_cmk_deletion": "NOT_OBSERVED",
    "retirement_status": "EXACT_RESOURCES_ABSENT_KEYS_PENDING_DELETION",
    "task_content_absent": true,
    "task_lifetime_4h_cap_respected": true,
    "task_lifetime_seconds": 1559.449554
  },
  "diagnostic_ledger": {
    "automatic_start_retries": 0,
    "cap": 12,
    "smoke_attempt": 9,
    "technical_starts_separate": 13,
    "used_before": 8
  },
  "duplicate_second_start": {
    "conflict_exception_observed": false,
    "definite_rejection": true,
    "error_code": "BadRequestException"
  },
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
    "SATISFIED_FOR_CLOSED_INTERNAL_ALPHA": 2,
    "total": 39
  },
  "frozen_gate_count": 39,
  "full_provider_admission": "NOT_ESTABLISHED",
  "host_validation": {
    "aws_tooling_tests_passed": 133,
    "measured_quality_reruns": 0,
    "owned_tests_passed": 51,
    "preparation_tests_passed": 13,
    "provider_contract_tests_passed": 26,
    "stage00_checks_passed": 7,
    "stage00_post_document_validation": "PASS"
  },
  "independent_review_findings": {
    "tech-missing_s3": {
      "basis": "FROZEN_SOURCE_ORDER_PRESTART_HEAD_ABSENCE_START_400_DELAYED_JOB_ABSENCE",
      "raw_oracle_verdict_unchanged": "INCOMPLETE",
      "typed_outcome": "SUPPORTED_MISSING_INPUT_REJECTION"
    },
    "tech-permission_denial": {
      "basis": "EXACT_ROOT_HEAD_SSE_KMS_OPERATOR_403_START_400_DELAYED_JOB_ABSENCE",
      "raw_oracle_verdict_unchanged": "INCOMPLETE",
      "typed_outcome": "SUPPORTED_ISOLATED_ACCESS_DENIAL"
    }
  },
  "limitations": [
    "Observed technical defects remain visible; separate adjudications do not rewrite raw oracles.",
    "No provider admission or Stage 6.3 execution is inferred.",
    "No new owner quality sample, speaker, or quality score is inferred."
  ],
  "owner_only_provider_admission": "NOT_ESTABLISHED",
  "quality_history": {
    "en_read": {
      "errors": 37,
      "historical_18_percent_benchmark": "FAIL",
      "normalized_wer_percent": "20.4420",
      "owner_only_product_decision": "ACCEPTED_WITH_MEASURED_QUALITY_LIMITATION",
      "reference_words": 181
    },
    "quality_rerun": "NONE",
    "ru_total": {
      "errors": 17,
      "historical_benchmark": "PASS",
      "normalized_wer_percent": "9.4972",
      "reference_words": 179
    }
  },
  "raw_oracle_counts": {
    "FAIL": 1,
    "INCOMPLETE": 6,
    "PASS": 5
  },
  "required_technical_cases": {
    "tech-duplicate_name": {
      "cleanup_observed": true,
      "input_integrity": true,
      "latency_status": "OBSERVED_SAME_BOOT",
      "observed_status": "COMPLETED",
      "output_integrity": true,
      "raw_oracle_reason_code": null,
      "raw_oracle_verdict": "INCOMPLETE",
      "submit_to_first_terminal_seconds": 126.10648
    },
    "tech-empty": {
      "cleanup_observed": true,
      "input_integrity": true,
      "latency_status": "DIFFERENT_BOOT_OR_CLOCK_INVALID",
      "observed_status": "FAILED",
      "output_integrity": true,
      "raw_oracle_reason_code": "FAILURE_CAUSE_NOT_ATTRIBUTABLE",
      "raw_oracle_verdict": "INCOMPLETE",
      "submit_to_first_terminal_seconds": null
    },
    "tech-en_300": {
      "cleanup_observed": true,
      "input_integrity": true,
      "latency_status": "OBSERVED_SAME_BOOT",
      "observed_status": "COMPLETED",
      "output_integrity": true,
      "raw_oracle_reason_code": "READABLE_WITH_STRUCTURAL_TIMESTAMPS",
      "raw_oracle_verdict": "PASS",
      "submit_to_first_terminal_seconds": 45.084558
    },
    "tech-en_600": {
      "cleanup_observed": true,
      "input_integrity": true,
      "latency_status": "OBSERVED_SAME_BOOT",
      "observed_status": "COMPLETED",
      "output_integrity": true,
      "raw_oracle_reason_code": "READABLE_WITH_STRUCTURAL_TIMESTAMPS",
      "raw_oracle_verdict": "PASS",
      "submit_to_first_terminal_seconds": 8.984426
    },
    "tech-malformed": {
      "cleanup_observed": true,
      "input_integrity": true,
      "latency_status": "OBSERVED_SAME_BOOT",
      "observed_status": "FAILED",
      "output_integrity": true,
      "raw_oracle_reason_code": "ATTRIBUTABLE_NEGATIVE_FIXTURE_REJECTION",
      "raw_oracle_verdict": "PASS",
      "submit_to_first_terminal_seconds": 9.650082
    },
    "tech-missing_s3": {
      "cleanup_observed": true,
      "input_integrity": true,
      "latency_status": "NOT_OBSERVED",
      "observed_status": "REJECTED",
      "output_integrity": true,
      "raw_oracle_reason_code": "FAILURE_CAUSE_NOT_ATTRIBUTABLE",
      "raw_oracle_verdict": "INCOMPLETE",
      "submit_to_first_terminal_seconds": null
    },
    "tech-near_empty": {
      "cleanup_observed": true,
      "input_integrity": true,
      "latency_status": "OBSERVED_SAME_BOOT",
      "observed_status": "FAILED",
      "output_integrity": true,
      "raw_oracle_reason_code": "FAILURE_CAUSE_NOT_ATTRIBUTABLE",
      "raw_oracle_verdict": "INCOMPLETE",
      "submit_to_first_terminal_seconds": 7.895164
    },
    "tech-permission_denial": {
      "cleanup_observed": true,
      "input_integrity": true,
      "latency_status": "NOT_OBSERVED",
      "observed_status": "REJECTED",
      "output_integrity": true,
      "raw_oracle_reason_code": "FAILURE_CAUSE_NOT_ATTRIBUTABLE",
      "raw_oracle_verdict": "INCOMPLETE",
      "submit_to_first_terminal_seconds": null
    },
    "tech-ru_300": {
      "cleanup_observed": true,
      "input_integrity": true,
      "latency_status": "OBSERVED_SAME_BOOT",
      "observed_status": "COMPLETED",
      "output_integrity": true,
      "raw_oracle_reason_code": "READABLE_WITH_STRUCTURAL_TIMESTAMPS",
      "raw_oracle_verdict": "PASS",
      "submit_to_first_terminal_seconds": 76.697799
    },
    "tech-ru_600": {
      "cleanup_observed": true,
      "input_integrity": true,
      "latency_status": "OBSERVED_SAME_BOOT",
      "observed_status": "COMPLETED",
      "output_integrity": true,
      "raw_oracle_reason_code": "READABLE_WITH_STRUCTURAL_TIMESTAMPS",
      "raw_oracle_verdict": "PASS",
      "submit_to_first_terminal_seconds": 112.177147
    },
    "tech-truncated": {
      "cleanup_observed": true,
      "input_integrity": true,
      "latency_status": "OBSERVED_SAME_BOOT",
      "observed_status": "COMPLETED",
      "output_integrity": true,
      "raw_oracle_reason_code": "TRUNCATED_EXTENT_UNPROVED",
      "raw_oracle_verdict": "INCOMPLETE",
      "submit_to_first_terminal_seconds": 9.39309
    },
    "tech-wrong_format": {
      "cleanup_observed": true,
      "input_integrity": true,
      "latency_status": "OBSERVED_SAME_BOOT",
      "observed_status": "COMPLETED",
      "output_integrity": true,
      "raw_oracle_reason_code": "UNEXPECTED_COMPLETED_NEGATIVE_FIXTURE",
      "raw_oracle_verdict": "FAIL",
      "submit_to_first_terminal_seconds": 8.135739
    }
  },
  "result_identity": {
    "additional_starts": 0,
    "identity_proof_sha256": "ba3aea55cbec031ae021fac5bcf8d49e12d550ee478525883fc25400498c1e3c",
    "job_account_output_body_bound": true,
    "status": "PASS"
  },
  "schema_version": "dora-62d-owner-technical-terminal-v0.1",
  "scope": "PROJECT_OWNER_ONLY_CLOSED_INTERNAL_ALPHA",
  "scope_limitations": [
    "One Project Owner only; no second real speaker, external tester, customer audio or public release.",
    "Historical EN 18% measured benchmark remains FAIL; owner acceptance is limited to closed internal Alpha.",
    "EN spontaneous quality, noise robustness and timestamp accuracy were not evaluated for admission.",
    "Technical long composites are not new owner-quality samples or WER measurements.",
    "Before a second real speaker or material provider/configuration/recording-path change, revalidation is mandatory."
  ],
  "smoke": {
    "new_owner_quality_start": false,
    "status": "PASS"
  },
  "source_binding": {
    "cloudshell_purge_proof_sha256": "e63c5ea2bab61a75caac18df2129d1ed8d3900c9a14726168d6e3cbe07b49e8c",
    "historical_result_lf_sha256": "4f49875a542a0c955b1d08b55634874241ea6c4337d9ba676b193024ac23a2c9",
    "historical_result_sha256": "44afebc340a535af1c020388330ad51f9656cd5b29d3f1b0acac3b23da5ce704",
    "owner_decision_sha256": "44fa77be4a3cfda065b30e0d14905745ee048a52767ee4b6558d3b78fde3dffa",
    "post_execution_host_tests_sha256": "a7cc763bd8d34050ec682c75ded8bba4a925e90756dcfe460d74ed455dcf4536",
    "result_identity_proof_sha256": "ba3aea55cbec031ae021fac5bcf8d49e12d550ee478525883fc25400498c1e3c",
    "smoke_ledger_intent_sha256": "ec91a2e3aa28f0d48349ef252fbd29c30aa8419173a8ff57838a85d27370bf2f",
    "smoke_ledger_result_sha256": "a3f6036cd2046a73e695ec0e2e5008486ad9833237e9b382b6fe1818f8885246",
    "stage00_observation_sha256": "57f3f361a9657b7bc019f7c830add056a8cbe939a430bb58439ba46110767552",
    "technical_adjudication_sha256": "12adfd67420ff6f672f89f583315b8640b003768a0c808757564c7bcf03d7fc8",
    "technical_budget_proof_sha256": "205c8014affaff16ca70bd6213508679ae5eee912a6763e0bf8ecc592df725ec",
    "technical_bundle_sha256": "2a8239a7b304ad1a482a56afad2b5913d3346d76d2833a2ed30039d636a17940",
    "technical_bundle_source_sha256": "ea251528953cb95cd145bacc10afec3425fd29b59b6ce7953195c989e0d4db39",
    "technical_catalog_sha256": "d2cb8201f026e66a8356922f8973f173fed878f0a15c8c1f0fc6760af40efad2",
    "technical_config_sha256": "99f9daac98f990e02892e739047d2719b4e64f57eb9defa22aa35d32f835bcd3",
    "technical_private_summary_sha256": "bdd26d65f8b35022a986503e3fa83d8d6695b234ddb5549d3fad6a8351d70470",
    "technical_protocol_sha256": "6f15e3a60fa29497264930b2962b8febb550a1ab29a236617d71ad80a180cc71",
    "technical_replay_archive_sha256": "1b0000de804903e0090ef4f8fa9e8aa2d3a56ebd81af9ccac664983a13180f90",
    "technical_replay_manifest_sha256": "c0662454073029cf7568fb30e968e25bc7524c354aef1dd4730f59ce558513a1",
    "technical_retirement_proof_sha256": "78a2ad3d3e7d59005ace441b06bb4988861c005982a3c4d78fb394cc1a5c8713"
  },
  "stage_6_2D": "BLOCKED",
  "stage_6_2D_reason": "REQUIRED_TECHNICAL_CASES_NOT_SATISFIED",
  "stage_6_3": "NOT_RUN / NOT_ELIGIBLE",
  "target_configuration": {
    "DataAccessRoleArn": "ABSENT",
    "JobExecutionSettings": "ABSENT",
    "automatic_start_retry": "NONE",
    "input": "PRIVATE_S3_EXACT_INPUT_CMK",
    "languages": [
      "ru-RU",
      "en-US"
    ],
    "output": "PRIVATE_S3_EXPLICIT_OUTPUT_ENCRYPTION_EXACT_OUTPUT_CMK",
    "provider": "Amazon Transcribe STANDARD_BATCH_FILE_ASR",
    "region": "eu-central-1",
    "region_fallback": "NONE"
  },
  "technical_scope": {
    "four_long_composites": "PASS_AS_TECHNICAL_INPUTS_ONLY",
    "owner_primary_starts": 0,
    "replaceability_provider_contract": "26_OF_26_PASS",
    "throttle": "DOCUMENTED_NOT_INDUCED"
  },
  "technical_suite_verdict": "NOT_PASS",
  "typed_minimum_adjudications_separate_from_raw_oracles": [
    {
      "adjudicated_verdict": "PASS",
      "basis": "EXACT_TYPED_MINIMUM_DURATION_0_5_SECONDS",
      "case_id": "tech-empty",
      "raw_oracle_verdict": "INCOMPLETE"
    },
    {
      "adjudicated_verdict": "PASS",
      "basis": "EXACT_TYPED_MINIMUM_DURATION_0_5_SECONDS",
      "case_id": "tech-near_empty",
      "raw_oracle_verdict": "INCOMPLETE"
    }
  ]
}
```
