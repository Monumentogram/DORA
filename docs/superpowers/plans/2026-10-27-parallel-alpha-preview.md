# Parallel Alpha Preview implementation plan

> **For agentic workers:** Use superpowers:executing-plans. The supplied Owner specification authorizes preparation, commit/push and Draft PR.

**Goal:** Private signed ARM64 Preview v4 alongside untouched Pilot v2.
**Architecture:** New release-derived ownerPreview at com.monumentogram.dora.alpha.preview. Main code, crypto and native bytes inherited unchanged from 953879250fcd2a6cb9fc4b605126a9806a641f5b. Dedicated Preview audit entrypoints preserve frozen Pilot tooling.
**Tech Stack:** Gradle/Kotlin, Python, Android SDK, existing private native inputs and Owner certificate.
**Spec:** Owner request 2026-10-27; ADR-0024.

## Global constraints and review focus
No POCO commands; physical runtime NOT_RUN; Stage8.6 NOT_READY. No Pilot update/migration, shared UID/storage, new bypass or PIN. Distinct package, authorities, UID and Keystore namespace. 300-second cap; first physical recording at most60seconds. Private inputs/APKs outside Git; model through UI, expiry exclusive2026-11-02T21:00:00Z. Frozen baseline and historical branches unchanged; Draft PR only. Fail closed on identity collision, bad native inventory or failed isolation.

### Task 1: Preview boundary
- [ ] RED host tests for Preview identity, authorities, build commands and SBOM.
- [ ] Add release-derived variant/display identity and reuse instrumented sources.
- [ ] Dedicated build/audit/SBOM, pinned base and branch; generate dependency locks.
- [ ] Pass host/JVM/lint/format checks.

### Task 2: Evidence
- [ ] API28/36 Preview UI/persistence and Stage9 checks; dual-package data/Keystore isolation.
- [ ] Two clean strict no-cache private builds, unsigned/signed byte comparison, actual APK audit.
- [ ] Independent P0/P1 source/artifact review.

### Task 3: Handoff
- [ ] Future physical protocol with NEW approval and fresh v2/user10/process/free-space checks.
- [ ] Commit/push stacked Draft PR, exact-head CI and sanitized report.
- [ ] Return SHA/private APK path and physical NOT_RUN.
