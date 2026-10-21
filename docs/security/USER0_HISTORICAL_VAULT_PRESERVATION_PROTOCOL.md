# USER0 historical vault preservation protocol

Status: **BLOCKED / SAFE_READ_ONLY_ACQUISITION_UNAVAILABLE** for physical execution.
This document is a reviewed procedure specification and a record of architectural
limits, not admission to access the phone. Baseline source:
`52e4ce8bdf074ec021ae52c3c38ad9d26c24e128`. PR106 remains draft/open/unmerged.
`DEV-SECURITY-RESTORE-BEFORE-ALPHA-CLOSE = OPEN`; all ten physical checks remain
NOT_RUN. No historical record, private identity, alias or key is included here.

## 1. Trust origin and scope

The owner must first approve an exact device/user/package/UID/APK/signer/private
policy tuple, independently established provenance for the installed executable,
and a prospectively selected source set. VersionCode alone cannot bind code to
source. The audited HEAD is not the older installed debug or production APK.
Historical receipts are useful custody evidence, not a current baseline.

The known historical protection admitted 47 sources including LONG01, excluding
LONG02. The 48-source successor was prepared and synthetically tested, but its
physical installation is not established. Neither launching the older app nor
installing the successor is authorized. Existing policy must not be removed or
silently replaced. Source identities and exact aliases stay in owner-private,
digest-bound manifests; no wildcard or heuristic record selection.

There are three different claims:

- **Byte preservation:** the original DB/WAL/SHM/ciphertext file set, lengths and
  SHA256 values remain unchanged during an explicitly bounded acquisition.
- **Cryptographic continuity:** existing keys authenticate the pinned catalog,
  manifests, key confirmations and complete committed PCM prefix.
- **User authorization:** a real current Android credential/App Lock proof gates
  access and is revoked on lifecycle/security epoch changes.

None implies the other. File hashes do not establish quiescence or a consistent
transaction snapshot. Test lambdas are not real App Lock. File-byte equality does
not establish unchanged atime, permissions, directory metadata or flash blocks.

## 2. Admissible read-only actions and earliest mutation boundaries

In this engineering task: source/local-receipt analysis and synthetic emulator
tests only. No physical ADB query, application start, storage enumeration or
Keystore access. In a later separately approved session, exact PM/user/process
metadata may establish identity and absence of recorder/FGS without opening DORA;
it cannot establish vault readability or that a future app startup is harmless.

The call graph below is pinned to the baseline source. Line references are
repository-relative source references; installed-code behavior remains unproven.

| Entry | First possible side effect / consequence |
| --- | --- |
| `DoraApplication.kt:16–19` | Constructs AndroidProductAudioRuntime, RecordingController and Sherpa factory; no vault open in this constructor. RecordingController starts its scheduled worker (`:178–184`). Ordinary app startup is not an inspection-only process. |
| `AndroidDeviceSecurityPolicy.kt:11–16`, `AndroidAppLock.kt:168–175` | Uses `noBackupFilesDir` during lifecycle security checks. Framework directory creation may occur; zero filesystem writes cannot be inferred from constructing a File. |
| `AndroidAppLock.kt:68–70` → `AndroidAuthProof.kt:95–112` | Creates a disposable auth-only Keystore key before vault opening; cleanup deletes it (`:63–67`). Persistent vault keys are separate. |
| `RecordingRecoveryCard.kt:59–79` | Authorized recording/recovery composable auto-loads and calls requestRecordingRecovery. A bare cold MainActivity launch alone does not prove this path executes. |
| `AndroidProductAudioRuntime.kt:62–76,185–197` | App Lock → OPEN_EXISTING → coordinator → EncryptedAudioVault.open (`:37–46`). |
| `EncryptedAudioVault.kt:400–430` | Loads diagnostic policy, opens existing selector/bundle/key, then Room. Missing persistent keys are not regenerated. Framework private-directory access precedes DB open. |
| `RoomAudioJournal.kt:1330` | **First SQLCipher write-capable open:** `openHelper.writableDatabase`. Builder permits migrations/WAL (`:1375–1376`); an empty binding can be inserted (`:1339–1342`). WAL/SHM or checkpoint changes may precede any explicit application UPDATE. |
| `SqlCipherJournalHelperFactory.kt:131–157,173–189` | Configure/migration callbacks; even readableDatabase returns writableDatabase. OPEN_EXISTING is not OPEN_READONLY. |
| `EncryptedAudioVault.kt:122–156` | Discovery returns protected identities unavailable before RecordingRecoveryReader, but the earlier writable DB open already happened. |
| `RecordingRecoveryReader.kt:29–32,113–115` | reconcileHeld, inspect, metadata repair. |
| `RecoveryAudioBridge.kt:275–304` | Pending Append/Finalize may commit catalog changes without a new user Finalize action. |
| `RecoveryAudioBridge.kt:341–342,420–423` | Ordinary extract also invokes reconcile; RecoveryMicrofileReconciliationController can persist quarantine intents and rename artifacts (`:450–465,561–564,637–649`). |
| `SherpaEngineFactory.kt:39–47` | Later model creation may write/delete the private model; constructing the factory alone does not extract it. |

Files above are under `android/app/src/main/kotlin/com/monumentogram/dora/`,
`android/core/audio/src/main/kotlin/com/monumentogram/dora/audio/`,
`android/poc/recovery/src/main/kotlin/com/monumentogram/dora/poc/recovery/`, and
`android/ml/vad-sherpa/src/main/kotlin/com/monumentogram/dora/vad/sherpa/`.

Therefore neither ordinary OPEN_EXISTING, discovery, nor ordinary authenticated
extract is an admitted preservation-only operation. Source-scoped mutation
fences protect selected assets, not shared DB/WAL bytes; they cannot retroactively
undo writable open. Production Recovery remains unchanged by this task.

## 3. Evidence sources and prospective baseline

Before physical acquisition, freeze the exact reader artifact/source/signature,
entrypoint, dependency/native versions, operator procedure, private policy digest,
expected source identities and expected coverage. Establish quiescence for every
process that can write the namespace, not just recording FGS absence. Do not
force-stop as an unstated read-only operation. The quiet-period strategy must be
audited against the installed code, including startup, jobs and scheduled work.

An admitted baseline would record, privately: original namespace and vault/owner
binding; schema and Room master hash; catalog/intents/tombstones/source and
technical states; source/run/physical-unit mapping; exact persistent-key
references/properties; original file set, lengths and SHA256 values, including
DB and any WAL/SHM. Absence of a sidecar is an explicit state. Record evidence
times, tool identities, termination status and failure receipts. Publish only
aggregate counts, status and receipt digests. Do not transfer audio or key data
to Git/CI or substitute a new vault, secondary user or different record.

Whole-vault ciphertext acquisition reads more than LONG01/LONG02. Such scope
requires explicit consent even if no plaintext is retained. A new baseline read
is a protected-data operation and is not granted by this document.

## 4. Conditions for key preservation

The reviewed root and per-run wrapping-key generator uses non-exportable
AndroidKeyStore AES/GCM keys without userAuthenticationRequired. Existing-only
opens validate key properties and fail if absent. This is architecture evidence,
not a guarantee about actual historical keys or OEM credential transitions.

Do not export, import, generate, replace or remove persistent keys. Compare exact
private references and authenticate the same pinned key confirmations with the
same user/app UID; alias names and KeyInfo alone do not prove usability. Losing
the original UID/Keystore material is not repaired by a ciphertext copy. A
different package/process UID cannot generally consume the original app's keys.
An app-specific same-UID process still requires an admitted executable and
startup path; it is not supplied by installing an unrelated helper APK.

Real App Lock creates/deletes temporary auth-only keys. A future approval must
name those permitted writes separately from the ban on persistent-key mutations.
If user0 has no secure credential, AppLockAuthenticationActivity (`:38–43`)
rejects before proof: **real authenticated pre-PIN baseline is unavailable**.
Current credential state is not queried in this task. Development markers,
historical ADR grants and no-op authorize callbacks cannot substitute for real
authorization. Owner approval may authorize a separately specified ciphertext
or key-readiness examination, but does not turn it into an App Lock PASS.

## 5. SQLCipher, WAL and acquisition boundary

Preferred design, subject to unavailable admission prerequisites:

1. Prove installed-source quiescence without ordinary runtime startup. Prospectively
   pin a coherent DB/WAL/SHM set. Do not checkpoint the original to make copying easy.
2. Open source files only O_RDONLY/no-follow with descriptor identity, regular-file,
   owner, hardlink, size and bounds checks. Reject symlink/parent replacement,
   extra/missing file or digest drift. Copies must never alias the original.
3. Create a new private destination, exclusively; stream, fsync, reopen and hash.
   This deliberately writes destination files, so requires a separate write scope.
   Record original source before/after, including failures. No automatic cleanup.
4. Open only the acquired copy with SQLCipher OPEN_READONLY, no Room helper and
   no ordinary runtime/coordinator. Verify integrity, binding, exact schema3 and
   Room identity `14326b90c3941099abb8dc04701d9d5c`; reject another schema rather
   than migrate. Source WAL/SHM must never be opened by SQLCipher.
5. Inspect copy-side WAL behavior explicitly: read-only SQLCipher may need/create
   or update copy-side SHM; missing WAL may expose an older valid database. A
   successful integrity_check alone is insufficient. Match expected catalog and
   authenticated committed range to the pinned baseline. Record source and copy
   invariance separately; copy changes must not be called source mutations.

Existing `ProtectedReadOnlyAcquisition` implements exclusive private-copy creation,
durable writes, no-follow reads and hash comparisons. It does not establish an
atomic snapshot or cross-process quiescence; its persisted inventory omits file
metadata and empty directories, and source reads omit O_NOATIME. A literal demand
for no filesystem metadata changes is not established. Destination write/storage
failure remains failure; never delete or repair the original to free space.

SQLite documents WAL as persistent database state and explains that read-only
WAL opening can require existing or creatable sidecars. These upstream rules
motivate testing the actual bundled SQLCipher implementation; they are not a
substitute for its measured behavior. [SQLite WAL](https://www.sqlite.org/wal.html).

Existing `ProtectedReadOnlyVault` opens the copy, denies persistent-key writes,
uses OPEN_READONLY and exact schema/integrity checks, and does not call Recovery.
However its admitted entrypoint requires a pinned successor policy with exactly
48 sources and 394 added claims belonging to ONE selected asset. It is not a
general forensic reader, has no product App Lock callsite, and receives an
arbitrary authorization callback. Direct internal inspect in a test is not a
new admitted physical entrypoint.

## 6. LONG01/LONG02 protection and unsupported states

Both identities must be independently pinned; never derive the sample from what
the reader happens to accept. The historical LONG01 evidence described 388 units
and 31,040,000 frames. LONG02 had 394 committed catalog claims / 31,520,000 claimed
frames; full authenticated readback was NOT_RUN. These are old evidence counts,
not measurements in this task or proof of whole-recording completeness.

For each source, verify physical-source/run ordering, contiguous committed frames,
existing-key confirmation, manifest/ciphertext AEAD and exact expected PCM length;
preserve catalog, intents, tombstones and technical CAP metadata without repair.
Retain interrupted/unfinalized status and distinguish committed prefix from
incomplete tail or unknown missing capture. A valid committed prefix does not
prove all intended recording audio was saved, nor explain the failure's cause.

Current primitive accepts only a narrow unfinalized layout, all selected claims
committed and contiguous, no finalization/deletion/original-reference rows, no
quarantine, and absent intents or one applied APPEND for the last claim. It
authenticates generation-1 single-unit MICROFILE layout, not every historical
format. It does not semantically validate all technical/semantic metadata.
Finalized sources, pending append/finalize, incomplete claims and unsupported
schemas must be rejected without changing source state. Two-source LONG01/LONG02
coverage is not supplied by the successor entrypoint. No policy relaxation,
ordinary Recovery or hidden direct-inspect bypass is admitted to solve this.

## 7. Authenticated continuity mechanism and synthetic evidence

Required future delivery boundary: real foreground proof, one-use/session/epoch
checks before and throughout readback and before issuing the receipt; revoked,
wrong, cancelled or stale authority yields no successful protected result. PCM
stays transient and owned buffers are cleared. Provider-internal erasure is not
claimed. Only complete success on the prospectively selected range can produce
its authenticated-prefix receipt; any remaining range stays NOT_RUN/FAILED.

Synthetic tests use actual encrypted fixtures, AndroidKeyStore and SQLCipher on
explicit emulators. Deterministic input PCM and expected ranges act as an
independent oracle; ciphertext and generated keys need not be byte-deterministic
between fresh fixtures. Tests distinguish setup writes from inspection and compare
source SHA inventories and aliases around inspection. They exercise accepted
unfinalized prefixes, finalized/pending/incomplete rejections, CAP-shaped physical
boundaries, schema/key/ciphertext negatives and actual DB/WAL/SHM behavior.

The test-only independent prefix oracle may read a committed prefix of a state
that the admitted primitive rejects. This demonstrates cryptographic feasibility
on a synthetic copy, **not** a deployed authenticated inspection route or permission
to reuse that test harness on historical data. App Lock integration, actual OEM
key continuity, physical quiescence and current historical states are NOT_TESTED.
Exact methods/results/artifact hashes belong in the synthetic execution receipt
and terminal RESULT, not inferred from test existence.

## 8. Error handling and recoverability

On error: stop, revoke the inspection session and close only owned inspection
handles. Retain bounded failure evidence and the approved copy; no sample
replacement or automatic retry through another API. Timeout, partial authentication,
transport loss, storage exhaustion or failed close/fsync cannot yield PASS.
Do not finalize, resume, delete, quarantine, rebuild DB, regenerate a key, migrate,
install/downgrade, clear app data, or restore copied files over the original.

Before original mutation, the recovery strategy is to leave the original alone.
If keys become invalid, this protocol has no software rollback guarantee. Do not
remove PIN or toggle credential type as an attempted repair. Ordinary Android
backup/signing-key backup and a vault ciphertext snapshot are different assets;
none is evidence of recoverable original non-exportable vault keys.

## 9. STOP conditions

STOP on unknown identity/provenance/baseline, absent admission, unprotected LONG02,
ordinary runtime/Recovery entry, unknown writer or inconsistent snapshot, sidecar
loss, source drift, missing/invalid key, schema or policy mismatch, unsupported
state, unresolved prefix/count mismatch, auth loss, or any required original
mutation. Also STOP if the proposed inspector must be installed by replacing
historical shared package code, or if pre-PIN authentication requirements cannot
be met. These blockers are not waived by synthetic PASS or owner willingness to
proceed without a verified preservation design.

## 10. Future PIN restoration sequence (not executable admission)

1. Resolve installed-code provenance, acquisition rights, quiescence, two-LONG
   coverage, safe entrypoint and the pre-PIN authentication ordering. Independently
   review exact artifacts/procedure. No currently deployed route satisfies this.
2. Owner separately authorizes narrowly defined baseline acquisition/key-readiness
   scope and permissible destination/auth-only writes. Do not claim real App Lock
   if no credential exists; if the required precondition cannot be proved, STOP.
3. Only after a verified preservation plan/baseline is accepted, owner separately
   authorizes Phase2 and personally sets PIN/password through user0 Android Settings.
   No PIN in chat, ADB, logs, scripts or Git. No root/locksettings/credential reset.
4. Under separate exact approval, verify/remove only the two debug markers:
   `/data/user/0/com.monumentogram.dora.debug/no_backup/development-device-no-lock`
   and `/data/user/0/com.monumentogram.dora.debug/no_backup/development-app-lock-no-prompt`.
   Validate exact namespace/owner/no-link regular empty file; absent stays absent.
   No recursive cleanup, policy removal or app launch between steps.
5. Use an audited safe probe for user0 isDeviceSecure and existing-key continuity.
   Then separately admit real App Lock positive/negative/lifecycle tests. Ordinary
   production launch remains blocked until exact startup/Recovery implications
   are safe. Restart must remain locked with microphone/recording FGS OFF.
6. Separately authorize historical readback through the admitted copy-only route;
   compare original baseline and key contour before/after and keep failures visible.
   No historical Finalize/Resume/Delete. Positive Resume/capture testing needs a
   different explicitly approved synthetic scope, not either LONG source.
7. Owner reviews all ten physical checks. This protocol never sets SATISFIED;
   release/install/distribution decisions remain separate.

## 11. Rights, alternatives and separate owner decisions

| Option | Rights / app startup / root | Risk and recovery | Separate admission |
| --- | --- | --- | --- |
| PM/user/process metadata | ADB shell; no app/root | Cannot prove storage/key continuity; no vault backup claim | Physical read-only diagnostics |
| Existing local receipts/source | Host file access; no device | Stale/non-exhaustive evidence; no current-state proof | Authorized in this task |
| Ciphertext through debug run-as | Installed debuggable package, original user/UID access; no root; shell need not run Application | Access alone does not prove correct UID/SELinux path, quiescence, atomic snapshot or key access. File reads may update metadata. Not available for non-debug production | Exact read commands/namespace, rights validation, full read scope, destination and custody; no execution here |
| Quiescent snapshot + copied SQLCipher | Approved same-UID inspector and consistent baseline; no ordinary app runtime; root not inherently required | Whole-vault reads and new-copy writes; cannot recover lost keys; no currently deployed inspector | Artifact/startup admission, acquisition, keys, private copy and historical read |
| Source-scoped fence | Admitted matching APK/private policy; often normal app startup | Protects selected mutations but shared writable DB remains; old policy omits LONG02. No bytewise rollback | Any policy/package change separately; not a preservation substitute |
| Separate app UID/process | Own sandbox; no root | Cannot authenticate original app keys just by copying files; same-signer is not same UID | Does not solve original-key access; reject data/key transfer workaround |
| Same-UID diagnostic process | Existing or separately admitted app executable/component, custom startup that excludes normal Application work | `android:process` alone still uses Application startup; instrumentation can initialize target Application. New entrypoint may need a forbidden package update | Separate engineering and installation decision; full startup/call graph/tests |
| OS/raw snapshot/backup | Privileged/root/system-specific access or backup support | Not generally available without prohibited privileges; ciphertext not portable Keystore backup | Outside current constraints; do not propose root bypass |

No approach currently establishes safe deployed authenticated acquisition of both
LONG sources under the owner's restrictions. The next useful owner decision is
whether to authorize a separate engineering scope for a two-source, same-UID,
inspection-only entrypoint and baseline strategy, with no physical installation
until its exact artifact and unavoidable startup/copy/auth writes are reviewed.
If that requires upgrading historical DORA, it is a new explicit risk decision,
not implied by this protocol. Alternatively keep restoration blocked and assess
the isolated pilot below.

## Alternative: operator-free owner-only Alpha in a clean Secondary User

Technically feasible as a new candidate, not supplied by an existing variant.
`alphaTest` inherits release but includes an exported operator launcher and operator
classes. `release` excludes the operator but uses `com.monumentogram.dora`; installing
it for another user still updates shared package code. A distinct runtime applicationId
(not only testApplicationId) avoids that package replacement. A separate signer,
no shared UID/cross-user privileges, private Context storage and UID-scoped keys
support sandbox isolation; they do not attest original-vault byte preservation.
The platform isolation model assigns applications distinct UIDs:
[AOSP application sandbox](https://source.android.com/docs/security/app-sandbox).

A future operator-free release-derived variant must exclude the entire alphaTest
source set, use release implementations of both override denials/DiagnosticBuild,
and have a distinct owner-Alpha label and independently pinned signer. Verify actual
APK manifest/DEX, no operator/test classes or private policy pin, no unsafe exports,
shared UID, cross-user permission or production authority. Build the exact variant's
dependency graph, SBOM/native inputs and two clean signed outputs. Existing build
wrappers hardcode either production identity/signer or the diagnostic alphaTest
variant; they cannot simply be relabeled.

The operator currently supplies the full readback/discovery/verified deletion route
used by isolated acceptance. Removing it removes that route; existing smoke PASS
cannot be inherited. Define narrower owner-only product acceptance and retention,
or separately implement ordinary product UI. Secondary-user admission currently
lives in the operator, so decide a variant-specific user guard or explicit host
installation restriction; do not imply removing operator retains its guard.

Required next owner decisions: implement the new variant/validation; approve local
signing/private-input build; then separately approve a clean full Secondary User,
exact APK installation, credential setup and bounded recording/readback/cleanup.
No APK is installed, signed or distributed for this alternative in this task.
It is not production-equivalent and does not close USER0 restoration, Stage8.6,
LONG02 continuity, or signed distribution gates.
