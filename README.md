# Dora

Dora is an Android local-first meeting assistant. This repository currently contains the approved MVP 1 technical/design baseline and the Stage 00 implementation bootstrap. The Android shell exposes four placeholder destinations and a clearly unavailable recording action; microphone permission and product features are intentionally not implemented yet.

Repository visibility is temporarily public so GitHub Free can enforce server-side protection of `main`; see [`ADR-0002`](docs/adr/ADR-0002-public-repository-for-branch-protection.md). This is not a product release or approval to store private data, credentials, signing material or production configuration in the repository.

## Sources of truth

1. [`docs/DORA_MVP1_TECHNICAL_PLAN.md`](docs/DORA_MVP1_TECHNICAL_PLAN.md)
2. [`docs/DORA_MVP1_DESIGN_SPEC.md`](docs/DORA_MVP1_DESIGN_SPEC.md)
3. [`docs/DORA_MVP1_PRODUCT_DECISIONS.md`](docs/DORA_MVP1_PRODUCT_DECISIONS.md)
4. [`docs/adr/`](docs/adr/)
5. [`docs/DORA_MVP1_TEST_STRATEGY.md`](docs/DORA_MVP1_TEST_STRATEGY.md)
6. [`docs/DORA_MVP1_IMPLEMENTATION_BACKLOG.md`](docs/DORA_MVP1_IMPLEMENTATION_BACKLOG.md)
7. [`docs/DORA_MVP1_STAGE_STATUS.md`](docs/DORA_MVP1_STAGE_STATUS.md)

See [`CONTRIBUTING.md`](CONTRIBUTING.md) and [`AGENTS.md`](AGENTS.md) before making changes.

## Bootstrap build

Prerequisites:

- JDK 17;
- Android SDK Platform 36;
- Android SDK Build Tools 36.0.0.

On macOS/Linux:

```bash
cd android
./gradlew spotlessCheck detekt
./gradlew :app:testDebugUnitTest :core:common:testDebugUnitTest :core:model:testDebugUnitTest :core:testing:testDebugUnitTest
./gradlew :app:compileDebugAndroidTestKotlin
./gradlew :app:lintDebug :core:common:lintDebug :core:model:lintDebug :core:testing:lintDebug :app:assembleDebug
```

On Windows, use the same tasks through `gradlew.bat`.

With an emulator or physical device connected, run the Compose UI suite:

```bash
./gradlew :app:connectedDebugAndroidTest
```

The test levels, required environments and future physical/release gates are defined in [`docs/DORA_MVP1_TEST_STRATEGY.md`](docs/DORA_MVP1_TEST_STRATEGY.md). Stage 00 requires instrumentation tests to compile in CI; device execution becomes mandatory only with a scoped Android behavior task.

Validate the handoff and governance artifacts from the repository root:

```bash
python3 tools/validate_stage00.py
```

After assembling an APK, verify the admitted native inventory and 16-KiB ELF alignment:

```bash
python3 tools/verify_apk_native_alignment.py android/app/build/outputs/apk/debug/app-debug.apk
```

Dependency lockfiles are generated artifacts but are committed. Update them only in an intentional dependency PR by running the canonical Gradle tasks with `--write-locks`, reviewing every resolved version, and rerunning the normal CI command without that flag.

The CI job uploads `dora-stage00-debug-apk` for seven days. It contains only the debug bootstrap application ID and no production signing credentials or configuration.
