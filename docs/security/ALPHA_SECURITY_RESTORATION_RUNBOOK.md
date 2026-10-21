# Alpha security restoration — owner-controlled user 0 procedure

Status: PREPARED, NOT EXECUTED. `DEV-SECURITY-RESTORE-BEFORE-ALPHA-CLOSE = OPEN`.
Authority for this task ends before Phase 2. This document grants no device mutation or vault access. Software preflight does not grant distribution permission.

## Phase 1 — baseline and preservation plan

1. Identify the exact physical POCO M5 / API34 and explicitly select user 0. Do not infer identity from the currently foreground secondary user. Retain device identifiers only in private local evidence.
2. Read package-manager metadata for `com.monumentogram.dora` and `com.monumentogram.dora.debug`: installed user, UID, versionCode/name, install/update metadata. Allowed diagnostics are host ADB enumeration, read-only package metadata and user metadata. Do not start either application, instrument it, run-as it, enumerate private files or query Keystore.
3. The current task's read-only production version observation is code 4 / `0.1.0-alpha.2`. Candidate code 5 / alpha.3 is monotonic against that observation; repeat metadata checks immediately before any separately approved future installation. Signature continuity must also be checked. Code and signature compatibility alone do not establish storage migration safety.
4. Review ADR-DEV-001/002, installed software provenance and existing owner-private baseline evidence. Establish a separately approved, content-free continuity protocol before changing credentials: exact namespaces and aliases, same vault identity and schema, no replacement keys/database, no destructive Recovery. Do not assume an ordinary Android backup can restore non-exportable Keystore material. Copying ciphertext alone is not a recovery plan.
5. If a trustworthy baseline cannot be acquired without opening protected storage, stop at `OWNER_DECISION_REQUIRED`. No new baseline read is authorized by this runbook. Document which facts are unavailable; do not claim bytewise preservation from package metadata.
6. Signing backup is a different asset from encrypted user recordings. The accepted 2026-09-30 encrypted signing-backup restore receipt remains historical. Any fresh restoration exercise needs owner-controlled encrypted backup access; never expose its recovery password or replace the active signer.

## Phase 2 — owner actions, separate authorization required

Owner approval must identify device, user 0, exact packages, preservation protocol and allowed operations. The owner restores a secure PIN/password personally through Android Settings. Never put the credential in chat, ADB arguments, scripts, logs or Git. No locksettings, root, Accessibility, Device Admin or credential-file manipulation.

Under that separate approval, disable the two exact opted-in debug markers (`development-device-no-lock`, `development-app-lock-no-prompt`) without clearing app data or uninstalling. Confirm their absence/disabled state using the approved narrow procedure. Record only booleans. Do not publish private directory listings. Confirm `isDeviceSecure=true`; restart DORA only after the owner approves that action and the startup/Recovery implications.

Any key invalidation, unavailable alias, unexpected database creation/migration, Recovery request or uncertain result is a STOP. Do not recreate keys, reinstall, clear storage, replace the SQLCipher database, or try a different recording to hide a failed sample.

## Phase 3 — real authorization validation, separate authorization required

Use the installed exact approved package with both overrides disabled. Confirm the normal system App Lock prompt; wrong input and cancellation must leave protected-audio access denied. Confirm background and device lock revoke captured handles. Foreground return alone must not unlock. Resume after revocation requires fresh accepted proof. A cold restart must be locked with microphone OFF and no recording FGS. Notifications must not create recording authority.

Record content-free pass/fail outcomes tied to package, version, user and evidence digest. APK release denial evidence must come from the exact candidate, including both override implementations and negative inputs. Emulator evidence supplements but cannot replace these physical checks.

## Phase 4 — historical vault continuity, separate read authorization required

The owner must explicitly authorize the exact historical-vault read protocol. Use OPEN_EXISTING, the same namespace and key contour, and the prospectively selected recording set from the approved baseline. Confirm no key replacement, no SQLCipher database recreation and no destructive Recovery. Startup may reconcile existing state, so do not describe an application open as byte-preserving without evidence.

Authenticated readback may produce only content-free success/count/hash evidence under the approved protocol; no audio, IDs, aliases, ciphertext dumps or keys in public reports. A failure aborts the protocol and remains a failure. Never substitute user 10 or a newly created vault.

Without explicit historical read authorization: `same_encrypted_vault_readable = NOT_RUN`. Without all ten checks and evidence: restoration remains OPEN. Only a later owner-reviewed gate decision can set SATISFIED.

## Approval boundary and next task

`OWNER_DECISION_REQUIRED / USER0_SECURITY_RESTORATION`

Requested next scope: owner reviews Phase 1 preservation feasibility and explicitly approves a precise Phase 2–4 protocol, or leaves restoration OPEN. The next task must separately decide production upgrade safety and required physical production-APK evidence. This runbook does not authorize installing the candidate. Production installation in the present task: NOT_RUN. Signed Alpha distribution: BLOCKED.
