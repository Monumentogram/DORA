# 6.3 — Alpha development admission, exact baseline

**PASS / DEVELOPMENT_ADMISSION_AND_EXACT_BASELINE_FROZEN.** Stage 0 is **PASS / ALPHA_IMPLEMENTATION_ADMITTED** for `OWNER_ONLY_CLOSED_INTERNAL_ALPHA`. `ALPHA_IMPLEMENTATION_BASELINE` is `92f00f7dd4a18a3d4b2fdd159fdaef86fa3f8699` on `chat/alpha-asr-runner-scope`, parent `a7701763a6486e4f707d23a70c0cc600431a88ac`. This decision admits development against the exact reviewed public Git source. The later commit publishing these admission documents is **not** the implementation baseline. This decision performs no implementation, uses no private/local or retired AWS resource, and does not integrate PR #86.

## Exact CI evidence

[Android CI run 36620762619](https://github.com/Monumentogram/DORA/actions/runs/36620762619), attempt 1, `workflow_dispatch`, exact head `92f00f7dd4a18a3d4b2fdd159fdaef86fa3f8699`, ran 2026-09-29T19:39:12Z–2026-09-29T19:49:22Z and concluded **success**. Both mandatory jobs completed successfully; only the failure-only emulator log print was skipped. The prior [run 36613265772](https://github.com/Monumentogram/DORA/actions/runs/36613265772) on `a7701763a6486e4f707d23a70c0cc600431a88ac` failed the REC-I3 scope-first ancestry check before Gradle and remains historical.

- `android-bootstrap` (job `109585257276`): **success**. Set up job; Check out repository; Verify immutable baseline is present; Set up JDK 17; Set up Gradle and validate wrapper; Install Android SDK 36; Validate Stage 00 artifacts; Validate isolated capture PoC contracts; Validate isolated search PoC contracts; Fetch pinned Recovery reviewed-source provenance; Validate recovery governance package; Verify exact recovery dependency inventory; Verify recovery execution remains fail-closed; Verify pure-host VPN contract kernel; Verify hermetic numeric-loopback VPN transport harness; Check formatting and Kotlin static analysis; Unit tests, instrumentation compilation, lint, and debug assembly; Verify locked search dependency artifact inventory; Verify exact search license and NOTICE inventory; Verify native library admission and ELF alignment; Verify capture PoC native library admission and ELF alignment; Verify 16 KiB APK zip alignment; Verify capture PoC 16 KiB APK zip alignment; Upload Stage 00 debug APK; Upload capture PoC debug APK; Post Set up Gradle and validate wrapper; Post Set up JDK 17; Post Check out repository; Complete job.
- `search-smoke` (job `109585257842`): **success**. Set up job; Check out repository; Set up JDK 17; Set up Gradle and validate wrapper; Install Android API 36 emulator; Create API 36 search smoke AVD; Start API 36 emulator; Run Room FTS4 schema, correctness, safety, mapping, and mutation smoke tests; Post Set up Gradle and validate wrapper; Post Set up JDK 17; Post Check out repository; Complete job.

| CI artifact | GitHub SHA-256 digest | Bytes |
| --- | --- | ---: |
| dora-poc-capture-debug-apk | `sha256:f522a0ae4563dd52da5f8148f0af16c583303e86b9a651b3e41d87683544aa3b` | 10429485 |
| dora-stage00-debug-apk | `sha256:50aed608ac9d6c10b90dfc95cd0cdd47734dc811e1b17795d023da33a690d05a` | 10296487 |

## Gate decision

The ten frozen implementation-entry predecessors were reread from the exact-bound 6.2D closure. Only `CLD-ADM-ADMISSION-001` changes: `BLOCKED` → `SATISFIED_FOR_OWNER_ONLY_CLOSED_INTERNAL_ALPHA`. All other 38 statuses are identical. The 39-gate count is 6 `SATISFIED`, 2 `SATISFIED_FOR_CLOSED_INTERNAL_ALPHA`, 3 `SATISFIED_FOR_OWNER_ONLY_CLOSED_INTERNAL_ALPHA`, 0 `PARTIALLY_SATISFIED`, 3 `OPEN`, 0 `BLOCKED`, 25 `NOT_RUN`.

| Predecessor | Effective status | Frozen roadmap tasks |
| --- | --- | --- |
| `CLD-ADM-ARCH-001` | `SATISFIED` | 6.1C |
| `CLD-ADM-CONSENT-001` | `SATISFIED` | 6.1A, 6.1B |
| `CLD-ADM-DATA-001` | `SATISFIED` | 6.1B |
| `CLD-ADM-SCOPE-001` | `SATISFIED` | 6.1 |
| `CLD-ADM-GAPS-001` | `SATISFIED` | 6.2 |
| `CLD-ADM-PRIVACY-001` | `SATISFIED_FOR_CLOSED_INTERNAL_ALPHA` | 11.1C, CLOUD-02, BE-LEGAL-001, BE-CONSENT-001 |
| `CLD-ADM-RETENTION-001` | `SATISFIED` | 11.1C, 8.2C, CLOUD-02, BE-DELETE-001 |
| `CLD-ADM-CONTROL-001` | `SATISFIED_FOR_CLOSED_INTERNAL_ALPHA` | 11.1C, BE-AUTH-001, BE-API-001, BE-DELETE-001 |
| `CLD-ADM-EVALUATION-001` | `SATISFIED_FOR_OWNER_ONLY_CLOSED_INTERNAL_ALPHA` | 6.2D, BE-PROVIDER-001, BE-PROVIDER-002 |
| `CLD-ADM-PROVIDER-001` | `SATISFIED_FOR_OWNER_ONLY_CLOSED_INTERNAL_ALPHA` | 6.2D, 18.1, BE-PROVIDER-001, BE-PROVIDER-002 |

All 28 downstream gates retain their frozen status, `required_before`, dependencies and roadmap tasks. The companion JSON carries each complete list; this table shows the deadlines and dependency links without changing them.

| Downstream gate | Status | Required before | Dependencies | Roadmap tasks |
| --- | --- | --- | --- | --- |
| `CLD-ADM-API-001` | `OPEN` | BEFORE_CLOUD_RUNTIME_INTEGRATION, BEFORE_FIRST_REAL_CLOUD_AUDIO_UPLOAD, BEFORE_INTERNAL_ALPHA_ACCEPTANCE, BEFORE_PUBLIC_RELEASE | CLD-ADM-ADMISSION-001 | 7.2C, 7.2E, BE-API-001 |
| `CLD-ADM-AUTH-001` | `NOT_RUN` | BEFORE_FIRST_REAL_CLOUD_AUDIO_UPLOAD, BEFORE_CLOUD_RUNTIME_INTEGRATION, BEFORE_INTERNAL_ALPHA_ACCEPTANCE, BEFORE_PUBLIC_RELEASE | CLD-ADM-CONTROL-001, CLD-ADM-API-001 | 7.2E, 18.1C, BE-AUTH-001 |
| `CLD-ADM-CONSENT-RUNTIME-001` | `NOT_RUN` | BEFORE_FIRST_REAL_CLOUD_AUDIO_UPLOAD, BEFORE_CLOUD_RUNTIME_INTEGRATION, BEFORE_INTERNAL_ALPHA_ACCEPTANCE, BEFORE_PUBLIC_RELEASE | CLD-ADM-CONSENT-001, CLD-ADM-AUTH-001, CLD-ADM-HARNESS-001 | 9.2C, 9.2D, 11.1C, 18.1C |
| `CLD-ADM-SECRETS-001` | `NOT_RUN` | BEFORE_FIRST_REAL_CLOUD_AUDIO_UPLOAD, BEFORE_CLOUD_RUNTIME_INTEGRATION, BEFORE_INTERNAL_ALPHA_ACCEPTANCE, BEFORE_PUBLIC_RELEASE | CLD-ADM-CONTROL-001, CLD-ADM-ADMISSION-001 | 18.1C, 11.1C |
| `CLD-ADM-UPLOAD-001` | `NOT_RUN` | BEFORE_FIRST_REAL_CLOUD_AUDIO_UPLOAD, BEFORE_CLOUD_RUNTIME_INTEGRATION, BEFORE_INTERNAL_ALPHA_ACCEPTANCE, BEFORE_PUBLIC_RELEASE | CLD-ADM-AUTH-001, CLD-ADM-CONSENT-RUNTIME-001, CLD-ADM-SECRETS-001, CLD-ADM-CRYPTO-001, CLD-ADM-API-001 | 9.2C, 8.4C, 18.1C |
| `CLD-ADM-CRYPTO-001` | `NOT_RUN` | BEFORE_FIRST_REAL_CLOUD_AUDIO_UPLOAD, BEFORE_CLOUD_RUNTIME_INTEGRATION, BEFORE_INTERNAL_ALPHA_ACCEPTANCE, BEFORE_PUBLIC_RELEASE | CLD-ADM-CONTROL-001, CLD-ADM-SECRETS-001 | 11.1C, 18.1C |
| `CLD-ADM-RETENTION-RUNTIME-001` | `NOT_RUN` | BEFORE_FIRST_REAL_CLOUD_AUDIO_UPLOAD, BEFORE_CLOUD_RUNTIME_INTEGRATION, BEFORE_INTERNAL_ALPHA_ACCEPTANCE, BEFORE_PUBLIC_RELEASE | CLD-ADM-RETENTION-001, CLD-ADM-AUTH-001, CLD-ADM-PROVIDER-001, CLD-ADM-DELETE-001, CLD-ADM-HARNESS-001 | 11.1C, 18.1C, BE-DELETE-001 |
| `CLD-ADM-DATA-RUNTIME-001` | `NOT_RUN` | BEFORE_FIRST_REAL_CLOUD_AUDIO_UPLOAD, BEFORE_CLOUD_RUNTIME_INTEGRATION, BEFORE_INTERNAL_ALPHA_ACCEPTANCE, BEFORE_PUBLIC_RELEASE | CLD-ADM-DATA-001, CLD-ADM-ADMISSION-001, CLD-ADM-HARNESS-001 | 7.2D, 8.2C, 8.4C |
| `CLD-ADM-FAILURE-001` | `NOT_RUN` | BEFORE_FIRST_REAL_CLOUD_AUDIO_UPLOAD, BEFORE_CLOUD_RUNTIME_INTEGRATION, BEFORE_INTERNAL_ALPHA_ACCEPTANCE, BEFORE_PUBLIC_RELEASE | CLD-ADM-API-001, CLD-ADM-DATA-RUNTIME-001, CLD-ADM-HARNESS-001 | 9.2C, 9.2E, 18.1C, 12.1C |
| `CLD-ADM-QUEUE-001` | `NOT_RUN` | BEFORE_FIRST_REAL_CLOUD_AUDIO_UPLOAD, BEFORE_CLOUD_RUNTIME_INTEGRATION, BEFORE_INTERNAL_ALPHA_ACCEPTANCE, BEFORE_PUBLIC_RELEASE | CLD-ADM-CONSENT-RUNTIME-001, CLD-ADM-DATA-RUNTIME-001, CLD-ADM-HARNESS-001 | 9.2D, 11.2C |
| `CLD-ADM-BACKGROUND-001` | `NOT_RUN` | BEFORE_INTERNAL_ALPHA_ACCEPTANCE, BEFORE_PUBLIC_RELEASE | CLD-ADM-QUEUE-001, CLD-ADM-FAILURE-001 | 11.2C, 9.2C, 12.1C |
| `CLD-ADM-ADAPTER-001` | `NOT_RUN` | BEFORE_FIRST_REAL_CLOUD_AUDIO_UPLOAD, BEFORE_CLOUD_RUNTIME_INTEGRATION, BEFORE_INTERNAL_ALPHA_ACCEPTANCE, BEFORE_PUBLIC_RELEASE | CLD-ADM-PROVIDER-001, CLD-ADM-API-001, CLD-ADM-HARNESS-001 | 7.2C, 18.1C |
| `CLD-ADM-COST-001` | `NOT_RUN` | BEFORE_FIRST_REAL_CLOUD_AUDIO_UPLOAD, BEFORE_INTERNAL_ALPHA_ACCEPTANCE, BEFORE_PUBLIC_RELEASE | CLD-ADM-PROVIDER-001, CLD-ADM-API-001, CLD-ADM-HARNESS-001 | 18.2C, 18.1C |
| `CLD-ADM-OBSERVABILITY-001` | `NOT_RUN` | BEFORE_FIRST_REAL_CLOUD_AUDIO_UPLOAD, BEFORE_INTERNAL_ALPHA_ACCEPTANCE, BEFORE_PUBLIC_RELEASE | CLD-ADM-PRIVACY-001, CLD-ADM-RETENTION-001, CLD-ADM-API-001, CLD-ADM-HARNESS-001 | 18.2C, 11.1C |
| `CLD-ADM-SECURITY-001` | `NOT_RUN` | BEFORE_FIRST_REAL_CLOUD_AUDIO_UPLOAD, BEFORE_INTERNAL_ALPHA_ACCEPTANCE, BEFORE_PUBLIC_RELEASE | CLD-ADM-CONTROL-001, CLD-ADM-UPLOAD-001, CLD-ADM-AUTH-001, CLD-ADM-SECRETS-001, CLD-ADM-COST-001, CLD-ADM-OBSERVABILITY-001, CLD-ADM-RESULT-001, CLD-ADM-DELETE-001, CLD-ADM-HARNESS-001 | 11.1C, 12.1C, 18.1C |
| `CLD-ADM-RESULT-001` | `NOT_RUN` | BEFORE_FIRST_REAL_CLOUD_AUDIO_UPLOAD, BEFORE_CLOUD_RUNTIME_INTEGRATION, BEFORE_INTERNAL_ALPHA_ACCEPTANCE, BEFORE_PUBLIC_RELEASE | CLD-ADM-DATA-RUNTIME-001, CLD-ADM-API-001, CLD-ADM-HARNESS-001 | 9.2E, 7.2D, 18.1C |
| `CLD-ADM-MERGE-001` | `NOT_RUN` | BEFORE_FIRST_REAL_CLOUD_AUDIO_UPLOAD, BEFORE_INTERNAL_ALPHA_ACCEPTANCE, BEFORE_PUBLIC_RELEASE | CLD-ADM-RESULT-001, CLD-ADM-DATA-RUNTIME-001, CLD-ADM-HARNESS-001 | 9.4C, 12.1C |
| `CLD-ADM-LOCAL-001` | `NOT_RUN` | BEFORE_INTERNAL_ALPHA_ACCEPTANCE, BEFORE_PUBLIC_RELEASE | CLD-ADM-DATA-RUNTIME-001, CLD-ADM-SCOPE-001 | 9.1C, 11.2C |
| `CLD-ADM-OFFLINE-001` | `NOT_RUN` | BEFORE_FIRST_REAL_CLOUD_AUDIO_UPLOAD, BEFORE_INTERNAL_ALPHA_ACCEPTANCE, BEFORE_PUBLIC_RELEASE | CLD-ADM-DATA-RUNTIME-001, CLD-ADM-QUEUE-001, CLD-ADM-HARNESS-001 | 8.2C, 9.1C, 9.2D, 11.2C |
| `CLD-ADM-DELETE-001` | `NOT_RUN` | BEFORE_FIRST_REAL_CLOUD_AUDIO_UPLOAD, BEFORE_CLOUD_RUNTIME_INTEGRATION, BEFORE_INTERNAL_ALPHA_ACCEPTANCE, BEFORE_PUBLIC_RELEASE | CLD-ADM-RETENTION-001, CLD-ADM-DATA-RUNTIME-001, CLD-ADM-API-001, CLD-ADM-HARNESS-001 | 11.1C, 8.2C, 9.2E, 10.3C |
| `CLD-ADM-HARNESS-001` | `NOT_RUN` | BEFORE_FIRST_REAL_CLOUD_AUDIO_UPLOAD, BEFORE_CLOUD_RUNTIME_INTEGRATION, BEFORE_INTERNAL_ALPHA_ACCEPTANCE, BEFORE_PUBLIC_RELEASE | CLD-ADM-ADMISSION-001 | 7.3C, 12.1C |
| `CLD-ADM-UX-001` | `NOT_RUN` | BEFORE_FIRST_REAL_CLOUD_AUDIO_UPLOAD, BEFORE_INTERNAL_ALPHA_ACCEPTANCE, BEFORE_PUBLIC_RELEASE | CLD-ADM-PRIVACY-001, CLD-ADM-CONSENT-RUNTIME-001, CLD-ADM-RETENTION-001, CLD-ADM-HARNESS-001 | 9.3C, 11.1C |
| `CLD-ADM-HISTORY-001` | `NOT_RUN` | BEFORE_INTERNAL_ALPHA_ACCEPTANCE, BEFORE_PUBLIC_RELEASE | CLD-ADM-MERGE-001, CLD-ADM-DELETE-001, CLD-ADM-DATA-RUNTIME-001 | 10.3C |
| `CLD-ADM-EXPORT-001` | `NOT_RUN` | BEFORE_INTERNAL_ALPHA_ACCEPTANCE, BEFORE_PUBLIC_RELEASE | CLD-ADM-HISTORY-001, CLD-ADM-MERGE-001 | 11.3C |
| `CLD-ADM-OPERATIONS-001` | `NOT_RUN` | BEFORE_FIRST_REAL_CLOUD_AUDIO_UPLOAD, BEFORE_INTERNAL_ALPHA_ACCEPTANCE, BEFORE_PUBLIC_RELEASE | CLD-ADM-RETENTION-RUNTIME-001, CLD-ADM-COST-001, CLD-ADM-OBSERVABILITY-001, CLD-ADM-ADAPTER-001 | 18.1C, 18.2C, 11.1C |
| `CLD-ADM-SUPPLY-001` | `OPEN` | BEFORE_FIRST_REAL_CLOUD_AUDIO_UPLOAD, BEFORE_CLOUD_RUNTIME_INTEGRATION, BEFORE_INTERNAL_ALPHA_ACCEPTANCE, BEFORE_PUBLIC_RELEASE | CLD-ADM-ADMISSION-001, CLD-ADM-PROVIDER-001 | 18.1C, 7.3C |
| `CLD-ADM-EXIT-001` | `NOT_RUN` | BEFORE_INTERNAL_ALPHA_ACCEPTANCE, BEFORE_PUBLIC_RELEASE | CLD-ADM-BACKGROUND-001, CLD-ADM-LOCAL-001, CLD-ADM-HISTORY-001, CLD-ADM-EXPORT-001, CLD-ADM-SECURITY-001, CLD-ADM-OPERATIONS-001, CLD-ADM-SUPPLY-001, CLD-ADM-UX-001, CLD-ADM-OFFLINE-001, CLD-ADM-FAILURE-001 | 12.1C, 12.4C |
| `CLD-ADM-RELEASE-001` | `OPEN` | BEFORE_PUBLIC_RELEASE | CLD-ADM-EXIT-001 | 19.1, 19.2 |

## Scope, limits and next work

The admitted provider configuration is **Amazon Transcribe `STANDARD_BATCH_FILE_ASR`, `eu-central-1`, `ru-RU` and `en-US`**, for this owner-only closed internal Alpha. Historical RU 17/179 (9.4972% WER) passed its bounded criterion. EN 37/181 (20.4420%) **failed** the frozen 18% benchmark; OD-62D-EN-01 accepts that measured limitation only for this scope. EN spontaneous speech, noise, other speakers/devices and timestamp accuracy are **EN_SPONTANEOUS/NOISE/TIMESTAMP_ACCURACY=NOT_EVALUATED** for broader admission. **PRE_ADDITIONAL_INTERNAL_TESTER_PROVIDER_REVALIDATION=MANDATORY_BEFORE_SECOND_REAL_SPEAKER.** A second real speaker or material provider/configuration/recording-path change requires prospective revalidation before scope expansion.

The original 6.1 core path remains: offline original-audio recording without network or a Local model; durable pending/DEFERRED work and current authorization at reconnect; Cloud-only ASR through the DORA-controlled backend and provider-independent `CloudAsrProvider`; optional Local Small q8_0 installation only within its proven POCO boundary. The product path is original audio → authorized ASR and immutable transcript versions → user edits → local history/lexical search → explicit export. Twelve capabilities remain deferred and five out of scope under the frozen 6.1 contract. ADR-0009 governs the architecture. Consent/deletion, ownership, immutable transcript versions, canonical media preflight, backend isolation and runtime security still need implementation and attributable tests.

**Cloud runtime is not implemented; FIRST_REAL_PRODUCT_AUDIO=NOT_READY.** Android holds no permanent AWS credentials; the engineering diagnostic harness is not a production backend. The companion JSON lists every mandatory pre-audio gate (including its current status), all frozen internal-Alpha gates and the fourteen exit criteria from the published gate contract. No downstream waiver is created. Broad provider admission, internal Alpha acceptance and public release are not established.

**Next: 7.1 — Идентификатор, подпись и установка alpha (READY / NEXT).** Establish internal app identity, signing custody outside Git and the installation route. Follow Stage 7 foundation → Stage 8 recording/storage/privacy → Stage 9 ASR/versioning → Stage 10 local history/search → Stage 11 offline/reconnect/delete → Stage 12 integrated Alpha acceptance. Frozen per-gate deadlines control the actual order: Stage 18 backend/control-plane and every first-real-audio prerequisite close when required, not at a convenient later stage. PR #86 remains OPEN / DRAFT / UNMERGED; future `PR86-INTEGRATION-RECONCILIATION` will compare its Recovery implementation/evidence against this baseline and decide its treatment separately.

## Public source binding

These are SHA-256 hashes of **Git blob bytes at the exact baseline**, not working-tree CRLF representations. The [companion JSON](../contracts/DORA_ALPHA_DEVELOPMENT_ADMISSION_6_3_V0_1.json) is the machine-readable authority for full gate/dependency parity.

| Git path | SHA-256 of blob bytes |
| --- | --- |
| `.github/workflows/android-ci.yml` | `5a7173c2f7e7896900ddb2ca664ee0e5528283d43655522c5c9a20af8a2f909e` |
| `docs/DORA_MVP1_IMPLEMENTATION_BACKLOG.md` | `263ff21911076dd8e4d3acd33eaa6e3e6f39e7ec686f59076c82639b4e9ceed9` |
| `docs/adr/ADR-0009-alpha-cloud-execution-boundary.md` | `e614c1817d68c03fb31f4e026bfda7a81099b8ea72deff3a82aea8a4bb8ec2ca` |
| `docs/adr/ADR-0013-closed-internal-alpha-expert-review-governance.md` | `09d07a85e2d8a04fd41915d495d4c947aeea3477f37cc1e3b2d83cdf9fd0833e` |
| `docs/contracts/DORA_ALPHA_SCOPE_V0_1.json` | `f8ab5373e5dee3fc96e4229d333f4c178baee5b4b67f8a896a21b15965b762be` |
| `docs/contracts/DORA_CLOUD_62D_EN_OWNER_ACCEPTANCE_SCOPE_OVERLAY_V0_1.json` | `44fa77be4a3cfda065b30e0d14905745ee048a52767ee4b6558d3b78fde3dffa` |
| `docs/contracts/DORA_CLOUD_ALPHA_ADMISSION_GATES_V0_1.json` | `3a03cffa43a0f2743da54b0d47f050f4ac06a260f8e63b66ef60869107a8e350` |
| `docs/evidence/cloud-6.2d-duplicate-name-closure-result-v0.1.json` | `56c3e836e3208a151e4add38d7b728b6acb494e90d46b438ab6958492a73682f` |
| `docs/evidence/cloud-6.2d-owner-technical-result-v0.1.json` | `c3d667c604e4ee12652cb314bacf4d780006e9f6ce5dd7e0c04b38556329b770` |
