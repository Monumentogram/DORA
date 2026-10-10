# OD-86B-LITE-PROTECTED-HISTORICAL-RECOVERY

Status: APPROVED / STRICTLY SCOPED by the owner on 2026-10-08. Implementation
admission is pending. Baseline 20057696c556a354d08e007dfcba736ff217fe6d; PR99
remains OPEN / DRAFT / UNMERGED. This supplements ADR-RECORDING-008 without
rewriting its evidence or the historical blocked result.

Exactly the 47 live historical sources in the authenticated private snapshot are
read-only during the controlled owner-only POCO debug campaign. Exact source,
asset, run and component identities define the policy. No UI selector, wildcard,
name or time filter. Every historical catalog/key/audio/metadata/tombstone state
is immutable. Deny mutating Recovery, Resume, Finalize and Delete before a durable
write. Existing authentication remains required for read-only inspection.

New positively identified test sources retain the ordinary full Recovery,
durable-prefix validation and deletion semantics. No global Recovery switch or
integrity bypass. New identities must not collide with any historical namespace.

Policy activation is debug-only, bound to the exact private snapshot and this
device/vault. Release cannot activate it. Missing, corrupt or mismatched policy
fails closed, including after process restart. Policy removal needs a separate
assessment before historical sources can encounter ordinary Recovery again.

Retain the original 8,550-file baseline. Only the three previously approved shared
SQLCipher files may change for new test sources; all other 8,547 files remain
byte-identical. All protected logical and cryptographic states must compare equal
in authenticated before/after inspection.

Isolation gate: implementation, positive/negative tests, unchanged historical
states, normal new-source create/recover/delete, release denial, restart proof,
independent clean review, exact APK, one bounded physical smoke and complete
preservation comparison. Only after PASS may the authorized fresh 60 cycles and
one USB-powered hour run proceed, with VAD/rotation/integrity/storage/thermal/
resource checks, bounded Recovery and deletion of only new test sources.

Stop affected operations on any protected-source risk or renewed long-run defect.
LONG01's historical cause remains UNPROVEN even if a successor succeeds. Battery
efficiency and USB-constrained Battery Saver deferrals remain unchanged. No battery
experiments, Stage9, GroupD, Cloud, ASR, Sheet update or merge/rebase/force-push.
Final exact-SHA four-job CI, review and publication audit remain separate gates.
