# DORA owned eight: guided mobile UI v0.2

Owner request: reset current progress and make the buttons understandable. The primary action stays visible outside the scrolling task text. The flow is task, recording, playback, personal word verification, then the next task. Service controls are secondary. Existing explicit readiness is preserved through the archived reset; only a matching private activation can enable capture. Activation never starts the microphone or confirms words.

Historical Phase A, corpus-size amendment and mobile v0.1 evidence remain unchanged. This is acquisition UI evidence, not a complete Phase A successor or AWS admission. The single-speaker eight-record limitation, unmeasured noise/timing properties, USD10 bound and prohibition on 6.3 remain in effect. No private words or files belong here.

```json
{
  "protocol_id": "dora-owned-reduced8-v2",
  "phase_a_successor": "NOT_CREATED",
  "phase_b": "NOT_RUN",
  "active_ids": [
    "ru-read-01",
    "ru-read-02",
    "ru-spontaneous-01",
    "ru-spontaneous-02",
    "en-read-01",
    "en-read-02",
    "en-spontaneous-01",
    "en-spontaneous-02"
  ],
  "wer_threshold_percent": {
    "ru": 20,
    "en": 18
  },
  "timestamp_accuracy": "NOT_EVALUATED",
  "noise_robustness": "NOT_EVALUATED",
  "budget_usd": {
    "total": 10,
    "asr": 2,
    "ancillary_tax": 8
  },
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
  "schema_version": "1.0",
  "source_parent": "6e814b699979798e428f27123642a55e12de455f",
  "application_id": "com.monumentogram.dora.stage0.ownedcorpus",
  "app_version": "0.2.0-owned-eight",
  "app_version_code": 2,
  "automatic_microphone_start": false,
  "activation": "EXISTING_OWNER_READINESS_AND_MATCHING_SEED_REQUIRED",
  "reference_confirmation": "EXPLICIT_HUMAN",
  "primary_action": "FIXED_OUTSIDE_SCROLLING_CONTENT",
  "owner_reset": "ARCHIVE_OLD_PRESERVE_CONSENT_MATERIALS_AND_EXISTING_READINESS",
  "accepted_capture_replacement": "FORBIDDEN",
  "broad_admission": "NOT_ESTABLISHED",
  "production_android": "UNCHANGED",
  "aws_evaluation": "NOT_RUN",
  "historical_phase_a": "UNCHANGED",
  "historical_eight_amendment": "UNCHANGED",
  "historical_mobile_v01": "UNCHANGED",
  "recorded_counts_after_reset": {
    "ru": 0,
    "en": 0
  },
  "verified_references_after_reset": {
    "ru": 0,
    "en": 0
  },
  "flow": [
    "TASK",
    "OWNER_STARTS_RECORDING",
    "PLAYBACK",
    "PERSONAL_WORD_VERIFICATION",
    "NEXT_TASK"
  ],
  "checks": {
    "android_gradle": {
      "status": "PASS",
      "tasks": 276,
      "scope": "owned corpus app plus required production app/core checks"
    },
    "final_test_only_followup": {
      "status": "PASS",
      "tasks": 67,
      "scope": "format, detekt, test APK and lint"
    },
    "android_unit_tests": {
      "status": "PASS",
      "tests": 5
    },
    "android_instrumentation": {
      "status": "PASS",
      "distinct_tests": 9,
      "baseline_passed": 8,
      "guided_flow_passed_separately": 1,
      "api": 36,
      "data": "SYNTHETIC_ONLY",
      "microphone_used": false
    },
    "fixed_primary_regression": "RED_BEFORE_CHANGE_GREEN_AFTER_CHANGE",
    "eight_task_human_confirmation_and_edit_revocation": "PASS",
    "immediate_edit_then_service_menu_draft_preservation": "PASS",
    "matching_activation_without_microphone_start": "PASS",
    "android_to_python_eight_record_synthetic_cycle": "PASS",
    "idempotent_import_and_finalization_without_noise_or_manual_timing": "PASS",
    "keyboard_and_sticky_primary_visual": {
      "status": "PASS",
      "api": 36,
      "font_scales": [
        1.0,
        1.3
      ],
      "older_api_visual_coverage": "NOT_RUN"
    },
    "preparation_report_guard_tests": {
      "status": "PASS",
      "tests": 11
    },
    "stage00": {
      "status": "PASS",
      "checks": 7
    },
    "independent_review": "NO_REMAINING_CODE_BLOCKERS",
    "samsung_apk_installation": "PASS",
    "samsung_reset_zero_of_eight_and_preserved_readiness": "PASS",
    "samsung_visual_qa": "NOT_OBSERVED_DEVICE_DISCONNECTED_AFTER_VERIFIED_INSTALLATION",
    "real_phone_acoustic_evaluation": "NOT_RUN",
    "ci": "NOT_OBSERVED"
  },
  "apk_sha256": "407eeb58333453ae1dd0d8416823e562d5eb93ea8b17949bd91b0b1f70fd3624",
  "tool_bindings": [
    {
      "path": "android/poc/owned-corpus/build.gradle.kts",
      "sha256_lf_utf8": "8cf4e4490b8c58d31726545dfb27d93240e971625bc85513b67ad87ccd4720a0"
    },
    {
      "path": "android/poc/owned-corpus/src/androidTest/java/com/monumentogram/dora/stage0/ownedcorpus/PrivateCorpusTest.kt",
      "sha256_lf_utf8": "e1f7a88d0d7466b565f575769907667190db61896d56da3ba92a4eb44852b52c"
    },
    {
      "path": "android/poc/owned-corpus/src/main/java/com/monumentogram/dora/stage0/ownedcorpus/GuidedFlow.kt",
      "sha256_lf_utf8": "2e928f9049c1dfccc267bdb37375ddae94b2e2ada491aa35609c520e42f72c85"
    },
    {
      "path": "android/poc/owned-corpus/src/main/java/com/monumentogram/dora/stage0/ownedcorpus/GuidedPage.kt",
      "sha256_lf_utf8": "0d59081f68724a9c2a88b3c96cb4149721747cc504f48ec3a99995a50ed974f3"
    },
    {
      "path": "android/poc/owned-corpus/src/main/java/com/monumentogram/dora/stage0/ownedcorpus/MainActivity.kt",
      "sha256_lf_utf8": "4696196a83ff6d0e185f418627bc4cfeddeb2fdeea806fe2e3cb357c72f792ad"
    },
    {
      "path": "android/poc/owned-corpus/src/test/java/com/monumentogram/dora/stage0/ownedcorpus/GuidedFlowTest.kt",
      "sha256_lf_utf8": "e5a11f79b8b5695342176cf08ce30d46c565c7272dea4f7e1b900a2cc455937b"
    },
    {
      "path": "tools/cloud62d_prepare.py",
      "sha256_lf_utf8": "1eca6a3b818ef4654a2b84494ad4b5dce2a9d80a732e4da7977385a964502f61"
    },
    {
      "path": "tools/test_cloud62d_prepare.py",
      "sha256_lf_utf8": "e80345ed7acf8bb5213ca7ba98f6620a984ca46565d64b2e1277300cd3b48b7a"
    }
  ]
}
```
