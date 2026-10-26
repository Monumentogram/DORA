# ARM82 Owner Alpha build implementation plan

> **For agentic workers:** Use superpowers:executing-plans; this owner request authorizes implementation without a separate planning approval.

**Goal:** Produce a reproducible, privately signed Pilot v4 APK and an exact prospective physical acceptance protocol.

**Architecture:** Reuse the release-derived ownerPilot variant and existing owner certificate. Package the already pinned ARM82 Whisper adapter/runtime alongside private VAD; extend actual APK and SBOM checks. Keep the original Stage9 product behavior and historical evidence.

**Tech Stack:** Android/Kotlin, Gradle, Python host validators, SDK apksigner/zipalign, existing SQLCipher/Keystore and Whisper JNI.

**Spec:** Owner request of 2026-10-26, recorded by ADR-0023 and the successor contract.

## Global constraints

- Baseline PR112 verified at 4d1a92311dcdfd3d7f7bbcf53625caa9c1bdfdba; stacked branch only, no merge/rebase/force-push/main edits.
- No POCO commands, including read-only ADB inventory. No physical install/update/launch or Android user/PIN changes.
- Same model SHA256 49c8fb02b65e6049d5fa6c04f81f53b867b5ec9540406812c643f177317f779f; use expires exclusively 2026-11-02T21:00:00Z.
- Private signed APK, weights, signing secrets and audio remain outside Git/public CI. No new keys.
- ADR-0021 applies; max 300 seconds / 4,800,000 frames. Stage8.6 NOT_READY remains.

## Review focus

- Package code updates are global across Android users; user10 alone does not prove safe upgrade.
- Private ASR/VAD collision, missing dependency, substituted JNI, or accidentally packaged model must fail audit.
- Same signer/package and increased code must match the actual compiled label and two complete APK byte streams.
- Pilot schema3-to4 migration must preserve source rows, encrypted data, versions and corrections without key replacement.
- Physical authorization and model expiry must fail closed independently of software/build readiness.

### Task 1: Successor identity and controlled build boundary

Files: app/build.gradle.kts, owner_alpha_pilot_{build,artifact,sbom}.py, version_label.py, new ARM82 audit module/tests and successor contract/ADR.
Consumes: existing pinned private native receipts, VAD AAR, existing signing directory.
Produces: v4 build commands and exact package/source/native/SBOM audit.

- [ ] Write negative tests for absent/substituted ASR, packaged weights, source mismatch and v4 identity; observe RED.
- [ ] Implement v4 identity and fail-closed local build/audit; retain existing denials.
- [ ] Run host suites and static checks; expect PASS. Commit.

### Task 2: Software verification and private reproducible APK

Files: relevant CI branch triggers and scoped tests; private output directory outside Git.
Consumes: clean Task1 source; produces two same-byte signed APKs and content-free receipts.

- [ ] Run JVM, lint, static analysis and required API28/36 release-derived/synthetic checks at exact source.
- [ ] Verify model/source/native pins and existing certificate, build twice clean without build/config caches, compare full signed bytes.
- [ ] Inspect Manifest/DEX/resources/native dependencies/alignment; generate complete SBOM including ASR.
- [ ] Obtain independent exact-APK safety review; fix evidenced P0/P1 and verify.

### Task 3: Physical protocol and evidence handoff

Files: dedicated prospective protocol, sanitized result/receipts, current status/decision/backlog entries.
Consumes: exact APK identity and review; produces reviewable owner approval package.

- [ ] Specify fresh future user/package topology checks, cross-user upgrade stop conditions, exact signer/hash, bounded RU/EN cycle and preservation checks.
- [ ] Record ARM82_DEVICE_RUNTIME=NOT_RUN and zero physical operations; do not claim current device safety.
- [ ] Preserve historical evidence, commit/push, create stacked draft PR, verify exact final SHA CI and clean tree.
- [ ] Stop READY_FOR_OWNER_APPROVAL / NO_PHYSICAL_ACTIONS_PERFORMED only if build prerequisites passed.
