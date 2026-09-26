# SMALL private storage ACL diagnosis/remediation — Stage 0 v0.1

Task **5.6C.2A.3 = PASS / PRIVATE_STORAGE_BOUNDARY_READY**. Date: 2026-09-26.
Root cause: **ACL_QUERY_FAILURE**, at the **CONTROLLED_ACCEPTANCE_ROOT** query.
The failure was module loading, before an ACL could be evaluated. No actual
acceptance ACL drift or enclosing foreign-Allow rejection was found.

This is a host/storage-security result. The model, data and measurement path were
not exercised. **5.6C.2B remains BLOCKED / NOT_AUTHORIZED.**

## Preserved preceding event

The separately authorized v0.3 invocation stopped with
`BLOCKED / PRIVATE_STORAGE_ACL_INVALID`, at operator commit
`9d92549b504d04e4b9022ce1f4fae793a7c4bdd0`.

| Historical fact | Value |
|---|---|
| Campaign API calls | 1 |
| Campaign-started marker | NOT_CREATED |
| Attempt journal / terminal aggregate | NOT_CREATED / NOT_CREATED |
| Primary measured attempts | 0 |
| Completed RU / EN | 0 / 0 |
| Quality / resources | NOT_EVALUABLE / NOT_EVALUABLE |
| Candidate disposition | NOT FORMED |
| Evaluator reached | No |
| Automatic / manual retries | 0 / 0 |
| Threshold changes / tuning | 0 / 0 |
| Repository result commit | None |

The retained private receipt, traceback, invocation configuration and failed work
directory were preserved. The receipt's whole-file identity is recorded in the
[aggregate evidence](../evidence/poc-asr-001/alpha-asr-small-private-storage-acl-remediation-stage0-v0.1.json).
This event is neither a model rejection nor an INCONCLUSIVE evaluator result.
It is distinct from the older, unchanged [schema-blocked report](DORA_ALPHA_ASR_SMALL_CAMPAIGN_RESULT_STAGE0_V0_1.md).

## Diagnosis before any remediation

The retained stack reaches `campaign`, `verify_inputs`, `require_private`, then
the ACL error. Reproducing only the unchanged v0.3 storage checker identifies the
first rejected object as the acceptance root; the failed work path independently
shows the same query error. Each child query exits 1 with
`CouldNotAutoloadMatchingModule`, not the foreign-Allow rejection exit 2.

The host uses PowerShell 7.6.5. Python inherits its module environment and starts
Windows PowerShell 5.1.26100.9444. In that two-hop process chain,
`Microsoft.PowerShell.Security` import fails with `FormatXmlUpdateException`.
The same child module import passes when its process-local `PSModulePath` selects
the Windows PowerShell system module directory. Direct invocation from the parent
PowerShell also imports successfully; it did not reproduce the Python-child failure.
No system-wide environment or module installation was changed.

Independent ACL metadata inspection established:

| Object class | Owner / Allow access | DACL | Result |
|---|---|---|---|
| CONTROLLED_ACCEPTANCE_ROOT | CURRENT_USER only, FullControl | PROTECTED_DACL, EXPLICIT | PASS |
| 111 acceptance descendants: 7 directories, 104 files | CURRENT_USER only, FullControl | INHERITED from protected store | PASS |
| PRIVATE_CLASS_ENCLOSING_ANCESTOR | CURRENT_USER only, FullControl | PROTECTED_DACL, EXPLICIT | PASS |
| Failed task work directory | CURRENT_USER only, FullControl | INHERITED, unprotected | Not eligible under future work contract |
| Failed task work parent | CURRENT_USER only, FullControl | INHERITED, unprotected | Preserved |

There were no foreign Allow ACEs, wrong owners or reparse entries in acceptance.
Both path chains were outside Git/worktrees. The first diagnosis performed 115
successful ACL reads; the v0.3 reproduction made two failed ACL-query attempts.

## Admitted boundary and selected remediation

The authority is the unchanged [5.6C.1.1 source-recovery evidence](../evidence/poc-asr-001/alpha-asr-small-holdout-source-recovery-stage0-v0.1.json)
and its [report](DORA_ALPHA_ASR_SMALL_HOLDOUT_SOURCE_RECOVERY_STAGE0_V0_1.md).
The original storage-establishment recipe identifies the same existing acceptance
location. Its root must have CURRENT_USER ownership and a protected DACL containing
exactly one explicit CURRENT_USER FullControl Allow rule. Descendants remain owned
by CURRENT_USER and contain only CURRENT_USER FullControl access. Inherited access
from that protected root is admitted; foreign inherited or explicit Allow is not.
ACL query failure remains fail-closed.

Ancestors retain location, Git/worktree, reparse/junction, sync/public and
nonadministrative-share checks. The enclosing private-class directory also happens
to be protected owner-only in the observed state. This diagnosis does not establish
the hypothesized CASE B ancestor mismatch. CASE A storage drift is also absent.

The remediation is **process-local host environment isolation**. Before the
Python storage checker is launched from PowerShell, use:

```powershell
$env:PSModulePath = Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\Modules'
```

This changes only that process and its future children. It does not persist user
or machine environment settings, alter a Windows directory ACL, suppress an ACL
error, or grant extra access. The unchanged v0.3 acceptance/work storage checks
then pass. No campaign API or full input verifier was called in this task.

**v0.3 is retained byte-identical; no v0.4 is created.** Operator ID:
`dora-alpha-asr-small-campaign-operator-v0.3`. Canonical SHA-256:
`e07f881b11fce21fed27c93178524279af641abb716343c7333884bdc21fea96`.
Its source commit remains `9d92549b504d04e4b9022ce1f4fae793a7c4bdd0`.
All 19 transitive source/test identities and 75 protected historical file identities
remain unchanged. This documentation commit does not change v0.3's repository
HEAD/parent/source gates; any later execution must satisfy them under separate scope.

## Future work-directory contract

A future launch requires a directory that already exists, is empty, belongs to
CURRENT_USER and has its own protected DACL with exactly one explicit CURRENT_USER
FullControl Allow rule. It must be in the approved local private storage class,
outside Git/worktrees, sync/public/nonadministrative shares, without any ancestor
or target reparse point, and without overlap with acceptance/model/runtime/native
inputs. These conditions must be rechecked immediately before a separately
authorized launch. Any failure blocks execution.

One new empty future work boundary was created and given that ACL. Existing ACL
modifications, including acceptance, the failed work directory and all ancestors:
**0**. New work-boundary ACL initializations: **1**; total ACL writes: **1**.
No file or campaign marker was placed in that new work directory.

The strengthened protected/empty work requirement is a mandatory external
storage-only prelaunch gate. Frozen v0.3 does not itself enforce a protected work
DACL or general directory emptiness. Its existing storage and one-shot checks remain
required in addition. The historical failed work is not reused or repaired.

## Bounded actual storage-only acceptance check

Result: **PRIVATE_STORAGE_BOUNDARY_READY**.

The final check verified the acceptance root, all 111 descendants, 48 audio-entry
names and 48 reference-entry names, the new work boundary, and path relationships.
It found no Git/worktree, reparse, sync/public or nonadministrative-share relationship.
Acceptance names, sizes, creation/write timestamps and attributes matched the
pre-remediation stat snapshot; the admitted locator is unchanged. No package
contents, documents or selections were written or relocated.

The complete ACL/stat check performed 113 successful ACL reads. A bounded call to
the original v0.3 **storage-only** function then passed for acceptance and future
work, making four further successful ACL reads. During that call, content-read and
content/model/device/campaign entry points were guarded against progression.
Total actual-storage ACL query attempts: **234**; successful reads: **232**;
failed diagnostic attempts: **2**. Synthetic host fixtures are accounted separately.

Private audio, references, metadata document bodies and model bytes were not read.
Consequently private content hashes were not recomputed. Frozen document/model
digests in the aggregate record are inherited authorities, not fresh content-integrity
claims. No content-byte change was performed; mandatory future content verification
is not replaced by this metadata-only result. Retention remains **2026-10-25**.

All actual forbidden-operation counters are **0**: campaign API calls, campaign
markers, attempt journals, measured attempts, model byte reads/loads, audio/reference
content reads, decoding, ASR inference, whisper_full, device connections/executions,
ADB, codec/warmup, threshold changes, candidate tuning, reselection,
rematerialization and private-package content writes.

## Validation, publication and limits

Applicable frozen ASR host tests: **233 PASS**, including **23 v0.3 tests**;
0 failures/errors/skips. Sixteen tests that call campaign APIs were explicitly
excluded to comply with this task. Additional campaign-entry guards recorded zero
call attempts. This is not a claim that all 249 tests were run. Fixtures were
generated; no real corpus/model/device was used. External diagnostic environment-key
casing and host-launcher import-path errors were corrected before the relevant
probe/test run; repository code/tests were unchanged, and no failed host test was retried.

`python -B tools/validate_stage00.py`: **7 PASS**. Strict JSON, local Markdown
references, privacy, `git diff --check`, exact four-file scope and historical raw
byte identity are checked. Prior status/backlog text is preserved by additive insertion.
Independent read-only review of the result, requirements, frozen source and sanitized
receipts found no actionable findings. It accessed no actual private data and ran no
scripts/tests; no human-approval claim is made. Only the following public files change:

- `docs/stage0/DORA_ALPHA_ASR_SMALL_PRIVATE_STORAGE_ACL_REMEDIATION_STAGE0_V0_1.md`
- `docs/evidence/poc-asr-001/alpha-asr-small-private-storage-acl-remediation-stage0-v0.1.json`
- `docs/DORA_MVP1_STAGE_STATUS.md`
- `docs/DORA_MVP1_IMPLEMENTATION_BACKLOG.md`

Branch: `chat/alpha-asr-runner-scope`; initial local/freshly fetched remote HEAD:
`9d92549b504d04e4b9022ce1f4fae793a7c4bdd0`; immediate parent:
`5c148a966a927842d15aa470d62b3629f62e354e`. The initial tree, including untracked
files, was clean. Publication is one atomic documentation commit to that branch.
No new PR, merge or CI dispatch. Configured push triggers do not match this branch;
no CI PASS is claimed.

[PR #86](https://github.com/Monumentogram/DORA/pull/86) was checked read-only:
OPEN, draft, unmerged, head `b951bc454d550e33669ebf4f276a4b09177a99ca`; untouched.
Recovery remains **0D.6 = ALPHA CLOSED / FULL OPEN**. POC-ASR-001 remains
BLOCKED / NOT_READY; Stage 5 and Group B are not marked PASS. Quality/resources
remain NOT_EVALUABLE and no SMALL candidate disposition or production readiness
is formed. No ACL-query blocker remains in the repaired host environment; a future
campaign still requires separate owner authorization and exact frozen source checks.

**5.6C.2B remains BLOCKED / NOT_AUTHORIZED.**
