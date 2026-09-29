# OD-62D-EN-01 — Owner acceptance of measured English quality for owner-only Alpha

**Status: APPROVED_BY_PROJECT_OWNER. Scope: OWNER_ONLY_CLOSED_INTERNAL_ALPHA, with the Project Owner as the sole human speaker and user.** This is an additive product decision and gate overlay. It does not amend the published Phase A v0.3 protocol, the eight-clip measurement, or the frozen 18% English benchmark.

The measured RU total normalized WER is **17/179 = 9.4972%**, a measured PASS against 20%. The measured EN READ normalized WER is **37/181 = 20.4420%**, comprising S=15, D=1 and I=21. It **failed** the historical 18% benchmark, which allowed at most 32 errors. `EN_MEASURED_BENCHMARK_RESULT = FAIL / 20.4420% > 18%` remains the historical evidence. Separately, the Project Owner records `EN_OWNER_ONLY_ALPHA_PRODUCT_ACCEPTANCE = ACCEPTED_WITH_MEASURED_QUALITY_LIMITATION`. English is never relabeled `MEASURED_PASS`.

For the first executable closed internal Alpha with only the Project Owner, this explicit acceptance can remove the *blocking effect* of the unmet English benchmark, but only after the remaining target integration, technical, integrity, privacy, security, retention, cleanup, cost and replacement-contract requirements pass. It does not satisfy a gate by itself. Today `CLD-ADM-EVALUATION-001` and `CLD-ADM-PROVIDER-001` remain **OPEN**, 6.2D remains **BLOCKED**, and 6.3 is **NOT_RUN / NOT_ELIGIBLE**. The frozen 39-gate statuses remain 6 SATISFIED, 2 scoped SATISFIED, 0 PARTIALLY_SATISFIED, 5 OPEN, 1 BLOCKED and 25 NOT_RUN.

If, and only if, every owner-only prerequisite passes in a later terminal result, the evaluation and provider gates may use `SATISFIED_FOR_OWNER_ONLY_CLOSED_INTERNAL_ALPHA_WITH_ACCEPTED_EN_LIMITATION`. The exact Amazon Transcribe `STANDARD_BATCH_FILE_ASR` configuration in `eu-central-1`, with `ru-RU` and `en-US`, private S3 input/Input CMK, private S3 output/explicit Output CMK, and no `JobExecutionSettings` or `DataAccessRoleArn`, may then be recorded as `ADMITTED_FOR_OWNER_ONLY_CLOSED_INTERNAL_ALPHA_WITH_EN_QUALITY_LIMITATION`. That would support `6.2D = PASS / OWNER_ONLY_CLOSED_INTERNAL_ALPHA_PROVIDER_ADMITTED_WITH_EN_QUALITY_LIMITATION` and make 6.3 **ELIGIBLE_TO_START / NEXT**. It would not execute 6.3 or satisfy the broad admission gate.

The current technical suite is **NOT_RUN**. The published measurement retains eight completed owner primary jobs and zero technical Starts: a frozen Lambda/manifest recipe-hash disagreement on terminal LF stopped composite generation before Put. That separate harness defect must be repaired, reviewed and published before technical execution; no invalid-input, long-composite or result-identity case is treated as passed. This decision authorizes no en-GB trial, en-US or RU quality rerun, new locale, custom vocabulary/model, reference-aware or LLM correction, transcript cleanup, rerecording or reference edit.

`PRE_ADDITIONAL_INTERNAL_TESTER_PROVIDER_REVALIDATION = MANDATORY_BEFORE_SECOND_REAL_SPEAKER`. Before another speaker/user, reconsider RU and EN quality on additional speakers, acceptance of 20.4420% EN, English spontaneous quality if in scope, and any material recording-path change. Revalidation is also required before external/non-internal testers, customer audio, public beta/release, provider or region change, or material ASR configuration change. EN spontaneous quality, noise robustness and timestamp accuracy remain **NOT_EVALUATED**; arbitrary-speaker and external/customer/public use are not admitted.

The JSON below is the normative machine record for this decision and overlay.

```json
{
  "schema_version": "dora-cloud-62d-en-owner-acceptance-scope-overlay-v0.1",
  "decision": {
    "id": "OD-62D-EN-01",
    "title": "OWNER ACCEPTANCE OF MEASURED EN QUALITY FOR OWNER-ONLY CLOSED INTERNAL ALPHA",
    "status": "APPROVED_BY_PROJECT_OWNER",
    "authority": "Project Owner explicit instruction, 2026-09-29",
    "scope": "OWNER_ONLY_CLOSED_INTERNAL_ALPHA",
    "sole_human_speaker_user": "PROJECT_OWNER",
    "effect": "The Project Owner accepts the exact measured en-US limitation for this scope only; this is not a measured benchmark pass or provider admission by itself."
  },
  "source_binding": {
    "required_baseline_commit": "ecaaef9011aaa310959e2b41d8790b6d09ae8a1c",
    "historical_result_path": "docs/evidence/cloud-6.2d-owner-bounded8-result-v0.1.json",
    "historical_result_sha256": "4f49875a542a0c955b1d08b55634874241ea6c4337d9ba676b193024ac23a2c9",
    "prospective_phase_a_path": "docs/contracts/DORA_CLOUD_EVALUATION_PHASE_A_BOUNDED8_V0_3.json",
    "prospective_phase_a_sha256": "f5a498c7e77bfd21c070257c834d53accffdb3460f813ab2388123a8bcde2e97",
    "owner_manifest_sha256": "27ea1593a46c9c17de0a568df6537dbe55df56c4def59559d758cb9f07ecbe65",
    "target_config_sha256": "7f16bc11e74961fa89106a8e65a11397748f41a472c09ba1a7f921c227814f17"
  },
  "measured_quality_truth": {
    "ru_total": {
      "language_code": "ru-RU",
      "substitutions": 17,
      "deletions": 0,
      "insertions": 0,
      "reference_words": 179,
      "normalized_wer_percent": "9.4972",
      "historical_threshold_percent": "20",
      "status": "PASS_MEASURED"
    },
    "en_read": {
      "language_code": "en-US",
      "substitutions": 15,
      "deletions": 1,
      "insertions": 21,
      "errors": 37,
      "reference_words": 181,
      "normalized_wer_percent": "20.4420",
      "historical_threshold_percent": "18",
      "maximum_historical_passing_errors": 32,
      "EN_MEASURED_BENCHMARK_RESULT": "FAIL",
      "comparison": "20.4420% > 18%; HISTORICAL_REFERENCE / NOT_MET",
      "EN_OWNER_ONLY_ALPHA_PRODUCT_ACCEPTANCE": "ACCEPTED_WITH_MEASURED_QUALITY_LIMITATION",
      "EN_QUALITY": "OWNER_ACCEPTED_WITH_MEASURED_LIMITATION"
    }
  },
  "scope_overlay": {
    "purpose": "Only for the first executable Project-Owner-only closed internal Alpha, explicit owner product acceptance removes the historical EN benchmark's blocking effect after every other applicable prerequisite passes.",
    "historical_en_18_percent_criterion": "IMMUTABLE_HISTORICAL_REFERENCE_NOT_MET",
    "historical_measured_result": "UNCHANGED_FAIL",
    "not_a_retroactive_threshold_change": true,
    "not_a_measured_english_pass": true,
    "owner_acceptance_alone_closes_no_gate": true,
    "required_before_owner_only_provider_admission": [
      "RU measured 9.4972% remains PASS against its existing 20% threshold",
      "EN measured 20.4420% and 37/181 remain visible as FAIL against historical 18%",
      "OD-62D-EN-01 remains explicitly applicable to Project Owner only",
      "Known-working target integration and exact configuration remain reproducible",
      "All required technical cases pass with no critical reliability, security or integrity defect",
      "Privacy, security, retention and cleanup controls remain satisfied",
      "Approved cost ceilings remain respected",
      "AWS adapter, NonAWSFake and normalized provider contract remain replaceable and pass",
      "No other measured blocking defect exists"
    ],
    "current_gates": {
      "CLD-ADM-EVALUATION-001": "OPEN",
      "CLD-ADM-PROVIDER-001": "OPEN",
      "CLD-ADM-ADMISSION-001": "BLOCKED",
      "stage_6_2D": "BLOCKED",
      "stage_6_3": "NOT_RUN / NOT_ELIGIBLE"
    },
    "conditional_future_gates_only_if_all_prerequisites_pass": {
      "CLD-ADM-EVALUATION-001": "SATISFIED_FOR_OWNER_ONLY_CLOSED_INTERNAL_ALPHA_WITH_ACCEPTED_EN_LIMITATION",
      "CLD-ADM-PROVIDER-001": "SATISFIED_FOR_OWNER_ONLY_CLOSED_INTERNAL_ALPHA_WITH_ACCEPTED_EN_LIMITATION",
      "provider_configuration": "ADMITTED_FOR_OWNER_ONLY_CLOSED_INTERNAL_ALPHA_WITH_EN_QUALITY_LIMITATION",
      "stage_6_2D": "PASS / OWNER_ONLY_CLOSED_INTERNAL_ALPHA_PROVIDER_ADMITTED_WITH_EN_QUALITY_LIMITATION",
      "stage_6_3": "ELIGIBLE_TO_START / NEXT; EXECUTION_NOT_RUN",
      "CLD-ADM-ADMISSION-001": "BLOCKED_UNTIL_SEPARATE_6_3_EXECUTION"
    },
    "exact_target_configuration": {
      "provider": "AWS Amazon Transcribe",
      "mode": "STANDARD_BATCH_FILE_ASR",
      "region": "eu-central-1",
      "language_codes": ["ru-RU", "en-US"],
      "input": "private S3 with Input CMK",
      "output": "private S3 with explicit OutputEncryptionKMSKeyId and exact Output CMK",
      "JobExecutionSettings": "ABSENT",
      "DataAccessRoleArn": "ABSENT"
    }
  },
  "future_revalidation": {
    "gate_id": "PRE_ADDITIONAL_INTERNAL_TESTER_PROVIDER_REVALIDATION",
    "status": "MANDATORY_BEFORE_SECOND_REAL_SPEAKER",
    "before_second_real_speaker_must_reconsider": [
      "RU and EN quality on additional speaker(s)",
      "whether measured EN 20.4420% remains acceptable",
      "EN spontaneous quality if it enters scope",
      "any material recording-path change"
    ],
    "also_required_before": [
      "external or non-internal tester",
      "customer audio",
      "public beta or public release",
      "provider change",
      "region change",
      "material ASR configuration change"
    ]
  },
  "current_unfinished_work": {
    "technical_evaluation": "INCOMPLETE_HARNESS_DEFECT",
    "technical_start_count": 0,
    "required_technical_cases": [
      "EMPTY", "NEAR_EMPTY", "MALFORMED", "WRONG_MEDIA_FORMAT",
      "TRUNCATED_AUDIO", "MISSING_INPUT", "PERMISSION_DENIAL",
      "DUPLICATE_OR_CONFLICTING_JOB_IDENTITY", "RU_300", "RU_600",
      "EN_300", "EN_600", "RESULT_IDENTITY"
    ],
    "technical_cases_status": "NOT_RUN",
    "composite_harness_defect": "Canonical persisted recipe bytes include terminal LF; frozen Lambda compared a no-LF serialization. Repair and publish before new technical AWS Starts.",
    "quality_tuning": "NOT_AUTHORIZED_OR_REQUIRED_IN_THIS_TASK"
  },
  "non_claims_and_prohibitions": [
    "EN did not meet the historical 18% benchmark",
    "No English measured PASS is asserted",
    "No external, customer, public, second-speaker or arbitrary-speaker admission",
    "EN spontaneous quality, noise robustness and timestamp accuracy remain NOT_EVALUATED",
    "No en-GB or other English-locale trial; no en-US or RU quality rerun",
    "No rerecording, reference edit, custom vocabulary/model, reference-aware or LLM correction, or post-hoc transcript cleanup",
    "No Stage 6.3 execution; no broad provider admission",
    "No technical PASS or gate satisfaction is claimed by this decision alone"
  ],
  "frozen_39_gate_accounting": {
    "current": {
      "SATISFIED": 6,
      "SATISFIED_FOR_CLOSED_INTERNAL_ALPHA": 2,
      "PARTIALLY_SATISFIED": 0,
      "OPEN": 5,
      "BLOCKED": 1,
      "NOT_RUN": 25,
      "total": 39
    },
    "conditional_if_evaluation_and_provider_later_satisfy_owner_overlay": {
      "SATISFIED": 6,
      "SATISFIED_FOR_CLOSED_INTERNAL_ALPHA": 2,
      "SATISFIED_FOR_OWNER_ONLY_CLOSED_INTERNAL_ALPHA_WITH_ACCEPTED_EN_LIMITATION": 2,
      "PARTIALLY_SATISFIED": 0,
      "OPEN": 3,
      "BLOCKED": 1,
      "NOT_RUN": 25,
      "total": 39
    },
    "external_legal_gate": "SEPARATE_FROM_FROZEN_39"
  }
}
```
