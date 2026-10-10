# Protected catalog investigation — stopped before physical admission

Verdict: **BLOCKED / PROTECTED_SOURCE_RECOVERY_POLICY_CONFLICT**.
Baseline: `b2d9408eaf06c04ab8ba363c8cda45fef01a5bfb`.
Branch: `stage/8.6-poco-recording-acceptance`; PR99 remains draft/open/unmerged.
The publication commit is a successor tools/tests/evidence commit; it does not
implement or admit a runtime isolation policy.

## What the owner decision permits

[ADR-RECORDING-008](../../../adr/ADR-RECORDING-008-protected-shared-catalog-exception.md)
records the approved exception for exactly three shared SQLCipher files. Their
canonical paths and original hashes were frozen privately before inspection.
That exception grants no mutation of protected logical rows, keys or namespaces.

## Fresh physical evidence

The existing physical product APK was not updated:
`c2adf61e7834b6202f62da80b56bd0b4447f71756f4b38f0786715e1280895c1`.
Only the independently reviewed diagnostic helper was installed. It blocked both
ordinary vault-opening routes before App Lock/Activity launch. Direct SQLCipher
OPEN_READONLY inspected an authenticated private encrypted copy, without Room,
normal Recovery, migration, creation fallback or product recording.

- All **8,550 original file hashes unchanged**, including the three shared files.
- Complete schema v3: **15 tables / 11,395 rows**; 47 live sources, 46 originals
  plus LONG01. Existing tombstones and applied intents were retained, not filtered.
- **1,710 existing AES keys** inventoried; private random challenge encryption and
  authenticated decryption verified all 1,710. No key generated, replaced, exported
  or deleted on POCO. These proofs establish the initial comparison baseline;
  a future after-campaign comparison must verify the same retained challenges.
- LONG01 remains unfinalized: **388 claims / 31,040,000 catalog frames**. Its single
  intent row is already applied, not an unapplied pending intent. No new PCM read
  was performed; the earlier 388-unit authenticated-prefix evidence stays historical.
- Microphone OFF, recording FGS absent. No normal product startup/reconcile or
  physical recording was admitted. No audio was created or deleted.

Public receipts are reduced counts, states and aggregate hashes. Complete catalog
rows, paths, aliases and key challenges remain private. This snapshot does not
prove that a future product launch is safe.

## New conflict and causal evidence

One original other than LONG01 has 127 committed units / 10,160,000 claimed frames,
two technical OPENs, one technical CLOSE and no interrupted-recovery marker.
Its final technical interval is open. A synthetic structural reproduction of
`RecoveryMetadata.plan` produces two additions over `[9600000,10160000)`:
TECHNICAL_CLOSE and RECOVERY_INTERRUPTED, both with RECOVERY reason.

The normal `RecordingRecoveryReader.metadata` path can persist these through
`journal.retainSegmentation` following successful resumable-prefix validation.
That precondition was not exercised on the protected original; the conclusion is
a proven prospective mutation path, not a claim that a mutation occurred or that
this original's PCM has now been authenticated.

LONG01-only isolation cannot establish the required BEFORE == AFTER for all 46
originals. Extending the exception silently would conflict with the owner's
requirement to preserve normal Recovery for other recordings. Independent review
therefore stopped physical admission under the owner's new-risk stop rule.

The [concrete proposed policy](owner-decision-proposal.md) requests temporary
diagnostic read-only protection for all 47 exact historical sources. It is
**not approved or implemented**. No product runtime code was changed by this work.

## Validation scope and retained attempts

The helper's synthetic attempts 01–03 failed during development: initially an
unlocalized reflective exception, then exact table-inventory rejection, then a
diagnostic identifying absent android_metadata. The accepted SQLCipher schema has
15 tables, not 16; the inspector now verifies that exact inventory and admitted
DDL/FK schema. Attempts 04, 05 and 06 completed. All attempts remain preserved
privately; none is a physical recording attempt or acceptance PASS.

Final emulator attempt 06 includes WAL-backed committed-row visibility,
OPEN_READONLY write denial, immutable source copies, prior forensic negative
controls, and same-alias key replacement/missing-key rejection. Only then was
the single physical protected snapshot executed successfully.

Host comparator tests cover exact typed row multisets, forbidden historical
updates/deletions/additions, schema/binding changes, cross-source namespace reuse,
new-run ownership and retained key challenge verification. The comparator accepts
already authenticated complete receipts; it is not an authentication mechanism
or authorization to mutate a source. Validation results and reviews accompany
this report. Historical Recovery 414/414 and baseline CI run 37788747312 (4/4,
API28/API36 inventory 138 and Recovery 22/22) are predecessor evidence, not fresh
executions or successor-SHA CI results.

Fresh local results: core audio 217 passed / 1 inherited Windows symlink skip;
71 non-battery host tests passed, including 23 snapshot controls; the broader
logical-recovery host discovery passed 134 tests (includes those same 71, not
205 distinct tests). Spotless and exact-argument Detekt passed. The repository
contract validator passed with every accepted product override unchanged.
Both independent reviewers report zero unresolved publication P0/P1/P2, while
the independent physical-admission verdict remains BLOCKED. Publication audit
checked the entire delta against private identities and five positive canaries.

## Remaining gates and unchanged governance

Runtime source isolation, release/debug fence, new APK reproducibility/install,
physical UI/recording/readback, terminal diagnostic injection, owned test deletion,
after-campaign preservation and new campaign are **NOT_RUN / NOT_ADMITTED**.
No physical test recording exists to clean up. Private diagnostic receipts and
encrypted inspection copies remain retained for evidence; no unrelated data was
deleted. No product rollback, pm clear or uninstall was used.

Stage 8.6 remains NOT_READY / LONG_SCREEN_OFF_ACCEPTANCE_PENDING. The historical
NOT_READY / POCO_SCREEN_OFF_CAPTURE_FAILED and UNKNOWN LONG01 cause remain intact.
The owner-authorized successor hour/60 cycles are stopped at the isolation gate.
Battery efficiency remains DEFERRED / NON_BLOCKING_FOR_ALPHA; Battery Saver remains
DEFERRED / USB_POWER_CONSTRAINT. Sheet C78 unchanged. Group D / Stage 9 / Cloud / ASR
NOT_STARTED. VAD artifacts/profile and all product safety thresholds unchanged.
DEV-SECURITY-RESTORE-BEFORE-ALPHA-CLOSE remains OPEN; PERF-REC-001 deferred/non-blocking.
