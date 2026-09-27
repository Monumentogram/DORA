# DORA Cloud Alpha admission gates v0.1

Status: APPROVED FOR ALPHA ARCHITECTURE / ADMISSION

Authority: explicit Project Owner Stage 6.2C task, 27 September 2026.
Baseline: `chat/alpha-asr-runner-scope`, `1107d4d9eef80691372800da0a17e609eba81957`,
independently fetched and matched to the remote branch before edits; working tree was clean.

This document freezes the conditions required for Cloud admission. It does not assert that those conditions are already satisfied and it does not authorize real Cloud audio processing by itself.

The human authority is this Markdown contract; the adjacent JSON is the identical machine-readable
matrix. Later changes require a versioned decision and synchronized MD/JSON/status update. No gate
can be removed or marked satisfied simply because its requirement is documented.

## Closure and current readiness

The closure result is **`6.2C = PASS / CLOUD_ALPHA_ADMISSION_GATES_FROZEN` only after** the complete
contract validates, the docs-only commit is pushed, remote HEAD is confirmed, and the existing
Google Sheet is updated with that exact commit and successfully read back. Until then, closure
is pending; a failed step yields PARTIAL/BLOCKED. This conditional form permits one atomic commit
without falsely claiming a future external write. The final task report and Sheet are the
post-publication closure receipt for the exact commit; this document does not self-certify it.

- Cloud implementation admission = **NOT READY / BLOCKED BY OPEN ADMISSION GATES**.
- First real Cloud audio admission = **NOT READY**.
- Cloud Alpha acceptance = **NOT_RUN / NOT READY**.
- Provider = **NOT SELECTED**; Model = **NOT SELECTED**.
- Cloud runtime = **NOT IMPLEMENTED**; Cloud runtime tests = **NOT_RUN**.

## Authority and evidence limits

[ADR-0009](../adr/ADR-0009-alpha-cloud-execution-boundary.md) settles the backend boundary:
`DORA Android -> DORA-controlled backend -> CloudAsrProvider -> separately admitted provider`.
Its prospective resolution of the older data contract's “DECISION REQUIRED” paragraph is explicit;
that historical paragraph is not a new conflict. The ADR also leaves earlier vendor/identity stack
and numeric Cloud retention recommendations unselected. No region, identity/storage provider,
model, SDK, endpoint or retention period is selected here.

The [product scenarios](../product/DORA_ASR_USER_SCENARIOS_V0_1.md),
[Gherkin](../product/DORA_ASR_USER_SCENARIOS_V0_1.feature),
[data contract](DORA_ASR_DATA_AND_VERSIONING_CONTRACT_V0_1.md),
[technical plan](../DORA_MVP1_TECHNICAL_PLAN.md) §§13/22/24/28/30,
[design specification](../DORA_MVP1_DESIGN_SPEC.md) §31,
[Product Decisions](../DORA_MVP1_PRODUCT_DECISIONS.md),
[readiness review](../DORA_MVP1_IMPLEMENTATION_READINESS.md),
[test strategy](../DORA_MVP1_TEST_STRATEGY.md) and
[storage/deletion contract](../design/DORA_MVP1_STORAGE_RETENTION_DELETE_CONTRACT.md) remain applicable.
Approved Alpha ASR overlays refine earlier local-first/consent wording only in their named scope.
No legal opinion or external security audit is supplied here.

Stage 5 remains `PASS / BOUNDED_ALPHA_LOCAL_ASR_CANDIDATE_ADMITTED`, exact Small q8_0/POCO M5
bounded RU/EN evidence only. Timestamp quality remains NOT_EVALUABLE; actual 16-KiB runtime NOT_RUN.
No extrapolation to production, all devices, languages or conditions. Recovery and all historical
ASR failures, artifacts, datasets, thresholds and result JSONs remain unchanged; no campaign runs.

Live Sheet inspection also found **6.2D — Alpha AI Provider & Model Admission**, inserted between
6.2C and 6.3 while this task was reading the Sheet. It is retained and used, not duplicated. It
requires per-function decisions for actual Alpha scope, not one default provider for all AI.
On latest readback, 6.1 and 6.2 are in progress, 6.2D planned/not run and 6.3 blocked. Gate domain owners below are
accountable roles, not invented personal assignments or signatures. CLOUD-01 stays closed for
boundary selection; CLOUD-02 remains blocked; CLOUD-03 remains NOT_RUN.

## Status and boundary semantics

`SATISFIED` means the particular gate's acceptance/evidence is proven in its declared scope;
`PARTIALLY_SATISFIED` means some explicit subrequirements are proven but the boundary is still
blocked; `OPEN` means required decisions/artifacts are missing; `BLOCKED` means named prerequisites
prevent closure; `NOT_RUN` means runtime acceptance has not been executed; `NOT_APPLICABLE` requires
an explicit scope decision and rationale (none at this baseline).

`blocking: true` means the gate prevents each boundary named in its `required_before` list until
satisfied. It does not mean every gate blocks writing code. Gate dependencies are gate IDs;
roadmap task IDs are owner/dependency lanes, not a command to start a later implementation now.
All boundaries fail closed on missing, stale, unknown or partial evidence. A gate closes only when
its criteria and dependencies have current reviewed evidence. Evidence must identify exact source,
build/configuration, provider/model where relevant, fixture/version, result, reviewer, date and
limitations; public evidence must be sanitized. Specification evidence never proves enforcement.

Required-before vocabulary:

| Level | Meaning |
|---|---|
| BEFORE_PROVIDER_ADMISSION | Before accepting the provider/model decision; safe evaluation rules below still apply. |
| BEFORE_CLOUD_RUNTIME_IMPLEMENTATION | Minimum pre-code set, including final 6.3 admission; added to separate coding from integration. |
| BEFORE_FIRST_REAL_CLOUD_AUDIO_UPLOAD | Before any real user audio leaves the device, including provider testing. |
| BEFORE_CLOUD_RUNTIME_INTEGRATION | Before accepting/wiring the implemented Cloud path; does not require runtime tests before coding. |
| BEFORE_INTERNAL_ALPHA_ACCEPTANCE | Before Cloud functionality is accepted in internal Alpha. |
| BEFORE_PUBLIC_RELEASE | Before public distribution; Alpha closure is insufficient. |

## A. Implementation entry and 6.3

Cloud runtime implementation may begin only after the minimum implementation-entry gate set is
satisfied or explicitly waived by a versioned Project Owner decision. An entry-only waiver must
name the gate, scope, reason, compensating restriction, owner and expiry/review trigger. No waiver
exists now. Such a waiver cannot authorize real audio, waive law/privacy obligations or bypass
the unwaived first-audio/internal-Alpha rules. This document itself is not a waiver.

The implementation-entry set consists of the exact IDs below. They require decisions, contracts,
provider admission and the development-baseline decision, **not completed Alpha runtime tests**.
The 6.2D provider decision may use separately scoped safe evaluation; it does not depend on already
implemented production adapters, so there is no code-before-admission cycle. Likewise 11.1C's
policy/design slice occurs before 6.3; its runtime enforcement and tests occur afterwards.
7.2C/E produce implementation schemas after 6.3 against the approved pre-code control design.

- `CLD-ADM-ARCH-001`
- `CLD-ADM-CONSENT-001`
- `CLD-ADM-DATA-001`
- `CLD-ADM-SCOPE-001`
- `CLD-ADM-GAPS-001`
- `CLD-ADM-PRIVACY-001`
- `CLD-ADM-RETENTION-001`
- `CLD-ADM-CONTROL-001`
- `CLD-ADM-EVALUATION-001`
- `CLD-ADM-PROVIDER-001`
- `CLD-ADM-ADMISSION-001`

**6.3 = BLOCKED.** Its minimum predecessor blocker set is:

- `CLD-ADM-SCOPE-001`
- `CLD-ADM-GAPS-001`
- `CLD-ADM-PRIVACY-001`
- `CLD-ADM-RETENTION-001`
- `CLD-ADM-CONTROL-001`
- `CLD-ADM-EVALUATION-001`
- `CLD-ADM-PROVIDER-001`

`CLD-ADM-ADMISSION-001` is the output of 6.3, not its own prerequisite. The already-satisfied ARCH,
CONSENT and DATA gates are not open blockers. No future background/merge/acceptance test is listed
as a prerequisite to beginning 6.3. Overall entry is NOT READY until those predecessor decisions
and 6.3 itself close. The immediate next task is **6.1**, because actual Alpha function/device scope
is needed for 6.2 and the live 6.2D per-function admission; do not silently treat 6.1A/B as full 6.1.

## Provider testing and admission

Offline synthetic reasoning/mocks may precede production implementation admission in a separately
authorized test scope. External synthetic provider evaluation requires an approved evaluation
envelope (terms/data-use, non-sensitive generated fixtures, destination, secure test-only secrets,
bounded cost and cleanup); an OPEN provider gate is not permission to call a provider. Real voice
evaluation data needs independent rights/consent for that provider; existing Stage 5 local datasets
are not automatically authorized. **Any real user audio leaving a device, including an experiment,
requires every first-real-audio gate.** Use safe synthetic/provider documentation evidence before
that boundary; do not rename real user data “test data” to bypass it. No external calls occur here.

Provider admission gates:

- `CLD-ADM-ARCH-001`
- `CLD-ADM-CONSENT-001`
- `CLD-ADM-SCOPE-001`
- `CLD-ADM-PRIVACY-001`
- `CLD-ADM-RETENTION-001`
- `CLD-ADM-EVALUATION-001`
- `CLD-ADM-PROVIDER-001`

## B. First real audio

**No real user audio may leave the device until every BEFORE_FIRST_REAL_CLOUD_AUDIO_UPLOAD gate is
satisfied.** No entry waiver substitutes for these controls. Evaluate the exact deployed
configuration/provider/region and current recording consent at the point of use. The stricter set
includes implemented consent/ownership, secrets, scoped/revocable upload, transport/key protection,
retention/deletion, provider privacy/admission, cost limits, telemetry, threat verification,
result/edit safety and operational stop controls. Current readiness: **NOT READY**.

- `CLD-ADM-ARCH-001`
- `CLD-ADM-CONSENT-001`
- `CLD-ADM-DATA-001`
- `CLD-ADM-PRIVACY-001`
- `CLD-ADM-RETENTION-001`
- `CLD-ADM-CONTROL-001`
- `CLD-ADM-PROVIDER-001`
- `CLD-ADM-ADMISSION-001`
- `CLD-ADM-API-001`
- `CLD-ADM-AUTH-001`
- `CLD-ADM-CONSENT-RUNTIME-001`
- `CLD-ADM-SECRETS-001`
- `CLD-ADM-UPLOAD-001`
- `CLD-ADM-CRYPTO-001`
- `CLD-ADM-RETENTION-RUNTIME-001`
- `CLD-ADM-DATA-RUNTIME-001`
- `CLD-ADM-FAILURE-001`
- `CLD-ADM-QUEUE-001`
- `CLD-ADM-ADAPTER-001`
- `CLD-ADM-COST-001`
- `CLD-ADM-OBSERVABILITY-001`
- `CLD-ADM-SECURITY-001`
- `CLD-ADM-RESULT-001`
- `CLD-ADM-MERGE-001`
- `CLD-ADM-OFFLINE-001`
- `CLD-ADM-DELETE-001`
- `CLD-ADM-HARNESS-001`
- `CLD-ADM-UX-001`
- `CLD-ADM-OPERATIONS-001`
- `CLD-ADM-SUPPLY-001`

## C. Runtime integration and internal Alpha acceptance

Cloud integration cannot be accepted until the runtime-integration set is satisfied:

- `CLD-ADM-ARCH-001`
- `CLD-ADM-CONSENT-001`
- `CLD-ADM-DATA-001`
- `CLD-ADM-CONTROL-001`
- `CLD-ADM-PROVIDER-001`
- `CLD-ADM-ADMISSION-001`
- `CLD-ADM-API-001`
- `CLD-ADM-AUTH-001`
- `CLD-ADM-CONSENT-RUNTIME-001`
- `CLD-ADM-SECRETS-001`
- `CLD-ADM-UPLOAD-001`
- `CLD-ADM-CRYPTO-001`
- `CLD-ADM-RETENTION-RUNTIME-001`
- `CLD-ADM-DATA-RUNTIME-001`
- `CLD-ADM-FAILURE-001`
- `CLD-ADM-QUEUE-001`
- `CLD-ADM-ADAPTER-001`
- `CLD-ADM-RESULT-001`
- `CLD-ADM-DELETE-001`
- `CLD-ADM-HARNESS-001`
- `CLD-ADM-SUPPLY-001`

Internal Cloud Alpha acceptance requires **all** gates tagged BEFORE_INTERNAL_ALPHA_ACCEPTANCE,
all fourteen exit criteria and deterministic critical tests plus applicable platform evidence.
Provider/model must be admitted; privacy/retention resolved; failure preservation and zero
unauthorized upload proven. No future test is PASS today. Current readiness: **NOT_RUN / NOT READY**.

- `CLD-ADM-ARCH-001`
- `CLD-ADM-CONSENT-001`
- `CLD-ADM-DATA-001`
- `CLD-ADM-SCOPE-001`
- `CLD-ADM-GAPS-001`
- `CLD-ADM-PRIVACY-001`
- `CLD-ADM-RETENTION-001`
- `CLD-ADM-CONTROL-001`
- `CLD-ADM-EVALUATION-001`
- `CLD-ADM-PROVIDER-001`
- `CLD-ADM-ADMISSION-001`
- `CLD-ADM-API-001`
- `CLD-ADM-AUTH-001`
- `CLD-ADM-CONSENT-RUNTIME-001`
- `CLD-ADM-SECRETS-001`
- `CLD-ADM-UPLOAD-001`
- `CLD-ADM-CRYPTO-001`
- `CLD-ADM-RETENTION-RUNTIME-001`
- `CLD-ADM-DATA-RUNTIME-001`
- `CLD-ADM-FAILURE-001`
- `CLD-ADM-QUEUE-001`
- `CLD-ADM-BACKGROUND-001`
- `CLD-ADM-ADAPTER-001`
- `CLD-ADM-COST-001`
- `CLD-ADM-OBSERVABILITY-001`
- `CLD-ADM-SECURITY-001`
- `CLD-ADM-RESULT-001`
- `CLD-ADM-MERGE-001`
- `CLD-ADM-LOCAL-001`
- `CLD-ADM-OFFLINE-001`
- `CLD-ADM-DELETE-001`
- `CLD-ADM-HARNESS-001`
- `CLD-ADM-UX-001`
- `CLD-ADM-HISTORY-001`
- `CLD-ADM-EXPORT-001`
- `CLD-ADM-OPERATIONS-001`
- `CLD-ADM-SUPPLY-001`
- `CLD-ADM-EXIT-001`

Public release additionally requires the public-release set (all gates in this version, including
CLD-ADM-RELEASE-001) and Stage 19 evidence. Internal Alpha does not waive public release controls.

## Gate matrix

Level aliases in this table: I = implementation; P = provider admission; U = first real audio;
R = runtime integration; A = internal Alpha; L = public release. Full levels appear in each record.

| Gate ID | Gate | Required before | Current state | Blocking? | Evidence / dependency | Next action |
|---|---|---|---|---|---|---|
| CLD-ADM-ARCH-001 | Architecture authority | I, P, U, R, A, L | SATISFIED | No | 6.1C | Retain approved specification |
| CLD-ADM-CONSENT-001 | Product and consent specification | I, P, U, R, A, L | SATISFIED | No | 6.1A, 6.1B | Retain approved specification |
| CLD-ADM-DATA-001 | Data and versioning specification | I, U, R, A, L | SATISFIED | No | 6.1B | Retain approved specification |
| CLD-ADM-SCOPE-001 | Alpha scope and supported environment | I, P, A, L | OPEN | Yes | 6.1 | Close evidence below: 6.1 |
| CLD-ADM-GAPS-001 | Non-Cloud readiness disposition | I, A, L | OPEN | Yes | 6.2 | Close evidence below: 6.2 |
| CLD-ADM-PRIVACY-001 | Privacy and disclosure policy | I, P, U, A, L | BLOCKED | Yes | 11.1C, CLOUD-02, BE-LEGAL-001, BE-CONSENT-001 | Close evidence below: 11.1C |
| CLD-ADM-RETENTION-001 | Retention and deletion policy | I, P, U, A, L | PARTIALLY_SATISFIED | Yes | 11.1C, 8.2C, CLOUD-02, BE-DELETE-001 | Close evidence below: 11.1C |
| CLD-ADM-CONTROL-001 | Cloud security and ownership design | I, U, R, A, L | OPEN | Yes | 11.1C, BE-AUTH-001, BE-API-001, BE-DELETE-001 | Close evidence below: 11.1C |
| CLD-ADM-EVALUATION-001 | Provider evaluation protocol and data admission | I, P, A, L | OPEN | Yes | 6.2D, BE-PROVIDER-001, BE-PROVIDER-002 | Close evidence below: 6.2D |
| CLD-ADM-PROVIDER-001 | Provider and model admission | I, P, U, R, A, L | OPEN | Yes | 6.2D, 18.1, BE-PROVIDER-001, BE-PROVIDER-002 | Close evidence below: 6.2D |
| CLD-ADM-ADMISSION-001 | Development admission and exact baseline | I, U, R, A, L | BLOCKED | Yes | 6.3 | Close evidence below: 6.3 |
| CLD-ADM-API-001 | Versioned API and job contract | R, U, A, L | OPEN | Yes | 7.2C, 7.2E, BE-API-001 | Close evidence below: 7.2C |
| CLD-ADM-AUTH-001 | Identity and ownership enforcement | U, R, A, L | NOT_RUN | Yes | 7.2E, 18.1C, BE-AUTH-001 | Close evidence below: 7.2E |
| CLD-ADM-CONSENT-RUNTIME-001 | Runtime consent enforcement | U, R, A, L | NOT_RUN | Yes | 9.2C, 9.2D, 11.1C, 18.1C | Close evidence below: 9.2C |
| CLD-ADM-SECRETS-001 | Privileged secrets protection | U, R, A, L | NOT_RUN | Yes | 18.1C, 11.1C | Close evidence below: 18.1C |
| CLD-ADM-UPLOAD-001 | Storage and scoped upload authorization | U, R, A, L | NOT_RUN | Yes | 9.2C, 8.4C, 18.1C | Close evidence below: 9.2C |
| CLD-ADM-CRYPTO-001 | Payload transport and key protection | U, R, A, L | NOT_RUN | Yes | 11.1C, 18.1C | Close evidence below: 11.1C |
| CLD-ADM-RETENTION-RUNTIME-001 | Retention enforcement and remote deletion | U, R, A, L | NOT_RUN | Yes | 11.1C, 18.1C, BE-DELETE-001 | Close evidence below: 11.1C |
| CLD-ADM-DATA-RUNTIME-001 | Durable versions and atomic activation | U, R, A, L | NOT_RUN | Yes | 7.2D, 8.2C, 8.4C | Close evidence below: 7.2D |
| CLD-ADM-FAILURE-001 | Idempotency and failure safety | U, R, A, L | NOT_RUN | Yes | 9.2C, 9.2E, 18.1C, 12.1C | Close evidence below: 9.2C |
| CLD-ADM-QUEUE-001 | Offline queue and reconnect | U, R, A, L | NOT_RUN | Yes | 9.2D, 11.2C | Close evidence below: 9.2D |
| CLD-ADM-BACKGROUND-001 | Android background execution | A, L | NOT_RUN | Yes | 11.2C, 9.2C, 12.1C | Close evidence below: 11.2C |
| CLD-ADM-ADAPTER-001 | Replaceable CloudAsrProvider implementation | U, R, A, L | NOT_RUN | Yes | 7.2C, 18.1C | Close evidence below: 7.2C |
| CLD-ADM-COST-001 | Cost, quotas and abuse controls | U, A, L | NOT_RUN | Yes | 18.2C, 18.1C | Close evidence below: 18.2C |
| CLD-ADM-OBSERVABILITY-001 | Content-free operational observability | U, A, L | NOT_RUN | Yes | 18.2C, 11.1C | Close evidence below: 18.2C |
| CLD-ADM-SECURITY-001 | Bounded Alpha threat verification | U, A, L | NOT_RUN | Yes | 11.1C, 12.1C, 18.1C | Close evidence below: 11.1C |
| CLD-ADM-RESULT-001 | Cloud result validation | U, R, A, L | NOT_RUN | Yes | 9.2E, 7.2D, 18.1C | Close evidence below: 9.2E |
| CLD-ADM-MERGE-001 | Edit-preserving merge and review | U, A, L | NOT_RUN | Yes | 9.4C, 12.1C | Close evidence below: 9.4C |
| CLD-ADM-LOCAL-001 | Optional Local package and account-free operation | A, L | NOT_RUN | Yes | 9.1C, 11.2C | Close evidence below: 9.1C |
| CLD-ADM-OFFLINE-001 | Cloud-only offline audio preservation | U, A, L | NOT_RUN | Yes | 8.2C, 9.1C, 9.2D, 11.2C | Close evidence below: 8.2C |
| CLD-ADM-DELETE-001 | Deletion, cancellation and stale-result safety | U, R, A, L | NOT_RUN | Yes | 11.1C, 8.2C, 9.2E, 10.3C | Close evidence below: 11.1C |
| CLD-ADM-HARNESS-001 | Deterministic Cloud test infrastructure | U, R, A, L | NOT_RUN | Yes | 7.3C, 12.1C | Close evidence below: 7.3C |
| CLD-ADM-UX-001 | Consent and operational UX validation | U, A, L | NOT_RUN | Yes | 9.3C, 11.1C | Close evidence below: 9.3C |
| CLD-ADM-HISTORY-001 | Active-version history and search | A, L | NOT_RUN | Yes | 10.3C | Close evidence below: 10.3C |
| CLD-ADM-EXPORT-001 | Active-version export and provenance | A, L | NOT_RUN | Yes | 11.3C | Close evidence below: 11.3C |
| CLD-ADM-OPERATIONS-001 | Operational stop, recovery and change control | U, A, L | NOT_RUN | Yes | 18.1C, 18.2C, 11.1C | Close evidence below: 18.1C |
| CLD-ADM-SUPPLY-001 | Dependency and deployment provenance | U, R, A, L | OPEN | Yes | 18.1C, 7.3C | Close evidence below: 18.1C |
| CLD-ADM-EXIT-001 | Cloud Alpha exit acceptance | A, L | NOT_RUN | Yes | 12.1C, 12.4C | Close evidence below: 12.1C |
| CLD-ADM-RELEASE-001 | Public release admission | L | OPEN | Yes | 19.1, 19.2 | Close evidence below: 19.1 |

## Gate acceptance records

### CLD-ADM-ARCH-001 — Architecture authority

- Purpose: Keep the accepted execution and original-audio boundary.
- Required before: `BEFORE_CLOUD_RUNTIME_IMPLEMENTATION`, `BEFORE_PROVIDER_ADMISSION`, `BEFORE_FIRST_REAL_CLOUD_AUDIO_UPLOAD`, `BEFORE_CLOUD_RUNTIME_INTEGRATION`, `BEFORE_INTERNAL_ALPHA_ACCEPTANCE`, `BEFORE_PUBLIC_RELEASE`
- Owner/domain: Architecture / Project Owner
- Roadmap/dependency lane: `6.1C`
- Gate dependencies: None
- Current status: `SATISFIED`; blocking: `false`.
- Blocking reason / missing evidence: None; specification-only evidence is verified.
- Next action: Retain this authority; re-review affected gates on a versioned change.

Acceptance criteria:

1. DORA-controlled backend is mandatory for Alpha file ASR; CloudAsrProvider isolates providers
2. Original audio is input; direct Android-to-provider file ASR is not admitted
3. Provider/model are NOT SELECTED at this baseline; no runtime admission is inferred

Required evidence:

- Accepted ADR and compatibility review against product/data contracts

Current repository evidence / requirement authority:

- [docs/adr/ADR-0009-alpha-cloud-execution-boundary.md](https://github.com/Monumentogram/DORA/blob/1107d4d9eef80691372800da0a17e609eba81957/docs/adr/ADR-0009-alpha-cloud-execution-boundary.md), section/scenario: Decision; Decision scope; Audio data plane; Provider abstraction.
- [docs/contracts/DORA_ASR_DATA_AND_VERSIONING_CONTRACT_V0_1.md](https://github.com/Monumentogram/DORA/blob/1107d4d9eef80691372800da0a17e609eba81957/docs/contracts/DORA_ASR_DATA_AND_VERSIONING_CONTRACT_V0_1.md), section/scenario: Cloud adapter and execution boundary.

### CLD-ADM-CONSENT-001 — Product and consent specification

- Purpose: Freeze intended authorization behavior separately from enforcement.
- Required before: `BEFORE_CLOUD_RUNTIME_IMPLEMENTATION`, `BEFORE_PROVIDER_ADMISSION`, `BEFORE_FIRST_REAL_CLOUD_AUDIO_UPLOAD`, `BEFORE_CLOUD_RUNTIME_INTEGRATION`, `BEFORE_INTERNAL_ALPHA_ACCEPTANCE`, `BEFORE_PUBLIC_RELEASE`
- Owner/domain: Product / Android / Backend
- Roadmap/dependency lane: `6.1A`, `6.1B`
- Gate dependencies: None
- Current status: `SATISFIED`; blocking: `false`.
- Blocking reason / missing evidence: None; specification-only evidence is verified.
- Next action: Retain this authority; re-review affected gates on a versioned change.

Acceptance criteria:

1. ALWAYS, ASK_EACH_RECORDING and MANUAL_ONLY remain unchanged
2. One logical recording is one authorization unit; ASK prompts at most once automatically across chunks/restarts
3. PENDING and DEFERRED upload zero bytes; DEFERRED survives reconnect/restart/policy change
4. Batch consent authorizes only its selected snapshot; explicit manual action only intended recordings
5. INHERITED_ALWAYS, EXPLICITLY_APPROVED and MANUAL_USER_ACTION require current unrevoked scope; zero bytes before valid authorization

Required evidence:

- Approved product policy and recording-state tables
- Declarative deterministic consent scenarios (not execution evidence)

Current repository evidence / requirement authority:

- [docs/product/DORA_ASR_USER_SCENARIOS_V0_1.md](https://github.com/Monumentogram/DORA/blob/1107d4d9eef80691372800da0a17e609eba81957/docs/product/DORA_ASR_USER_SCENARIOS_V0_1.md), section/scenario: Cloud upload policies; Batch after an offline period; Preference changes and cancellation.
- [docs/contracts/DORA_ASR_DATA_AND_VERSIONING_CONTRACT_V0_1.md](https://github.com/Monumentogram/DORA/blob/1107d4d9eef80691372800da0a17e609eba81957/docs/contracts/DORA_ASR_DATA_AND_VERSIONING_CONTRACT_V0_1.md), section/scenario: User policy and consent scope; Recording-level authorization.
- [docs/product/DORA_ASR_USER_SCENARIOS_V0_1.feature](https://github.com/Monumentogram/DORA/blob/1107d4d9eef80691372800da0a17e609eba81957/docs/product/DORA_ASR_USER_SCENARIOS_V0_1.feature), section/scenario: ASK decline never repeatedly prompts; Batch selection uploads only selected recordings; Revocation prevents further bytes.

### CLD-ADM-DATA-001 — Data and versioning specification

- Purpose: Preserve original audio, raw results and user authority.
- Required before: `BEFORE_CLOUD_RUNTIME_IMPLEMENTATION`, `BEFORE_FIRST_REAL_CLOUD_AUDIO_UPLOAD`, `BEFORE_CLOUD_RUNTIME_INTEGRATION`, `BEFORE_INTERNAL_ALPHA_ACCEPTANCE`, `BEFORE_PUBLIC_RELEASE`
- Owner/domain: Data / Android / Architecture
- Roadmap/dependency lane: `6.1B`
- Gate dependencies: None
- Current status: `SATISFIED`; blocking: `false`.
- Blocking reason / missing evidence: None; specification-only evidence is verified.
- Next action: Retain this authority; re-review affected gates on a versioned change.

Acceptance criteria:

1. Original Audio -> Cloud Authorization -> ASR Versions -> User Edits -> Active Transcript
2. Immutable Local/Cloud raw ASR outputs; active pointer; separate edit provenance and semantic/audio anchors
3. Original audio identity and provider/model provenance are retained; unavailable versions explicitly unknown
4. USER EDIT > CLOUD ASR > LOCAL ASR is proposal priority; unsafe mapping requires review; no silent overwrite

Required evidence:

- Accepted identities, versions, edits, activation and deletion contract

Current repository evidence / requirement authority:

- [docs/contracts/DORA_ASR_DATA_AND_VERSIONING_CONTRACT_V0_1.md](https://github.com/Monumentogram/DORA/blob/1107d4d9eef80691372800da0a17e609eba81957/docs/contracts/DORA_ASR_DATA_AND_VERSIONING_CONTRACT_V0_1.md), section/scenario: Relationship and identities; Transcript versions; User edits and anchors; Deletion and retention compatibility.

### CLD-ADM-SCOPE-001 — Alpha scope and supported environment

- Purpose: Determine what must be admitted, including conditional AI capabilities.
- Required before: `BEFORE_CLOUD_RUNTIME_IMPLEMENTATION`, `BEFORE_PROVIDER_ADMISSION`, `BEFORE_INTERNAL_ALPHA_ACCEPTANCE`, `BEFORE_PUBLIC_RELEASE`
- Owner/domain: Project Owner / Product
- Roadmap/dependency lane: `6.1`
- Gate dependencies: None
- Current status: `OPEN`; blocking: `true`.
- Blocking reason / missing evidence: Live 6.1 is in progress; formal overall Alpha scope/support decision is absent.
- Next action: 6.1: produce and review the required evidence; Live 6.1 is in progress; formal overall Alpha scope/support decision is absent.

Acceptance criteria:

1. Versioned list of Alpha functions, devices, languages and support limits approved
2. Each AI function has a required/local/deferred disposition; diarization, LLM and embeddings are not silently added or excluded
3. Consume Stage 5 bounded POCO evidence without production/all-device/all-language extrapolation; timestamp and 16-KiB limitations remain explicit

Required evidence:

- Approved 6.1 scope decision and capability table
- Traceability to bounded Stage 5 closeout and unresolved limitations

Current repository evidence / requirement authority:

- [docs/product/DORA_ASR_USER_SCENARIOS_V0_1.md](https://github.com/Monumentogram/DORA/blob/1107d4d9eef80691372800da0a17e609eba81957/docs/product/DORA_ASR_USER_SCENARIOS_V0_1.md), section/scenario: Verified starting state; Alpha acceptance and sequencing.
- [docs/stage0/DORA_ASR_SMALL_Q8_STAGE5_CLOSEOUT_STAGE0_V0_1.md](https://github.com/Monumentogram/DORA/blob/1107d4d9eef80691372800da0a17e609eba81957/docs/stage0/DORA_ASR_SMALL_Q8_STAGE5_CLOSEOUT_STAGE0_V0_1.md), section/scenario: Stage5 closure and Stage6 handoff.

### CLD-ADM-GAPS-001 — Non-Cloud readiness disposition

- Purpose: Keep broader Stage 0 readiness visible without rerunning campaigns.
- Required before: `BEFORE_CLOUD_RUNTIME_IMPLEMENTATION`, `BEFORE_INTERNAL_ALPHA_ACCEPTANCE`, `BEFORE_PUBLIC_RELEASE`
- Owner/domain: Technical lead / QA / Project Owner
- Roadmap/dependency lane: `6.2`
- Gate dependencies: `CLD-ADM-SCOPE-001`
- Current status: `OPEN`; blocking: `true`.
- Blocking reason / missing evidence: Live 6.2 is in progress; no complete Alpha-specific gap disposition exists.
- Next action: 6.2: produce and review the required evidence; Live 6.2 is in progress; no complete Alpha-specific gap disposition exists.

Acceptance criteria:

1. For approved Alpha scope, applicable capture/VAD/search/energy/offline evidence and mandatory gaps are individually dispositioned
2. Each unresolved mandatory gap has accepted bounded limitation/owner decision or required evidence before its affected implementation
3. Preserve Recovery and historical failures; no blanket re-run or silent waiver

Required evidence:

- Versioned 6.2 evidence applicability and mandatory-gap decision

Current repository evidence / requirement authority:

- [docs/DORA_MVP1_IMPLEMENTATION_READINESS.md](https://github.com/Monumentogram/DORA/blob/1107d4d9eef80691372800da0a17e609eba81957/docs/DORA_MVP1_IMPLEMENTATION_READINESS.md), section/scenario: 9. Go/no-go для следующего чата; 3. Перекрёстная проверка и вопросы.

### CLD-ADM-PRIVACY-001 — Privacy and disclosure policy

- Purpose: Resolve processing scope before implementation and data transfer.
- Required before: `BEFORE_CLOUD_RUNTIME_IMPLEMENTATION`, `BEFORE_PROVIDER_ADMISSION`, `BEFORE_FIRST_REAL_CLOUD_AUDIO_UPLOAD`, `BEFORE_INTERNAL_ALPHA_ACCEPTANCE`, `BEFORE_PUBLIC_RELEASE`
- Owner/domain: Product / Privacy / Legal
- Roadmap/dependency lane: `11.1C`, `CLOUD-02`, `BE-LEGAL-001`, `BE-CONSENT-001`
- Gate dependencies: `CLD-ADM-SCOPE-001`, `CLD-ADM-CONSENT-001`
- Current status: `BLOCKED`; blocking: `true`.
- Blocking reason / missing evidence: No approved Alpha Cloud disclosure/legal/data-use package; DEC-010/011 remain Proposed and CLOUD-02 is BLOCKED.
- Next action: 11.1C, CLOUD-02, BE-LEGAL-001, BE-CONSENT-001: produce and review the required evidence; No approved Alpha Cloud disclosure/legal/data-use package; DEC-010/011 remain Proposed and CLOUD-02 is BLOCKED.

Acceptance criteria:

1. Approved data-flow inventory states what leaves device, destination/service role, purpose and applicable controller/processor responsibilities
2. Versioned user-visible disclosure covers provider/subprocessors, data location, retention, deletion and revocation; revocation is not retroactive erasure
3. ASR processing consent does not authorize training, human review or unrelated artifacts
4. Provider/region changes cannot silently expand grants; no location inference from VPN
5. Record qualified owner approval for actual scope; this gate contract supplies no legal approval

Required evidence:

- Reviewed disclosure text/version, data-flow map and privacy/legal decision
- Candidate/provider terms and data-use/subprocessor/location evidence bound to scope, then revalidated for selected provider

Current repository evidence / requirement authority:

- [docs/DORA_MVP1_PRODUCT_DECISIONS.md](https://github.com/Monumentogram/DORA/blob/1107d4d9eef80691372800da0a17e609eba81957/docs/DORA_MVP1_PRODUCT_DECISIONS.md), section/scenario: DEC-010; DEC-011; DEC-014; DEC-039.
- [docs/adr/ADR-0009-alpha-cloud-execution-boundary.md](https://github.com/Monumentogram/DORA/blob/1107d4d9eef80691372800da0a17e609eba81957/docs/adr/ADR-0009-alpha-cloud-execution-boundary.md), section/scenario: Retention and deletion.

### CLD-ADM-RETENTION-001 — Retention and deletion policy

- Purpose: Resolve every stored copy and its deletion semantics.
- Required before: `BEFORE_CLOUD_RUNTIME_IMPLEMENTATION`, `BEFORE_PROVIDER_ADMISSION`, `BEFORE_FIRST_REAL_CLOUD_AUDIO_UPLOAD`, `BEFORE_INTERNAL_ALPHA_ACCEPTANCE`, `BEFORE_PUBLIC_RELEASE`
- Owner/domain: Privacy / Backend / Storage / Project Owner
- Roadmap/dependency lane: `11.1C`, `8.2C`, `CLOUD-02`, `BE-DELETE-001`
- Gate dependencies: `CLD-ADM-SCOPE-001`
- Current status: `PARTIALLY_SATISFIED`; blocking: `true`.
- Blocking reason / missing evidence: Local policy is approved, but Cloud periods, provider retention, backup behavior and remote receipts remain unresolved under DEC-012.
- Next action: 11.1C, 8.2C, CLOUD-02, BE-DELETE-001: produce and review the required evidence; Local policy is approved, but Cloud periods, provider retention, backup behavior and remote receipts remain unresolved under DEC-012.

Acceptance criteria:

1. Preserve approved local default: original audio until explicit deletion; automatic retention OFF with unapproved numeric catalog unavailable
2. Approve actual periods/triggers for DORA objects, provider copies, job metadata, transcripts, caches, logs, backups and replicas; no arbitrary TTL from recommendations
3. Define deletion propagation, receipt scope, failure/retry, late callbacks, restore suppression and source-unavailable behavior
4. Consent revocation != deletion; local deletion != proof of remote deletion; disclose limits of provider receipts
5. Audio-only deletion preserves transcripts/edits; whole recording deletion uses exact disclosed cascade

Required evidence:

- Approved per-artifact/per-holder lifecycle matrix
- Versioned Cloud period/trigger decision plus backup/replica and receipt semantics

Current repository evidence / requirement authority:

- [docs/DORA_MVP1_PRODUCT_DECISIONS.md](https://github.com/Monumentogram/DORA/blob/1107d4d9eef80691372800da0a17e609eba81957/docs/DORA_MVP1_PRODUCT_DECISIONS.md), section/scenario: DEC-012; DEC-013.
- [docs/design/DORA_MVP1_STORAGE_RETENTION_DELETE_CONTRACT.md](https://github.com/Monumentogram/DORA/blob/1107d4d9eef80691372800da0a17e609eba81957/docs/design/DORA_MVP1_STORAGE_RETENTION_DELETE_CONTRACT.md), section/scenario: Normative principles and defaults; Remote deletion states and transitions.

### CLD-ADM-CONTROL-001 — Cloud security and ownership design

- Purpose: Close the pre-code trust/key/identity design questions.
- Required before: `BEFORE_CLOUD_RUNTIME_IMPLEMENTATION`, `BEFORE_FIRST_REAL_CLOUD_AUDIO_UPLOAD`, `BEFORE_CLOUD_RUNTIME_INTEGRATION`, `BEFORE_INTERNAL_ALPHA_ACCEPTANCE`, `BEFORE_PUBLIC_RELEASE`
- Owner/domain: Security / Backend architecture
- Roadmap/dependency lane: `11.1C`, `BE-AUTH-001`, `BE-API-001`, `BE-DELETE-001`
- Gate dependencies: `CLD-ADM-PRIVACY-001`, `CLD-ADM-RETENTION-001`
- Current status: `OPEN`; blocking: `true`.
- Blocking reason / missing evidence: ADR-0009 specifies responsibilities, but does not resolve credential mechanism, key custody, consent/deletion ledgers or bounded design review.
- Next action: 11.1C, BE-AUTH-001, BE-API-001, BE-DELETE-001: produce and review the required evidence; ADR-0009 specifies responsibilities, but does not resolve credential mechanism, key custody, consent/deletion ledgers or bounded design review.

Acceptance criteria:

1. Reviewed design defines caller/installation identity and server-issued credentials, optional future accounts, ownership, refresh/revocation and replay protection; no identity provider chosen here
2. Resolve payload protection, encryption at rest, key custody/worker decrypt access and audit; no unproved E2EE claim
3. Specify independent server authorization, upload authority revocation, consent ledger and durable deletion ledger
4. Bounded threat/design review dispositions RDY-011/012/013/018 and identifies testable controls, owners and residual risks; unresolved blocking risks prevent entry
5. Recording, offline use and Local ASR do not require a Cloud account

Required evidence:

- Accepted bounded Alpha security/identity/key-custody design with reviewer and risk dispositions
- Approved consent/deletion ledger and ownership model; runtime proof remains separate

Current repository evidence / requirement authority:

- [docs/DORA_MVP1_IMPLEMENTATION_READINESS.md](https://github.com/Monumentogram/DORA/blob/1107d4d9eef80691372800da0a17e609eba81957/docs/DORA_MVP1_IMPLEMENTATION_READINESS.md), section/scenario: RDY-011; RDY-012; RDY-013; RDY-018.
- [docs/adr/ADR-0009-alpha-cloud-execution-boundary.md](https://github.com/Monumentogram/DORA/blob/1107d4d9eef80691372800da0a17e609eba81957/docs/adr/ADR-0009-alpha-cloud-execution-boundary.md), section/scenario: Identity principle; Audio data plane.

### CLD-ADM-EVALUATION-001 — Provider evaluation protocol and data admission

- Purpose: Prevent API availability being mistaken for quality admission.
- Required before: `BEFORE_CLOUD_RUNTIME_IMPLEMENTATION`, `BEFORE_PROVIDER_ADMISSION`, `BEFORE_INTERNAL_ALPHA_ACCEPTANCE`, `BEFORE_PUBLIC_RELEASE`
- Owner/domain: ML / QA / Privacy / Project Owner
- Roadmap/dependency lane: `6.2D`, `BE-PROVIDER-001`, `BE-PROVIDER-002`
- Gate dependencies: `CLD-ADM-SCOPE-001`
- Current status: `OPEN`; blocking: `true`.
- Blocking reason / missing evidence: No 6.2D Cloud evaluation protocol/dataset admission exists; existing Local ASR campaigns cannot be repurposed silently.
- Next action: 6.2D, BE-PROVIDER-001, BE-PROVIDER-002: produce and review the required evidence; No 6.2D Cloud evaluation protocol/dataset admission exists; existing Local ASR campaigns cannot be repurposed silently.

Acceptance criteria:

1. Freeze RU and EN quality/latency/timestamp metrics, test slices, thresholds, failure criteria and repeat policy prospectively; historical Stage 5 thresholds are not automatically Cloud thresholds
2. Declare diarization applicability from 6.1; if required freeze capability/quality criteria; explicit non-applicability requires scope evidence
3. Every evaluation dataset has rights and Cloud-processing authorization for its exact destination; local PoC permission does not authorize third-party upload
4. External synthetic tests need reviewed terms, non-sensitive generated fixtures, secure test-only credentials, budget and allowed destination; mocks stay isolated
5. Test protocol covers error classes, limits, long/partial audio, replacement feasibility and per-function quality for other in-scope AI functions

Required evidence:

- Versioned provider comparison protocol and approved thresholds
- Dataset provenance/authorization manifest and synthetic test envelope; no benchmarks are run in 6.2C

Current repository evidence / requirement authority:

- [docs/adr/ADR-0009-alpha-cloud-execution-boundary.md](https://github.com/Monumentogram/DORA/blob/1107d4d9eef80691372800da0a17e609eba81957/docs/adr/ADR-0009-alpha-cloud-execution-boundary.md), section/scenario: Provider abstraction.

### CLD-ADM-PROVIDER-001 — Provider and model admission

- Purpose: Require an explicit per-function admission with reproducible evidence.
- Required before: `BEFORE_CLOUD_RUNTIME_IMPLEMENTATION`, `BEFORE_PROVIDER_ADMISSION`, `BEFORE_FIRST_REAL_CLOUD_AUDIO_UPLOAD`, `BEFORE_CLOUD_RUNTIME_INTEGRATION`, `BEFORE_INTERNAL_ALPHA_ACCEPTANCE`, `BEFORE_PUBLIC_RELEASE`
- Owner/domain: ML / Backend / Privacy / Project Owner
- Roadmap/dependency lane: `6.2D`, `18.1`, `BE-PROVIDER-001`, `BE-PROVIDER-002`
- Gate dependencies: `CLD-ADM-EVALUATION-001`, `CLD-ADM-PRIVACY-001`, `CLD-ADM-RETENTION-001`
- Current status: `OPEN`; blocking: `true`.
- Blocking reason / missing evidence: Provider = NOT SELECTED; Model = NOT SELECTED; live 6.2D is PLANNED / NOT RUN.
- Next action: 6.2D, 18.1, BE-PROVIDER-001, BE-PROVIDER-002: produce and review the required evidence; Provider = NOT SELECTED; Model = NOT SELECTED; live 6.2D is PLANNED / NOT RUN.

Acceptance criteria:

1. Cloud ASR RU and EN each meet frozen quality criteria; latency and timestamp availability/quality assessed; diarization evaluated if scope requires
2. Audio formats/duration/size limits, API stability/versioning, normalized errors, rate limits and availability documented/tested
3. Exact provider/model version or honest unavailable identity, cost, privacy, retention, training/data-use, location and terms/licensing evidence accepted
4. Replacement feasibility demonstrated against CloudAsrProvider; successful API call alone is insufficient
5. For every other AI function in approved 6.1 scope, 6.2D records its own admitted provider/model or explicit local/deferred disposition; factuality/hallucination evidence where relevant
6. Admission record names decision authority, evidence, date, limitations and change/revalidation triggers; no provider/model chosen in 6.2C

Required evidence:

- Reproducible per-language/per-function evaluation and accepted decision
- Provider documentation/terms snapshots and cost/privacy/retention/availability review
- Replaceability assessment and admitted configuration identity

Current repository evidence / requirement authority:

- [docs/adr/ADR-0009-alpha-cloud-execution-boundary.md](https://github.com/Monumentogram/DORA/blob/1107d4d9eef80691372800da0a17e609eba81957/docs/adr/ADR-0009-alpha-cloud-execution-boundary.md), section/scenario: Provider abstraction; Decision scope.

### CLD-ADM-ADMISSION-001 — Development admission and exact baseline

- Purpose: Make 6.3 the final explicit entry decision, not a circular prerequisite.
- Required before: `BEFORE_CLOUD_RUNTIME_IMPLEMENTATION`, `BEFORE_FIRST_REAL_CLOUD_AUDIO_UPLOAD`, `BEFORE_CLOUD_RUNTIME_INTEGRATION`, `BEFORE_INTERNAL_ALPHA_ACCEPTANCE`, `BEFORE_PUBLIC_RELEASE`
- Owner/domain: Project Owner / Technical lead
- Roadmap/dependency lane: `6.3`
- Gate dependencies: `CLD-ADM-ARCH-001`, `CLD-ADM-CONSENT-001`, `CLD-ADM-DATA-001`, `CLD-ADM-SCOPE-001`, `CLD-ADM-GAPS-001`, `CLD-ADM-PRIVACY-001`, `CLD-ADM-RETENTION-001`, `CLD-ADM-CONTROL-001`, `CLD-ADM-EVALUATION-001`, `CLD-ADM-PROVIDER-001`
- Current status: `BLOCKED`; blocking: `true`.
- Blocking reason / missing evidence: 6.3 remains blocked by SCOPE, GAPS, PRIVACY, RETENTION, CONTROL, EVALUATION and PROVIDER; exact-base admission/CI decision is not recorded.
- Next action: 6.3: produce and review the required evidence; 6.3 remains blocked by SCOPE, GAPS, PRIVACY, RETENTION, CONTROL, EVALUATION and PROVIDER; exact-base admission/CI decision is not recorded.

Acceptance criteria:

1. All other implementation-entry gates satisfied or eligible entry-only waiver explicitly approved
2. Record Stage 0 go/no-go, approved scope, exact development commit/branch, CI evidence and integration plan
3. 6.3 may close after its predecessor decisions; it does not require already implemented Alpha-exit tests
4. No merge of PR #86 is authorized by this contract

Required evidence:

- Versioned 6.3 admission decision plus exact-baseline CI and unresolved-gate ledger

Current repository evidence / requirement authority:

- Baseline ADR-0009 and ASR product/data contracts define requirements only; no gate-specific completion evidence found. Live roadmap remains planned/not started.

### CLD-ADM-API-001 — Versioned API and job contract

- Purpose: Keep DORA logical identity authoritative.
- Required before: `BEFORE_CLOUD_RUNTIME_INTEGRATION`, `BEFORE_FIRST_REAL_CLOUD_AUDIO_UPLOAD`, `BEFORE_INTERNAL_ALPHA_ACCEPTANCE`, `BEFORE_PUBLIC_RELEASE`
- Owner/domain: Backend / Android
- Roadmap/dependency lane: `7.2C`, `7.2E`, `BE-API-001`
- Gate dependencies: `CLD-ADM-ADMISSION-001`
- Current status: `OPEN`; blocking: `true`.
- Blocking reason / missing evidence: Conceptual API/job identities exist; implementation-ready schemas and compatibility fixtures are absent.
- Next action: 7.2C, 7.2E, BE-API-001: produce and review the required evidence; Conceptual API/job identities exist; implementation-ready schemas and compatibility fixtures are absent.

Acceptance criteria:

1. Freeze processing_job_id, processing_action_id, recording_id, source_audio_version, idempotency_key, attempt_id, provider_request_id and asr_result_id relationships
2. Define create job, upload authorization, processing state, retrieval, cancel, retry, unknown-outcome reconciliation, duplicates and stale responses
3. Stable normalized errors, bounded request/response sizes and schema compatibility; provider wire format remains behind CloudAsrProvider
4. One logical action has one idempotency key across retries; explicit reprocess creates new action; IDs alone never prove ownership

Required evidence:

- Reviewed versioned API/state contract and generated contract fixtures

Current repository evidence / requirement authority:

- [docs/contracts/DORA_ASR_DATA_AND_VERSIONING_CONTRACT_V0_1.md](https://github.com/Monumentogram/DORA/blob/1107d4d9eef80691372800da0a17e609eba81957/docs/contracts/DORA_ASR_DATA_AND_VERSIONING_CONTRACT_V0_1.md), section/scenario: Jobs, queue, idempotency and state.
- [docs/DORA_MVP1_TECHNICAL_PLAN.md](https://github.com/Monumentogram/DORA/blob/1107d4d9eef80691372800da0a17e609eba81957/docs/DORA_MVP1_TECHNICAL_PLAN.md), section/scenario: 28. API contracts.

### CLD-ADM-AUTH-001 — Identity and ownership enforcement

- Purpose: Prevent cross-owner access across all operations.
- Required before: `BEFORE_FIRST_REAL_CLOUD_AUDIO_UPLOAD`, `BEFORE_CLOUD_RUNTIME_INTEGRATION`, `BEFORE_INTERNAL_ALPHA_ACCEPTANCE`, `BEFORE_PUBLIC_RELEASE`
- Owner/domain: Backend / Security / Android
- Roadmap/dependency lane: `7.2E`, `18.1C`, `BE-AUTH-001`
- Gate dependencies: `CLD-ADM-CONTROL-001`, `CLD-ADM-API-001`
- Current status: `NOT_RUN`; blocking: `true`.
- Blocking reason / missing evidence: No Cloud authentication/ownership runtime or executed evidence.
- Next action: 7.2E, 18.1C, BE-AUTH-001: produce and review the required evidence; No Cloud authentication/ownership runtime or executed evidence.

Acceptance criteria:

1. Server authenticates caller and binds installation/device where used; optional account linking cannot transfer ownership implicitly
2. Server-issued credentials have verified issue/expiry/refresh/rotation/revocation lifecycle and replay resistance where applicable
3. Recording, job, object, result and deletion operations reject cross-user/cross-device access; caller-supplied owner IDs cannot override verified identity
4. Local recording/offline/Local ASR work without Cloud account

Required evidence:

- Credential lifecycle and adversarial cross-owner tests on admitted build/configuration

Current repository evidence / requirement authority:

- Baseline ADR-0009 and ASR product/data contracts define requirements only; no gate-specific completion evidence found. Live roadmap remains planned/not started.

### CLD-ADM-CONSENT-RUNTIME-001 — Runtime consent enforcement

- Purpose: Prove zero unauthorized upload bytes in every dispatch path.
- Required before: `BEFORE_FIRST_REAL_CLOUD_AUDIO_UPLOAD`, `BEFORE_CLOUD_RUNTIME_INTEGRATION`, `BEFORE_INTERNAL_ALPHA_ACCEPTANCE`, `BEFORE_PUBLIC_RELEASE`
- Owner/domain: Android / Backend / QA
- Roadmap/dependency lane: `9.2C`, `9.2D`, `11.1C`, `18.1C`
- Gate dependencies: `CLD-ADM-CONSENT-001`, `CLD-ADM-AUTH-001`, `CLD-ADM-HARNESS-001`
- Current status: `NOT_RUN`; blocking: `true`.
- Blocking reason / missing evidence: Only declarative scenarios exist; executable consent/byte-count evidence is absent.
- Next action: 9.2C, 9.2D, 11.1C, 18.1C: produce and review the required evidence; Only declarative scenarios exist; executable consent/byte-count evidence is absent.

Acceptance criteria:

1. Persist authorization/disclosure receipt before any outbound audio; backend independently checks current scope/ownership
2. Recheck each chunk, retry, renewed upload authority and processing dispatch; stale token/queue cannot bypass revocation
3. Prove all three policies and five recording authorization states, batch selected snapshot, prompt-once and durable DEFERRED
4. Leaving ALWAYS invalidates unstarted inherited work; explicit grants retain only unrevoked disclosed scope; revocation stops further bytes

Required evidence:

- Deterministic byte-counter, backend rejection and revocation-race tests for every outbound path

Current repository evidence / requirement authority:

- Baseline ADR-0009 and ASR product/data contracts define requirements only; no gate-specific completion evidence found. Live roadmap remains planned/not started.

### CLD-ADM-SECRETS-001 — Privileged secrets protection

- Purpose: Keep provider credentials out of clients and public artifacts.
- Required before: `BEFORE_FIRST_REAL_CLOUD_AUDIO_UPLOAD`, `BEFORE_CLOUD_RUNTIME_INTEGRATION`, `BEFORE_INTERNAL_ALPHA_ACCEPTANCE`, `BEFORE_PUBLIC_RELEASE`
- Owner/domain: Backend / Security / Release
- Roadmap/dependency lane: `18.1C`, `11.1C`
- Gate dependencies: `CLD-ADM-CONTROL-001`, `CLD-ADM-ADMISSION-001`
- Current status: `NOT_RUN`; blocking: `true`.
- Blocking reason / missing evidence: No admitted server secret lifecycle or built Cloud-client leak evidence.
- Next action: 18.1C, 11.1C: produce and review the required evidence; No admitted server secret lifecycle or built Cloud-client leak evidence.

Acceptance criteria:

1. Privileged provider credentials MUST NOT be present in Android/APK/client configuration
2. Server-side secret storage uses least privilege, rotation, revocation and separate environments
3. Application logs, crash reports, CI output, build artifacts and temporary URLs cannot leak credentials
4. Review credential provisioning/incident procedures and CI/build leak checks; no secrets provisioned in 6.2C

Required evidence:

- Server configuration review, rotation/revocation test and APK/config/build/log secret scan

Current repository evidence / requirement authority:

- Baseline ADR-0009 and ASR product/data contracts define requirements only; no gate-specific completion evidence found. Live roadmap remains planned/not started.

### CLD-ADM-UPLOAD-001 — Storage and scoped upload authorization

- Purpose: Bind every byte to current authorization and the intended source.
- Required before: `BEFORE_FIRST_REAL_CLOUD_AUDIO_UPLOAD`, `BEFORE_CLOUD_RUNTIME_INTEGRATION`, `BEFORE_INTERNAL_ALPHA_ACCEPTANCE`, `BEFORE_PUBLIC_RELEASE`
- Owner/domain: Backend / Storage / Android
- Roadmap/dependency lane: `9.2C`, `8.4C`, `18.1C`
- Gate dependencies: `CLD-ADM-AUTH-001`, `CLD-ADM-CONSENT-RUNTIME-001`, `CLD-ADM-SECRETS-001`, `CLD-ADM-CRYPTO-001`, `CLD-ADM-API-001`
- Current status: `NOT_RUN`; blocking: `true`.
- Blocking reason / missing evidence: No storage mechanism, runtime authorization or revocation proof exists.
- Next action: 9.2C, 8.4C, 18.1C: produce and review the required evidence; No storage mechanism, runtime authorization or revocation proof exists.

Acceptance criteria:

1. DORA-controlled storage; authority scoped to owner/recording/job/source-audio version, operation and expiry
2. Enforce revocation, size/content/checksum constraints and authenticated secure transport; no public bucket/object access
3. A still-valid temporary credential cannot bypass revoked recording consent; demonstrate this on selected mechanism
4. Retry/resume/renewal and duplicate prevention preserve identity; no provider/storage vendor selected here
5. Chunk manifest completeness/order/digests verified before dispatch; malicious content, path/object substitution and oversized/decompression inputs rejected

Required evidence:

- Storage ACL/configuration and byte-boundary revocation tests
- Interrupted/resumed/duplicate/malformed upload integration tests with checksums

Current repository evidence / requirement authority:

- Baseline ADR-0009 and ASR product/data contracts define requirements only; no gate-specific completion evidence found. Live roadmap remains planned/not started.

### CLD-ADM-CRYPTO-001 — Payload transport and key protection

- Purpose: Protect audio, transcripts and credentials across storage/worker boundaries.
- Required before: `BEFORE_FIRST_REAL_CLOUD_AUDIO_UPLOAD`, `BEFORE_CLOUD_RUNTIME_INTEGRATION`, `BEFORE_INTERNAL_ALPHA_ACCEPTANCE`, `BEFORE_PUBLIC_RELEASE`
- Owner/domain: Security / Backend / Storage
- Roadmap/dependency lane: `11.1C`, `18.1C`
- Gate dependencies: `CLD-ADM-CONTROL-001`, `CLD-ADM-SECRETS-001`
- Current status: `NOT_RUN`; blocking: `true`.
- Blocking reason / missing evidence: No bounded implemented Cloud payload/key/transport evidence.
- Next action: 11.1C, 18.1C: produce and review the required evidence; No bounded implemented Cloud payload/key/transport evidence.

Acceptance criteria:

1. Validate secure transport, certificate/endpoint identity and fail-closed behavior for Android/storage/backend/provider/result paths
2. Implement reviewed encryption-at-rest/key-custody, worker access, least privilege and environment separation
3. Verify access audit, key rotation/revocation, temporary plaintext cleanup and failure paths; no unsupported E2EE/erasure claim
4. No silent region/fallback switch outside consent; distinguish unavailable network from authenticated endpoint

Required evidence:

- Transport/adversarial certificate tests and key/access configuration review
- Worker temporary-data and revoked-key tests

Current repository evidence / requirement authority:

- Baseline ADR-0009 and ASR product/data contracts define requirements only; no gate-specific completion evidence found. Live roadmap remains planned/not started.

### CLD-ADM-RETENTION-RUNTIME-001 — Retention enforcement and remote deletion

- Purpose: Prove actual cleanup and honest receipts before handling real audio.
- Required before: `BEFORE_FIRST_REAL_CLOUD_AUDIO_UPLOAD`, `BEFORE_CLOUD_RUNTIME_INTEGRATION`, `BEFORE_INTERNAL_ALPHA_ACCEPTANCE`, `BEFORE_PUBLIC_RELEASE`
- Owner/domain: Backend / Storage / Privacy / QA
- Roadmap/dependency lane: `11.1C`, `18.1C`, `BE-DELETE-001`
- Gate dependencies: `CLD-ADM-RETENTION-001`, `CLD-ADM-AUTH-001`, `CLD-ADM-PROVIDER-001`, `CLD-ADM-DELETE-001`, `CLD-ADM-HARNESS-001`
- Current status: `NOT_RUN`; blocking: `true`.
- Blocking reason / missing evidence: No approved implemented lifecycle or remote deletion receipt tests.
- Next action: 11.1C, 18.1C, BE-DELETE-001: produce and review the required evidence; No approved implemented lifecycle or remote deletion receipt tests.

Acceptance criteria:

1. Execute approved lifecycles for original audio, DORA objects, provider copies, metadata, transcripts, caches and logs
2. Verify deletion propagation/receipt authenticity and exact scope; backup/replica limits disclosed
3. Durable retries and reconciliation preserve local/remote outcome distinction through failure/restart
4. Tombstones prevent late callbacks or restored backups resurrecting deleted content; deletion cleanup can proceed after processing consent revocation under separately valid deletion authority

Required evidence:

- Controlled-clock retention tests and end-to-end receipt/failed-delete/restore tests
- Provider deletion assurance bound to approved scope and limitations

Current repository evidence / requirement authority:

- Baseline ADR-0009 and ASR product/data contracts define requirements only; no gate-specific completion evidence found. Live roadmap remains planned/not started.

### CLD-ADM-DATA-RUNTIME-001 — Durable versions and atomic activation

- Purpose: Implement the accepted data invariant without destructive migrations.
- Required before: `BEFORE_FIRST_REAL_CLOUD_AUDIO_UPLOAD`, `BEFORE_CLOUD_RUNTIME_INTEGRATION`, `BEFORE_INTERNAL_ALPHA_ACCEPTANCE`, `BEFORE_PUBLIC_RELEASE`
- Owner/domain: Android / Data / Backend
- Roadmap/dependency lane: `7.2D`, `8.2C`, `8.4C`
- Gate dependencies: `CLD-ADM-DATA-001`, `CLD-ADM-ADMISSION-001`, `CLD-ADM-HARNESS-001`
- Current status: `NOT_RUN`; blocking: `true`.
- Blocking reason / missing evidence: No Cloud versioned storage integration or migration evidence.
- Next action: 7.2D, 8.2C, 8.4C: produce and review the required evidence; No Cloud versioned storage integration or migration evidence.

Acceptance criteria:

1. Persist original-audio identity, immutable Local/Cloud versions, edits, provenance and active pointer
2. Logical recording/chunk/source bindings survive restart; all IDs/reference integrity validated
3. Atomic activation checks latest revision and tombstones; failures cannot orphan edits
4. Upgrade/downgrade/rollback strategy preserves retained data, queue, consent and deletion state; no destructive fallback

Required evidence:

- Persistence, transaction fault and migration/rollback fixtures with invariant assertions

Current repository evidence / requirement authority:

- Baseline ADR-0009 and ASR product/data contracts define requirements only; no gate-specific completion evidence found. Live roadmap remains planned/not started.

### CLD-ADM-FAILURE-001 — Idempotency and failure safety

- Purpose: Bound uncertainty and preserve all successful data.
- Required before: `BEFORE_FIRST_REAL_CLOUD_AUDIO_UPLOAD`, `BEFORE_CLOUD_RUNTIME_INTEGRATION`, `BEFORE_INTERNAL_ALPHA_ACCEPTANCE`, `BEFORE_PUBLIC_RELEASE`
- Owner/domain: Backend / Android / QA
- Roadmap/dependency lane: `9.2C`, `9.2E`, `18.1C`, `12.1C`
- Gate dependencies: `CLD-ADM-API-001`, `CLD-ADM-DATA-RUNTIME-001`, `CLD-ADM-HARNESS-001`
- Current status: `NOT_RUN`; blocking: `true`.
- Blocking reason / missing evidence: Failure semantics are documented, not executed in Cloud runtime.
- Next action: 9.2C, 9.2E, 18.1C, 12.1C: produce and review the required evidence; Failure semantics are documented, not executed in Cloud runtime.

Acceptance criteria:

1. Inject network loss, interrupted upload, backend/Android/worker restart, provider timeout, 4xx, 5xx and 429
2. Cover duplicate requests/callbacks, late callbacks, cancel during upload/processing and authorization revocation
3. Same action/idempotency across retries; reconcile unknown provider outcome before resubmission; no competing result/billing effects
4. Bounded retry/backoff/Retry-After, leases and budgets; nonretryable auth/schema errors require correction
5. Every failure preserves original audio, Local transcript, active transcript and user edits; explicit deletion remains a distinct user operation

Required evidence:

- Deterministic fault matrix with state/identity/cost and four data-preservation assertions

Current repository evidence / requirement authority:

- Baseline ADR-0009 and ASR product/data contracts define requirements only; no gate-specific completion evidence found. Live roadmap remains planned/not started.

### CLD-ADM-QUEUE-001 — Offline queue and reconnect

- Purpose: Keep authorization durable and reconnect non-authorizing.
- Required before: `BEFORE_FIRST_REAL_CLOUD_AUDIO_UPLOAD`, `BEFORE_CLOUD_RUNTIME_INTEGRATION`, `BEFORE_INTERNAL_ALPHA_ACCEPTANCE`, `BEFORE_PUBLIC_RELEASE`
- Owner/domain: Android / QA
- Roadmap/dependency lane: `9.2D`, `11.2C`
- Gate dependencies: `CLD-ADM-CONSENT-RUNTIME-001`, `CLD-ADM-DATA-RUNTIME-001`, `CLD-ADM-HARNESS-001`
- Current status: `NOT_RUN`; blocking: `true`.
- Blocking reason / missing evidence: No executed durable Cloud queue evidence.
- Next action: 9.2D, 11.2C: produce and review the required evidence; No executed durable Cloud queue evidence.

Acceptance criteria:

1. Durable pending queue, action identity, deduplication, cancellation, authorization and DEFERRED survive app/device restart
2. Reconnect revalidates source/current grant; NEVER creates consent
3. MANUAL_ONLY never autonomously uploads; ALWAYS does not unnecessarily reprompt; ASK automatically prompts once per logical recording
4. Batch Process all/Select/Not now applies to selected snapshot; deferred items excluded from new automatic batch

Required evidence:

- Restart/reconnect/batch/policy tests with durable state, prompt counts and upload-byte assertions

Current repository evidence / requirement authority:

- Baseline ADR-0009 and ASR product/data contracts define requirements only; no gate-specific completion evidence found. Live roadmap remains planned/not started.

### CLD-ADM-BACKGROUND-001 — Android background execution

- Purpose: Validate scheduling within declared Alpha platform constraints.
- Required before: `BEFORE_INTERNAL_ALPHA_ACCEPTANCE`, `BEFORE_PUBLIC_RELEASE`
- Owner/domain: Android / QA
- Roadmap/dependency lane: `11.2C`, `9.2C`, `12.1C`
- Gate dependencies: `CLD-ADM-QUEUE-001`, `CLD-ADM-FAILURE-001`
- Current status: `NOT_RUN`; blocking: `true`.
- Blocking reason / missing evidence: No Cloud scheduling implementation or device/background acceptance run.
- Next action: 11.2C, 9.2C, 12.1C: produce and review the required evidence; No Cloud scheduling implementation or device/background acceptance run.

Acceptance criteria:

1. Verify backgrounding, process death, reboot, network constraints, battery restrictions and user cancellation
2. Document Android scheduler and applicable foreground-service constraints; no invented endless service exemption
3. Large upload interruption resumes only with current authority; force-stop/reboot recovery never silently restarts microphone
4. Test declared API/OEM/device scope; simulated reboot abstraction cannot alone prove physical behavior

Required evidence:

- Versioned scheduling policy and host plus applicable Android/device execution evidence

Current repository evidence / requirement authority:

- Baseline ADR-0009 and ASR product/data contracts define requirements only; no gate-specific completion evidence found. Live roadmap remains planned/not started.

### CLD-ADM-ADAPTER-001 — Replaceable CloudAsrProvider implementation

- Purpose: Contain provider-specific protocol and error behavior.
- Required before: `BEFORE_FIRST_REAL_CLOUD_AUDIO_UPLOAD`, `BEFORE_CLOUD_RUNTIME_INTEGRATION`, `BEFORE_INTERNAL_ALPHA_ACCEPTANCE`, `BEFORE_PUBLIC_RELEASE`
- Owner/domain: Backend
- Roadmap/dependency lane: `7.2C`, `18.1C`
- Gate dependencies: `CLD-ADM-PROVIDER-001`, `CLD-ADM-API-001`, `CLD-ADM-HARNESS-001`
- Current status: `NOT_RUN`; blocking: `true`.
- Blocking reason / missing evidence: CloudAsrProvider is an architectural requirement, not an implemented port.
- Next action: 7.2C, 18.1C: produce and review the required evidence; CloudAsrProvider is an architectural requirement, not an implemented port.

Acceptance criteria:

1. Provider request/response schemas, errors, IDs, limits and metadata terminate inside adapter
2. Core consumes normalized job/result/provenance/usage and explicit missing metadata
3. Contract tests swap controlled provider implementations without changing core; production Alpha may have one admitted provider
4. Only admitted provider/model/scope routed; no implicit fallback to another service

Required evidence:

- Boundary/dependency review and adapter contract/replacement tests

Current repository evidence / requirement authority:

- Baseline ADR-0009 and ASR product/data contracts define requirements only; no gate-specific completion evidence found. Live roadmap remains planned/not started.

### CLD-ADM-COST-001 — Cost, quotas and abuse controls

- Purpose: Prevent unbounded cost before real processing.
- Required before: `BEFORE_FIRST_REAL_CLOUD_AUDIO_UPLOAD`, `BEFORE_INTERNAL_ALPHA_ACCEPTANCE`, `BEFORE_PUBLIC_RELEASE`
- Owner/domain: Backend / Operations
- Roadmap/dependency lane: `18.2C`, `18.1C`
- Gate dependencies: `CLD-ADM-PROVIDER-001`, `CLD-ADM-API-001`, `CLD-ADM-HARNESS-001`
- Current status: `NOT_RUN`; blocking: `true`.
- Blocking reason / missing evidence: No operational accounting, budget enforcement or cost-amplification evidence.
- Next action: 18.2C, 18.1C: produce and review the required evidence; No operational accounting, budget enforcement or cost-amplification evidence.

Acceptance criteria:

1. Account request count, processed duration, uploaded bytes, provider usage and estimated/actual cost where available
2. Versioned per-user/device/project quota strategy, retry budget and rate-limit behavior enforced before costly work
3. Duplicate/unknown outcomes cannot multiply spend; fail closed on exhausted budget; kill switch and abuse limits available
4. Alpha operational budget approved by responsible owner; commercial pricing plans remain out of scope

Required evidence:

- Usage reconciliation and quota/rate-limit/retry amplification tests against configured budget

Current repository evidence / requirement authority:

- Baseline ADR-0009 and ASR product/data contracts define requirements only; no gate-specific completion evidence found. Live roadmap remains planned/not started.

### CLD-ADM-OBSERVABILITY-001 — Content-free operational observability

- Purpose: Trace jobs without leaking private content or credentials.
- Required before: `BEFORE_FIRST_REAL_CLOUD_AUDIO_UPLOAD`, `BEFORE_INTERNAL_ALPHA_ACCEPTANCE`, `BEFORE_PUBLIC_RELEASE`
- Owner/domain: Operations / Backend / Privacy
- Roadmap/dependency lane: `18.2C`, `11.1C`
- Gate dependencies: `CLD-ADM-PRIVACY-001`, `CLD-ADM-RETENTION-001`, `CLD-ADM-API-001`, `CLD-ADM-HARNESS-001`
- Current status: `NOT_RUN`; blocking: `true`.
- Blocking reason / missing evidence: No implemented Cloud telemetry or redaction evidence.
- Next action: 18.2C, 11.1C: produce and review the required evidence; No implemented Cloud telemetry or redaction evidence.

Acceptance criteria:

1. Correlate job/recording safely with upload/processing durations, provider/model, error class, retry count, transitions, quota and cost/usage
2. Redact audio, transcript, edits, names, paths, URIs, object keys, credentials and sensitive stable identifiers from public logs/telemetry
3. Use scoped opaque correlation with controlled access/retention; diagnostics/export preview cannot leak content
4. Inject failures and verify redaction across client/backend/worker/provider errors and crash logs

Required evidence:

- Telemetry field/access/retention inventory and synthetic log-leak tests
- Operational queries demonstrating safe correlation and error diagnosis

Current repository evidence / requirement authority:

- Baseline ADR-0009 and ASR product/data contracts define requirements only; no gate-specific completion evidence found. Live roadmap remains planned/not started.

### CLD-ADM-SECURITY-001 — Bounded Alpha threat verification

- Purpose: Validate defenses before real user data crosses the boundary.
- Required before: `BEFORE_FIRST_REAL_CLOUD_AUDIO_UPLOAD`, `BEFORE_INTERNAL_ALPHA_ACCEPTANCE`, `BEFORE_PUBLIC_RELEASE`
- Owner/domain: Security / QA
- Roadmap/dependency lane: `11.1C`, `12.1C`, `18.1C`
- Gate dependencies: `CLD-ADM-CONTROL-001`, `CLD-ADM-UPLOAD-001`, `CLD-ADM-AUTH-001`, `CLD-ADM-SECRETS-001`, `CLD-ADM-COST-001`, `CLD-ADM-OBSERVABILITY-001`, `CLD-ADM-RESULT-001`, `CLD-ADM-DELETE-001`, `CLD-ADM-HARNESS-001`
- Current status: `NOT_RUN`; blocking: `true`.
- Blocking reason / missing evidence: No bounded implemented Cloud threat verification exists.
- Next action: 11.1C, 12.1C, 18.1C: produce and review the required evidence; No bounded implemented Cloud threat verification exists.

Acceptance criteria:

1. Bounded review covers unauthorized upload, IDOR/cross-owner access, stolen upload authority, replay and secret leaks
2. Cover oversized/malformed audio, abuse/cost amplification, stale result injection, logging leakage, deletion/resurrection races and provider response spoofing where applicable
3. Every blocking finding fixed and verified; residual limitations explicitly reviewed by accountable owner
4. Review authenticates callbacks/result origins and restricts worker egress/input references; no arbitrary URL/SSRF destination or cross-region fallback
5. Evidence scope/date/build/configuration recorded; no formal external security audit claim

Required evidence:

- Threat register mapped to implemented controls and negative tests, with reviewer disposition

Current repository evidence / requirement authority:

- Baseline ADR-0009 and ASR product/data contracts define requirements only; no gate-specific completion evidence found. Live roadmap remains planned/not started.

### CLD-ADM-RESULT-001 — Cloud result validation

- Purpose: Do not automatically activate a provider response.
- Required before: `BEFORE_FIRST_REAL_CLOUD_AUDIO_UPLOAD`, `BEFORE_CLOUD_RUNTIME_INTEGRATION`, `BEFORE_INTERNAL_ALPHA_ACCEPTANCE`, `BEFORE_PUBLIC_RELEASE`
- Owner/domain: Android / Backend / Data
- Roadmap/dependency lane: `9.2E`, `7.2D`, `18.1C`
- Gate dependencies: `CLD-ADM-DATA-RUNTIME-001`, `CLD-ADM-API-001`, `CLD-ADM-HARNESS-001`
- Current status: `NOT_RUN`; blocking: `true`.
- Blocking reason / missing evidence: No executed result validation or atomic activation evidence.
- Next action: 9.2E, 7.2D, 18.1C: produce and review the required evidence; No executed result validation or atomic activation evidence.

Acceptance criteria:

1. Validate job identity, original source version/digest, action/generation, provider result identity and authenticated origin
2. Reject invalid schema/size/segment ranges and incomplete/failed results as complete active text
3. Check latest edit revision and deletion/cancellation/tombstone state at atomic activation
4. Invalid/stale/duplicate results cannot regress active text or resurrect deleted content; raw valid versions retained as appropriate

Required evidence:

- Forged/mismatched/partial/oversized/stale/duplicate result and activation-race tests

Current repository evidence / requirement authority:

- Baseline ADR-0009 and ASR product/data contracts define requirements only; no gate-specific completion evidence found. Live roadmap remains planned/not started.

### CLD-ADM-MERGE-001 — Edit-preserving merge and review

- Purpose: Preserve user truth across reprocessing and concurrent edits.
- Required before: `BEFORE_FIRST_REAL_CLOUD_AUDIO_UPLOAD`, `BEFORE_INTERNAL_ALPHA_ACCEPTANCE`, `BEFORE_PUBLIC_RELEASE`
- Owner/domain: Android / Data / QA
- Roadmap/dependency lane: `9.4C`, `12.1C`
- Gate dependencies: `CLD-ADM-RESULT-001`, `CLD-ADM-DATA-RUNTIME-001`, `CLD-ADM-HARNESS-001`
- Current status: `NOT_RUN`; blocking: `true`.
- Blocking reason / missing evidence: No implemented merge/review or preservation test evidence.
- Next action: 9.4C, 12.1C: produce and review the required evidence; No implemented merge/review or preservation test evidence.

Acceptance criteria:

1. Test no edits, clean mapping, ambiguous mapping, edits during processing and edits during review
2. Unsafe mapping keeps correction and active text, marks conflict and requires review
3. With edits present, wait for explicit proposal acceptance against latest revision; correction replacement requires explicit conflict-level choice and preserves history
4. Cloud result rejected or failed leaves original audio/Local/active/edits unchanged; never use Local text as ASR input

Required evidence:

- Deterministic semantic/audio-anchor merge fixtures and concurrent activation tests

Current repository evidence / requirement authority:

- Baseline ADR-0009 and ASR product/data contracts define requirements only; no gate-specific completion evidence found. Live roadmap remains planned/not started.

### CLD-ADM-LOCAL-001 — Optional Local package and account-free operation

- Purpose: Cloud admission must not make Local installation mandatory.
- Required before: `BEFORE_INTERNAL_ALPHA_ACCEPTANCE`, `BEFORE_PUBLIC_RELEASE`
- Owner/domain: Android / ML / QA
- Roadmap/dependency lane: `9.1C`, `11.2C`
- Gate dependencies: `CLD-ADM-DATA-RUNTIME-001`, `CLD-ADM-SCOPE-001`
- Current status: `NOT_RUN`; blocking: `true`.
- Blocking reason / missing evidence: Local PoC evidence exists, but optional-package and Cloud-only product flows are NOT_RUN.
- Next action: 9.1C, 11.2C: produce and review the required evidence; Local PoC evidence exists, but optional-package and Cloud-only product flows are NOT_RUN.

Acceptance criteria:

1. Cloud-only user can operate online and record offline without downloading Local
2. Optional Local can be installed later or removed without breaking recording; install/update integrity/version/size/in-use states are safe
3. Installed Local ASR works offline without account/network/GMS; user may choose wait instead
4. Existing bounded Stage 5 result is not proof of product integration or new devices/languages

Required evidence:

- Cloud-only, later install/removal and installed Local offline acceptance runs in declared scope

Current repository evidence / requirement authority:

- Baseline ADR-0009 and ASR product/data contracts define requirements only; no gate-specific completion evidence found. Live roadmap remains planned/not started.

### CLD-ADM-OFFLINE-001 — Cloud-only offline audio preservation

- Purpose: Recording remains safe with no model and no internet.
- Required before: `BEFORE_FIRST_REAL_CLOUD_AUDIO_UPLOAD`, `BEFORE_INTERNAL_ALPHA_ACCEPTANCE`, `BEFORE_PUBLIC_RELEASE`
- Owner/domain: Android / Storage / QA
- Roadmap/dependency lane: `8.2C`, `9.1C`, `9.2D`, `11.2C`
- Gate dependencies: `CLD-ADM-DATA-RUNTIME-001`, `CLD-ADM-QUEUE-001`, `CLD-ADM-HARNESS-001`
- Current status: `NOT_RUN`; blocking: `true`.
- Blocking reason / missing evidence: No integrated Cloud-only offline evidence.
- Next action: 8.2C, 9.1C, 9.2D, 11.2C: produce and review the required evidence; No integrated Cloud-only offline evidence.

Acceptance criteria:

1. No Local model + no internet still yields durable original audio with stable identity and no data loss
2. Audio survives restart and failure until explicit approved deletion; low/full-storage behavior is honest and does not silently delete prior recordings
3. Later network plus valid authorization permits Cloud processing of the same original audio; waiting never forces a Local download
4. Source unavailable after explicit deletion blocks reprocess rather than substituting transcript text

Required evidence:

- No-model/no-network/restart/storage-pressure preservation and later dispatch tests

Current repository evidence / requirement authority:

- Baseline ADR-0009 and ASR product/data contracts define requirements only; no gate-specific completion evidence found. Live roadmap remains planned/not started.

### CLD-ADM-DELETE-001 — Deletion, cancellation and stale-result safety

- Purpose: Prevent resurrection and distinguish local from remote outcomes.
- Required before: `BEFORE_FIRST_REAL_CLOUD_AUDIO_UPLOAD`, `BEFORE_CLOUD_RUNTIME_INTEGRATION`, `BEFORE_INTERNAL_ALPHA_ACCEPTANCE`, `BEFORE_PUBLIC_RELEASE`
- Owner/domain: Android / Backend / Storage
- Roadmap/dependency lane: `11.1C`, `8.2C`, `9.2E`, `10.3C`
- Gate dependencies: `CLD-ADM-RETENTION-001`, `CLD-ADM-DATA-RUNTIME-001`, `CLD-ADM-API-001`, `CLD-ADM-HARNESS-001`
- Current status: `NOT_RUN`; blocking: `true`.
- Blocking reason / missing evidence: No implemented Cloud deletion or stale-result suppression tests.
- Next action: 11.1C, 8.2C, 9.2E, 10.3C: produce and review the required evidence; No implemented Cloud deletion or stale-result suppression tests.

Acceptance criteria:

1. Test recording deletion while upload pending, original-audio-only deletion after transcript, result after deletion and after cancellation
2. Test active transcript deletion and correction deletion with explicit scope and reference safety
3. Local success plus failed remote deletion stays visibly partial; no fake receipt or automatic retry that sends deleted audio
4. Durable tombstones/generation checks block late callbacks, restore and indexing resurrection; receipt metadata minimized
5. Retain transcripts/edits on audio-only deletion; whole-recording cascade follows approved policy

Required evidence:

- Deletion/cancel/result races, restart and remote failure/receipt tests

Current repository evidence / requirement authority:

- Baseline ADR-0009 and ASR product/data contracts define requirements only; no gate-specific completion evidence found. Live roadmap remains planned/not started.

### CLD-ADM-HARNESS-001 — Deterministic Cloud test infrastructure

- Purpose: Make every safety claim reproducible before integration acceptance.
- Required before: `BEFORE_FIRST_REAL_CLOUD_AUDIO_UPLOAD`, `BEFORE_CLOUD_RUNTIME_INTEGRATION`, `BEFORE_INTERNAL_ALPHA_ACCEPTANCE`, `BEFORE_PUBLIC_RELEASE`
- Owner/domain: QA / Android / Backend
- Roadmap/dependency lane: `7.3C`, `12.1C`
- Gate dependencies: `CLD-ADM-ADMISSION-001`
- Current status: `NOT_RUN`; blocking: `true`.
- Blocking reason / missing evidence: Gherkin describes intended behavior; Cloud step bindings/harness are not implemented. Prior synthetic VPN host work is not Alpha Cloud runtime acceptance.
- Next action: 7.3C, 12.1C: produce and review the required evidence; Gherkin describes intended behavior; Cloud step bindings/harness are not implemented. Prior synthetic VPN host work is not Alpha Cloud runtime acceptance.

Acceptance criteria:

1. Control network, clock, provider adapter, upload outcomes, timeouts, retries, consent, authorization, job identity and results
2. Control app restart and device restart abstraction where feasible; use later real platform tests for unsupported abstractions
3. Observe bytes, requests, prompt counts, versions, edits, activation, deletion and cost effects
4. Bind approved Gherkin cases and new gate cases to deterministic executable tests; CI critical suites cannot silently skip or retry green

Required evidence:

- Harness/step bindings, reproducible fixture manifest and exact-commit CI run with per-case results

Current repository evidence / requirement authority:

- [docs/product/DORA_ASR_USER_SCENARIOS_V0_1.feature](https://github.com/Monumentogram/DORA/blob/1107d4d9eef80691372800da0a17e609eba81957/docs/product/DORA_ASR_USER_SCENARIOS_V0_1.feature), section/scenario: Background; all scenarios.
- [docs/DORA_MVP1_IMPLEMENTATION_BACKLOG.md](https://github.com/Monumentogram/DORA/blob/1107d4d9eef80691372800da0a17e609eba81957/docs/DORA_MVP1_IMPLEMENTATION_BACKLOG.md), section/scenario: POC-VPN-001.

### CLD-ADM-UX-001 — Consent and operational UX validation

- Purpose: Make sending, waiting, failure and cancellation understandable.
- Required before: `BEFORE_FIRST_REAL_CLOUD_AUDIO_UPLOAD`, `BEFORE_INTERNAL_ALPHA_ACCEPTANCE`, `BEFORE_PUBLIC_RELEASE`
- Owner/domain: Product / Android / Accessibility / QA
- Roadmap/dependency lane: `9.3C`, `11.1C`
- Gate dependencies: `CLD-ADM-PRIVACY-001`, `CLD-ADM-CONSENT-RUNTIME-001`, `CLD-ADM-RETENTION-001`, `CLD-ADM-HARNESS-001`
- Current status: `NOT_RUN`; blocking: `true`.
- Blocking reason / missing evidence: No Cloud consent/status UX implementation or validation.
- Next action: 9.3C, 11.1C: produce and review the required evidence; No Cloud consent/status UX implementation or validation.

Acceptance criteria:

1. Before consent show actual destination, sent artifacts, purpose, retention, revocation and deletion limits with approved disclosure version
2. Present recording-level/batch policies without repeated ASK or blocked local recording; explicit manual action covers only intended set
3. Honest pending/uploading/processing/failed/retry/cancel/remote-delete states; no jargon required
4. Critical consent/review/delete flows accessible in RU/EN, with text/state cues and supported input/scaling tests
5. Before broader Alpha release verify scope comprehension; no accidental opt-in

Required evidence:

- Approved copy-to-runtime binding, consent comprehension and accessibility/functional evidence

Current repository evidence / requirement authority:

- Baseline ADR-0009 and ASR product/data contracts define requirements only; no gate-specific completion evidence found. Live roadmap remains planned/not started.

### CLD-ADM-HISTORY-001 — Active-version history and search

- Purpose: Keep derived views consistent with user-approved text.
- Required before: `BEFORE_INTERNAL_ALPHA_ACCEPTANCE`, `BEFORE_PUBLIC_RELEASE`
- Owner/domain: Android / Data / Search
- Roadmap/dependency lane: `10.3C`
- Gate dependencies: `CLD-ADM-MERGE-001`, `CLD-ADM-DELETE-001`, `CLD-ADM-DATA-RUNTIME-001`
- Current status: `NOT_RUN`; blocking: `true`.
- Blocking reason / missing evidence: No version-aware Cloud history/search integration evidence.
- Next action: 10.3C: produce and review the required evidence; No version-aware Cloud history/search integration evidence.

Acceptance criteria:

1. Atomic active-pointer changes invalidate/rebuild search and derived views against the same revision
2. Historical versions/edit provenance remain available under deletion policy
3. Cloud failure leaves current history/search unchanged; deleted or rejected text cannot reappear in index
4. Crash/restart while reindexing is reconciled without losing user edits

Required evidence:

- Version switch, concurrent edit, deletion and index reconciliation tests

Current repository evidence / requirement authority:

- Baseline ADR-0009 and ASR product/data contracts define requirements only; no gate-specific completion evidence found. Live roadmap remains planned/not started.

### CLD-ADM-EXPORT-001 — Active-version export and provenance

- Purpose: Keep Cloud consent separate from deliberate export.
- Required before: `BEFORE_INTERNAL_ALPHA_ACCEPTANCE`, `BEFORE_PUBLIC_RELEASE`
- Owner/domain: Android / Privacy / QA
- Roadmap/dependency lane: `11.3C`
- Gate dependencies: `CLD-ADM-HISTORY-001`, `CLD-ADM-MERGE-001`
- Current status: `NOT_RUN`; blocking: `true`.
- Blocking reason / missing evidence: No Cloud active-version export acceptance evidence.
- Next action: 11.3C: produce and review the required evidence; No Cloud active-version export acceptance evidence.

Acceptance criteria:

1. Export accepted active/merged text and relevant provenance without losing corrections
2. Preserve existing export consent/temp-copy rules; Cloud recognition consent is not permission to export
3. Source-unavailable state truthful; snapshot is revision-consistent during concurrent edits
4. Do not change historical export authority or claim control of external copies

Required evidence:

- Version-aware export fixtures and existing export-contract regression evidence

Current repository evidence / requirement authority:

- Baseline ADR-0009 and ASR product/data contracts define requirements only; no gate-specific completion evidence found. Live roadmap remains planned/not started.

### CLD-ADM-OPERATIONS-001 — Operational stop, recovery and change control

- Purpose: Keep service failures and later configuration changes fail-closed.
- Required before: `BEFORE_FIRST_REAL_CLOUD_AUDIO_UPLOAD`, `BEFORE_INTERNAL_ALPHA_ACCEPTANCE`, `BEFORE_PUBLIC_RELEASE`
- Owner/domain: Operations / Backend / Security
- Roadmap/dependency lane: `18.1C`, `18.2C`, `11.1C`
- Gate dependencies: `CLD-ADM-RETENTION-RUNTIME-001`, `CLD-ADM-COST-001`, `CLD-ADM-OBSERVABILITY-001`, `CLD-ADM-ADAPTER-001`
- Current status: `NOT_RUN`; blocking: `true`.
- Blocking reason / missing evidence: No Cloud operational deployment/runbook or recovery drill evidence.
- Next action: 18.1C, 18.2C, 11.1C: produce and review the required evidence; No Cloud operational deployment/runbook or recovery drill evidence.

Acceptance criteria:

1. Named operational owner and runbook can disable new uploads/dispatch, revoke credentials and stop cost amplification without breaking recording or Local ASR
2. Restore/reconciliation preserves consent revocation, tombstones, job idempotency and edit provenance; backup policy matches disclosure
3. Provider/model/API/region/retention/terms changes invalidate affected admissions pending re-review; no silent failover to unadmitted service
4. Service/storage outage, capacity exhaustion and incident handling preserve existing user data and truthful pending status

Required evidence:

- Synthetic stop/recovery/restore drills, change-invalidation record and escalation/runbook review

Current repository evidence / requirement authority:

- Baseline ADR-0009 and ASR product/data contracts define requirements only; no gate-specific completion evidence found. Live roadmap remains planned/not started.

### CLD-ADM-SUPPLY-001 — Dependency and deployment provenance

- Purpose: Admit exact server/client components with controlled updates.
- Required before: `BEFORE_FIRST_REAL_CLOUD_AUDIO_UPLOAD`, `BEFORE_CLOUD_RUNTIME_INTEGRATION`, `BEFORE_INTERNAL_ALPHA_ACCEPTANCE`, `BEFORE_PUBLIC_RELEASE`
- Owner/domain: Engineering / Security / Release
- Roadmap/dependency lane: `18.1C`, `7.3C`
- Gate dependencies: `CLD-ADM-ADMISSION-001`, `CLD-ADM-PROVIDER-001`
- Current status: `OPEN`; blocking: `true`.
- Blocking reason / missing evidence: No Cloud implementation dependency/build/deployment inventory is admitted.
- Next action: 18.1C, 7.3C: produce and review the required evidence; No Cloud implementation dependency/build/deployment inventory is admitted.

Acceptance criteria:

1. Record exact server/client/adapter build/configuration/dependency identities, licenses and applicable vulnerability review
2. Environment separation, least-privilege deployment and rollback preserve schema/consent/tombstone compatibility
3. No unapproved SDK/model/native dependency is admitted by this contract; provider/model drift triggers re-admission

Required evidence:

- Pinned dependency/SBOM/license/security record and reproducible build/deployment review

Current repository evidence / requirement authority:

- Baseline ADR-0009 and ASR product/data contracts define requirements only; no gate-specific completion evidence found. Live roadmap remains planned/not started.

### CLD-ADM-EXIT-001 — Cloud Alpha exit acceptance

- Purpose: Collect all fourteen user outcomes on an exact admitted build.
- Required before: `BEFORE_INTERNAL_ALPHA_ACCEPTANCE`, `BEFORE_PUBLIC_RELEASE`
- Owner/domain: QA / Product Owner / Technical lead
- Roadmap/dependency lane: `12.1C`, `12.4C`
- Gate dependencies: `CLD-ADM-BACKGROUND-001`, `CLD-ADM-LOCAL-001`, `CLD-ADM-HISTORY-001`, `CLD-ADM-EXPORT-001`, `CLD-ADM-SECURITY-001`, `CLD-ADM-OPERATIONS-001`, `CLD-ADM-SUPPLY-001`, `CLD-ADM-UX-001`, `CLD-ADM-OFFLINE-001`, `CLD-ADM-FAILURE-001`
- Current status: `NOT_RUN`; blocking: `true`.
- Blocking reason / missing evidence: All fourteen integrated Cloud Alpha exit conditions remain unproven; runtime tests NOT_RUN.
- Next action: 12.1C, 12.4C: produce and review the required evidence; All fourteen integrated Cloud Alpha exit conditions remain unproven; runtime tests NOT_RUN.

Acceptance criteria:

1. All mandatory internal Alpha gates satisfied and provider/model admitted; privacy/retention resolved
2. All fourteen approved product exit conditions have attributable passing execution evidence
3. Deterministic critical tests and applicable platform integration tests pass; no unauthorized upload path or unresolved blocking safety finding
4. Accepted scope/devices/limitations and exact build/configuration/provider/model/evidence recorded; docs or Local PoC never substitute for runtime

Required evidence:

- Complete fourteen-row exit ledger, deterministic test run, platform scope evidence and signed acceptance decision

Current repository evidence / requirement authority:

- Baseline ADR-0009 and ASR product/data contracts define requirements only; no gate-specific completion evidence found. Live roadmap remains planned/not started.

### CLD-ADM-RELEASE-001 — Public release admission

- Purpose: Prevent bounded Alpha closure becoming public-release authorization.
- Required before: `BEFORE_PUBLIC_RELEASE`
- Owner/domain: Release / Product / Legal / Security
- Roadmap/dependency lane: `19.1`, `19.2`
- Gate dependencies: `CLD-ADM-EXIT-001`
- Current status: `OPEN`; blocking: `true`.
- Blocking reason / missing evidence: Public release evidence and approvals are outside 6.2C and absent.
- Next action: 19.1, 19.2: produce and review the required evidence; Public release evidence and approvals are outside 6.2C and absent.

Acceptance criteria:

1. Revalidate Cloud admissions against release scope/provider/regions and current applicable distribution/privacy requirements
2. Complete full release test strategy, support/device matrix, security/privacy decisions, signing/update/rollback and store disclosures
3. No critical Cloud safety gate waived by Alpha closure; accepted limitations and retention/data-use claims remain accurate

Required evidence:

- Stage 19 release decision, Tier C evidence, signing/update/rollback and disclosure records

Current repository evidence / requirement authority:

- Baseline ADR-0009 and ASR product/data contracts define requirements only; no gate-specific completion evidence found. Live roadmap remains planned/not started.

## Fourteen Alpha exit conditions

Every row is NOT_RUN. Each row is owned by QA plus the linked domain owners; Product Owner accepts.
The evidence listed is required future evidence, not a claim that it exists.

| Exit | Approved condition | Gate/evidence ownership | Roadmap | Required proof | Status |
|---|---|---|---|---|---|
| CLOUD-EXIT-01 | Cloud-only operation | CLD-ADM-LOCAL-001, CLD-ADM-OFFLINE-001, CLD-ADM-AUTH-001, CLD-ADM-UPLOAD-001, CLD-ADM-ADAPTER-001 | 9.1C, 9.2C | Online recognition without Local installation | NOT_RUN |
| CLOUD-EXIT-02 | Optional Local package | CLD-ADM-LOCAL-001 | 9.1C | Install/update/remove/integrity states while recording remains usable | NOT_RUN |
| CLOUD-EXIT-03 | Authorized online Cloud ASR | CLD-ADM-CONSENT-RUNTIME-001, CLD-ADM-AUTH-001, CLD-ADM-UPLOAD-001, CLD-ADM-ADAPTER-001 | 9.2C, 18.1C | Authorized original-audio result with zero unauthorized bytes | NOT_RUN |
| CLOUD-EXIT-04 | Offline recordings never lost | CLD-ADM-OFFLINE-001, CLD-ADM-DATA-RUNTIME-001 | 8.2C, 11.2C | No-model/no-network/restart/storage-fault preservation | NOT_RUN |
| CLOUD-EXIT-05 | Installed Local offline ASR | CLD-ADM-LOCAL-001 | 9.1C, 11.2C | Integrated Local without account/network/GMS in admitted device scope | NOT_RUN |
| CLOUD-EXIT-06 | Original audio reprocessable later | CLD-ADM-UPLOAD-001, CLD-ADM-RESULT-001, CLD-ADM-OFFLINE-001 | 8.2C, 9.2E | Exact source version reprocessed; absent audio refuses text substitution | NOT_RUN |
| CLOUD-EXIT-07 | User edits survive Local-to-Cloud | CLD-ADM-MERGE-001, CLD-ADM-DATA-RUNTIME-001 | 9.4C | Clean/ambiguous/concurrent mapping and explicit acceptance | NOT_RUN |
| CLOUD-EXIT-08 | All three consent policies | CLD-ADM-CONSENT-RUNTIME-001, CLD-ADM-QUEUE-001, CLD-ADM-UX-001 | 9.2D, 9.3C, 11.1C | Policy/prompt/batch/revocation assertions | NOT_RUN |
| CLOUD-EXIT-09 | Cloud failures preserve existing data | CLD-ADM-FAILURE-001, CLD-ADM-RESULT-001, CLD-ADM-DELETE-001 | 9.2C, 9.2E | Four preservation invariants across complete failure matrix | NOT_RUN |
| CLOUD-EXIT-10 | Queue survives restart | CLD-ADM-QUEUE-001, CLD-ADM-BACKGROUND-001 | 9.2D, 11.2C | App/device restart with authorization and DEFERRED intact | NOT_RUN |
| CLOUD-EXIT-11 | No unauthorized upload | CLD-ADM-CONSENT-RUNTIME-001, CLD-ADM-AUTH-001, CLD-ADM-UPLOAD-001, CLD-ADM-SECURITY-001 | 11.1C, 12.1C | All dispatch/chunk/retry/revocation/cross-owner paths reject invalid authority | NOT_RUN |
| CLOUD-EXIT-12 | Provenance recorded | CLD-ADM-DATA-RUNTIME-001, CLD-ADM-RESULT-001, CLD-ADM-HISTORY-001, CLD-ADM-EXPORT-001 | 7.2D, 10.3C, 11.3C | Source/action/attempt/provider/model/result/edit/active lineage | NOT_RUN |
| CLOUD-EXIT-13 | Usage and cost measurable | CLD-ADM-COST-001, CLD-ADM-OBSERVABILITY-001 | 18.2C | Duration/bytes/requests/usage/cost reconciliation and quota controls | NOT_RUN |
| CLOUD-EXIT-14 | Deterministic critical tests pass | CLD-ADM-HARNESS-001, CLD-ADM-FAILURE-001, CLD-ADM-SECURITY-001, CLD-ADM-EXIT-001 | 7.3C, 12.1C, 12.4C | Exact-build reproducible run with no missing/skipped critical cases | NOT_RUN |

## Deterministic roadmap sequence

Task numbers locate ownership; they do not force a later-numbered prerequisite to wait until after
the consumer. No duplicate implementation tasks are created. Every ID below is an existing roadmap
lane; 6.2D was independently observed in the live Sheet.

1. **6.1**: approve actual Alpha functions/devices/limitations (SCOPE). This is the single immediate
   next task; it is not executed in 6.2C.
2. **6.2**: disposition applicable non-Cloud evidence/gaps (GAPS), consuming bounded Stage 5.
3. **11.1C policy/design slice + BE-LEGAL/CONSENT/DELETE/AUTH**: resolve PRIVACY, RETENTION and
   CONTROL. Candidate-specific facts may be gathered with 6.2D; approvals must bind the final choice.
4. **6.2D**: freeze EVALUATION then admit PROVIDER per approved AI function; reuse 18.1 and
   BE-PROVIDER-001/002 ownership. No candidate chosen in this contract. Final 6.2D cannot close
   without applicable privacy/retention decisions; no circular requirement for production runtime.
5. **6.3**: close ADMISSION from the exact minimum predecessor set and exact-base CI. No merge implied.
6. **7.2C/D/E + 7.3C**: provider/API ports, versioned storage/edit anchors, identity/ownership and
   deterministic harness. Produce implementation evidence before integration acceptance.
7. **8.2C/8.4C**: original-audio lifecycle and logical recording/chunk binding; preserve Recovery.
8. **18.1C/18.2C**: DORA backend/storage/worker/adapter plus quota/observability/operations. These
   service lanes must supply dependencies before 9.2C/12, not be postponed beyond Alpha acceptance.
9. **9.1C/9.2C/9.2D/9.2E/9.3C/9.4C**: optional Local, upload, durable queue, reprocess/result
   validation, consent/status UX and edit-preserving review. Synthetic tests precede real-audio gates.
10. **10.3C/11.1C runtime/11.2C/11.3C**: active history/search, privacy/deletion enforcement,
    background/reconnect and active-version export. First real audio waits for its entire gate set.
11. **12.1C/12.4C**: deterministic matrix and all fourteen exits on exact admitted build/provider.
12. **19.1/19.2**: expanded public-release evidence, signing/update/rollback and disclosures.

## Coverage and second-pass safety review

The full route was reviewed twice: authorization -> temporary authority -> objects -> worker ->
provider -> result -> edits/active view -> deletion/backup/operations. Beyond the requested categories,
the matrix explicitly adds CRYPTO (key custody and transport), OPERATIONS (stop/restore/change
invalidation), SUPPLY (dependency/deployment provenance), HISTORY/EXPORT (derived consumers), UX
(disclosure/accessibility), and EVALUATION (prospective thresholds and lawful test scope).

- Stolen/still-valid upload authority after revocation: AUTH, CONSENT-RUNTIME, UPLOAD, SECURITY.
- Untrusted source URL, oversized/malformed audio, forged callback or schema: UPLOAD, SECURITY, RESULT.
- Unknown outcome, concurrent retries/edits or repeated costs: API, FAILURE, RESULT, MERGE, COST.
- Provider/model/region/terms drift or silent fallback: PROVIDER, PRIVACY, ADAPTER, OPERATIONS.
- Deleted data restored from backup/index or arriving late: RETENTION, RETENTION-RUNTIME, DELETE, HISTORY, OPERATIONS.
- Plaintext/key/log leakage: CRYPTO, SECRETS, OBSERVABILITY, SECURITY.
- No Local model, no network, full storage or service outage: OFFLINE, QUEUE, LOCAL, BACKGROUND, OPERATIONS.

No runtime, UI, schema, worker, adapter, endpoint or credential is implemented/provisioned. No Cloud
API, benchmark, ASR/device/Recovery campaign is run. Approval of this contract is admission-policy
authority only. All currently blocking IDs and counts are reproducible from the JSON gate records.
