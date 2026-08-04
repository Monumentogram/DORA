# ADR-0001: Android Stage 00 bootstrap

Status: Accepted for bootstrap; production identity remains provisional\
Date: 4 August 2026\
Decision owners: Stage 00 engineering\
Related: DEC-005, DEC-006, DEC-015; readiness RDY-001/RDY-026

## Context

Dora has an approved documentary baseline but no Android project. Stage 00 needs a reproducible build and CI without implementing product behavior or prematurely admitting storage, native ML, recording or backend dependencies.

The technical plan places the Android source under `android/`, uses Kotlin/JVM 17, Gradle Kotlin DSL, version catalogs, Compose and a gradually modularized architecture. It selects `minSdk 28` and target API 36 as the current release floor, with an API 37 migration gate.

Production package ownership, signing custody and developer/package verification are not yet approved. Publishing a guessed application ID would be an irreversible error.

## Decision

1. Put the standalone Android Gradle build under `android/`.
2. Use JVM/Java 17, Kotlin, Compose Material 3, Gradle Kotlin DSL and a version catalog.
3. Set `minSdk 28`, `compileSdk 36` and `targetSdk 36`.
4. Create only modules needed to prove the boundary: `:app`, `:core:common`, `:core:model` and `:core:testing`; avoid empty feature/recording/ML/backend modules.
5. Provide a neutral placeholder Activity and smoke tests. It is not Home, onboarding or another Dora feature screen.
6. Use `com.monumentogram.dora.bootstrap` as an explicitly non-release application ID. Do not register, verify, sign for distribution or publish it.
7. Keep Room/SQLCipher/Tink/WorkManager/Hilt/native ML/model weights/backend clients out of Stage 00 unless required by the build itself. Admit them only in a later scoped PR with evidence.
8. CI validates documentation artifacts, Gradle wrapper, unit tests, lint and debug assembly with read-only repository permissions.
9. AndroidX artifacts stay on releases whose AAR metadata supports `compileSdk 36`; dependencies requiring API 36.1/37 enter only through the explicit API 37 migration gate.

## Consequences

Positive:

- reproducible minimal surface for future PoC work;
- no false claim that feature architecture or native feasibility is complete;
- toolchain/API choices are tested early;
- dependency and module count remain small.

Costs/risks:

- production ID must be changed before internal/release signing;
- later Hilt/Room/native convention plugins will be admitted separately;
- placeholder UI is intentionally disposable and must not become an accidental product design.

## Verification

- clean wrapper invocation on Java 17;
- JVM unit tests for each applicable module;
- Android lint and debug assembly;
- CI runs the same checks from `android/`;
- APK is debug-only, contains no native model libraries/weights and uses the bootstrap ID;
- every transitive `.so` is explicitly allowlisted and passes both ELF and APK 16-KiB alignment checks.

## Supersession rule

A later ADR may replace the application ID or module/toolchain choices only with owner approval, migration impact and updated CI. This ADR does not approve production signing or publication.
