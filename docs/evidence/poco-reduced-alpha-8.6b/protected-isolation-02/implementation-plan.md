# Protected historical Recovery implementation plan

Execution: native implementation with independent review. Owner explicitly
authorizes autonomous work under ADR-RECORDING-009; no repeat scope approval.

Goal: protect the exact 47 historical sources before mutation and retain normal
Recovery for newly created test sources, then prove preservation on the exact APK.

Architecture: immutable private snapshot-bound policy loaded before vault open;
debug-only activation, release denial. Lower catalog lease/operation/run ownership
fences plus key/storage guards. Ordinary historical Recovery returns unavailable
without reconciliation; the separately admitted authenticated forensic inspector
remains the read-only access mechanism. No historical row or schema migration.

- [ ] Pure policy: synthetic exact47 source/component/run collision tests first;
      ordinary mode unchanged, private pin validation and release-denial tests.
- [ ] Loader and composition: pin-only local build input, immutable private policy,
      missing/corrupt mismatch denial; exact authenticated vault/schema binding
      before Room create/migration; restart revalidation with no implicit fallback.
- [ ] Runtime boundaries: source lease/create/operation/run checks, component
      reservation and segmentation validation, key and file mutation guards;
      discovery/manual/page/continuation/deletion cannot bypass lower fences.
- [ ] Synthetic encrypted device tests: all protected attempts leave files/rows/
      keys identical; new-source Start/recovery/finalize/delete still work;
      release/inactive and existing API28/API36 regressions remain exact.
- [ ] Independent review before any POCO update; two clean private builds and
      exact signing/payload identity; retained originals unchanged before install.
- [ ] One predeclared bounded isolation recording on exact new APK, full readback,
      diagnostics and owned deletion; verify 47 sources/8547 files plus classify
      the three shared-file changes, using retained key challenges.
- [ ] After isolation PASS only: fresh60 cycles and distinct 3600-second USB
      screen-off recording, required functional/resource checks; stop on defect.
- [ ] Final preservation, publication canaries, independent acceptance and exact
      final-SHA CI4/4. Historical failures and all deferrals stay explicit.

Review focus: alternate vault open/create, direct catalog/run operations,
historical component reuse, partial policy removal/restart, key ownership before
filesystem writes, automatic metadata repair, debug policy accidentally active in
release, and falsely transferring old APK physical proof. No normal POCO startup
or new recording before the independently reviewed implementation gate.
