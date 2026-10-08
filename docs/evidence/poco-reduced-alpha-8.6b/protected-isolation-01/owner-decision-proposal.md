# Proposed resolution: protected historical Recovery policy

Status: PROPOSED / NOT_APPROVED. This document grants no execution authority.

OD-86B-LITE-SHARED-CATALOG-EXCEPTION protects all 47 historical sources while
requiring normal Recovery for sources other than LONG01. The authenticated
snapshot found another original whose open technical tail can cause normal
Recovery to persist metadata. LONG01-only exclusion cannot guarantee preservation.
The risk is conditional on successful canonical-prefix authentication; that
original's PCM was not read in this investigation. Do not mutate it to resolve
the uncertainty.

Requested owner decision: permit a temporary diagnostic read-only policy for
exactly the 47 existing historical sources, including LONG01, during the scoped
Stage 8.6B-LITE campaign. This is a deliberate, explicit exception to normal
mutating Recovery for those exact sources only, not a product Recovery change.

Implementation constraints if approved:

- Freeze exact authenticated private source, run and component identities from
  the preserved snapshot. No names, timestamps, wildcard or arbitrary UI selector.
- Block mutating recovery/discovery, continuation, finalize, deletion, key and
  filesystem operations for protected identities before the first durable write.
  Read-only inspection may display a protected diagnostic state without repair.
- New, positively admitted test sources retain normal Recovery and deletion.
  No global disableRecovery switch. No bypass for source/run identity collisions.
- Activation is owner-only, private and diagnostic; missing, corrupt or mismatched
  policy fails closed. Release builds cannot activate this policy. Lifecycle and
  process restart cannot silently remove the fence.
- Preserve every historical row, key and namespace. Only the three already
  approved shared files may change for new test objects; the other 8,547 files
  remain byte-identical. Retain the original 8,550-file baseline unchanged.
- Add negative tests and obtain independent review before in-place APK update;
  then one bounded recording/readback/deletion and full before/after preservation
  gate. No campaign until that gate passes.
- After isolation PASS, the already authorized 60-cycle and one-hour USB campaign
  may proceed with fresh identities. No battery tests or later-stage admission.

No repair, deletion, finalization or Resume of any historical source is requested.
No permission to conceal the old failure or to weaken canonical/durability gates
is requested. Removing diagnostic policy later requires a separate disposition
if it would expose protected records to normal mutating Recovery.
