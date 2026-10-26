# Pilot v4 ARM82 — prospective physical acceptance

Status: PREPARATION ONLY. No POCO commands are authorized by this document.
Authority: Owner request dated2026-10-26; [ADR-0023](adr/ADR-0023-arm82-owner-alpha-preparation.md).
This replaces PIN-era procedure requirements only for the new candidate; older
protocols, receipts and LONG-01/LONG-02 evidence remain historical and untouched.

## Exact candidate and approval record

Expected package `com.monumentogram.dora.alpha.pilot`, code4,
name `0.1.0-owner-alpha.4`, ARM64 only. Expected certificate SHA256:
`a5f865ce5d28056429b323ede48f53e9ae8ad06880ea20ed242c2a2ba274603d`.
The exact APK SHA256, size, source SHA, private path and independent audit must be
copied from the completed private build receipt and final PR report into the Owner's
approval. A filename/version alone is insufficient. Missing or mismatching receipt
means STOP. `ARM82_DEVICE_RUNTIME=NOT_RUN` until this future campaign succeeds.

Request one separate explicit approval identifying the exact APK hash, this protocol,
POCO M5 target identity and full Secondary user10/serial10. Approval must cover fresh
read-only package/user/device preflight, conditional same-package update, launch,
model transfer/import and short synthetic/public recording/ASR cycle. No implicit
permission to change PIN/users/security settings, clear storage, uninstall, downgrade,
repair keys, run diagnostics/Recovery or touch historical user0 data is included.
If the model-use window has ended, obtain a separate renewal before import/inference.

## Phase A — future read-only preflight, then stop on any mismatch

Only after approval, choose one explicitly pinned ADB serial from the previously
accepted private device receipt. Never use unqualified adb, a guessed serial, an
automatic first-device selection or a bulk runner. Do not execute existing operator
scripts: they are synthetic/emulator or historical workflows, not this protocol.

1. Match serial, POCO M5 model/device, ARM64 ABI, Android14/API34 and build fingerprint
   against the privately held accepted baseline. Any firmware/identity change needs
   reassessment; do not infer ARM82 CPU support from ABI alone.
2. Read current user and full Android user topology. Require full Secondary userId10,
   serial10, not a managed/cloned profile, unlocked/available by the Owner's normal
   device interaction. Do not create/start/switch/remove users or set/unset credentials
   automatically. If needed state is not already available, STOP for Owner action.
3. Read exact Pilot package installation metadata for every existing user. Require
   Pilot installed only for user10, absent for user0 and every other user. Package
   code updates are global: `install --user 10` does not contain their code impact.
   Any other-user installation is STOP; no uninstall workaround is authorized.
4. Verify installed package/versionCode3/name0.1.0-owner-alpha.3, single base APK,
   owner certificate above, existing UID, package path and saved v3 APK hash against
   the accepted private v3 receipt. Read APK bytes only from the exact package code
   path if necessary for signature/hash verification; never read its data directory.
   If installed version differs, STOP and obtain a revised upgrade assessment.
5. Require no active Pilot recording/foreground service, instrumentation, package
   installation session or pending interrupted-record recovery. Do not force-stop an
   active recording to make preflight pass. Record available storage and require space
   for APK staging,264464607-byte model and bounded encrypted test recordings.
6. Record content-free package metadata for production/debug/diagnostic packages and
   users from existing authorized records/read-only package queries. Do not launch,
   enumerate private files, decrypt, hash or invoke Recovery on LONG-01/LONG-02 or
   any historical user0 store. Metadata equality is not content-integrity proof.
7. Independently verify local candidate bytes/signature/manifest again. Require release
   flags, no hidden operator/test instrumentation, exact ABI/native receipt and still
   valid model custody. Record reviewer signoff and explicit GO/STOP before mutation.

Current task cannot confirm any of these fresh device conditions. Physical execution
is blocked until all are observed under the separate authorization.

## Phase B — controlled update and model import

With Phase A GO, update only the approved Pilot using the same certificate and higher
code, preserving data. The future explicit command shape is
`adb -s <approved-serial> install --user 10 -r <exact-approved-apk>`.
No `-d`, `-t`, uninstall, data clear, install-existing, signing rotation or automatic
retry. On any install error STOP and retain evidence. Do not downgrade as rollback.

Verify post-install package code4/name/certificate/UID/user membership and exact APK
hash before launch. User0/history package metadata must be unchanged. A package-manager
update may change Pilot's code path legitimately; existing UID/data namespace must remain.

Launch normal MainActivity only in already confirmed user10. Confirm visible
`DORA Owner Alpha 0.1.0-owner-alpha.4 (4)` and no app PIN/biometric/system-credential
request. No microphone starts automatically. Ordinary schema3→4 migration may occur;
it must preserve the existing vault/Keystore. Missing keys, corruption, unexpected
recovery UI, lost saved records or migration failure means STOP, never recreate keys
or storage. Existing app sandbox is not a backup and rollback is not promised.

Model absence must be shown honestly. Transfer only the exact approved public model
to a separately approved temporary location belonging to user10's document-provider
environment; do not assume `/sdcard` denotes user10. If location/user ownership cannot
be confirmed without touching user0, STOP. Use the ordinary model document picker.
Verify model size264464607 and SHA256
`49c8fb02b65e6049d5fa6c04f81f53b867b5ec9540406812c643f177317f779f` before transfer;
the app independently checks these values and expiry. Remove only the newly created
transfer copy after successful import if that exact cleanup was authorized. Do not
use run-as/root, diagnostic intents or direct vault/model injection on the phone.

## Phase C — one bounded end-to-end cycle

Use Owner-approved synthetic/public speech; no private conversation or bystander data.
One recording at most60seconds (including the explicit Pause/Resume check):

1. Start manually, speak a short known RU phrase, Pause, Resume, then Stop/save.
2. Record the exact new recording ID, finalized state and durable frame count. Open
   its ordinary saved-record integrity/readback action; keep identifiers private.
3. Start local ASR, observe real queued/running/completed or truthful error state.
   Unsupported CPU/runtime failure is STOP for ARM82 acceptance, not fake success.
4. Confirm nonempty intelligible expected text locally; do not publish full transcript
   or audio. Record only content-free PASS/FAIL and timing/memory metadata if available.
5. Save a deliberate correction. Leave/close and reopen the app; confirm the original
   machine text and separate correction persist and active version is unchanged.
6. Rerun the same original audio. Confirm a second immutable machine version appears,
   the correction and first version remain, and active selection changes only after
   explicit user choice/conflict decisions. Reopen once more to verify persistence.
7. If authorized within the short campaign, repeat a separate short EN sample. A
   skipped language remains NOT_RUN; it is never inferred from prior x86 evidence.

No stress/long recording, destructive deletion, Cloud, historical Recovery or production
upgrade test is included. Recording remains capped at300seconds /4800000frames.
Timestamp availability remains UNAVAILABLE, not an invented alignment score.

## Phase D — preservation and content-free evidence

After capture/inference stop, record Pilot package identity, signer, UID/user membership,
new-record readback/reopen and both versions/correction. Compare previously captured
historical package metadata. The operator log must demonstrate zero commands targeting
user0 app data, historical packages, LONG records, device security or user configuration.

Inspect the approved transfer location for only the test-created copy; no audio export
is expected. Static source/audit and synthetic tests establish the in-memory protected
PCM path. A nondebuggable phone APK cannot be inspected with run-as: do not turn on
debugging/root or create an operator just to inspect private files. Therefore device
private-files plaintext absence remains qualified by static/synthetic evidence unless
a separately approved nondestructive inspection method exists. Never claim a full
filesystem audit from an empty public directory.

Keep raw diagnostics private and avoid transcript/audio log capture. Publish only
sanitized hashes/counts/statuses. No operation performed is distinct from proof of
historical content integrity; the latter remains unresolved without separate scope.
Any deviation: stop the campaign, preserve current state, report exact failure and
request a separate remediation task. Do not retry with weaker gates.
