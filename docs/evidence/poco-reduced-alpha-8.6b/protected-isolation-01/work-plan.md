# Protected-source isolation execution plan

Execution disposition: BLOCKED / PROTECTED_SOURCE_RECOVERY_POLICY_CONFLICT.
Step 1 completed. Synthetic comparison controls were added; runtime implementation
and physical steps 3–8 are stopped. Another original can require mutating Recovery.
See README.md and owner-decision-proposal.md. The original conditional plan below
is retained; it is not evidence that those later steps ran.

Owner has explicitly selected autonomous implementation/review/execution. This
plan does not request another approval of ordinary permitted shared-file writes.
Scope and exception: ADR-RECORDING-008. Baseline b2d9408. No physical mutation before
the isolation implementation and negative controls are independently admitted.

1. Preserve the initial exact Git/PR/device/file state. Extend the accepted
   snapshot-only inspector to export a private canonical whole-catalog projection,
   source/run ownership and key-state manifest. Keep identifiers local, emit only
   content-free receipts. Verify all original bytes on every inspection exit.
2. Freeze the private exact policy: vault/schema/shared paths, all historical
   identity namespaces, protected source states, LONG01 and explicit new test
   identities. Add negative controls for missing/mismatched policy, component
   collisions, row changes, key changes and forbidden file changes.
3. Implement a small immutable policy and debug-only loader pinned to an exact
   private configuration digest. Default CI remains unactivated. Release rejects
   activation. Active missing/invalid configuration rejects original vault open;
   no create/migration fallback. Write failing synthetic tests first.
4. Enforce source and run guards before recovery/read routes that can mutate,
   journal transaction boundaries, key mutation and filesystem publication or
   deletion. Protect LONG01 before discovery/manual/page/continuation paths. Do
   not globally disable Recovery or enlarge the 256,000-frame durability fence.
5. Prove all bypass/restart/recreation/release cases on synthetic data and real
   encrypted emulator storage. Run relevant regressions and independent review.
   Two independent clean private builds must have identical runtime payloads.
6. Snapshot and install in place only with verified signing/data continuity.
   Predeclare one 30-second isolation smoke: normal preflight/Start/capture/Stop/
   finalize/authenticated readback, bounded diagnostics injection, owned deletion.
   Authenticate complete protected before/after state and all 8,547 file hashes;
   identify every new artifact by the new source. No mutation repair on failure.
7. Only after reviewed physical isolation PASS: freeze the new exact-APK campaign
   receipts/IDs, execute 60 short cycles and one distinct long retry with safety
   watchdog. Preserve every failed attempt and never recycle the old LONG01 ID.
8. Run remaining applicable functional checks, preservation/cleanup, exact-SHA
   CI4/4, independent review and publication canaries. Publish truthful result;
   no Stage8.6/Sheet/later-stage closure claim without separate decision.

Critical review focus: shared-catalog exception is not protected-row permission;
run IDs must resolve to allowed new sources before key/file mutation; acquiring a
catalog lease alone is not mutation protection; ordinary reads may reconcile or
retain references; diagnostics must not expose identifiers/PCM; old physical
evidence cannot substitute for the new APK.
