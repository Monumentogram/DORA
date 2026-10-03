# ADR-DEV-002: Temporary development on the owner's device without a system PIN

Status: owner-authorized temporary development exception, 2026-10-02.
Implementation and physical evidence remain subject to separate verification.

## Decision and historical scope

The Project Owner explicitly permits disabling Android's secure screen-lock
credential on the dedicated POCO development device through normal Android
Settings, after the debug support and existing-vault baseline are verified.
This supersedes only ADR-DEV-001's requirement that the opted-in local debug
device retain a secure credential. ADR-DEV-001 and prior real-authentication
evidence remain historical; release and signed Alpha requirements do not change.

## Two independent opt-ins

- `no_backup/development-device-no-lock` permits the local debug device-security
  precondition to accept an insecure device. It must be an empty regular file at
  that exact private location, not a directory, nonempty file or symlink.
- `no_backup/development-app-lock-no-prompt` keeps its existing meaning: a
  foreground development App Lock grant without the system prompt.
- Prompt-free development on an insecure device requires BOTH opt-ins. The new
  marker alone never becomes a resource authorization or cryptographic proof.
- Only the debug source-set implementation can grant the new exception, only
  for exact package `com.monumentogram.dora.debug` with FLAG_DEBUGGABLE. The
  release source-set implementation always denies, even with injected matching
  package, debuggable flag or marker. There is no exported Intent, broadcast,
  public API or product UI that enables either marker.

## Central boundary and unchanged authority

One internal device-security policy supplies credential availability and
unlocked-device readiness to AndroidAppLock/AppLockSession and the recording
access manager. Foreground acquisition requires readiness and the existing
process-local authorization. The existing active writer may continue in the
background using credential availability plus its current recording epoch;
this does not permit a new background microphone start.

AndroidAuthProof and AppLockAuthenticationActivity still require a REAL secure
credential for the real system-authentication route. They never treat a debug
marker as a system authentication result. Foreground loss, screen-off, stale
generations, resource retirement, process death and service ownership retain
their existing fencing. No unlock token is persisted and no microphone starts
on reboot or process recreation. A development entry using the insecure-device
exception visibly discloses: "Режим разработки: системная блокировка устройства
отключена". Release cannot display this debug-source notice.

## Existing encrypted storage

The accepted vault wrapping keys use `setUserAuthenticationRequired(false)`.
This change does not alter key creation, wrapping, aliases, SQLCipher, schema,
fsync/readback, Recovery or backup exclusions. Existing storage must be opened
with OPEN_EXISTING; a failed key/open operation must never fall back to creating
or replacing keys or a database. Before and after the owner changes the PIN,
retain content-free proof of the same aliases, existing vault and readable
recording. If the actual device invalidates a key, stop and report that result;
do not regenerate keys, clear data, uninstall, recreate storage or use plaintext.

The local instrumentation receipt covers a selected page of existing catalog
candidates (normal discovery excludes deletion tombstones), not exhaustive
readback of all old recordings. Selection is fixed prospectively: cursors
`f`, `e`, `c`, `8`, then empty; broaden ONLY after successful empty discovery.
Any failed/timeout discovery or failed candidate aborts, without replacement.
Read every candidate on the first nonempty page. The after-PIN test must use the
exact cursor retained in the before receipt and verify the same complete list.
All persistent aliases/key properties and all ciphertext files are checked
separately; matching these does not prove unread audio sources are readable.
The retained initial exhaustive-discovery timeouts are not converted to passes.
This authorized OPEN_EXISTING
readback can run the existing Recovery reconciliation; it is not a claim that
opening the database preserves every ciphertext byte. Separate quiescent file
inventories must be compared immediately before PIN removal and after removal
but before reopening DORA. The test must actually execute and produce a fresh
private receipt; its default skipped state in generic CI is not device evidence.

## Trade-off and restoration gate

An unlocked insecure development device offers less protection against a person
holding the phone. Development latency evidence is NOT production App Lock
acceptance. No cloud credential exception is granted. Marker files remain
private no-backup app data and must not be included in artifacts or exports.

`DEV-SECURITY-RESTORE-BEFORE-ALPHA-CLOSE` is OPEN and blocks final Alpha closure
and signed Alpha security acceptance until: system credential restored; both
development markers removed/disabled; DORA restarted; isDeviceSecure=true;
normal App Lock prompt verified; background/device lock revocation verified;
Resume after revocation requires fresh proof; release override impossibility
reverified; the same encrypted vault remains readable. Do not close this blocker
as part of the no-PIN development campaign.

Only the owner may remove the PIN in normal Android Settings with Android's
credential confirmation. No root, hidden locksettings manipulation, credential
deletion, Device Admin or Accessibility bypass is authorized.

## Stage and evidence boundaries

This is Stage 8.3 remediation on PR #94, not a new stage. A new complete
30 Pause + 30 Resume campaign must use the new exact commit, frozen thresholds,
and immediate detection of missing/ambiguous presentation evidence. Previous
failed measurements remain retained. Any successful campaign is qualified as
`PHYSICAL_LATENCY_ACCEPTANCE_EXECUTED_IN_OWNER-AUTHORIZED DEBUG NO-PIN DEVELOPMENT MODE`.
8.4 remains NOT_STARTED; Stage 8 remains IN_PROGRESS. Historical Sheet C74 stays
ПРОЙДЕНО; I74/J74 change only after successful instant-control publication.
