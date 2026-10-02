# ADR-DEV-001: Temporary owner-authorized development App Lock exception

Status: temporary development exception, explicitly requested by the owner on
2026-10-02 after the Stage 8.3 specification. Not production or stage acceptance.

The owner requested that DORA stop requiring their PIN/fingerprint repeatedly
while development proceeds. This narrow exception supersedes the earlier
no-authentication-bypass rule only for the opted-in local debug installation.

## Scope

- Only the debug source set contains the automatic grant implementation.
- Its package must be exactly `com.monumentogram.dora.debug`, Android must mark
  the installation debuggable, and the private no-backup marker
  `development-app-lock-no-prompt` must exist. It is disabled by default.
- The release source set always denies the exception, regardless of package,
  flags or marker. No intent, exported component or application UI enables it.
- Only an explicit existing foreground unlock/start/resume/recovery request may
  receive the development grant. The phone must still have a secure credential
  and be unlocked. Android's lock screen is not bypassed or reconfigured.
- Foreground loss, screen-off, explicit revocation, stale authorization fencing
  and process-local lifetime are unchanged. No microphone auto-start is added.
- SQLCipher, Keystore wrapping, encrypted audio, backup exclusions and stored
  recordings remain unchanged. No PIN or credential is stored or injected.
- Successful use displays a development-mode notice in the app.

## Enabling and removing the exception

On the owner's selected development phone, after installing the debug build,
create the empty marker with `run-as com.monumentogram.dora.debug` in the app's
`no_backup` directory. Do not create it in an emulator acceptance installation.
To restore normal authentication, remove that exact marker and restart DORA.
Clearing app data/uninstalling also removes it, but is not the recommended reset
because that would delete local recordings.

## Evidence boundary

Tests with this marker present are development-flow evidence, not proof of real
PIN/biometric enforcement. Prior real-authentication evidence remains historical.
Acceptance authentication tests must run with the marker absent. The Stage 8.3
source inventory and publication review must account for this later owner
exception before any final publication; no PASS, commit or publication is made
by this side task.
