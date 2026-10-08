# Protected historical Recovery successor

Owner authority: [ADR-RECORDING-009](../../../adr/ADR-RECORDING-009-protected-historical-recovery.md).
Starting source: `20057696c556a354d08e007dfcba736ff217fe6d`, PR99 draft/open/unmerged.
Implementation and synthetic verification are in progress. Physical isolation,
new APK installation, bounded smoke and the successor campaign are NOT_RUN.
No Stage 8.6 PASS is asserted. Earlier failures and the policy-conflict receipt in
`protected-isolation-01` remain historical; only owner authorization is superseded.

The signed debug candidate carries a SHA-256 pin, never private identities. The
private no-backup policy binds exact47 live historical triples, all historical
component/run namespaces, authenticated vault binding, device profile and retained
snapshot. It is immutable during a vault open and revalidated on every reopen.
Missing, altered or half-removed pin/policy fails closed. Release rejects activation.
Removing the policy is not a supported recovery or security bypass.

| Entrypoint | Guard before mutation |
| --- | --- |
| Startup / authenticated open | Policy before root bootstrap; existing v3 schema, Room identity, vault binding and all47 source triples before Room callback |
| Discovery / refresh / pagination / continuation lookup | Exact protected source yields unavailable/non-resumable before RecordingRecoveryReader |
| Original audio acquire / inspect / extract | Guard before any reference retention, reconciliation or quarantine |
| Writer append / finalize / reconcile / segmentation | Source/component checks; catalog lease owner denied before commit/transaction |
| Bootstrap / publication / quarantine transaction | Protected lease denied before beginTransaction; every run must belong to the held new source |
| Key creation / removal | Positive run ownership before keystore operation |
| File create / rename / fsync / quarantine / deletion | Typed storage guards before adapter call; bound handles checked again before writes |
| Diagnostic forensic inspection | Separate accepted copied-database SQLCipher read-only inspector; never normal Recovery |

Synthetic API36 tests found a real missing-master-table admission hole: Room can
repair its own metadata on open. The negative canary failed before the fix; the
successor rejects missing/changed Room identity before callback. All15 additive
instrumentation methods now pass, including complete new-source recovery,
finalization, authenticated readback and deletion with historical rows/files equal.
This is synthetic evidence, not proof of POCO preservation or physical recording.

The new15-method emulator suite runs separately after the unchanged21-method UI
suite. Historical138 persistence methods, no-credential behavior,22 Recovery phases
and API28/API36 time budgets are unchanged. The fresh Recovery host run remains
414/414. Final exact-SHA CI and physical review remain required.

The operator has verified wireless ADB against the private saved POCO identity.
Wireless addresses/identifiers remain private. This changes only control transport;
it does not admit battery measurements or change the future powered-run protocol.
No DORA APK has been updated during implementation verification.

Original8550 file inventory remains the preservation baseline: only the exact3
previously admitted shared SQLCipher files may change for positively owned new test
objects. All8547 other files, historical rows and retained1710 key proofs must be
verified before/after the physical isolation smoke. That gate precedes fresh60
cycles and the owner-authorized one-hour run. Stop on a protected-source risk.

Battery efficiency remains deferred/non-blocking for Alpha. Sheet unchanged;
Stage9/GroupD/Cloud/ASR not started; security restoration OPEN. LONG01 historical
cause remains UNPROVEN regardless of any successor result.

## Successor physical checkpoint on c0f1587

The exact protected candidate was installed in place over wireless ADB with signing
continuity. Two independent clean builds are APK-byte-identical (170 entries,
19 DEX files); private pin and admitted VAD binaries were not published.

The one bounded isolation smoke passed: 30,183 ms screen-off and 483,200 canonical
frames admitted, durable and fully authenticated on readback. Real sherpa/Silero
ran; no gaps, duplicates, corruption, read errors or short reads occurred. The
controlled no-audio storage rejection produced the fresh bounded asynchronous
terminal receipt. Exact-owned product deletion verified49 absent targets.
Physical preflight exposes Start/Cancel and the storage budget; Start remains
disabled until acknowledgement, Cancel returns home, microphone stays off.

All47 historical source projections,11,395 prior rows and1,710 key challenges are
unchanged. All8,547 immutable files remain byte-identical. The permitted shared
main database changed; its exact WAL/SHM sidecars are absent after close. Final
vault count is8,548, with no new remaining vault files. The initial overly broad
all8,550-presence assertion was retained as a verifier failure and corrected to
the already approved shared-file exception. This is not8550 unchanged and does
not prove the exact syscall timing of sidecar removal.

Isolation physical evidence is a subgate; final exact-SHA CI is still running.
Fresh60 cycles and the successor hour are NOT_RUN at this checkpoint. Historical
LONG01 failure/cause, battery deferral and overall Stage8.6 nonacceptance remain.
The seven dated receipts here add evidence without replacing previous attempts.
