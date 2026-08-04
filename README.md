# Dora

Dora is an Android local-first meeting assistant. This repository currently contains the approved MVP 1 technical/design baseline and the Stage 00 implementation bootstrap. Product features are intentionally not implemented yet.

## Sources of truth

1. [`docs/DORA_MVP1_TECHNICAL_PLAN.md`](docs/DORA_MVP1_TECHNICAL_PLAN.md)
2. [`docs/DORA_MVP1_DESIGN_SPEC.md`](docs/DORA_MVP1_DESIGN_SPEC.md)
3. [`docs/DORA_MVP1_PRODUCT_DECISIONS.md`](docs/DORA_MVP1_PRODUCT_DECISIONS.md)
4. [`docs/adr/`](docs/adr/)
5. [`docs/DORA_MVP1_IMPLEMENTATION_BACKLOG.md`](docs/DORA_MVP1_IMPLEMENTATION_BACKLOG.md)
6. [`docs/DORA_MVP1_STAGE_STATUS.md`](docs/DORA_MVP1_STAGE_STATUS.md)

See [`CONTRIBUTING.md`](CONTRIBUTING.md) and [`AGENTS.md`](AGENTS.md) before making changes.

## Bootstrap build

Prerequisites:

- JDK 17;
- Android SDK Platform 36;
- Android SDK Build Tools 36.0.0.

On macOS/Linux:

```bash
cd android
./gradlew :app:testDebugUnitTest :core:common:testDebugUnitTest :core:model:testDebugUnitTest :core:testing:testDebugUnitTest
./gradlew :app:lintDebug :core:common:lintDebug :core:model:lintDebug :core:testing:lintDebug :app:assembleDebug
```

On Windows, use the same tasks through `gradlew.bat`.

Validate the handoff and governance artifacts from the repository root:

```bash
python3 tools/validate_stage00.py
```

After assembling an APK, verify the admitted native inventory and 16-KiB ELF alignment:

```bash
python3 tools/verify_apk_native_alignment.py android/app/build/outputs/apk/debug/app-debug.apk
```

Dependency lockfiles are generated artifacts but are committed. Update them only in an intentional dependency PR by running the canonical Gradle tasks with `--write-locks`, reviewing every resolved version, and rerunning the normal CI command without that flag.
