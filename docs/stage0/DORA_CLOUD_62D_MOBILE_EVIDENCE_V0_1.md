# DORA 6.2D mobile acquisition evidence v0.1

Prospective acquisition tooling only; no complete Phase A successor or AWS run.
The [design and requirement changes](DORA_CLOUD_62D_MOBILE_ACQUISITION_V0_1.md) define the bounded phone workflow. Only synthetic capture data was tested; real microphone testing is deferred until explicit Owner readiness.

```json
{
  "protocol_id": "dora-owned-reduced8-v2",
  "phase_a_successor": "NOT_CREATED",
  "phase_b": "NOT_RUN",
  "recording": "DEFERRED_UNTIL_EXPLICIT_OWNER_READY",
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
  "source_parent": "e0d1479333122166192b5f6a5f27813d9eb86f09",
  "application_id": "com.monumentogram.dora.stage0.ownedcorpus",
  "initial_seed_enabled": false,
  "accepted_capture_replacement": "FORBIDDEN",
  "technical_retry": "EXPLICIT_BEFORE_ACCEPTANCE_WITH_ALL_ATTEMPTS_RETAINED",
  "real_microphone_test": "NOT_RUN",
  "broad_admission": "NOT_ESTABLISHED",
  "production_android": "UNCHANGED",
  "aws_evaluation": "NOT_RUN",
  "historical_phase_a": "UNCHANGED",
  "historical_eight_amendment": "UNCHANGED",
  "previous_corpus_files": "BYTE_IDENTICAL",
  "recorded_counts": {
    "ru": 1,
    "en": 0
  },
  "verified_references": {
    "ru": 0,
    "en": 0
  },
  "phone_installation": "PENDING_ANDROID_OWNER_CONFIRMATION",
  "actual_private_seed": "PREPARED_DISABLED_OUTSIDE_GIT",
  "checks": {
    "android_gradle": {
      "status": "PASS",
      "tasks": 276,
      "scope": "new module plus required existing application/core checks"
    },
    "android_unit_tests": {
      "status": "PASS",
      "tests": 2
    },
    "android_instrumentation": {
      "status": "PASS",
      "tests": 7,
      "api": 36,
      "data": "SYNTHETIC_ONLY",
      "microphone_used": false
    },
    "native_android_to_python_eight_record_cycle": "PASS",
    "windows_raw_usb_byte_identity": "PASS",
    "import_idempotence_and_finalization_without_noise_or_timing": "PASS",
    "corpus_server_mobile_usb_host_tests": {
      "status": "PASS",
      "tests": 41
    },
    "preparation_report_guard_tests": {
      "status": "PASS",
      "tests": 10
    },
    "frozen_cloud_harness_tests": {
      "status": "PASS",
      "tests": 26
    },
    "aws_offline_tests": {
      "status": "PASS",
      "tests": 13
    },
    "stage00": {
      "status": "PASS",
      "checks": 7
    },
    "independent_review": "NO_REMAINING_CODE_BLOCKERS",
    "real_phone_installation": "BLOCKED_BY_ANDROID_INSTALLATION_CONFIRMATION",
    "real_phone_acoustics": "NOT_RUN",
    "ci": "NOT_OBSERVED"
  },
  "apk_sha256": "74ad271bc9e5a16d68f4e5c936953f50b7be4bc61f4c7617d65e17b4de781ee0",
  "tool_bindings": [
    {
      "path": "android/poc/owned-corpus/build.gradle.kts",
      "sha256_lf_utf8": "b3b93585960def77667afe87e895565762348ae3f649eae3894bb04bf7159359"
    },
    {
      "path": "android/poc/owned-corpus/gradle.lockfile",
      "sha256_lf_utf8": "bca704e33c38a015e1461c23451973fff429fbf2154365f74256a61d91398fe0"
    },
    {
      "path": "android/poc/owned-corpus/src/androidTest/java/com/monumentogram/dora/stage0/ownedcorpus/PrivateCorpusTest.kt",
      "sha256_lf_utf8": "6b613397e40052d2dcec93ba0ebdfc7ed13411cad42346703826f652fd50061a"
    },
    {
      "path": "android/poc/owned-corpus/src/main/AndroidManifest.xml",
      "sha256_lf_utf8": "79087bf525cbee8f8b1091ff1945ded95918c912a35746679ae7dbfe19f3de33"
    },
    {
      "path": "android/poc/owned-corpus/src/main/java/com/monumentogram/dora/stage0/ownedcorpus/AudioCapture.kt",
      "sha256_lf_utf8": "3b06ee3c0732d6143a170e06de42f24ddb08391cdfa3a761d9e34e12bde4539a"
    },
    {
      "path": "android/poc/owned-corpus/src/main/java/com/monumentogram/dora/stage0/ownedcorpus/CorpusStore.kt",
      "sha256_lf_utf8": "909bc34bbf5ed5c56a314d01cd132c31a1ce17c02fd297387c1d57e23c3ff0cf"
    },
    {
      "path": "android/poc/owned-corpus/src/main/java/com/monumentogram/dora/stage0/ownedcorpus/MainActivity.kt",
      "sha256_lf_utf8": "27a088406fa3adc1d1f162d48d245a508fd25c47b3c345c39da397ab53915dde"
    },
    {
      "path": "android/poc/owned-corpus/src/main/java/com/monumentogram/dora/stage0/ownedcorpus/OwnedSession.kt",
      "sha256_lf_utf8": "52832bc2663817afa96f9e5f16ac85378145205cc91a9f2276619a61d5b39708"
    },
    {
      "path": "android/poc/owned-corpus/src/main/java/com/monumentogram/dora/stage0/ownedcorpus/Wav.kt",
      "sha256_lf_utf8": "c63151c2c8c5707abeb6df78d073f7ec5b398a59d64ec1b8dab2ae4165d6520f"
    },
    {
      "path": "android/poc/owned-corpus/src/main/res/drawable/ic_owned.xml",
      "sha256_lf_utf8": "7ccb7a0350bba30d0ca54f77166651f010dd8e9d79a4afacd9e25ef71a83e7b2"
    },
    {
      "path": "android/poc/owned-corpus/src/main/res/values/colors.xml",
      "sha256_lf_utf8": "8506b93276816a7638aa7f614df45b26814b67cff14f855502f846d6c6e551f1"
    },
    {
      "path": "android/poc/owned-corpus/src/main/res/values/styles.xml",
      "sha256_lf_utf8": "e3b0a466e5190f2781886127ba0f7c11beacea48b4d702aaab3f85e438ca0fc2"
    },
    {
      "path": "android/poc/owned-corpus/src/main/res/xml/data_extraction_rules.xml",
      "sha256_lf_utf8": "26e75fcc52a52378a13d4b9577bf4b95e9f0afbd25a70dd1798a8f2ac7f6247e"
    },
    {
      "path": "android/poc/owned-corpus/src/test/java/com/monumentogram/dora/stage0/ownedcorpus/WavTest.kt",
      "sha256_lf_utf8": "f5f88cb7b8185465a5bb6322bf842d02ad1e9a9e91828e7b00a1ffddc07b3fb2"
    },
    {
      "path": "android/settings.gradle.kts",
      "sha256_lf_utf8": "07f9c24004a1b4f80afad3408ff7af98aa84a01fd8b864af821b6bac97f4c8cf"
    },
    {
      "path": "docs/stage0/DORA_CLOUD_62D_MOBILE_ACQUISITION_V0_1.md",
      "sha256_lf_utf8": "7e3dfd5a00f06eff3b767d3b9c0dcb2bf907c19f560ea8e1197668848c45c77b"
    },
    {
      "path": "tools/cloud62d_owned/corpus.py",
      "sha256_lf_utf8": "5493be60925e5dea6dec1a6f340ffe35aef0d1a4a09d929dc4a9e1fe0e1c525b"
    },
    {
      "path": "tools/cloud62d_owned/mobile_bridge.py",
      "sha256_lf_utf8": "45ed67023b7bf28206182232c1fe29c6fa10bce4982ff2ea5048b9d150134a41"
    },
    {
      "path": "tools/cloud62d_owned/mobile_device.py",
      "sha256_lf_utf8": "3bef1c0765033bb88fa76b33d05b5556f3d3e35e34e3dc1270e8dc276587e9cf"
    },
    {
      "path": "tools/cloud62d_owned/test_corpus.py",
      "sha256_lf_utf8": "921459fbf28e1cbf12937ee3a8b697bf5431aae65532a20166b74d2d884d24fd"
    },
    {
      "path": "tools/cloud62d_owned/test_mobile_bridge.py",
      "sha256_lf_utf8": "b0f99d571b2a2da2ff7f38b17113fccbe573d77b4bc9e0c3185b70422c936548"
    },
    {
      "path": "tools/cloud62d_owned/test_mobile_device.py",
      "sha256_lf_utf8": "e36cce24f92528bfa54675b1e3ddb3d5cd4ad20ecc4cbb7cb91583e5d4e7dff2"
    },
    {
      "path": "tools/cloud62d_prepare.py",
      "sha256_lf_utf8": "f66fffb7a2e2a98f717d53882f86120a932c210ae8393f7d8d4db892ec880bb3"
    },
    {
      "path": "tools/test_cloud62d_prepare.py",
      "sha256_lf_utf8": "4ca0e0a1cf960df7004941fe4571031dd5b4804835c1e21566b0b7da79359321"
    }
  ]
}
```
