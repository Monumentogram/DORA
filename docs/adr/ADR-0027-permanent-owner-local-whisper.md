# ADR-0027 — No calendar expiry for owner-local Whisper model

Date: 2026-10-28. Status: owner-approved for a scoped Android fix; physical
upgrade and release acceptance remain separate.

## Context

ADR-0022 and the Stage9 Alpha implementation admitted an optional, owner-supplied,
exactly pinned Whisper SMALL q8 model only through 2026-11-02 inclusive (Moscow).
The `PinnedModelStore` deadline did more than disable recognition: on the next
lookup it could delete the valid app-private model. This would unexpectedly
disable offline RU/EN even though the bytes, runtime and user permission had not
changed. The owner explicitly requested permanent local usability on 2026-10-28.

## Decision

- Remove the calendar deadline and calendar-triggered deletion from the Android
  model store, native ASR availability/cancellation check and visible copy. A
  valid existing model remains installed and eligible on every later calendar
  date, including after 2026-11-02; it is not automatically fetched or uploaded.
- Preserve exact verification of the model's 264,464,607-byte size and pinned
  SHA-256 `49c8fb02b65e6049d5fa6c04f81f53b867b5ec9540406812c643f177317f779f`.
  Preserve bounded, atomic import, symlink rejection, cancellation by the user,
  and rejection of malformed/corrupt replacement files without overwriting a
  valid admitted model.
- Model weights remain outside the APK, Git, CI, telemetry, remote backup and
  cloud. The already owner-provided file is held in app-private `noBackupFilesDir`.
  User deletion, application uninstall or actual storage loss can still remove
  the model; "permanent" means no calendar-based software expiry or deletion.
- This authorization is **only** for continued owner-local evaluation and use of
  the pinned model. It does not change data-set consent/deletion schedules,
  personal-audio retention, cloud/S3/Amazon retention, user-edit history,
  production model delivery, public redistribution or release permissions.
- ADR-0022's model deadline is superseded only in this one field. Its other
  bounded Alpha acceptance, privacy, JNI and historical-vault gates stay intact.
  Immutable Stage5/Stage9 source evidence is not rewritten.

## Verification and rollout

Tests must show a valid model survives the former deadline and repeated app
access, while wrong-size/digest imports are refused and an existing valid model
is never replaced after a failed import. Full Android CI, exact-source code
review and a new in-place APK installed on the target device are distinct gates.
The old installed APK remains capable of deleting its model on or after
2026-11-03 00:00 MSK; merely merging this ADR or shipping source is not enough.
No PR merge, device modification or AWS operation is implied by this decision.
