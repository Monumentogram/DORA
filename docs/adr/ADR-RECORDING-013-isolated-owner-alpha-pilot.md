# ADR-RECORDING-013 — Operator-free isolated owner Alpha pilot

Status: owner approved SOFTWARE_IMPLEMENTATION_ONLY, 2026-10-21.
Baseline: `52e4ce8bdf074ec021ae52c3c38ad9d26c24e128`.

The owner authorizes a separate signed release-derived candidate, ordinary recording
acceptance UI, synthetic checks and a prospective physical protocol. This decision
does not authorize physical installation, launch, distribution or historical access.
PR107 is an independent preservation investigation and is not a dependency.

## Identity and runtime

Build type `ownerPilot` initializes from release, selects release library variants,
and uses `com.monumentogram.dora.alpha.pilot`, code 1, version
`0.1.0-owner-alpha.1`. Its label identifies the owner pilot. The source namespace
stays unchanged. No alphaTest operator source set enters this variant. The final
APK uses a dedicated owner-local signer, different from production, diagnostic
and Android debug identities. CI test signing is disposable evidence only.

Reuse AudioRecord, microphone FGS, Silero, SQLCipher, Keystore, App Lock, Recovery,
durability/storage admission and the exact 4,800,000-frame ceiling. No new engine,
schema, key format, Cloud or ASR. Installing a different runtime package does not
replace the package code of the production, debug or diagnostic packages.

## Ordinary acceptance

Extend the existing saved-recording surface: show the exact local recording ID,
completion state and durable frames; offer authenticated verification of finalized
audio and explicit audio deletion. Verification streams all OriginalAudioPort
frames, validates contiguous counts and identity, clears borrowed PCM, and displays
metadata only. It is integrity/readback verification, not audible playback or an
export. Interrupted records retain their partial state and existing Resume path.
No verification success is inferred from catalog counts alone.

All work uses the existing real App Lock/session-bound runtime. No user-entered
paths, identities, command intents or bypass credentials. Blocking persistence
work runs off main. Discovery is explicit, avoiding an auth/reload loop. Revoke
visible private state on background/lock; stale callbacks cannot publish success.
Deletion reuses the existing exact-asset, one-use confirmation and checks durable
tombstone plus absence of retained audio/key targets. It does not promise physical
overwrite or delete unrelated structured data. Explicit deletion need not be gated
by a successful readback (DEC-013).

The app uses ordinary product authorization, not the diagnostic operator's user
admission form. The future host protocol independently requires full Secondary
User ID 10 / serial 10, fresh topology and exact package/signer checks before any
installation. App sandbox isolation is distinct from a proof about historical data.

## Acceptance and unchanged gates

Require actual pilot DEX/manifest negatives, exact dependency graph/SBOM, native
alignment and pinned private VAD, two clean signed builds, API28/API36 real
credential/encrypted tests and accepted Stage8 regressions at the final commit.
All test control and synthetic PIN handling stays outside the distributed APK.
Compact/large-font UI, verification failures, cancellation/revocation, deletion,
package isolation and stale sessions need explicit tests and independent review.

`DEV-SECURITY-RESTORE-BEFORE-ALPHA-CLOSE = OPEN`, `STAGE_8_6 = NOT_READY`,
`LONG02_ROOT_CAUSE = NOT_PROVEN`, `PRODUCTION_UPGRADE_SAFETY = UNPROVEN`.
Software PASS is specific to this pilot, never production equivalence or physical PASS.

## Owner-approved version-label P2 remediation — 2026-10-22

The installed version 1 candidate at source `37677527ba8e6261c17e1be27819045512c8f442`
showed `DORA Alpha 0.1.0-alpha.3 (5)` instead of its actual package identity
`0.1.0-owner-alpha.1 (1)`. Physical acceptance stopped before recording.
The defaultConfig generated resource shadowed the ownerPilot XML override and
was evaluated before variant output version overrides (and debug suffixes).

The successor is code 2 / `0.1.0-owner-alpha.2`, same package and owner signer.
Generate one `alpha_version_label` lazily from the final variant output name/code;
remove both competing definitions. Multiple outputs fail closed until explicitly
supported. Production remains code 5 / `0.1.0-alpha.3`; debug includes `-debug`.
No runtime, schema, Recovery, security, VAD or model behavior changes.

The actual APK audit rejects missing, conflicting/qualified or mismatched labels.
Independent PackageManager expectations drive normal MainActivity display and
recreation tests for debug, alphaTest and ownerPilot on API28/API36. Release is
checked through its actual packaged resource. Require all sixteen mandatory CI
jobs at the exact successor SHA plus two identical clean signed owner builds.
Historical v1 identity/finding and private acceptance evidence remain preserved.
This decision authorizes software remediation only, not another POCO install.
