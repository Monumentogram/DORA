# DORA 6.2D: prospective easy-English amendment v0.1

Explicit Owner change, 2026-09-28: the English portion now contains four short readings with basic everyday words. Russian remains two READ and two SPONTANEOUS cases. Exactly eight recordings enter the evaluation.

Replaced requirements: EN two READ plus two SPONTANEOUS becomes EN four READ; EN spontaneous IDs 01/02 are retired from the active set and EN read IDs 03/04 are activated. All four English scripts are newly authored and frozen privately before recording. The earlier requirement to reuse the first two original scripts of every class is superseded for English only. Original inventory, protocols, consent, readiness, recordings and reference history remain archived or unchanged. Existing English takes cannot silently acquire new prompts.

READ remains 20–45 seconds; Russian SPONTANEOUS remains 20–60 seconds. WER remains RU ≤20% and EN ≤18%, with actual per-record and per-language word/error denominators. EN spontaneous coverage is **NOT_EVALUATED**, with zero cases and no WER denominator. An absent slice cannot be reported as a pass. English findings cover only these basic readings by one speaker, not general English, conversational speech or other speakers. Noise and timestamp accuracy also remain **NOT_EVALUATED**.

This is a prospective acquisition amendment, not a full Phase A successor. Published Phase A v0.1 and earlier amendments remain immutable. A full successor still needs the real verified corpus and checked AWS configuration, and must be published and refetched before the first AWS evaluation. USD10, privacy and the prohibition on 6.3 remain unchanged. No AWS evaluation or automatic microphone start is performed. Readiness does not verify actual spoken words.

Private scripts, speech, per-record hashes and device identity are excluded from this public record. The following JSON is reproduced exactly.

```json
{
  "schema_version": "1.0",
  "source_parent": "b4c6776fb3d5b872d09b284c0fee938de66e6ed5",
  "protocol_id": "dora-owned-easy-en8-v3",
  "active_ids": [
    "ru-read-01",
    "ru-read-02",
    "ru-spontaneous-01",
    "ru-spontaneous-02",
    "en-read-01",
    "en-read-02",
    "en-read-03",
    "en-read-04"
  ],
  "composition": {
    "ru": {
      "READ": 2,
      "SPONTANEOUS": 2
    },
    "en": {
      "READ": 4,
      "SPONTANEOUS": 0
    }
  },
  "wer_threshold_percent": {
    "ru": 20,
    "en": 18
  },
  "english_spontaneous": "NOT_EVALUATED",
  "english_scope": "BASIC_VOCABULARY_READ_ONLY_ONE_OWNER",
  "timestamp_accuracy": "NOT_EVALUATED",
  "noise_robustness": "NOT_EVALUATED",
  "phase_a_successor": "NOT_CREATED",
  "phase_b": "NOT_RUN",
  "budget_usd": {
    "total": 10,
    "asr": 2,
    "ancillary_tax": 8
  },
  "read_materials": "PROJECT_AUTHORED_REVISION_FROZEN_BEFORE_RECORDING",
  "automatic_microphone_start": false,
  "reference_confirmation": "EXPLICIT_HUMAN",
  "prior_corpus_and_readiness": "PRESERVED_WITH_ARCHIVED_PROVENANCE",
  "superseded_requirements": [
    {
      "previous": "EN 2 READ + 2 SPONTANEOUS",
      "current": "EN 4 READ + 0 SPONTANEOUS"
    },
    {
      "previous": "Use the first two original materials for each English speech class",
      "current": "Use four newly authored simple English read scripts, frozen before capture"
    },
    {
      "previous": "English spontaneous quality slice required in the bounded eight-case profile",
      "current": "English spontaneous coverage NOT_EVALUATED; no denominator and no pass claim"
    }
  ],
  "unchanged_requirements": [
    "RU 2 READ + 2 SPONTANEOUS",
    "Exactly eight recordings, all evaluated",
    "READ 20-45 seconds; RU SPONTANEOUS 20-60 seconds",
    "No reserves or noise recordings",
    "No manual timing truth; noise/timestamp properties NOT_EVALUATED",
    "Personal verification of actual words",
    "No provider-driven text or take replacement",
    "Full published/refetched Phase A successor before first AWS run",
    "USD10, privacy, no6.3, no automatic microphone or AWS evaluation"
  ],
  "recorded_counts_at_revision": {
    "ru": 0,
    "en": 0
  },
  "verified_references_at_revision": {
    "ru": 0,
    "en": 0
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
  "broad_admission": "NOT_ESTABLISHED",
  "provider_admission": "NOT_ESTABLISHED",
  "application_id": "com.monumentogram.dora.stage0.ownedcorpus",
  "app_version": "0.3.0-easy-english",
  "app_version_code": 3,
  "historical_phase_a_and_amendments": "UNCHANGED",
  "checks": {
    "android_gradle": {
      "status": "PASS",
      "tasks": 276
    },
    "android_unit_tests": {
      "status": "PASS",
      "tests": 5
    },
    "android_instrumentation": {
      "status": "PASS",
      "distinct_tests": 12,
      "api": 36,
      "baseline_executed": 11,
      "guided_eight_executed_separately": 1,
      "new_profile_cross_transfer": "PASS",
      "data": "SYNTHETIC_ONLY",
      "microphone_used": false
    },
    "android_to_host_v3_eight_case_import_idempotence_finalization": "PASS",
    "host_corpus_bridge_server_usb": {
      "status": "PASS",
      "tests": 51
    },
    "browser_v2_v3_synthetic_flows": "PASS_NO_MICROPHONE_CALLS",
    "preparation_guard_tests": {
      "status": "PASS",
      "tests": 13
    },
    "stage00": {
      "status": "PASS",
      "checks": 7
    },
    "legacy_missing_material_hash_map_and_interrupted_migration_retry": "PASS",
    "independent_review": "NO_REMAINING_CODE_BLOCKERS",
    "samsung_installation_and_seed_byte_identity": "PASS",
    "samsung_four_english_readings_and_preserved_readiness": "PASS",
    "samsung_visual_qa": "NOT_OBSERVED",
    "ci": "NOT_OBSERVED"
  },
  "apk_sha256": "4e3ab52b9b0264d7a249aca70a7d3951fbbddd69100e1eda713c126ecb4479b7",
  "tool_bindings": [
    {
      "path": "android/poc/owned-corpus/build.gradle.kts",
      "sha256_lf_utf8": "2399b05651b8d28359961d4bf2fee142e1059c652eb197528a1484b12cbefe8f"
    },
    {
      "path": "android/poc/owned-corpus/src/androidTest/java/com/monumentogram/dora/stage0/ownedcorpus/PrivateCorpusTest.kt",
      "sha256_lf_utf8": "93464c72d96239c60ff8713d078ef18184c15c3e30276ed58781c68d73566108"
    },
    {
      "path": "android/poc/owned-corpus/src/main/java/com/monumentogram/dora/stage0/ownedcorpus/CorpusStore.kt",
      "sha256_lf_utf8": "be88cea3cae128fe9e330a930f4aec2d68f80509432fdf86943b0cba1b665769"
    },
    {
      "path": "android/poc/owned-corpus/src/main/java/com/monumentogram/dora/stage0/ownedcorpus/MainActivity.kt",
      "sha256_lf_utf8": "3ccc41698ca419ce77e7f25b874a0b128eb996acd58c75076bec235e69d0f339"
    },
    {
      "path": "tools/cloud62d_owned/README.md",
      "sha256_lf_utf8": "0edd71f88f6209951517e3cbe5e905b68d4653fc1b0782f8e7bc79e78683a908"
    },
    {
      "path": "tools/cloud62d_owned/corpus.py",
      "sha256_lf_utf8": "29dbd5d04c6cb26d210a61d09d152f76dcecc879020a1fe0618212e520bbad8a"
    },
    {
      "path": "tools/cloud62d_owned/mobile_bridge.py",
      "sha256_lf_utf8": "ed4d967dc58756c1568b36b8aec30604b4d12d77511b3e6f3a6d832b423eb510"
    },
    {
      "path": "tools/cloud62d_owned/test_browser_fixture.py",
      "sha256_lf_utf8": "e8e6af4a314bcebe0d69c65fe3e0e42f544e2d6796c3d630a87a62653ae6602f"
    },
    {
      "path": "tools/cloud62d_owned/test_corpus.py",
      "sha256_lf_utf8": "ae7fbd232a17719809fc8f68ca7db61230474ad33bee0b8f7543608e46fce78c"
    },
    {
      "path": "tools/cloud62d_owned/test_mobile_bridge.py",
      "sha256_lf_utf8": "34803649a680e44ca4e5511da725281b7be56fa8b5850aa7d66b228704960aad"
    },
    {
      "path": "tools/cloud62d_owned/test_reduced_browser.mjs",
      "sha256_lf_utf8": "855b42d0688cb623cda6df8e30408514454a4b550fcf28dcbe4be832c8241bad"
    },
    {
      "path": "tools/cloud62d_owned/ui.js",
      "sha256_lf_utf8": "0c34f4f366ec1b2d29fb2f67bc080f8531a0a38d678fd238c39888132ae3545a"
    },
    {
      "path": "tools/cloud62d_prepare.py",
      "sha256_lf_utf8": "2493c9043e469c77a6f0e7bd60cd1068d5d8bc14833d24641ac361423777c760"
    },
    {
      "path": "tools/test_cloud62d_prepare.py",
      "sha256_lf_utf8": "246436fe21d9e0e7ca7493bbe50212f1e018a6bbd810a43abfca35476506befb"
    }
  ]
}
```
