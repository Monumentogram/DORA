# POC-OFFLINE-001 I1 semantics and local implementation scope — Stage 0 v0.2

Decision packet ID: `OFF-I1-SEMANTICS-SCOPE-002`

Machine mirror: [`docs/evidence/poc-offline-001/i1-semantics-and-implementation-scope-stage0-v0.2.json`](../evidence/poc-offline-001/i1-semantics-and-implementation-scope-stage0-v0.2.json)

Disposition: `LOCAL_PURE_HOST_IMPLEMENTATION_SCOPE_DEFINED_REVIEW_REQUIRED`

Artifact state: `DECISION_PACKET_COMPLETE_PENDING_INDEPENDENT_ADVISORY_REVIEW`

Backlog truth: `POC-OFFLINE-001 = TODO / NOT_READY / NOT_RUN / NOT_AUTHORIZED`

Recorded: `2026-08-16T17:41:31.2773422+03:00`, `Europe/Moscow`

This additive packet resolves the seven fail-closed semantic groups recorded by I1 v0.1 and freezes
one narrow, local, unpublished implementation contour. It creates no oracle source, runs no test,
and changes no product, Android, device, network, model, provider, dependency, production, Legal,
formal Security, publication or merge authority. A Markdown/JSON semantic mismatch invalidates the
packet. The v0.1 scope artifacts remain immutable.

## 1. Exact source and authority boundary

The stacked local branch starts at PR #28 head
`d164b6372ad3e717762d48a9a3ea7e6551602ab7`, tree
`076c9eeda8180d775c6ec6dbb1dc5cc53aeb7ce6`, whose parent is exact GitHub `main`
`78e9dd07d616989987118f26bb16ebb9932ddb2b`, tree
`f403bf60b86273ee8a2634ed5e8530c9d4af20e4`.

| Immutable input | Bytes | SHA-256 |
|---|---:|---|
| `docs/stage0/DORA_MVP1_POC_OFFLINE_READINESS_CONTRACT_STAGE0_V0_1.md` | 45377 | `9a905eabbcd75601fc598cccc001dcff56a5b6b65eeb936f2a9c4602d658682a` |
| `docs/evidence/poc-offline-001/readiness-contract-stage0-v0.1.json` | 58124 | `a564cf9031f610006327c374baff82983de2ea9ba4b6c9d13d6185e7967a6dae` |
| `docs/stage0/DORA_MVP1_POC_OFFLINE_I1_HOST_ORACLE_SCOPE_STAGE0_V0_1.md` | 32916 | `f3134d5e97fc8c728b31260a056d3d916e523a5a34256bee2dd89d486292f4f2` |
| `docs/evidence/poc-offline-001/i1-host-oracle-scope-stage0-v0.1.json` | 52997 | `83075ca723f007b1f282fe0e089a7725a68d8bcf69ebb084f0f1a44704fb97b7` |

Applicable authority was reconciled in repository precedence: Technical Plan §§21.3, 22 and 28;
Product Decisions `DEC-009`, `DEC-012`, `DEC-013`, `DEC-015`, `DEC-017`, `DEC-020`; accepted
ADRs; Test Strategy `TS-UNIT`, `TS-OFFLINE`, `TS-PRIVACY` and Tier A; the parent Offline contract;
and I1 v0.1. `DEC-012` remains Proposed: this packet preserves the already-versioned deletion
pending/receipt boundary but selects no retention duration, provider or production deletion policy.

The owner's standing delegation covers ordinary reversible local project decisions. Therefore this
packet may define a future disposable host implementation. Activation is derived only from strict
packet validation plus a separate, immutable, hash-bound clean AI advisory-review record. The
packet is never rewritten merely to activate it. That review is not a formal-human, Security, Legal
or production approval.

The existing 25 `authorityFlags` remain `false`. Separate packet facts are:

- `prospectiveLocalImplementationScopeAllowed=true`;
- `activationRequiresCleanIndependentAdvisoryReview=true`;
- `activationConditionsMet=false` in this immutable packet; a conforming external review record is
  the only activation input;
- `implementationCreatedByThisPacket=false`, `implementationExecutedByThisPacket=false`;
- `commitAllowed=false`, `pushAllowed=false`, `pullRequestMutationAllowed=false`,
  `readyAllowed=false`, `mergeAllowed=false`.

The versioned `scopeFlags` mirror also fixes all of these to false: validator/test/build execution,
data-store or human-data access, new dependency, Android integration, status mutation, publication,
formal Security approval, formal reviewer status and PR advancement. The prospective local scope
fact above is the only true capability flag and is not implementation or execution authority.

## 2. Common deterministic primitives

- Inherited `contractId` is exactly `poc-offline-readiness-stage0-v0.1`.
- All text bytes are strict UTF-8 without BOM; `LF` is exact byte `0x0A`.
- No normalization, case folding, trimming, locale conversion or platform-default charset occurs.
- Synthetic opaque IDs match `[A-Za-z0-9._:-]{1,128}`; `~` is forbidden.
- Digests are SHA-256 rendered as exactly 64 lowercase hexadecimal characters.
- Integers are signed 64-bit non-negative values serialized in base 10 without leading zeroes.
- Future oracle canonical input, result and snapshot JSON accepts no duplicate, unknown, missing or
  reordered key; no trailing byte or insignificant whitespace; no float, exponent, negative zero,
  `NaN` or infinity. This rule does not apply to the two-space pretty-printed governance mirror,
  which instead requires strict duplicate rejection and exact Markdown semantic parity.
- Malformed UTF-8, unknown catalog value, overflow, invalid phase or noncanonical bytes are
  `INVALID_INPUT`.

## 3. `OFF-I1-UNRESOLVED-001` — six exact hash preimages

Status: `RESOLVED_BY_V0_2`.

The framing functions are exact:

```text
PREFIX(domain) = UTF8(contractId) || LF || ASCII(domain) || LF
STRING_FIELD(name,value) = ASCII(name) || ASCII("=S") ||
  ASCII(decimal UTF-8 byte length of value) || ASCII(":") || UTF8(value) || LF
NULL_FIELD(name) = ASCII(name) || ASCII("=N") || LF
```

The decimal length has no leading zero. `NULL_FIELD` is not used by the six identifier formulas; it
is used only by the separately defined replay-input digest when deletion scope is absent. A
nullable identifier hash itself is JSON `null`: no preimage is built and no digest of empty bytes,
`NULL_FIELD`, `"null"`, zero bytes or zeroes is allowed.

| Ledger field | Domain | Exact ordered non-null fields |
|---|---|---|
| `logicalKeyHash` | `OFF_I1_LOGICAL_KEY_V1` | `operationClass`, `opaqueLogicalKeyId` |
| `jobIdHash` | `OFF_I1_JOB_ID_V1` | `logicalKeyHash`, `opaqueJobId` |
| `resultIdHash` | `OFF_I1_RESULT_ID_V1` | `jobIdHash`, `opaqueResultId` |
| `deletionScopeDigest` | `OFF_I1_DELETION_SCOPE_V1` | `scopeClass`, `opaqueScopeId` |
| `deletionIdHash` | `OFF_I1_DELETION_ID_V1` | `logicalKeyHash`, `deletionScopeDigest`, `opaqueDeletionId` |
| `deletionReceiptIdHash` | `OFF_I1_DELETION_RECEIPT_ID_V1` | `deletionIdHash`, `deletionScopeDigest`, `opaqueDeletionReceiptId` |

Each digest is `lowercaseHex(SHA-256(PREFIX(domain) || STRING_FIELD(...)...))`. The only deletion
scope class in I1 is `REMOTE_CONVERSATION_COPY`, an I1 semantic label rather than a product schema.

Lifecycle is exact: `logicalKeyHash` is required and immutable for every queue row; `jobIdHash` is
null before non-deletion acceptance and required/immutable after it; `resultIdHash` is required only
for `RESULT_AVAILABLE`, `APPLIED` or `CONFLICT`. Deletion-specific fields are null on non-deletion
rows. `deletionScopeDigest` is required from enqueue, `deletionIdHash` is null before durable
acceptance and immutable afterward, and `deletionReceiptIdHash` is null until the exact scoped
receipt verifies and immutable afterward. Raw values and preimages never enter evidence,
diagnostics, exceptions, stdout or stderr.

Replay identity is content-free and separate from the six identifier hashes:

```text
canonicalInputDigest = lowercaseHex(SHA-256(
  PREFIX("OFF_I1_REPLAY_INPUT_V1") ||
  STRING_FIELD("operationClass", exactOperationClass) ||
  STRING_FIELD("logicalKeyHash", logicalKeyHash) ||
  STRING_FIELD("inputVariant", PRIMARY or ALTERNATE) ||
  (deletionScopeDigest is null
    ? NULL_FIELD("deletionScopeDigest")
    : STRING_FIELD("deletionScopeDigest", deletionScopeDigest))))
```

`inputVariant` is a synthetic I1 fixture discriminator, never product payload or user content.

## 4. `OFF-I1-UNRESOLVED-002` — total reducer and 67-action projection

Status: `RESOLVED_BY_V0_2`.

Structural validation precedes rule selection. Eligible rules are ordered ascending by
`(priority, secondaryOrder, ASCII ruleId)`. At most one rule is selected. Axes not named by a rule
remain byte-identical. Every known valid action is covered by the projection below; a valid action
with no eligible rule returns `NO_STATE_CHANGE`; a well-formed policy refusal returns
`REJECTED_NO_STATE_CHANGE`; malformed input returns `INVALID_INPUT`.

Every queue rule below has exact `secondaryOrder=0`. Its exact machine fields additionally freeze
`event`, `outcome`, `queueAffecting`, counter deltas and both ledger-append booleans; prose in the
last column is only a compact mirror of those fields.

### 4.1 Non-deletion queue rules

| Rule | Priority | Eligible source and guard | Target / exact effect |
|---|---:|---|---|
| `OFF-I1-RULE-Q-001` | 10 | non-deletion, invalid/revoked consent | `CANCELLED`; no attempt/effect/apply |
| `OFF-I1-RULE-Q-002` | 20 | same key, different canonical input | `NO_STATE_CHANGE`; mismatch code; zero ledger/counter delta |
| `OFF-I1-RULE-Q-003` | 30 | `WAITING_NETWORK`/`PENDING_UPLOAD`/`FAILED_RETRYABLE`, profile/runtime blocked | `PENDING_UPLOAD`; no attempt |
| `OFF-I1-RULE-Q-004` | 40 | `PENDING_UPLOAD`/any `FAILED_RETRYABLE`, denied | `WAITING_NETWORK`; no attempt; preserve null or immutable non-null `jobIdHash` |
| `OFF-I1-RULE-Q-005` | 41 | `UPLOADING`/`REMOTE_PROCESSING`, connectivity lost | `FAILED_RETRYABLE`; retain started attempt/effect |
| `OFF-I1-RULE-Q-006` | 50 | active pre-application, explicit valid cancel/revoke | `CANCELLED`; no new counters |
| `OFF-I1-RULE-Q-007` | 100 | `LOCAL_ONLY`, enqueue | `PENDING_UPLOAD`; new immutable intent/key; counters zero |
| `OFF-I1-RULE-Q-008` | 110 | `PENDING_UPLOAD`, scheduler while denied | `WAITING_NETWORK`; counters unchanged |
| `OFF-I1-RULE-Q-009` | 120 | `WAITING_NETWORK`, all revalidation allows | `PENDING_UPLOAD`; no attempt yet |
| `OFF-I1-RULE-Q-010` | 130 | `PENDING_UPLOAD`, available and positive budget | `UPLOADING`; `attemptCount += 1` |
| `OFF-I1-RULE-Q-011` | 140 | `UPLOADING`, first durable remote acceptance | `REMOTE_PROCESSING`; set immutable job; effect `0→1` |
| `OFF-I1-RULE-Q-012` | 150 | `REMOTE_PROCESSING`, valid result | `RESULT_AVAILABLE`; set immutable result |
| `OFF-I1-RULE-Q-013` | 160 | `RESULT_AVAILABLE`, validation/user truth allow | `APPLIED`; apply `0→1` |
| `OFF-I1-RULE-Q-014` | 161 | `RESULT_AVAILABLE`, current user truth blocks | `CONFLICT`; apply remains zero |
| `OFF-I1-RULE-Q-015` | 170 | `UPLOADING`/`REMOTE_PROCESSING`, retryable error | `FAILED_RETRYABLE`; no rollback |
| `OFF-I1-RULE-Q-016` | 171 | `FAILED_RETRYABLE`, no job, revalidation allows; positive remaining with no grant, or zero remaining with eligible grant | `PENDING_UPLOAD`; counter delta zero in the positive/no-grant branch, otherwise manual grant `+1`; attempt zero |
| `OFF-I1-RULE-Q-017` | 172 | `FAILED_RETRYABLE`, job exists, revalidation allows; positive remaining with no grant, or zero remaining with eligible grant | `REMOTE_PROCESSING`; counter delta zero in the positive/no-grant branch, otherwise manual grant `+1`; attempt/effect zero |
| `OFF-I1-RULE-Q-018` | 180 | active/retryable, terminal or automatic budget exhausted | `FAILED_FINAL`; no automatic wakeup |
| `OFF-I1-RULE-Q-019` | 190 | `REMOTE_PROCESSING`, same-input transport replay | state unchanged; attempt `+1`; effect stays one |
| `OFF-I1-RULE-Q-020` | 191 | `APPLIED`, same-input duplicate trigger | return prior result; no counter delta |

`FAILED_RETRYABLE` phase is derived only from `jobIdHash` nullity. Initial automatic attempt budget
is exactly 3; an explicit manual grant adds exactly one attempt. These are I1 fixture values, not a
product retry/energy policy. There is no sleep, jitter or wall-clock scheduling.

The exact reducer outcome catalog is `TRANSITION_APPLIED`, `NO_STATE_CHANGE`,
`REJECTED_NO_STATE_CHANGE`, `INVALID_INPUT`. The machine mirror contains a flattened 67-row
`actionRuleProjection`. Every row binds the inherited action and derived event to a scalar rule and
outcome, transition, `queueAffecting` and exact flow/queue append booleans. Direct local,
processing, lifecycle, snapshot and restore rules have ID
`OFF-I1-RULE-A-<scenario three digits>-<ordinal two digits>`, priority 1000 and
`secondaryOrder=<one-based global inherited action index>`; their source is the pinned action slot,
guard is structural validation plus the exact directive precondition, target is the row transition
and outcome is `TRANSITION_APPLIED`. Eligibility is the conjunction of: exact pinned
scenario/action/event/ordinal tuple; exact next ordinal; structurally valid typed input and vector;
the directive-specific source predicate; absence from the exact 15 non-direct action IDs in the
machine mirror; and no scenario-026 selector. Every direct rule has zero counter delta,
`queueAffecting=false`, `flowLedgerAppend=true` and `queueLedgerAppend=false`.

Direct transitions are an exact lookup catalog, not a parsed free-form language: `=`,
`L:LOCAL_OPERATION_RUNNING`, `L:LOCAL_OPERATION_SUCCEEDED`,
`L:LOCAL_OPERATION_SUCCEEDED;P:PRESERVE`,
`L:LOCAL_OPERATION_SUCCEEDED;Q:PRESERVE_WAITING_NETWORK`, `L:PRESERVE`,
`P:PENDING_CAPABILITY`, `P:PROCESSING_SUCCEEDED`, `P:WAITING_MODEL`, `SNAPSHOT:=` and
`RESTORE:=`. Each of the five axes is either `PRESERVE` with no value or `SET` with exactly one
member of that axis catalog; unmentioned axes and counters are byte-identical. The waiting-network
compound additionally requires pre-queue `WAITING_NETWORK`. `SNAPSHOT:=` captures only after the
result and ledger append are complete; `RESTORE:=` performs strict decode before transition and
appends its flow row only after successful restore. The machine mirror gives the finite semantics
for every remaining queue/composite/deletion transition literal. Of the general catalog's 25
literals, exactly `APPLY_EXACT_INHERITED_ROW` and
`Q:PRESERVE_DELETE_PENDING;ATTEMPT:RESOLVE_BY_SUBCASE` are packet templates and never runtime
values. The runtime domain is exactly the other 23 general literals plus the 18 scalar
scenario-026 literals, 41 values total. Unknown tokens and the old placeholder guards are never
executable.

`OFF-I1-RULE-C-001` is the single atomic validate-and-apply rule for `OFF-I1-ACT-018-02`; it has
priority 200, secondary 0, consumes one already-valid candidate, changes queue to `APPLIED`, changes
apply `0→1`, preserves effect one and appends exactly one flow and one queue row. It never selects
two rules. Remote-deletion rules are `OFF-I1-RULE-D-001..015` in exact inherited mapping order,
priority 300 and secondary order 1..15. The canonical machine locator is
`docs/evidence/poc-offline-001/readiness-contract-stage0-v0.1.json#/queueCatalogBoundary/deletionEventOutcomeMapping`;
its 15 rows must be byte-semantically identical to pinned I1 v0.1 JSON
`#/deletionEventOutcomeMapping`. No deletion value is copied or changed here.

### 4.2 Exact scenario/action projection

The inherited 67 action IDs remain exact and bijective. `=` means the entire vector is preserved;
`L/P/C/M/Q` mean only the named axis changes. Each comma-separated clause corresponds in order to
one inherited action ID for that scenario.

| Scenario | Ordered projection |
|---|---|
| `001` | launch `=`; inspect `=`; initial vector remains fresh/not-requested/denied/model-absent/local-only |
| `002` | capture `L→RUNNING`; finalize `L=RUNNING`; save `L→SUCCEEDED` |
| `003` | restart `=` by strict restore; open source `L→SUCCEEDED` |
| `004` | history `L→RUNNING`; search `L=RUNNING`; open result `L→SUCCEEDED` |
| `005` | task `L→SUCCEEDED`; edit `L=SUCCEEDED`; reopen `L=SUCCEEDED` |
| `006` | copy `L→SUCCEEDED` |
| `007` | export `L→RUNNING`; verify/cleanup `L→SUCCEEDED` |
| `008` | prepare `L→RUNNING`; handoff `L→SUCCEEDED`; observe Dora `=` |
| `009` | required processing `P→WAITING_MODEL`; non-model flow `L→SUCCEEDED`, `P` preserved |
| `010` | RU model-shape event `P→PROCESSING_SUCCEEDED`; no model execution claim |
| `011` | EN model-shape event `P→PROCESSING_SUCCEEDED`; no model execution claim |
| `012` | mixed model-shape event `P→PROCESSING_SUCCEEDED`; no model execution claim |
| `013` | enqueue `Q→PENDING_UPLOAD`; denied scheduler `Q→WAITING_NETWORK` |
| `014` | search/edit/task/copy/export each `L→SUCCEEDED`; `Q=WAITING_NETWORK` throughout |
| `015` | kill projection `=` with snapshot; restart `=` by strict restore |
| `016` | reboot projection `=` with snapshot; open `=` by strict restore; no device claim |
| `017` | revalidate `Q→PENDING_UPLOAD`; resume/send `Q→UPLOADING`, attempt `0→1`; accept `Q→REMOTE_PROCESSING`, effect `0→1`; validate `Q→RESULT_AVAILABLE`; apply `Q→APPLIED`, apply `0→1` |
| `018` | start remote-processing at attempt/effect/apply `1/1/0`; replay `Q=`, attempt `1→2`; reconcile/apply `Q→APPLIED`, apply `0→1` |
| `019` | pre-canaries `=`; close `=`; local flows `L→SUCCEEDED`; close `=`; post-canaries `=`; labels are not network evidence |
| `020` | injected clock advance `=`; restore `=`; local flows `L→SUCCEEDED` |
| `021` | optional summary `P→PENDING_CAPABILITY`; required flows `L→SUCCEEDED`, `P` preserved |
| `022` | revoke non-deletion `Q→CANCELLED`; observer restart `=`; local flows `L→SUCCEEDED` |
| `023` | confirm local delete `L→RUNNING`; reconcile `L→SUCCEEDED`; restart `=` |
| `024` | local delete `L→SUCCEEDED`; enqueue deletion `Q→DELETE_PENDING/DELETE_WAITING_NETWORK`; restart `=` |
| `025` | non-deletion row `Q→CANCELLED`; preserve local `=`; append separate deletion row and project `Q→DELETE_PENDING`; restart `=` |
| `026` | first action selects one scripted inherited deletion class; second reconciles it; only verified receipt reaches `DELETED_REMOTE` |

The vector's queue value projects the most recently mutated durable queue row. The ordered queue
ledger retains all rows. Thus scenario 025 retains both the cancelled non-deletion row and the
separate pending deletion row. The inherited 15 deletion rows remain exact; no deletion row may use
`FAILED_RETRYABLE` or `FAILED_FINAL`.

Scenario 026 expands to 15 exact scripted pairs without adding action IDs. Action 026-01 selects
`OFF-I1-RULE-DP-001..015` by row, priority 250 and secondary 1..15, appends one flow row, preserves
`DELETE_PENDING`, and appends one queue audit row even when its exact attempt delta is zero. Action
026-02 selects the matching `OFF-I1-RULE-D-001..015`, appends one flow and queue row, and applies
the pinned inherited row. Reconcile outcome is `REJECTED_NO_STATE_CHANGE` only for row 13 and
`TRANSITION_APPLIED` otherwise.

One scenario-026 invocation has exact typed input `{scenarioId, scriptedDeletionRowOrdinal}` with
`scenarioId=OFF-SYN-026` and required int64 ordinal 1..15. The ordinal is forbidden for every other
scenario and remains immutable across actions 026-01 and 026-02. It selects the same event/outcome
class for both actions. The machine mirror expands this into 30 scalar cases (15 dispatch plus 15
reconcile): exact action/event, rule, outcome, transition, attempt/effect/apply/deletion-effect
deltas and append flags. Thus there are 67 action slots, 65 fixed runtime cases plus 30 conditional
scenario-026 cases = 95 possible scalar cases; one selected complete run still executes exactly 67
actions. No `RESOLVE_*`, `SELECT_*`, `RECONCILE_*` or other template value may reach a reducer
result.

| Row | Inherited event/outcome class | 026-01 attempt delta |
|---:|---|---:|
| 1 | `DURABLE_ENQUEUE_WHILE_NETWORK_DENIED` | 0 |
| 2 | `PRE_ACCEPTANCE_RESUME` | 1 |
| 3 | `ACCEPTED_RESPONSE_OR_RECEIPT_POLL_PENDING` | 1 |
| 4 | `NETWORK_DENIED_AFTER_ACCEPTANCE` | 0 |
| 5 | `RETRYABLE_FAILURE_OR_BACKOFF` | 1 |
| 6 | `PROFILE_INVALID` | 0 |
| 7 | `SCOPE_INVALID` | 0 |
| 8 | `FINAL_TLS_TRUST_OR_NAME_REJECT` | 1 |
| 9 | `FINAL_SCHEMA_OR_FORMAT_REJECT` | 1 |
| 10 | `FINAL_RESPONSE_INTEGRITY_REJECT` | 1 |
| 11 | `FINAL_IDEMPOTENCY_PAYLOAD_MISMATCH` | 1 |
| 12 | `RETRY_BUDGET_EXHAUSTED` | 0 |
| 13 | `CANCEL_REQUESTED_WHILE_DELETE_PENDING` | 0 |
| 14 | `DELETE_REVALIDATED_OR_USER_ACTION_CONFIRMED_AFTER_ACCEPTANCE` | 1 |
| 15 | `VERIFIED_SCOPED_RECEIPT` | 1 |

The resolved scenario-026 runtime transition catalog has exactly 18 lookup values: dispatch
`PRESERVE_DELETE_PENDING_ATTEMPT_0`, `PRESERVE_DELETE_PENDING_ATTEMPT_1` and
`PRESERVE_DELETE_PENDING_ATTEMPT_1_AFTER_EXACT_GRANT`, plus exact
`APPLY_INHERITED_DELETION_ROW_001..015`. The first three specify attempt/grant deltas `0/0`, `1/0`
and `1/1`; all preserve the valid pending row. Each suffixed apply literal binds only its same
ordinal inherited row, has attempt/grant/apply deltas zero, and has effect/deletion-effect `1/1`
only for row 15. Row 13 is the exact rejection/preserve row. Runtime literals are the union of the
general runtime catalog and these 18 values; lookup is exact and pattern/prefix parsing is forbidden.

## 5. `OFF-I1-UNRESOLVED-003` — exact dispositions

Status: `RESOLVED_BY_V0_2`.

The result diagnostic catalog is exactly `NONE`, `NO_ELIGIBLE_RULE`,
`IDEMPOTENCY_INPUT_MISMATCH`, `POLICY_REJECTED`, plus inherited
`OFF-I1-INVALID-001..012` and v0.2 `OFF-I1-INVALID-013..015`. Validation is an ordered,
first-failure classifier: 013 covers malformed UTF-8/BOM/JSON, trailing bytes,
duplicate/unknown/missing/reordered keys and noncanonical escape/number/whitespace before any
nested inspection; the exact inherited categories 001..012 follow in numeric semantic order; 014
is the total fallback for generic type/null/catalog/pattern/range/overflow failures; 015 is the
total fallback for generic sequence/phase/identity/cross-field relationship failures. Every invalid
input has exactly one first category; no raw parser message is observable.

`INVALID_INPUT` applies only to an uninterpretable/noncanonical envelope, catalog, relationship,
hash, phase, counter, pin, fixture or forbidden source/API. It returns `selectedRuleId=null`, exact
outcome `INVALID_INPUT`, the first classifier diagnostic, original immutable vector,
ledgers and replay records, both append values null and zero counter delta.

`REJECTED_NO_STATE_CHANGE` has exactly one eligible rule in v0.2: deletion row 13,
`OFF-I1-RULE-D-013`, cancel requested while `DELETE_PENDING`. It returns `POLICY_REJECTED`, appends
one content-free flow and one queue audit row, and preserves vector, identities and counters. Other
structurally valid ineligible actions return no-rule `NO_STATE_CHANGE`; immutable-identity mutation
is invalid relationship code 015. Invalidated non-deletion consent instead selects Q-001/Q-006 and
transitions that row to `CANCELLED`.

`NO_STATE_CHANGE` has two exact forms. A valid event with no eligible rule returns
`selectedRuleId=null`, `NO_ELIGIBLE_RULE`, null flow/queue appends and identical vector, ledgers,
replay records and counters. Same-key/different-canonical-input selects Q-002, returns
`IDEMPOTENCY_INPUT_MISMATCH`, makes no flow/queue append and preserves those same values. No other
form is allowed.

The outcome/diagnostic relation is total: every `TRANSITION_APPLIED` has a non-null rule and
`NONE`; no-rule `NO_STATE_CHANGE` has null rule and `NO_ELIGIBLE_RULE`; Q-002
`NO_STATE_CHANGE` has `IDEMPOTENCY_INPUT_MISMATCH`; D-013 rejection has `POLICY_REJECTED`; and
`INVALID_INPUT` has null rule plus exactly one classifier code 001..015.

## 6. `OFF-I1-UNRESOLVED-004` — counter algebra

Status: `RESOLVED_BY_V0_2`.

| Field | Exact rule |
|---|---|
| `attemptCount` | Non-negative monotonic 64-bit integer; increment exactly once immediately before an eligible synthetic transport attempt; never on enqueue, denial, revalidation, local work, parse, apply or receipt-only reconciliation. |
| `effectCount` | `{0,1}`; non-deletion changes `0→1` only on first durable remote acceptance; same-input replay preserves one. For deletion it mirrors `remoteDeletionEffectCount`. |
| `applyCount` | `{0,1}`; changes `0→1` only when one validated non-deletion result is applied without overwriting user truth; always zero for deletion. |
| `remoteDeletionEffectCount` | `{0,1}`; changes `0→1` only with `VERIFIED_SCOPED_RECEIPT`; every pending/error/retry/revalidation branch stays zero. |
| `replayMarker` | required `ORIGINAL`, `SAME_INPUT_REPLAY` or `DIFFERENT_INPUT_REJECTED`. |

For non-deletion `applyCount <= effectCount`; for deletion
`remoteDeletionEffectCount == effectCount` and `applyCount=0`. `effectCount=0` implies
`applyCount=0`. `DELETED_REMOTE` is equivalent to deletion effect one plus a verified receipt and
the inherited terminal null fields. Replay may increase only `attemptCount`; restore changes no
counter; overflow is `INVALID_INPUT`.

Attempt budget belongs to the one currently projected active remote operation in one scenario
invocation and uses checked int64 arithmetic. I1 permits at most one nonterminal remote operation;
scenario 025 retains a cancelled historical non-deletion row, but only the projected deletion row
owns these scalars. A new active intent resets attempt/grant/remaining to `0/0/3`.

```text
totalBudget = 3 + manualRetryGrantCount
remainingAutomaticAttemptBudget = totalBudget - attemptCount
0 <= attemptCount <= totalBudget
```

An eligible explicit manual-retry/revalidation/user-action grant is permitted only after structural
and non-budget revalidation and while remaining budget is zero; it atomically increments
`manualRetryGrantCount` by one without an attempt. Immediately before each admitted synthetic send,
the reducer requires positive remaining budget, atomically increments `attemptCount` and decrements
remaining by one; the attempt stays consumed after every response or failure. Q-010 and Q-019 and
deletion dispatch rows 2, 3, 5, 8, 9, 10, 11, 14 and 15 consume one; every other rule consumes
zero. Q-016/Q-017 change no counter for positive-budget/no-grant resume, or grant one without an
immediate send for zero-budget/eligible-grant retry. DP-014 performs one new grant followed by
one admitted send, so grant and attempt each increase by one and remaining is unchanged. Replaying
that same grant cannot grant again. Denial, invalid/rejected/no-match, local work, snapshot, restore,
enqueue and reconcile-only paths consume zero. Underflow, overflow, grant reuse or a send without
positive post-grant budget is `INVALID_INPUT` with no mutation.

## 7. `OFF-I1-UNRESOLVED-005` — queue/flow digest correlation

Status: `RESOLVED_BY_V0_2`.

```text
localStateDigest = lowercaseHex(SHA-256(
  PREFIX("OFF_I1_LOCAL_STATE_V1") || STRING_FIELD("local", exactLocalCatalogValue)))
```

This one-axis digest is intentionally different from the flow's full five-axis state digest.
Flow sequence is contiguous. A queue row is emitted for every and only `queueAffecting=true` flow
result, including attempt-only replay and deletion rejection evidence, in the same relative order.
Because the inherited 12/19-field ledgers contain no rule-ID field, pairing is deterministic by the
producing reducer result, equal `queue.intentIdHash == flow.queueIntentIdHash`, and the next
unpaired occurrence for that intent; array position alone or cross-intent global position is
insufficient. The reducer result's selected rule is the provenance edge. Queue
`preLocalStateDigest`/`postLocalStateDigest` equal the local projection of the paired flow vectors.
When local truth is unchanged, those two digests are equal. Full-vector digests are recomputed
independently and change when any other axis changes.

## 8. `OFF-I1-UNRESOLVED-006` — strict immutable snapshot

Status: `RESOLVED_BY_V0_2`.

Snapshot schema is `poc-offline-i1-snapshot-v0.2`. Exact top-level key order is:
`snapshotSchema`, `contractId`, `scenarioId`, `nextActionOrdinal`, `monotonicOffsetMs`,
`remainingAutomaticAttemptBudget`, `manualRetryGrantCount`, `stateVector`, `flowLedger`,
`queueLedger`, `replayRecords`, `lastResult`.

Nested schemas are exact. `snapshotSchema` and `contractId` equal the frozen literals;
`scenarioId` is one inherited scenario ID; `nextActionOrdinal` is an integer from 1 through that
scenario's action count plus one; `monotonicOffsetMs` and `manualRetryGrantCount` are non-negative
int64; and `remainingAutomaticAttemptBudget` is from zero through
`3 + manualRetryGrantCount`. `stateVector` uses pinned I1 v0.1 JSON
`#/catalogs/stateVectorKeys`; every `flowLedger` row uses
`#/ledgerContract/flowLedgerFields`; every `queueLedger` row uses
`#/ledgerContract/queueLedgerFields`. The pointers freeze field order; v0.2 separately freezes the
types and relationships below rather than pretending those arrays are schemas.

Reusable exact types are non-negative/positive signed int64, integer `{0,1}`, lowercase hex-64,
exact scenario/action/rule catalogs, the ordered five-axis state-vector object and the exact
diagnostic catalog. A flow row has the inherited 12 fields in order. `sequence` is positive,
starts at one and is contiguous within one scenario invocation and restore continuation;
scenario/action belong together; both vectors are exact objects;
outcome is catalogued; monotonic offset is non-negative/nondecreasing; state digests recompute from
canonical vector bytes. `processingRequestIdHash` is null iff both applicable processing states are
`PROCESSING_NOT_REQUESTED`, otherwise required/immutable. `queueIntentIdHash` is null iff no
durable row exists before or after the action, otherwise equals the paired/projected queue
`intentIdHash`. `contentFreeErrorCode` is null iff diagnostic is `NONE`, otherwise it equals that
diagnostic.

A queue row has the inherited 19 fields in order. `operationClass` is required and exactly
`NON_DELETION_CLOUD_INTENT` or `DELETE_CLOUD_COPY`; intent/key hashes are required and immutable.
`jobIdHash` is null for deletion and before non-deletion acceptance, then required and immutable
on every later row regardless of retry/final/cancel/result state; `resultIdHash` is required only for
the final three result-bearing non-deletion states. Deletion scope is required only for deletion;
deletion/receipt hashes follow acceptance/verified phases. Queue state, six substatuses, eight
deletion errors and receipt outcome follow the inherited 15-row mapping. Attempt is non-negative
int64; marker is one exact replay value; three counters are `{0,1}`; local digests are hex-64 and
equal paired flow local-axis projections. Deletion permits only pending/deleted, has apply zero and
effect equal deletion effect; non-deletion forbids those states, has deletion effect zero and
apply no greater than effect. `DELETED_REMOTE` is equivalent to verified receipt, present receipt
hash, null substatus/error and deletion effect one.

The inherited result order at `#/reducerContract/resultFields` stays exact:
`selectedRuleId`, `outcome`, `postStateVector`, `typedLedgerDeltas`, `effectCount`, `applyCount`,
`remoteDeletionEffectCount`. `typedLedgerDeltas` has exact keys `flowAppend`, `queueAppend`,
`diagnosticCode`; appends are JSON null or exactly one fully typed row and the diagnostic is one
exact result-catalog value. `selectedRuleId` is null exactly for invalid and no-rule outcomes;
Q-002 is non-null. Result counters are cumulative for the affected row and otherwise zero. Flow
append is present iff the selected rule declares it; queue append is present iff that rule declares
it; both appends share the same intent hash and flow post-vector equals result post-vector.
`lastResult` is JSON null before the first action and otherwise exactly this deeply immutable
seven-field object.

Each replay record has exact key order `logicalKeyHash`, `canonicalInputDigest`, `selectedRuleId`,
`outcome`, `postStateDigest`, `resultIdHash`, `replayMarker`, `attemptCount`, `effectCount`,
`applyCount`, `remoteDeletionEffectCount`. It contains only frozen catalogs, hashes and bounded
counters. There is exactly zero or one replay record per `logicalKeyHash`; rows are ordered by that
hash. A different-input rejection preserves the original row and never stores the alternate
digest. `resultIdHash` follows the inherited phase nullability. Empty ledgers/replay records are
`[]`; absent `lastResult` is JSON `null`; no other null or omitted member is permitted.

Replay lifecycle is total and remote-key-only. First durable remote enqueue creates one `ORIGINAL`
record. Ordinary same-operation state changes preserve key/input digest and replace current
rule/outcome/post-digest/result-under-phase/cumulative counters. Q-019 preserves identity/result,
stores Q-019/current unchanged digest, sets `SAME_INPUT_REPLAY`, increments attempt once and
preserves effects. Q-020 may return Q-020 in the reducer result but preserves the cached prior
applied rule/outcome/digest/result and all counters, changing only marker to
`SAME_INPUT_REPLAY`. Q-002, invalid, no-rule and policy rejection preserve the existing record
byte-identically and never store an alternate digest. Strict restore deep-restores records in
lowercase logical-key-hash order without a lifecycle update. Reducer result and cached replay record
are distinct immutable objects.

Canonical encoding uses the common future-oracle primitives, these exact nested key orders,
semantic array order, queue event order and replay ordering. The snapshot contains no raw
identifier. Its digest is external SHA-256 over complete canonical bytes and is not self-embedded.

Decode rejects malformed UTF-8, BOM, trailing bytes, duplicate/unknown/missing/reordered keys,
noncanonical number/string/whitespace, invalid catalog/ID/hash/null phase/counter, noncontiguous
sequence or failed cross-invariant. It validates and re-encodes before constructing a new deeply
immutable value graph; byte-for-byte inequality rejects. No partial restore occurs. Failure returns
`INVALID_INPUT`, unchanged state and zero deltas. No snapshot/cache file is written.

Continuation equality compares uninterrupted prefix+suffix with prefix→canonical bytes→strict
decode→fresh object graph→identical injected suffix. Final state vector, ledgers, replay records,
selected rule/outcome, counters and canonical bytes must match exactly in at least two fresh runs.

## 9. `OFF-I1-UNRESOLVED-007` — typed revalidation

Status: `RESOLVED_BY_V0_2`.

Consent values: `CURRENT`, `MISSING`, `REVOKED`, `SCOPE_MISMATCH`, `VERSION_MISMATCH`,
`NOT_APPLICABLE_DELETE`.

Profile values: `CURRENT`, `MISSING`, `CHANGED`, `SCOPE_MISMATCH`.

Runtime values: `NOT_REQUIRED`, `ELIGIBLE`, `MODEL_NOT_INSTALLED`,
`MODEL_UNAVAILABLE_OR_INVALID`, `ARTIFACT_NOT_EVALUATION_APPROVED`, `DIGEST_MISMATCH`,
`API_ABI_INCOMPATIBLE`, `REQUIRED_16K_EVIDENCE_MISSING`.

Outcome values: `INVALID_INPUT`, `NO_STATE_CHANGE`, `ALLOW`, `CANCEL_NON_DELETION_CONSENT_INVALID`,
`BLOCK_PROFILE_REVALIDATION_REQUIRED`, `BLOCK_RUNTIME_REVALIDATION_REQUIRED`,
`BLOCK_CONNECTIVITY_DENIED`, `BLOCK_FINITE_BUDGET_EXHAUSTED`, `WAIT_REQUIRED_MODEL`,
`WAIT_OPTIONAL_CAPABILITY`, `ALLOW_DELETE_CONSENT_NOT_APPLICABLE`.

Priority is exact: (1) structural/canonical validation; (2) deletion versus non-deletion operation
class; (3) non-deletion consent in order `REVOKED`, `MISSING`, `SCOPE_MISMATCH`,
`VERSION_MISMATCH`, `CURRENT`; (4) profile; (5) runtime; (6) connectivity; (7) positive finite
budget; (8) `ALLOW`. Only the highest-priority categorical outcome is emitted.

Operation class is exactly inherited `NON_DELETION_CLOUD_INTENT` or `DELETE_CLOUD_COPY`;
processing requirement is
exactly `NONE`, `REQUIRED_MODEL` or `OPTIONAL_CAPABILITY`; explicit grant is exactly `NONE` or
`ELIGIBLE_EXPLICIT_GRANT`. A grant is eligible only after all non-budget gates pass at zero
remaining budget for post-accept deletion row 14 or for non-deletion `FAILED_RETRYABLE` selecting
Q-016/Q-017 by job-hash nullity. Every other tuple requires `NONE`. The ordered table is total; the
first matching row wins:

Deletion phase is derived, never trusted: non-deletion plus all deletion fields null is
`NOT_APPLICABLE`; pending deletion with null deletion ID is `PRE_ACCEPTANCE`; pending deletion with
non-null deletion ID and null receipt is `POST_ACCEPTANCE`; and `DELETED_REMOTE` with both IDs,
verified receipt and null substatus/error is terminal `VERIFIED`. Delete requires consent
`NOT_APPLICABLE_DELETE`, processing requirement `NONE`, runtime `NOT_REQUIRED` and pre/post
acceptance phase. Non-deletion requires phase `NOT_APPLICABLE` and non-delete consent; processing
`NONE` iff runtime is `NOT_REQUIRED`, while required/optional processing forbids `NOT_REQUIRED`.
Allowed source queues are `PENDING_UPLOAD`, `WAITING_NETWORK` or `FAILED_RETRYABLE` for
non-deletion and only `DELETE_PENDING` for deletion. Verified deletion never re-enters outbound
revalidation.

| Step | Exact predicate | Outcome and exact effect |
|---:|---|---|
| 1 | any malformed/noncanonical relationship or value | `INVALID_INPUT`; no append/state/counter change |
| 2 | derived deletion phase `VERIFIED` | `NO_STATE_CHANGE`; terminal, no send |
| 3 | operation/consent/processing/runtime/phase/grant tuple is outside the exact relationship matrix or source queue is disallowed | `INVALID_INPUT`; no change |
| 4 | non-deletion consent `REVOKED`, `MISSING`, `SCOPE_MISMATCH` or `VERSION_MISMATCH` | `CANCEL_NON_DELETION_CONSENT_INVALID`; Q-001 to `CANCELLED`; no attempt |
| 5 | profile `MISSING`/`CHANGED` or `SCOPE_MISMATCH` | `BLOCK_PROFILE_REVALIDATION_REQUIRED`; non-deletion Q-003 to `PENDING_UPLOAD`; deletion D-006 or D-007 in either acceptance phase; no attempt |
| 6 | non-deletion `REQUIRED_MODEL` plus `MODEL_NOT_INSTALLED` | `WAIT_REQUIRED_MODEL`; only processing becomes `WAITING_MODEL`; no attempt |
| 7 | non-deletion `OPTIONAL_CAPABILITY` plus `MODEL_NOT_INSTALLED` | `WAIT_OPTIONAL_CAPABILITY`; only processing becomes `PENDING_CAPABILITY`; no attempt |
| 8 | non-deletion required/optional processing plus runtime other than `ELIGIBLE` | `BLOCK_RUNTIME_REVALIDATION_REQUIRED`; Q-003 to `PENDING_UPLOAD`; no attempt |
| 9 | connectivity `NETWORK_DENIED`, `AIRPLANE_MODE` or `RECONNECTING` | `BLOCK_CONNECTIVITY_DENIED`; non-deletion maps exactly by source; deletion D-001 before acceptance or D-004 after; no attempt |
| 10 | remaining budget zero and explicit grant `NONE` | `BLOCK_FINITE_BUDGET_EXHAUSTED`; non-deletion Q-018 to `FAILED_FINAL`; deletion D-012; no attempt |
| 11 | delete passed rows 1–10 and has either positive budget with no grant, or post-accept zero budget plus eligible row-14 grant | `ALLOW_DELETE_CONSENT_NOT_APPLICABLE`; no state/counter change before the selected scalar DP rule |
| 12 | non-deletion passed rows 1–10 and has either positive budget with no grant, or `FAILED_RETRYABLE` zero budget plus eligible grant | `ALLOW`; exact source/outcome rule mapping applies |

`ALLOW_DELETE...` does not predict the response/outcome class. The machine mirror has one exact
15-row `(phase, grant, budget, revalidation outcome, row ordinal) → DP-nnn` table: pre-accept rows
are 1/2/3/5–13; post-accept rows are 4–15 excluding 1–3; row 14 alone consumes an eligible grant;
row 13 is an explicit cancel event that bypasses outbound revalidation. The same immutable ordinal
then selects D-nnn. There is no free-form “other poll/send”.
Every `phases` value in the 15-row eligibility table is the derived source phase immediately
before action 026-01 dispatch. Action 026-02 may change that phase only according to the selected
inherited deletion row.

For non-deletion, the machine mirror maps every allowed source (`PENDING_UPLOAD`,
`WAITING_NETWORK`, `FAILED_RETRYABLE`) and revalidation outcome to exactly Q-001/Q-003/Q-004/
Q-009/Q-016/Q-017/Q-018 or an explicit no-state ready/wait result. The exact table has 23 rows. In
particular Q-003 accepts all three sources; Q-004 accepts any `FAILED_RETRYABLE` and preserves its
null or immutable non-null job hash. Q-016/Q-017 select by job-hash nullity/presence: positive
budget requires grant `NONE` and changes no counter, while zero budget requires the eligible
explicit grant and increments only `manualRetryGrantCount`. The
two processing waits are separately exact: scenario/action 009-01 selects A-009-01 to
`WAITING_MODEL`, and 021-01 selects A-021-01 to `PENDING_CAPABILITY`, preserving queue state.

Invalid non-deletion consent cancels only that remote row and preserves local truth. Deletion does
not require renewed upload/processing consent and cannot be cancelled by its revocation, but it
still passes structural, operation-class, profile, connectivity and budget gates. Model/capability
absence never blocks an unrelated local flow.

## 10. Narrow future implementation contour

No implementation is created by this packet. After strict validation and a clean independent
advisory review, the only permitted source scope is exactly:

1. `tools/offline_i1_oracle/src/main/java/com/monumentogram/dora/stage0/offline/i1/OfflineI1Oracle.java`
2. `tools/offline_i1_oracle/src/test/java/com/monumentogram/dora/stage0/offline/i1/OfflineI1OracleTest.java`

Package is exactly `com.monumentogram.dora.stage0.offline.i1`. Language level is Java 17 standard
library only: no Gradle module, repository, plugin, coordinate, generated source or third-party
parser. The exact local implementation profile is Microsoft OpenJDK `17.0.10+7-LTS`, Windows
x86_64, with:

| Tool | SHA-256 |
|---|---|
| `java.exe` | `98fd4a0eec7fa39abbc2b3f55007ed9c8c24ef8fa5d7c04c3895b4c5915ec3f1` |
| `javac.exe` | `0e79806dea4681cf6bb1fd41b4d5ba8c579481cb70a28890fe65813639294ffe` |
| JDK `release` metadata | `f463618c7067d1d7421a2aacdc8a4cfa939e9844436462c1c165cbed20a9769a` |

Future compilation uses explicit UTF-8, `--release 17` and an initially empty validated OS-temp
output outside the repository, followed by bounded cleanup and a proof that no repository output,
cache, listener or child process remains. The oracle API itself may not read/write filesystem state,
environment variables, network, socket, DNS, subprocess, thread, wall clock, random/UUID, Android
or user/free-form content. Contract reads belong only to the test harness allowlist.

The prospective, still-unexecuted argv shapes are exact. `<PINNED_JDK_BIN>` resolves only to the
directory whose three hashes are listed above; `<VALIDATED_OS_TEMP>` is a newly created empty
directory outside the repository and user home:

```text
<PINNED_JDK_BIN>/javac.exe -encoding UTF-8 --release 17 -d
  <VALIDATED_OS_TEMP>/classes <exact-main-source> <exact-test-source>
<PINNED_JDK_BIN>/java.exe -cp <VALIDATED_OS_TEMP>/classes
  com.monumentogram.dora.stage0.offline.i1.OfflineI1OracleTest
```

The command environment is an explicit empty allowlist except OS variables required to launch the
pinned executable; it may not supply classpath, Java options, proxy, network, locale, timezone or
home-directory behavior. Classpath contains only the temp classes directory. Cleanup and absence
checks are mandatory even after compilation/test failure.

The current CI profile is not authorized: workflow `Temurin 17` is patch/hash-floating and
`ubuntu-latest` is not an exact OS image. A later separately reviewed CI change must pin and prove a
cross-platform profile. Local Python is unusable/unpinned; Python and Kotlin/Gradle paths are not
selected. A handwritten Java strict JSON parser is a residual P1 audit surface and must pass the
adversarial decoder tests before any evidence claim.

## 11. Readiness and acceptance

| Predicate | v0.2 result |
|---|---|
| `OFF-I1-RDY-003` | `SATISFIED_ONLY_AFTER_CLEAN_INDEPENDENT_ADVISORY_REVIEW`: exact local implementation scope is defined here. |
| `OFF-I1-RDY-004` | `SATISFIED_BY_V0_2`: all seven groups are selected and versioned. |
| `OFF-I1-RDY-005` | `SATISFIED_FOR_LOCAL_PROFILE_BY_V0_2`: exact files/package/language/local JDK hashes are frozen; CI remains blocked. |
| `OFF-I1-RDY-006..011` | `NOT_RUN`: implementation and evidence do not exist. |
| `OFF-I1-RDY-012` | `BLOCKED`: later exact-implementation review/publication authority is absent. |

The JSON mirror binds this Markdown's final raw bytes and SHA-256. Activation requires an external
immutable review record with exact keys: `packetId`, `mdPath`, `mdBytes`, `mdSha256`, `jsonPath`,
`jsonBytes`, `jsonSha256`, `reviewerModel`, `reviewerOrganization`, `reviewerRole`,
`formalReviewer=false`, `reviewedAt`, `timezone`, `verdict=CLEAN`, `packetP0=0`, `packetP1=0`,
`packetP2=0`. Its prospective, not-yet-created carrier is exact repository-relative path
`docs/evidence/poc-offline-001/reviews/off-i1-semantics-scope-002.external-review.json`; it is a
separate review artifact, not a third packet source file. Serialization is strict UTF-8 without
BOM, LF, two-space JSON, the listed key order, exactly one final LF, no duplicate/unknown/missing or
reordered key, and no self-hash. Paths/reviewer/time/hash fields are non-null strings; byte and
severity counts are non-negative int64; hashes are lowercase hex-64; `formalReviewer` is boolean;
`reviewedAt` is RFC3339 with seconds and explicit offset; timezone is `Europe/Moscow`; reviewer role
is `INDEPENDENT_ADVISORY_REVIEWER`. Paths, byte counts and hashes must equal the reviewed frozen
files. The carrier is created only after independent CLEAN review; any packet-byte change
invalidates it. It grants only the exact local source scope and no execution, commit, push, PR,
Ready, merge, publication or formal approval authority.

Known future implementation risks are not packet findings: the handwritten strict parser needs
adversarial tests and the floating CI profile remains unauthorized. Packet-review findings are
separate and must be empty at activation.

Acceptance checks require strict duplicate-aware JSON, exact Markdown/JSON parity, seven unique
resolved IDs, exact pins/tool hashes/paths, exact 20 queue rules and 26-to-67 projection, inherited
15 deletion rows, 30 conditional scenario-026 cases/95 possible resolved cases, 25 general plus
18 resolved-deletion transition literals of which 11 are direct, 19 diagnostics/15 classifier rows,
exact 12/19/7 field schemas, eight replay lifecycle rows and 12 revalidation rows,
authority/status truth, UTF-8 LF with final newline, privacy/secret/absolute-path
scans, exact two-file packet-authoring scope before the separately authorized review carrier, and
independent advisory review with no P0/P1/P2.

Even after packet acceptance and a future green host oracle, `POC-OFFLINE-001` remains
`TODO / NOT_READY / NOT_RUN / NOT_AUTHORIZED`; all `OFF-RDY-01..10`, device/network/model/data,
integration, product and production gates remain unchanged. No PR is merged or advanced.
