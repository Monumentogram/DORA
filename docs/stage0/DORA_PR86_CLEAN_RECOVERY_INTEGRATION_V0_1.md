# Recovery clean replacement preparation v0.1

Owner-scoped preparation on 2026-09-30. This record implements the accepted
`CLEAN_REPLACEMENT_PR` reconciliation decision; it is not product integration,
Stage 8 execution, campaign authority, merge authority or full Recovery PASS.

Alpha baseline: `fd943aff885028c6143550ee7bb843798b8105fb` on
`stage/7-alpha-foundation`. It descends from the reconciliation baseline
`4e7742d88377d3d915be904618fc220d479ab25d`. Stage 7 remains
`PASS / ALPHA_FOUNDATION_READY`; 7.3C remains
`PASS / DETERMINISTIC_CLOUD_CONTRACT_HARNESS_READY`; Group C remains
`IN_PROGRESS`; Stage 8 remains `NOT_STARTED`.

## Scope and provenance

The independently reviewed reconciliation selected 42 source paths: 20 measured
Recovery runtime/contracts, 19 regression files, and three test/tooling support
files. Four additional files provide this record, its machine contract and the
additive validator/self-tests. Two necessary Alpha release-validator/test paths
extend the bounded implementation to 48 files: the existing release successor
allowlist only recognized 7.3C and rejected this isolated Recovery delta. The new
adapter requires this exact contract digest and path list; it retains every release
identity, artifact, source and workflow gate. Negative controls reject changed
authority, unknown Recovery paths, product inputs and historical release evidence.
The early release step does not claim Recovery provenance: the unchanged later
Recovery gate fetches its source and verifies the full lifecycle before CI can pass.
The machine contract enumerates every path, original
blob, replacement blob, baseline and evidence authority. Its SHA-256 is pinned in
the validator. The reconciliation report's SHA-256 is also retained there.

The 20 measured implementation/contract files come from accepted app
`79d930d73ce7836c3cf5bec10be85f800936e829`; their blobs equal PR #86 at
`b951bc454d550e33669ebf4f276a4b09177a99ca`. The one-line streaming journal
visibility change is test support. The remaining tests/tooling come from that PR
HEAD, with one explicitly bound test correction described below. All 47 retained
`src/main` files equal the measured source. This composite tree is not the full
52-file measured tree and has no new device measurement.

PR #86's 79 commits and full 124-path diff are not imported. Its historical
campaign writer intentionally abandons an unfinalized Tink stream; it, its wiring,
commit observer, PCM fixture and SQLite metadata serializer remain excluded.
Campaign/device runners, forensic tools, obsolete admission profiles, workflow,
lockfile and old current-status documents are excluded. Current Alpha workflow,
Cloud/privacy governance, frozen 7.2C/D/E and the 7.3C harness stay intact.

Accepted historical evidence remains source-specific: reduced E36 114/114
requirements, 165/165 variants, original 121 PASS and 44 accepted expected-negative
FAIL; API33 7/7; physical 9/9 with the original STREAM K12 host FAIL and separate
offline derived PASS. These denominators are not summed. E36 applicability is
113/164 reused plus final TRU-03 1/1, not 165 new runs at a new source. The accepted
17-entry closeout index/archive hashes were independently rechecked. Raw data,
private paths, device identifiers and signing material are not published.

Historical Recovery decisions remain immutable references at PR #86 HEAD:
`docs/adr/ADR-0008-stream-surviving-checkpoint-prefix.md` and
`docs/adr/ADR-0009-microfile-referenced-quarantine.md`. They are not copied into
the current ADR namespace, where those numbers have later Alpha meanings.
Existing ADR-0003 through ADR-0007 and source-specific receipts remain unchanged.

## Boundaries

`:poc:recovery` remains an isolated application/reference module. The product
`:app` has no dependency on it. Both storage candidates are retained as references;
this does not select a production route. Schema 4/5/6 creation/migration composition
is preserved for the PoC; no installed PoC database is imported into the product.
The measured SQLite adapter refuses API28–32 before creating a database. This
API33+ PoC boundary does not revoke the broader minSdk28 product obligation.
K12 persistence evidence does not close K12 consumer access/retirement, full
D1/D2/D5/OEM/endurance campaigns, ADR-AUDIO-001 or production admission.

Product identity remains `com.monumentogram.dora`, `0.1.0-alpha.2` (4).
Accepted signed APK SHA-256:
`ad13ddbd2b01e4e61112ecfb889408576dd04748b9e65627abd6700d1440fa57`.
Accepted SBOM SHA-256:
`230fbb099d99fee5a2dd1f698cddeeac722de83ea0e0be0cb51583f26da165ba`.
No product runtime/build/release dependency/signing/version file changes are
admitted. No new signed release is created. Host assembly, unsigned release graph
and alignment checks are verification only.

## Test synchronization correction

At Alpha, `executor.submit` may start the read and signal `readEntered` before
returning its Future to the submitting thread's `lateinit read` assignment.
Waiting on the earlier signal cannot publish the later assignment. Historical
CI run 36733811000 attempt 1 failed on this exact Alpha SHA; an identical-SHA
rerun passing did not resolve it.

A controlled executor now holds the second `execute` call before returning,
while the read worker signals entry. The original test under this ordering fails
with `UninitializedPropertyAccessException`, retained in private RED evidence.
The PR86 `FutureTask` correction constructs the Future before workers start;
the new regression retains that fix and forces the formerly failing ordering.
Descriptor-open, successful byte read, exactly-once close and escaped-read
rejection assertions remain. Injected assertion, timeout and read failures
exercise cleanup: every barrier is released, workers terminate, the descriptor
closes once, and escaped reads remain rejected. No sleep, retry, timeout increase
or production behavior change is used.

## Additive governance profile

The old accepted-squash profile permits only an unchanged Recovery tree and
therefore correctly rejects this new reference tree. Its historical anchor,
reviewed-source identity, correction ancestry/content, dependency objects and
linear-history proof are reused unchanged by an additive successor. The new
profile binds the exact Alpha ancestor, source objects, whole replacement Recovery
tree and contract digest. The first commit must change exactly the 48 listed
paths; later commits may change only the six named evidence/status paths.
Implementation changes after the first commit, even reverted changes, fail.
Dirty index/worktree, untracked paths, new protected names, altered historical
evidence, product inputs, merges and wrong GitHub event identities fail closed.
Status/backlog updates must retain their baseline bytes as an exact suffix.

The branch is `codex/recovery-clean-replacement`; the draft targets
`stage/7-alpha-foundation`. Current workflow triggers do not automatically run for
that pair, so use its existing `workflow_dispatch` on the exact task branch.
Do not broaden workflow triggers or replace current CI. Both `android-bootstrap`
and `search-smoke`, including all mandatory steps, must pass for implementation
and separately for the final evidence HEAD. New schema regressions execute in
the existing Recovery governance self-test step. Failed attempts stay in evidence.

## Next gate

After independent acceptance and exact-final-SHA CI, the owner must separately
authorize the replacement integration and any PR #86 lifecycle changes. Only then
may PR #86 be cross-linked and closed as superseded without merging, preserving
its SHA/history and omitted-work disposition. This open draft is not an integrated
prerequisite. Product extraction/recording/storage acceptance remains a separate
Stage 8 scope and is not started here.

## Reconciliation matrix

The following accounts for every PR86 changed path against the exact Alpha base.
`No` means the PR blob is not already present; it does not mean Alpha lacks all
behavior in that file. A/M denotes measured app provenance; T denotes selected
PR86 test/tooling provenance; H denotes historical PR86-only context. The machine
contract retains exact blob identities for every row.

| PR86 change | Already present in Alpha? | Accepted evidence | Transfer or exclude | Reason |
|---|---|---|---|---|
| `.github/workflows/android-ci.yml` | No | H: PR86 historical context | EXCLUDE | PR-specific historical branch/metadata/workflow admission. Preserve 92f00f7d content-based lineage repair; no wholesale import. Future exact successor requires narrowly tested admission. |
| `android/poc/recovery/gradle.lockfile` | No | H: PR86 historical context | EXCLUDE | Adds Android-test lint configuration membership only, no dependency coordinate changes. Regenerate minimally if selected tests need it; no wholesale protected-lock overwrite. |
| `android/poc/recovery/src/androidTest/kotlin/com/monumentogram/dora/poc/recovery/candidate/RecoveryCampaignArtifactFaults.kt` | No | H: PR86 historical context | EXCLUDE | One-shot campaign fixture, device identity/kill control, observation or harness regression; not production dependency. |
| `android/poc/recovery/src/androidTest/kotlin/com/monumentogram/dora/poc/recovery/candidate/RecoveryCampaignBootstrapFixtures.kt` | No | H: PR86 historical context | EXCLUDE | One-shot campaign fixture, device identity/kill control, observation or harness regression; not production dependency. |
| `android/poc/recovery/src/androidTest/kotlin/com/monumentogram/dora/poc/recovery/candidate/RecoveryCampaignCleanupFaults.kt` | No | H: PR86 historical context | EXCLUDE | One-shot campaign fixture, device identity/kill control, observation or harness regression; not production dependency. |
| `android/poc/recovery/src/androidTest/kotlin/com/monumentogram/dora/poc/recovery/candidate/RecoveryCampaignInstrumentedTest.kt` | No | H: PR86 historical context | EXCLUDE | One-shot campaign fixture, device identity/kill control, observation or harness regression; not production dependency. |
| `android/poc/recovery/src/androidTest/kotlin/com/monumentogram/dora/poc/recovery/candidate/RecoveryCampaignKeyConfirmationFaults.kt` | No | H: PR86 historical context | EXCLUDE | One-shot campaign fixture, device identity/kill control, observation or harness regression; not production dependency. |
| `android/poc/recovery/src/androidTest/kotlin/com/monumentogram/dora/poc/recovery/candidate/RecoveryCampaignMicrofilePublisher.kt` | No | H: PR86 historical context | EXCLUDE | One-shot campaign fixture, device identity/kill control, observation or harness regression; not production dependency. |
| `android/poc/recovery/src/androidTest/kotlin/com/monumentogram/dora/poc/recovery/candidate/RecoveryCampaignMicrofileRecovery.kt` | No | H: PR86 historical context | EXCLUDE | One-shot campaign fixture, device identity/kill control, observation or harness regression; not production dependency. |
| `android/poc/recovery/src/androidTest/kotlin/com/monumentogram/dora/poc/recovery/candidate/RecoveryCampaignNormalStream.kt` | No | H: PR86 historical context | EXCLUDE | One-shot campaign fixture, device identity/kill control, observation or harness regression; not production dependency. |
| `android/poc/recovery/src/androidTest/kotlin/com/monumentogram/dora/poc/recovery/candidate/RecoveryCampaignPublicationFaults.kt` | No | H: PR86 historical context | EXCLUDE | One-shot campaign fixture, device identity/kill control, observation or harness regression; not production dependency. |
| `android/poc/recovery/src/androidTest/kotlin/com/monumentogram/dora/poc/recovery/candidate/RecoveryCampaignPublicationFaultsTest.kt` | No | H: PR86 historical context | EXCLUDE | One-shot campaign fixture, device identity/kill control, observation or harness regression; not production dependency. |
| `android/poc/recovery/src/androidTest/kotlin/com/monumentogram/dora/poc/recovery/candidate/RecoveryCampaignRunSnapshot.kt` | No | H: PR86 historical context | EXCLUDE | One-shot campaign fixture, device identity/kill control, observation or harness regression; not production dependency. |
| `android/poc/recovery/src/androidTest/kotlin/com/monumentogram/dora/poc/recovery/candidate/RecoveryCampaignStreamPersistenceObservation.kt` | No | H: PR86 historical context | EXCLUDE | One-shot campaign fixture, device identity/kill control, observation or harness regression; not production dependency. |
| `android/poc/recovery/src/androidTest/kotlin/com/monumentogram/dora/poc/recovery/candidate/RecoveryCheckpointAndroidTestFixture.kt` | No | H: PR86 historical context | EXCLUDE | One-shot campaign fixture, device identity/kill control, observation or harness regression; not production dependency. |
| `android/poc/recovery/src/androidTest/kotlin/com/monumentogram/dora/poc/recovery/candidate/RecoveryE36GapiPreflightInstrumentedTest.kt` | No | H: PR86 historical context | EXCLUDE | One-shot campaign fixture, device identity/kill control, observation or harness regression; not production dependency. |
| `android/poc/recovery/src/androidTest/kotlin/com/monumentogram/dora/poc/recovery/candidate/RecoveryExternalSigkillProbeInstrumentedTest.kt` | No | H: PR86 historical context | EXCLUDE | One-shot campaign fixture, device identity/kill control, observation or harness regression; not production dependency. |
| `android/poc/recovery/src/androidTest/kotlin/com/monumentogram/dora/poc/recovery/candidate/RecoveryPlatformPrerequisitesInstrumentedTest.kt` | No | H: PR86 historical context | EXCLUDE | One-shot campaign fixture, device identity/kill control, observation or harness regression; not production dependency. |
| `android/poc/recovery/src/androidTest/kotlin/com/monumentogram/dora/poc/recovery/candidate/RecoveryPreflightInstrumentationIdentity.kt` | No | H: PR86 historical context | EXCLUDE | One-shot campaign fixture, device identity/kill control, observation or harness regression; not production dependency. |
| `android/poc/recovery/src/androidTest/kotlin/com/monumentogram/dora/poc/recovery/journal/RecoveryJournalConnectionConfigurationTest.kt` | No | T: PR86 selected regression/support | TRANSFER | Regression for authenticated recovery, quarantine, exact SQL/history, path policy or per-connection durability configuration; preserve assertions with PoC/product scope separation. |
| `android/poc/recovery/src/androidTest/kotlin/com/monumentogram/dora/poc/recovery/journal/RecoveryMicrofileDispositionMigrationVerification.kt` | No | T: PR86 selected regression/support | TRANSFER | Regression for authenticated recovery, quarantine, exact SQL/history, path policy or per-connection durability configuration; preserve assertions with PoC/product scope separation. |
| `android/poc/recovery/src/androidTest/kotlin/com/monumentogram/dora/poc/recovery/journal/RecoveryStreamPrefixMigrationVerification.kt` | No | T: PR86 selected regression/support | TRANSFER | Regression for authenticated recovery, quarantine, exact SQL/history, path policy or per-connection durability configuration; preserve assertions with PoC/product scope separation. |
| `android/poc/recovery/src/main/kotlin/com/monumentogram/dora/poc/recovery/candidate/AndroidRecoveryStreamingOrphanArtifacts.kt` | No | A/M: app79d930d = PR86 | TRANSFER | Measured recovery controller, authentication, journal or storage behavior absent from baseline; preserve behind isolated boundary before recording acceptance. |
| `android/poc/recovery/src/main/kotlin/com/monumentogram/dora/poc/recovery/candidate/AndroidRecoveryStreamingPublication.kt` | No | A/M: app79d930d = PR86 | EXCLUDE | Campaign fixture / preflight serializer / deliberately non-finalizing writer or its transaction-observer wiring; do not ship verbatim. |
| `android/poc/recovery/src/main/kotlin/com/monumentogram/dora/poc/recovery/candidate/AndroidRecoveryStreamingReconciliation.kt` | No | A/M: app79d930d = PR86 | TRANSFER | Measured recovery controller, authentication, journal or storage behavior absent from baseline; preserve behind isolated boundary before recording acceptance. |
| `android/poc/recovery/src/main/kotlin/com/monumentogram/dora/poc/recovery/candidate/RecoveryArtifactSizeLimitObservation.kt` | No | A/M: app79d930d = PR86 | TRANSFER | Measured recovery controller, authentication, journal or storage behavior absent from baseline; preserve behind isolated boundary before recording acceptance. |
| `android/poc/recovery/src/main/kotlin/com/monumentogram/dora/poc/recovery/candidate/RecoveryCampaignFixture.kt` | No | A/M: app79d930d = PR86 | EXCLUDE | Campaign fixture / preflight serializer / deliberately non-finalizing writer or its transaction-observer wiring; do not ship verbatim. |
| `android/poc/recovery/src/main/kotlin/com/monumentogram/dora/poc/recovery/candidate/RecoveryMicrofileReconciliationController.kt` | No | A/M: app79d930d = PR86 | TRANSFER | Measured recovery controller, authentication, journal or storage behavior absent from baseline; preserve behind isolated boundary before recording acceptance. |
| `android/poc/recovery/src/main/kotlin/com/monumentogram/dora/poc/recovery/candidate/RecoveryMicrofileReferencedArtifacts.kt` | No | A/M: app79d930d = PR86 | TRANSFER | Measured recovery controller, authentication, journal or storage behavior absent from baseline; preserve behind isolated boundary before recording acceptance. |
| `android/poc/recovery/src/main/kotlin/com/monumentogram/dora/poc/recovery/candidate/RecoveryReconciliationOutcomes.kt` | No | A/M: app79d930d = PR86 | TRANSFER | Measured recovery controller, authentication, journal or storage behavior absent from baseline; preserve behind isolated boundary before recording acceptance. |
| `android/poc/recovery/src/main/kotlin/com/monumentogram/dora/poc/recovery/candidate/RecoveryStreamingOrphanReconciler.kt` | No | A/M: app79d930d = PR86 | TRANSFER | Measured recovery controller, authentication, journal or storage behavior absent from baseline; preserve behind isolated boundary before recording acceptance. |
| `android/poc/recovery/src/main/kotlin/com/monumentogram/dora/poc/recovery/candidate/RecoveryStreamingPublicationWriter.kt` | No | A/M: app79d930d = PR86 | EXCLUDE | Campaign fixture / preflight serializer / deliberately non-finalizing writer or its transaction-observer wiring; do not ship verbatim. |
| `android/poc/recovery/src/main/kotlin/com/monumentogram/dora/poc/recovery/candidate/RecoveryStreamingReconciliationController.kt` | No | A/M: app79d930d = PR86 | TRANSFER | Measured recovery controller, authentication, journal or storage behavior absent from baseline; preserve behind isolated boundary before recording acceptance. |
| `android/poc/recovery/src/main/kotlin/com/monumentogram/dora/poc/recovery/candidate/RecoveryStreamingTinkPrerequisiteCrypto.kt` | No | A/M: app79d930d = PR86 | TRANSFER | Measured recovery controller, authentication, journal or storage behavior absent from baseline; preserve behind isolated boundary before recording acceptance. |
| `android/poc/recovery/src/main/kotlin/com/monumentogram/dora/poc/recovery/candidate/SqliteCompileOptionsCanonicalizer.kt` | No | A/M: app79d930d = PR86 | EXCLUDE | Campaign fixture / preflight serializer / deliberately non-finalizing writer or its transaction-observer wiring; do not ship verbatim. |
| `android/poc/recovery/src/main/kotlin/com/monumentogram/dora/poc/recovery/contract/RecoveryBinary.kt` | No | A/M: app79d930d = PR86 | TRANSFER | Typed proof/quarantine contract or exact shared schema definition required by the recovery delta. Historical migrations are PoC compatibility, not a fresh Alpha data-support promise. |
| `android/poc/recovery/src/main/kotlin/com/monumentogram/dora/poc/recovery/contract/RecoveryQuarantineIntent.kt` | No | A/M: app79d930d = PR86 | TRANSFER | Typed proof/quarantine contract or exact shared schema definition required by the recovery delta. Historical migrations are PoC compatibility, not a fresh Alpha data-support promise. |
| `android/poc/recovery/src/main/kotlin/com/monumentogram/dora/poc/recovery/contract/RecoveryRecords.kt` | No | A/M: app79d930d = PR86 | TRANSFER | Typed proof/quarantine contract or exact shared schema definition required by the recovery delta. Historical migrations are PoC compatibility, not a fresh Alpha data-support promise. |
| `android/poc/recovery/src/main/kotlin/com/monumentogram/dora/poc/recovery/contract/RecoveryStreamingPersistence.kt` | No | A/M: app79d930d = PR86 | TRANSFER | Typed proof/quarantine contract or exact shared schema definition required by the recovery delta. Historical migrations are PoC compatibility, not a fresh Alpha data-support promise. |
| `android/poc/recovery/src/main/kotlin/com/monumentogram/dora/poc/recovery/journal/AndroidRecoveryJournalDatabase.kt` | No | A/M: app79d930d = PR86 | TRANSFER | Measured recovery controller, authentication, journal or storage behavior absent from baseline; preserve behind isolated boundary before recording acceptance. |
| `android/poc/recovery/src/main/kotlin/com/monumentogram/dora/poc/recovery/journal/AndroidRecoveryQuarantineJournal.kt` | No | A/M: app79d930d = PR86 | TRANSFER | Measured recovery controller, authentication, journal or storage behavior absent from baseline; preserve behind isolated boundary before recording acceptance. |
| `android/poc/recovery/src/main/kotlin/com/monumentogram/dora/poc/recovery/journal/AndroidRecoveryReconciliationSource.kt` | No | A/M: app79d930d = PR86 | TRANSFER | Measured recovery controller, authentication, journal or storage behavior absent from baseline; preserve behind isolated boundary before recording acceptance. |
| `android/poc/recovery/src/main/kotlin/com/monumentogram/dora/poc/recovery/journal/AndroidRecoveryStreamingJournal.kt` | No | A/M: app79d930d = PR86 | TRANSFER | One-line private-to-internal visibility required by the selected migration instrumentation; no algorithm change. |
| `android/poc/recovery/src/main/kotlin/com/monumentogram/dora/poc/recovery/journal/RecoveryMicrofileDispositionSchema.kt` | No | A/M: app79d930d = PR86 | TRANSFER | Typed proof/quarantine contract or exact shared schema definition required by the recovery delta. Historical migrations are PoC compatibility, not a fresh Alpha data-support promise. |
| `android/poc/recovery/src/main/kotlin/com/monumentogram/dora/poc/recovery/journal/RecoveryStreamPrefixSchema.kt` | No | A/M: app79d930d = PR86 | TRANSFER | Typed proof/quarantine contract or exact shared schema definition required by the recovery delta. Historical migrations are PoC compatibility, not a fresh Alpha data-support promise. |
| `android/poc/recovery/src/main/kotlin/com/monumentogram/dora/poc/recovery/journal/RecoveryStreamingCheckpointCommitAdapter.kt` | No | A/M: app79d930d = PR86 | EXCLUDE | Campaign fixture / preflight serializer / deliberately non-finalizing writer or its transaction-observer wiring; do not ship verbatim. |
| `android/poc/recovery/src/main/kotlin/com/monumentogram/dora/poc/recovery/storage/AndroidOsRecoveryReconciliationStorage.kt` | No | A/M: app79d930d = PR86 | TRANSFER | Measured recovery controller, authentication, journal or storage behavior absent from baseline; preserve behind isolated boundary before recording acceptance. |
| `android/poc/recovery/src/main/kotlin/com/monumentogram/dora/poc/recovery/storage/RecoveryCandidatePathPolicy.kt` | No | A/M: app79d930d = PR86 | TRANSFER | Measured recovery controller, authentication, journal or storage behavior absent from baseline; preserve behind isolated boundary before recording acceptance. |
| `android/poc/recovery/src/sharedTest/kotlin/com/monumentogram/dora/poc/recovery/candidate/RecoveryCampaignConfirmationAccess.kt` | No | H: PR86 historical context | EXCLUDE | One-shot campaign fixture, device identity/kill control, observation or harness regression; not production dependency. |
| `android/poc/recovery/src/sharedTest/kotlin/com/monumentogram/dora/poc/recovery/candidate/RecoveryCampaignJournalRows.kt` | No | H: PR86 historical context | EXCLUDE | One-shot campaign fixture, device identity/kill control, observation or harness regression; not production dependency. |
| `android/poc/recovery/src/sharedTest/kotlin/com/monumentogram/dora/poc/recovery/candidate/RecoveryCampaignParserClassification.kt` | No | T: PR86 selected regression/support | TRANSFER | One-shot campaign fixture, device identity/kill control, observation or harness regression; not production dependency. Exact AndroidRecoveryReconciliationSourceTest dependency: 99-line shared-test helper retained only in synthetic candidate; primary purpose remains campaign-only. |
| `android/poc/recovery/src/sharedTest/kotlin/com/monumentogram/dora/poc/recovery/candidate/RecoveryCampaignStreamPersistenceDigest.kt` | No | H: PR86 historical context | EXCLUDE | One-shot campaign fixture, device identity/kill control, observation or harness regression; not production dependency. |
| `android/poc/recovery/src/sharedTest/kotlin/com/monumentogram/dora/poc/recovery/candidate/RecoveryCampaignStreamPersistenceSummary.kt` | No | H: PR86 historical context | EXCLUDE | One-shot campaign fixture, device identity/kill control, observation or harness regression; not production dependency. |
| `android/poc/recovery/src/sharedTest/kotlin/com/monumentogram/dora/poc/recovery/candidate/RecoveryExternalSigkillProbeGate.kt` | No | H: PR86 historical context | EXCLUDE | One-shot campaign fixture, device identity/kill control, observation or harness regression; not production dependency. |
| `android/poc/recovery/src/sharedTest/kotlin/com/monumentogram/dora/poc/recovery/candidate/RecoveryPhysicalDeviceIdentityGuard.kt` | No | H: PR86 historical context | EXCLUDE | One-shot campaign fixture, device identity/kill control, observation or harness regression; not production dependency. |
| `android/poc/recovery/src/sharedTest/kotlin/com/monumentogram/dora/poc/recovery/candidate/RecoveryPreflightDeviceIdentityGuard.kt` | No | H: PR86 historical context | EXCLUDE | One-shot campaign fixture, device identity/kill control, observation or harness regression; not production dependency. |
| `android/poc/recovery/src/test/kotlin/com/monumentogram/dora/poc/recovery/candidate/RecoveryCampaignConfirmationAccessTest.kt` | No | H: PR86 historical context | EXCLUDE | One-shot campaign fixture, device identity/kill control, observation or harness regression; not production dependency. |
| `android/poc/recovery/src/test/kotlin/com/monumentogram/dora/poc/recovery/candidate/RecoveryCampaignFixtureTest.kt` | No | H: PR86 historical context | EXCLUDE | One-shot campaign fixture, device identity/kill control, observation or harness regression; not production dependency. |
| `android/poc/recovery/src/test/kotlin/com/monumentogram/dora/poc/recovery/candidate/RecoveryCampaignJournalRowsTest.kt` | No | H: PR86 historical context | EXCLUDE | One-shot campaign fixture, device identity/kill control, observation or harness regression; not production dependency. |
| `android/poc/recovery/src/test/kotlin/com/monumentogram/dora/poc/recovery/candidate/RecoveryCampaignParserClassificationTest.kt` | No | H: PR86 historical context | EXCLUDE | One-shot campaign fixture, device identity/kill control, observation or harness regression; not production dependency. |
| `android/poc/recovery/src/test/kotlin/com/monumentogram/dora/poc/recovery/candidate/RecoveryCampaignStreamPersistenceDigestTest.kt` | No | H: PR86 historical context | EXCLUDE | One-shot campaign fixture, device identity/kill control, observation or harness regression; not production dependency. |
| `android/poc/recovery/src/test/kotlin/com/monumentogram/dora/poc/recovery/candidate/RecoveryCampaignStreamPersistenceSummaryTest.kt` | No | H: PR86 historical context | EXCLUDE | One-shot campaign fixture, device identity/kill control, observation or harness regression; not production dependency. |
| `android/poc/recovery/src/test/kotlin/com/monumentogram/dora/poc/recovery/candidate/RecoveryExternalSigkillProbeGateTest.kt` | No | H: PR86 historical context | EXCLUDE | One-shot campaign fixture, device identity/kill control, observation or harness regression; not production dependency. |
| `android/poc/recovery/src/test/kotlin/com/monumentogram/dora/poc/recovery/candidate/RecoveryMicrofileReferencedIntentTest.kt` | No | T: PR86 selected regression/support | TRANSFER | Regression for authenticated recovery, quarantine, exact SQL/history, path policy or per-connection durability configuration; preserve assertions with PoC/product scope separation. |
| `android/poc/recovery/src/test/kotlin/com/monumentogram/dora/poc/recovery/candidate/RecoveryPhysicalDeviceIdentityGuardTest.kt` | No | H: PR86 historical context | EXCLUDE | One-shot campaign fixture, device identity/kill control, observation or harness regression; not production dependency. |
| `android/poc/recovery/src/test/kotlin/com/monumentogram/dora/poc/recovery/candidate/RecoveryPreflightDeviceIdentityGuardTest.kt` | No | H: PR86 historical context | EXCLUDE | One-shot campaign fixture, device identity/kill control, observation or harness regression; not production dependency. |
| `android/poc/recovery/src/test/kotlin/com/monumentogram/dora/poc/recovery/candidate/RecoveryStreamPrefixControllerCryptoTest.kt` | No | T: PR86 selected regression/support | TRANSFER | Regression for authenticated recovery, quarantine, exact SQL/history, path policy or per-connection durability configuration; preserve assertions with PoC/product scope separation. |
| `android/poc/recovery/src/test/kotlin/com/monumentogram/dora/poc/recovery/candidate/RecoveryStreamingPublicationWriterTest.kt` | No | H: PR86 historical context | EXCLUDE | One-shot campaign fixture, device identity/kill control, observation or harness regression; not production dependency. |
| `android/poc/recovery/src/test/kotlin/com/monumentogram/dora/poc/recovery/candidate/RecoveryStreamingReconciliationControllerTest.kt` | No | T: PR86 selected regression/support | TRANSFER | Regression for authenticated recovery, quarantine, exact SQL/history, path policy or per-connection durability configuration; preserve assertions with PoC/product scope separation. |
| `android/poc/recovery/src/test/kotlin/com/monumentogram/dora/poc/recovery/candidate/RecoveryStreamingSurvivingPrefixTest.kt` | No | T: PR86 selected regression/support | TRANSFER | Regression for authenticated recovery, quarantine, exact SQL/history, path policy or per-connection durability configuration; preserve assertions with PoC/product scope separation. |
| `android/poc/recovery/src/test/kotlin/com/monumentogram/dora/poc/recovery/candidate/RecoveryStreamingTinkPrerequisiteCryptoTest.kt` | No | T: PR86 selected regression/support | TRANSFER | Regression for authenticated recovery, quarantine, exact SQL/history, path policy or per-connection durability configuration; preserve assertions with PoC/product scope separation. |
| `android/poc/recovery/src/test/kotlin/com/monumentogram/dora/poc/recovery/candidate/SqliteCompileOptionsCanonicalizerTest.kt` | No | H: PR86 historical context | EXCLUDE | One-shot campaign fixture, device identity/kill control, observation or harness regression; not production dependency. |
| `android/poc/recovery/src/test/kotlin/com/monumentogram/dora/poc/recovery/journal/AndroidRecoveryReconciliationSourceTest.kt` | No | T: PR86 selected regression/support | TRANSFER | Regression for authenticated recovery, quarantine, exact SQL/history, path policy or per-connection durability configuration; preserve assertions with PoC/product scope separation. |
| `android/poc/recovery/src/test/kotlin/com/monumentogram/dora/poc/recovery/journal/RecoveryJournalApiTest.kt` | No | T: PR86 selected regression/support | TRANSFER | Regression for authenticated recovery, quarantine, exact SQL/history, path policy or per-connection durability configuration; preserve assertions with PoC/product scope separation. |
| `android/poc/recovery/src/test/kotlin/com/monumentogram/dora/poc/recovery/journal/RecoveryJournalSchemaPlanTest.kt` | No | T: PR86 selected regression/support | TRANSFER | Regression for authenticated recovery, quarantine, exact SQL/history, path policy or per-connection durability configuration; preserve assertions with PoC/product scope separation. |
| `android/poc/recovery/src/test/kotlin/com/monumentogram/dora/poc/recovery/journal/RecoveryMicrofileDispositionSchemaTest.kt` | No | T: PR86 selected regression/support | TRANSFER | Regression for authenticated recovery, quarantine, exact SQL/history, path policy or per-connection durability configuration; preserve assertions with PoC/product scope separation. |
| `android/poc/recovery/src/test/kotlin/com/monumentogram/dora/poc/recovery/journal/RecoveryMicrofileQuarantineReadbackTest.kt` | No | T: PR86 selected regression/support | TRANSFER | Regression for authenticated recovery, quarantine, exact SQL/history, path policy or per-connection durability configuration; preserve assertions with PoC/product scope separation. |
| `android/poc/recovery/src/test/kotlin/com/monumentogram/dora/poc/recovery/journal/RecoveryStreamPrefixSchemaTest.kt` | No | T: PR86 selected regression/support | TRANSFER | Regression for authenticated recovery, quarantine, exact SQL/history, path policy or per-connection durability configuration; preserve assertions with PoC/product scope separation. |
| `android/poc/recovery/src/test/kotlin/com/monumentogram/dora/poc/recovery/storage/AndroidOsRecoveryReconciliationStorageTest.kt` | No | T: PR86 selected regression/support | TRANSFER | Regression for authenticated recovery, quarantine, exact SQL/history, path policy or per-connection durability configuration; preserve assertions with PoC/product scope separation. |
| `android/poc/recovery/src/test/kotlin/com/monumentogram/dora/poc/recovery/storage/AndroidOsRecoveryStreamingSourceTest.kt` | No | T: PR86 selected regression/support | TRANSFER | Retain PR86 FutureTask fix; add controlled pre-return ordering and failure/timeout cleanup regressions. |
| `android/poc/recovery/src/test/kotlin/com/monumentogram/dora/poc/recovery/storage/RecoveryCandidatePathPolicyTest.kt` | No | T: PR86 selected regression/support | TRANSFER | Regression for authenticated recovery, quarantine, exact SQL/history, path policy or per-connection durability configuration; preserve assertions with PoC/product scope separation. |
| `docs/DORA_MVP1_IMPLEMENTATION_BACKLOG.md` | No | H: PR86 historical context | EXCLUDE | MUST_NOT_OVERWRITE_CURRENT_STATUS: current Stage0/5/6/6.3, Cloud and privacy authority wins; retain PR passages only as dated history. |
| `docs/DORA_MVP1_PRODUCT_DECISIONS.md` | No | H: PR86 historical context | EXCLUDE | MUST_NOT_OVERWRITE_CURRENT_STATUS: current Stage0/5/6/6.3, Cloud and privacy authority wins; retain PR passages only as dated history. |
| `docs/DORA_MVP1_STAGE_STATUS.md` | No | H: PR86 historical context | EXCLUDE | MUST_NOT_OVERWRITE_CURRENT_STATUS: current Stage0/5/6/6.3, Cloud and privacy authority wins; retain PR passages only as dated history. |
| `docs/DORA_MVP1_TECHNICAL_PLAN.md` | No | H: PR86 historical context | EXCLUDE | MUST_NOT_OVERWRITE_CURRENT_STATUS: current Stage0/5/6/6.3, Cloud and privacy authority wins; retain PR passages only as dated history. |
| `docs/REC-I3-SQLITE-PER-CONNECTION-POC-DECISION.md` | No | H: PR86 historical context | EXCLUDE | HISTORICAL_ONLY / SAFE_TO_ARCHIVE by immutable commit link. PoC scope/design/owner record or old implementation plan, not current product admission. |
| `docs/adr/ADR-0008-stream-surviving-checkpoint-prefix.md` | No | H: PR86 historical context | EXCLUDE | HISTORICAL_ONLY / SAFE_TO_ARCHIVE by immutable commit link. PoC scope/design/owner record or old implementation plan, not current product admission. |
| `docs/adr/ADR-0009-microfile-referenced-quarantine.md` | No | H: PR86 historical context | EXCLUDE | HISTORICAL_ONLY / SAFE_TO_ARCHIVE by immutable commit link. PoC scope/design/owner record or old implementation plan, not current product admission. |
| `docs/stage0/DORA_0D6_ALPHA_E36_CAMPAIGN_OWNER_DECISION_20260914.md` | No | H: PR86 historical context | EXCLUDE | HISTORICAL_ONLY / SAFE_TO_ARCHIVE by immutable commit link. PoC scope/design/owner record or old implementation plan, not current product admission. |
| `docs/stage0/DORA_0D6_ALPHA_PREFLIGHT_OWNER_DECISION_20260914.md` | No | H: PR86 historical context | EXCLUDE | HISTORICAL_ONLY / SAFE_TO_ARCHIVE by immutable commit link. PoC scope/design/owner record or old implementation plan, not current product admission. |
| `docs/stage0/DORA_0D6_ALPHA_REDUCED_SCOPE_OWNER_DECISION_20260915.md` | No | H: PR86 historical context | EXCLUDE | HISTORICAL_ONLY / SAFE_TO_ARCHIVE by immutable commit link. PoC scope/design/owner record or old implementation plan, not current product admission. |
| `docs/stage0/DORA_0D6_API33_HARNESS_SCOPE_20260918.md` | No | H: PR86 historical context | EXCLUDE | HISTORICAL_ONLY / SAFE_TO_ARCHIVE by immutable commit link. PoC scope/design/owner record or old implementation plan, not current product admission. |
| `docs/stage0/DORA_0D6_EXECUTION_SCOPE_20260914.md` | No | H: PR86 historical context | EXCLUDE | HISTORICAL_ONLY / SAFE_TO_ARCHIVE by immutable commit link. PoC scope/design/owner record or old implementation plan, not current product admission. |
| `docs/stage0/DORA_0D6_POCO_PHYSICAL_SCOPE_20260918.md` | No | H: PR86 historical context | EXCLUDE | HISTORICAL_ONLY / SAFE_TO_ARCHIVE by immutable commit link. PoC scope/design/owner record or old implementation plan, not current product admission. |
| `docs/superpowers/plans/2026-09-09-rec-i3-v8-host-run-contract-repair.md` | No | H: PR86 historical context | EXCLUDE | HISTORICAL_ONLY / SAFE_TO_ARCHIVE by immutable commit link. PoC scope/design/owner record or old implementation plan, not current product admission. |
| `docs/superpowers/plans/2026-09-10-rec-i3-v11-sqlite-openparams-full.md` | No | H: PR86 historical context | EXCLUDE | HISTORICAL_ONLY / SAFE_TO_ARCHIVE by immutable commit link. PoC scope/design/owner record or old implementation plan, not current product admission. |
| `tools/rec_i3_owned_process.psm1` | No | H: PR86 historical context | EXCLUDE | Host orchestration/evidence generation/cleanup or its tests; no product runtime dependency. |
| `tools/rec_i3_preserve_and_cleanup.ps1` | No | H: PR86 historical context | EXCLUDE | Host orchestration/evidence generation/cleanup or its tests; no product runtime dependency. |
| `tools/recovery_alpha_prefix_repair.py` | No | H: PR86 historical context | EXCLUDE | Host orchestration/evidence generation/cleanup or its tests; no product runtime dependency. |
| `tools/recovery_alpha_repair.py` | No | H: PR86 historical context | EXCLUDE | Host orchestration/evidence generation/cleanup or its tests; no product runtime dependency. |
| `tools/recovery_api33.py` | No | H: PR86 historical context | EXCLUDE | Host orchestration/evidence generation/cleanup or its tests; no product runtime dependency. |
| `tools/recovery_campaign.py` | No | H: PR86 historical context | EXCLUDE | Host orchestration/evidence generation/cleanup or its tests; no product runtime dependency. |
| `tools/recovery_instrumentation_status.py` | No | H: PR86 historical context | EXCLUDE | Host orchestration/evidence generation/cleanup or its tests; no product runtime dependency. |
| `tools/recovery_physical.py` | No | H: PR86 historical context | EXCLUDE | Host orchestration/evidence generation/cleanup or its tests; no product runtime dependency. |
| `tools/run_rec_i3_v8.ps1` | No | H: PR86 historical context | EXCLUDE | Host orchestration/evidence generation/cleanup or its tests; no product runtime dependency. |
| `tools/test_poc_recovery_i3_governance.py` | No | H: PR86 historical context | EXCLUDE | PR-specific historical branch/metadata/workflow admission. Preserve 92f00f7d content-based lineage repair; no wholesale import. Future exact successor requires narrowly tested admission. |
| `tools/test_rec_i3_owned_process.ps1` | No | H: PR86 historical context | EXCLUDE | Host orchestration/evidence generation/cleanup or its tests; no product runtime dependency. |
| `tools/test_rec_i3_preserve_and_cleanup.py` | No | H: PR86 historical context | EXCLUDE | Host orchestration/evidence generation/cleanup or its tests; no product runtime dependency. |
| `tools/test_rec_microfile_disposition_schema.py` | No | T: PR86 selected regression/support | TRANSFER | Regression for authenticated recovery, quarantine, exact SQL/history, path policy or per-connection durability configuration; preserve assertions with PoC/product scope separation. |
| `tools/test_rec_stream_prefix_schema.py` | No | T: PR86 selected regression/support | TRANSFER | Regression for authenticated recovery, quarantine, exact SQL/history, path policy or per-connection durability configuration; preserve assertions with PoC/product scope separation. |
| `tools/test_recovery_alpha_prefix_repair.py` | No | H: PR86 historical context | EXCLUDE | Host orchestration/evidence generation/cleanup or its tests; no product runtime dependency. |
| `tools/test_recovery_alpha_repair.py` | No | H: PR86 historical context | EXCLUDE | Host orchestration/evidence generation/cleanup or its tests; no product runtime dependency. |
| `tools/test_recovery_api33.py` | No | H: PR86 historical context | EXCLUDE | Host orchestration/evidence generation/cleanup or its tests; no product runtime dependency. |
| `tools/test_recovery_campaign.py` | No | H: PR86 historical context | EXCLUDE | Host orchestration/evidence generation/cleanup or its tests; no product runtime dependency. |
| `tools/test_recovery_instrumentation_status.py` | No | H: PR86 historical context | EXCLUDE | Host orchestration/evidence generation/cleanup or its tests; no product runtime dependency. |
| `tools/test_recovery_microfile_tru03.py` | No | H: PR86 historical context | EXCLUDE | Host orchestration/evidence generation/cleanup or its tests; no product runtime dependency. |
| `tools/test_recovery_physical.py` | No | H: PR86 historical context | EXCLUDE | Host orchestration/evidence generation/cleanup or its tests; no product runtime dependency. |
| `tools/test_run_rec_i3_v8.py` | No | H: PR86 historical context | EXCLUDE | Host orchestration/evidence generation/cleanup or its tests; no product runtime dependency. |
| `tools/test_validate_recovery_0d6_candidate.py` | No | H: PR86 historical context | EXCLUDE | PR-specific historical branch/metadata/workflow admission. Preserve 92f00f7d content-based lineage repair; no wholesale import. Future exact successor requires narrowly tested admission. |
| `tools/validate_poc_recovery_governance.py` | No | H: PR86 historical context | EXCLUDE | PR-specific historical branch/metadata/workflow admission. Preserve 92f00f7d content-based lineage repair; no wholesale import. Future exact successor requires narrowly tested admission. |
| `tools/validate_recovery_0d6_candidate.py` | No | H: PR86 historical context | EXCLUDE | PR-specific historical branch/metadata/workflow admission. Preserve 92f00f7d content-based lineage repair; no wholesale import. Future exact successor requires narrowly tested admission. |
| `tools/validate_stage00.py` | No | H: PR86 historical context | EXCLUDE | PR-specific historical branch/metadata/workflow admission. Preserve 92f00f7d content-based lineage repair; no wholesale import. Future exact successor requires narrowly tested admission. |
| `tools/verify_poc_recovery_dependency_inventory.py` | No | H: PR86 historical context | EXCLUDE | PR-specific historical branch/metadata/workflow admission. Preserve 92f00f7d content-based lineage repair; no wholesale import. Future exact successor requires narrowly tested admission. |
| `tools/verify_rec_i3_streaming_sqlite.py` | No | T: PR86 selected regression/support | TRANSFER | Host orchestration/evidence generation/cleanup or its tests; no product runtime dependency. Needed as SQL extractor by two valuable host schema tests; use PR version only as test support, never as admission replacement. |
