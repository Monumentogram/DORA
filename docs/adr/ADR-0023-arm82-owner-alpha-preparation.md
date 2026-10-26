# ADR-0023 — ARM82 Owner Alpha preparation

Date: 2026-10-26. Status: accepted for software/build preparation by explicit Owner request.
Baseline: PR112 `4d1a92311dcdfd3d7f7bbcf53625caa9c1bdfdba`; PR111 remains its ancestor.

## Decision

Build the existing release-derived `ownerPilot` variant as code4 /
`0.1.0-owner-alpha.4`, same `com.monumentogram.dora.alpha.pilot` package and
existing certificate `a5f865ce5d28056429b323ede48f53e9ae8ad06880ea20ed242c2a2ba274603d`.
This is a successor to Pilot v3, not a new package/key or production rollout.
Retain Stage8 capture/Pause/Resume, exact 300-second limit, private VAD, SQLCipher,
Keystore/AEAD, backup exclusions and ADR-0021 noninteractive local authority.

Package six exact ARM64 ASR libraries from Stage9's pinned receipt. Keep the four
Pilot v3 native libraries byte-identical. Validate every native byte hash, AArch64
machine, ELF/ZIP16KB layout, JNI exports and dependency closure. The adapter must
not eagerly depend on specialized ARM82 libraries before its CPU capability guard.
Extend the actual release-variant graph/SBOM with ASR and retained notices.

Two independent clean strict builds, without build/configuration caches, must yield
identical complete unsigned and signed bytes. Sign outside Gradle using the existing
owner-local key. No private APK/key/model/audio enters Git or public CI. Model import
continues through the ordinary document picker and exact digest/size/expiry checks.
The approved model is unchanged, valid through 2026-11-02 inclusive Europe/Moscow
(exclusive cutoff `2026-11-02T21:00:00Z`). No ASR weights are bundled.

## Successor verification matrix

The v2-era sixteen-job matrix remains historical. For this PIN-free schema4
successor, the following twelve exact-head jobs preserve its applicable assertions:

- `owner-alpha-pilot-software`: strict compilation, JVM, lint, source/privacy
  preservation, host denial tests, actual release-derived Manifest/DEX/backup/label
  audit with disposable CI signing. Replaces the four overlapping old software jobs.
- `owner-alpha-pilot-api28` and `owner-alpha-pilot-api36`: Pilot ordinary UI,
  Pause/Resume/readback/deletion/restart, secure/no-credential authority and cross-user
  isolation, plus the inherited encrypted persistence inventory. Replaces six old
  encrypted/isolated/operator/Pilot API jobs. Obsolete requirements to display an
  AppLock prompt or ship development overrides are superseded by ADR-0021 and actual
  APK absence/denial assertions, not relabelled PASS.
- Six `version-label-{debug,alphaTest,ownerPilot}-api{28,36}` jobs remain required.
- Stage9 `software`, `emulator (28)` and `emulator (36)` add the transcript/editor,
  migration, source/cap and separate-process checks (20 per API).

The versioned successor persistence inventory accounts for all167 discovered
methods:160 bulk methods,2 separate SEED/READ phases in Stage9,2 inherited long-test
deferrals,3 explicitly NOT_QUALIFIED positive historical diagnostic-v3 methods.
Their schema3-only factory intentionally rejects schema4; modifying that historical
gate or accessing LONG-01/LONG-02 is outside this task. Remaining diagnostic denial
and acquisition methods stay in the bulk suite. A denial at schema admission is
not evidence of positive historical decryption/key continuity. Ordinary published
v1/v2/v3 migrations and missing-key product failures remain mandatory. No selected
test skip is accepted; inventory groups must be exhaustive and disjoint.

## Physical boundary

`ARM82_DEVICE_RUNTIME = NOT_RUN`. x86 functional evidence is applicable to the shared
product flow, not ARM82 execution. Fresh device safety cannot be established in this
task because ALL POCO commands, including read-only ADB, are prohibited by the Owner.
The [prospective protocol](../ARM82_OWNER_ALPHA_PHYSICAL_PROTOCOL.md) requires a
separate exact-APK approval and fresh fail-closed preflight before any mutation.

An Android package update changes code globally across users. `--user 10` is not
proof of isolation. Pilot must be confirmed installed only in full Secondary user10,
serial10. This successor performs the ordinary schema3→4 migration; no destructive
fallback, key replacement, production/history migration or rollback guarantee exists.
Stage8.6 NOT_READY, security restoration OPEN, historical diagnostic-v3 qualification,
production upgrade safety and general distribution remain separate gates.
