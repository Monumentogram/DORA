# Dora MVP 1 — Test Strategy

Статус документа: Stage 00 test governance baseline\
Дата: 4 августа 2026 года\
Recovery governance amendment: 12 августа 2026 года — Proposed `DEC-044`/`OD-14`; execution withheld\
Область: Android MVP 1, обязательные PoC и будущий optional backend\
Источники: Technical Plan §§ 10, 34, 35, 39; Design Spec §§ 14, 36, 37; Implementation Readiness Review.

## 1. Назначение и границы

Стратегия задаёт, какое доказательство требуется на каждом уровне разработки Dora. Она не повышает готовность production recording, storage, ML или backend: эти области остаются закрыты readiness-гейтами и отдельными PoC/ADR.

Stage 00 реализует только основу: host unit tests, Compose/instrumentation test infrastructure, formatting, Kotlin static analysis, Android lint, debug assembly, CI и native packaging checks. Эмуляторные, физические, endurance, ML и release проверки становятся обязательными на указанных ниже этапах, когда соответствующий код разрешён.

Принципы:

- проверяется наблюдаемое поведение и контракт, а не только константы или факт компиляции;
- local mode тестируется без account, network, GMS и cloud configuration;
- private audio, персональные данные, credentials и unapproved model weights не используются;
- синтетические fixtures — default; consent-governed corpus допускается только после `POC-DATA-001`;
- manual/user truth не перезаписывается model result без review/diff;
- PoC evidence не превращает библиотеку или модель в production dependency без admission ADR;
- support/release claim ограничивается реально прошедшей device matrix.

## 2. Уровни тестирования

| ID | Уровень | Цель | Этап внедрения | Среда запуска | Критерий прохождения |
|---|---|---|---|---|---|
| TS-UNIT | Unit | Детерминированно проверять reducers, state transitions, value objects, token mapping, parsers и policy functions без Android framework. | Начинается в Stage 00; обязателен в каждом последующем PR. | JVM 17 на developer workstation и GitHub Actions; fake clock/IDs/ports. | Все релевантные branches и invariants покрыты; одинаковый input даёт одинаковый result; suite проходит без сети и flaky retry. |
| TS-INTEGRATION | Integration | Проверять взаимодействие соседних модулей через реальные contracts и controlled fakes: repository↔domain, engine port↔adapter, outbox↔worker. | Stage 1 для foundation; затем с первым разрешённым vertical slice каждого этапа. | Host JVM where possible; Android test process для platform-backed components; synthetic fixtures. | Контракты, idempotency, cancellation, error mapping и provenance сохраняются; forbidden dependency/network call отсутствует. |
| TS-INSTRUMENTATION | Android instrumentation | Проверять код, зависящий от Android runtime, resources, process, filesystem, system services и packaged manifest. | Infrastructure компилируется в Stage 00; запускается с первой platform behavior task в Stage 1/PoC. | Managed emulator и physical Android devices на API/support matrix; `connectedDebugAndroidTest` локально. | Stage 00 suite компилируется; в feature stages все scoped device tests проходят на declared API/ABI без permission/state leakage. |
| TS-COMPOSE | Compose UI | Проверять semantic tree, четыре navigation destinations, selection, honest record stub/real state contract, themes, adaptive layout и critical interactions. | Bootstrap coverage в Stage 00; расширяется вместе с каждым approved UI flow и design prototype. | Compose test rule на emulator/physical device; screenshot matrix отдельно для visual regression. | Required nodes/labels/roles/states доступны, actions меняют только разрешённый state, 48-dp targets и compact/wide behavior не регрессируют. |
| TS-DB-MIGRATION | Database migration | Доказывать открытие каждой опубликованной schema, сохранность user edits/source links и deterministic FTS rebuild без destructive fallback. | Harness в Stage 1; обязательный gate с первой Room schema и каждой migration. | Instrumented SQLite/Room tests с generated databases from every released schema version. | Upgrade проходит без потери/подмены данных, foreign/provenance invariants выполняются, downgrade policy явна, FTS rebuild повторяем. |
| TS-LIFECYCLE | Lifecycle и process death | Проверять rotation, resize, background, Task Manager stop, process kill/recreate, reboot reconciliation и отсутствие silent mic restart. | Stage 0 recovery PoC; production gate Stages 2–3 и regression thereafter. | Для Proposed `POC-RECOVERY-001` v0.4 contract: 12 frozen strata, 120 base hard kills/candidate, ≥100 valid; 45 mandatory fault rows (33 inherited + 12 durable key-confirmation/bootstrap); Phase A emulator+D2, full verdict physical D1/D2/D5. Production regression later returns to the applicable D1–D7 matrix. | Authenticated committed prefix сохраняется с committed loss `0`, tail loss ≤5.000 с на каждом valid kill, durable run-key confirmation validates before publication, duplicate/missing processing intent отсутствует, invalid attempts явны, mic не стартует сам. Phase A без D1/D5 не может PASS. |
| TS-FGS | Foreground service | Доказывать только user-initiated microphone FGS, корректную notification/state/Stop semantics и OEM endurance. | Isolated Stage 0 capture PoC; production regression с Stage 2. | Physical D1–D7, API 28/36+, screen-off/background/route change/user-stop; 1/3/8-hour runs per gate. | Start/Stop/finalize success и sample integrity проходят approved thresholds; notification всегда видима; unsupported device получает честный fallback. |
| TS-OFFLINE | Offline | Доказывать, что local core работает без DNS/network/account/GMS и что queued cloud work не блокирует и не повреждает local state. | Stage 0 PoC 9; regression при каждом network/cloud dependency и release. | Airplane mode + OS/firewall deny, fresh/install-model/missing-model, no-GMS device D4, later reconnect. | Zero forbidden network attempt; local capture/storage/history/search/tasks/export remain usable in their approved scope; reconnect is idempotent. |
| TS-STORAGE | Storage | Проверять budgeting, low/full storage, atomic write/finalize, crypto/file/DB reconciliation, retention и temporary artifact cleanup. | Recovery PoC in Stage 0; production Stages 3, 10, 12. | Emulator faults plus physical slow/normal storage; quota/full-disk/truncation/bit-flip/key-loss fixtures. | No whole-session loss or silent corruption; scoped error names preserved data; cleanup cannot delete source unexpectedly; reserved safety budget enforced. |
| TS-BATTERY | Battery/thermal | Измерять capture/VAD/ML overhead, wake locks, CPU, memory, thermal throttling и safe scheduling policy. | Stage 0 PoC 6; repeat after native/model/scheduling change and before release claims. | Physical D1–D5, controlled brightness/radio/temperature, ≥3 repeats with Batterystats/Perfetto. | Capture-only overhead ≤ approved baseline (initial target 1.25×), no severe thermal/drop; heavy work defers or degrades according to documented policy. |
| TS-ASR | ASR | Сравнивать RU/EN/mixed transcript quality и operational fitness выбранного exact artifact. | Stage 0 PoC 4; admission before Stage 4; regression for every model/runtime update. | Immutable consent-governed corpus; D1–D4 and server reference; clean/noisy/speakerphone slices. | Approved WER/timestamp/RTF/PSS/thermal gates pass per tier; digest/license/provenance fixed; JNI package/load/inference passes 16-KiB runtime checks. |
| TS-DIARIZATION | Diarization | Измерять speaker segmentation/clustering, overlap, speaker count и human correction burden без identity overclaim. | Stage 0 PoC 5; admission before Stage 5; regression for exact weight/runtime changes. | Consent-governed 1–6 speaker corpus, local D2/D3 and optional server reference; correction UX harness. | Approved DER/JER/count/correction gates pass; ambiguity remains reviewable; exact code/weights license and redistribution evidence approved. |
| TS-PRIVACY | Privacy | Проверять absence of secrets/content leakage, consent scope, data minimization, local/cloud boundary, deletion receipts and safe diagnostics/artifacts. | Secret/log baseline from Stage 00; data-flow tests before real data; cloud/delete gates Stages 11–14. | Secret scanners, repository/history/Actions audit, network proxy, filesystem/database inspection, authZ/adversarial tests. | No credential/private content in Git/log/artifact/analytics; zero outbound artifact without current scoped consent; deletion/export scope and receipts reconcile. |
| TS-ACCESSIBILITY | Accessibility | Обеспечить TalkBack/Switch/keyboard traversal, labels/roles/states, 200% text, contrast, touch targets and reduced motion. | Semantic baseline in Stage 00; component gate from first UI; D9 specialist/physical audit before release. | Compose semantics tests plus manual physical RU/EN matrix, font/display scaling, animator 0×, navigation modes and color-vision simulation. | 100% critical flow completable; no unlabeled/obscured critical control; all actions ≥48 dp, status not color-only, critical contrast/reflow gates pass. |
| TS-CI | CI | Воспроизводимо проверять handoff, formatting, Kotlin analysis, unit/instrumentation compilation, lint, APK and native admission from clean checkout. | Stage 00 and every PR/push thereafter. | Least-privilege GitHub-hosted runner, JDK 17, pinned Actions, SDK/build-tools 36. | Required `android-bootstrap` job green; no skipped mandatory task; debug APK uploaded for 7 days; native allowlist and 16-KiB checks pass. |
| TS-DEVICE | Physical device matrix | Выявлять API/OEM/ABI/RAM/storage/route differences and bound support claims to evidence. | Matrix definition in Stage 0; used by every capture/native/energy/accessibility/release gate. | D1 weak, D2 mainstream, D3 flagship, D4 no-GMS, D5 OEM, D6 16-KiB, D7 API-next; exact firmware recorded. | Required scenario passes on every declared supported class; failure either blocks release or narrows the versioned support matrix with owner-visible rationale. |
| TS-RELEASE | Release gates | Доказать identity/signing/update safety, dependency/model provenance, privacy/store completeness, performance and rollback before distribution. | Stage 14 after all relevant P0/P1 gates and owner decisions. | Clean release build, internal track/store preflight, signed upgrade/rollback devices, SBOM/license/CVE and 16-KiB reports. | Green full matrix; no unresolved release P0; approved application ID/key custody; reproducible signed artifact, policy/legal assets, tested upgrade and rollback runbook. |

## 3. CI и execution tiers

### Tier A — каждый Pull Request

- `tools/validate_stage00.py` и другие scoped schema/handoff validators;
- `spotlessCheck` и `detekt`;
- host unit tests;
- compilation of Android instrumentation/Compose tests;
- Android lint and debug APK assembly;
- native allowlist, ELF alignment and APK 16-KiB zip alignment;
- short-lived debug APK artifact without production signing/configuration.

### Tier B — scoped device/PoC gate

Запускается для backlog item, который вводит Android lifecycle, FGS, native code, storage, ML, UI accessibility or networking. Результат содержит commit, device/firmware/API/ABI, fixtures/digests, environment, raw non-content traces, pass/fail threshold and fallback.

### Tier C — release candidate

Объединяет approved physical matrix, endurance, privacy/security, migration, accessibility, signed-update, store and rollback gates. Green Tier A не заменяет Tier B/C.

## 4. Fixtures, данные и воспроизводимость

- Stage 00 использует только deterministic in-code state and UI fixtures без meeting content.
- Generated IDs, clocks, audio frames, transcripts and databases имеют fixed seed/version.
- Real meeting/voice data запрещены до `POC-DATA-001`, consent/access/retention process и immutable evaluation split.
- Model/native artifacts идентифицируются exact digest, source, license/terms, ABI and build provenance.
- Test output/logs store metrics and categorical states, never audio, transcript, query, name, credential or local private path.
- Flaky test не перезапускается молча до зелёного: фиксируются owner, cause, quarantine scope and deadline; critical safety test cannot be quarantined for release.

## 5. Physical device matrix

| Class | Minimum purpose |
|---|---|
| D1 | Weak supported device: capture deadlines, memory, battery and usable fallback. |
| D2 | Mainstream reference: primary local processing and UX baseline. |
| D3 | Flagship: enhanced local capability without relaxing correctness. |
| D4 | No-GMS/offline: prove core independence from Google services and account/network. |
| D5 | OEM matrix: at least Samsung plus another restrictive firmware family for background/endurance behavior. |
| D6 | 16-KiB runtime/device image: package, load and execute every admitted native library. |
| D7 | Current next-API preview/final gate: identify target/API behavior and memory regressions before migration. |

Exact models, RAM, storage, ABI, firmware, battery health and OTA policy belong to `POC-DEVICE-001`; this document does not fabricate inventory.

## 6. Stage 00 commands

From `android/` with JDK 17 and Android SDK 36:

```bash
./gradlew spotlessCheck detekt
./gradlew :app:testDebugUnitTest :core:common:testDebugUnitTest :core:model:testDebugUnitTest :core:testing:testDebugUnitTest
./gradlew :app:compileDebugAndroidTestKotlin
./gradlew :app:lintDebug :core:common:lintDebug :core:model:lintDebug :core:testing:lintDebug
./gradlew :app:assembleDebug
```

With an emulator or physical device connected, execute the Stage 00 Compose suite:

```bash
./gradlew :app:connectedDebugAndroidTest
```

From repository root:

```bash
python3 tools/validate_stage00.py
python3 tools/verify_apk_native_alignment.py android/app/build/outputs/apk/debug/app-debug.apk
```

`zipalign -c -P 16 -v 4` from Android Build Tools 36 verifies APK page alignment. Instrumentation execution is intentionally not required on the Stage 00 GitHub-hosted runner; compilation is required, and device execution becomes mandatory when a scoped Android behavior is admitted.

## 7. Release gate ownership

- Engineering owns deterministic tests, build reproducibility and defect evidence.
- QA owns matrix completeness, independent execution and regression traceability.
- Security/Privacy/Legal own the relevant consent, data-flow, artifact and store evidence; code cannot self-approve it.
- Product owner approves support/quality claims and any versioned threshold change.
- Release engineer blocks distribution when a required artifact, owner decision, P0/P1 gate or rollback proof is absent.

Any material gate change updates this strategy or an accepted ADR plus backlog/status in the same PR. Live GitHub check/artifact status remains authoritative and is not hard-coded as a run ID here.
