# Dora MVP 1 — PoC Gates and Result Contract

Task: `POC-GATES-001`
Gate set: `stage0-v0.1`
Schema: `benchmark-result.schema.json`
Updated: 12 August 2026
Approval effective: 4 August 2026
Owner: **Project owner**
Status: **Approved for Stage 0 — unresolved thresholds remain Proposed**

## 1. Purpose

Every Stage 0 PoC must answer one bounded hypothesis with reproducible evidence. This document supplies a common result shape and the Stage 0 gate set approved by `OD-05`. It does not run a PoC, admit a dependency, approve a production feature or create a product claim.

The predicates with explicit values or invariants in section 6 are `Approved` only for Stage 0. The six undefined numeric thresholds listed in section 7 remain `Proposed`; approval of `stage0-v0.1` did not invent or approve them. A gate instance that depends on one of those values cannot produce `PASS` until a value is frozen in a new pre-run decision/version.

## 2. Gate rules

1. Define the hypothesis, success gate, failure gate and fallback before the first measured run.
2. Record exact commit, application/harness version, device profile, OS/API/ABI, fixture digest and environment.
3. Do not average away a failing mandatory device/language/noise slice.
4. A missing required device, fixture, license or metric makes the result `INCONCLUSIVE`, not `PASS`.
5. A triggered approved failure gate makes the result `FAIL`, even when the mean looks good.
6. `PASS` is allowed only when every mandatory gate used for the verdict is `Approved`, every required slice ran, all success predicates passed and no failure predicate triggered.
7. Comparison against a `Proposed` gate is reported as observation only. Use `INCONCLUSIVE` until approval.
8. Failed or inconclusive PoC evidence is retained and linked; do not rerun silently until green.
9. Every result names a fallback or scope reduction.
10. Passing a PoC does not admit its code, native library, model or dataset to production. Admission needs a separate ADR and artifact gates.

## 3. Result statuses

| Status | Meaning |
|---|---|
| `NOT_RUN` | Result shell exists but no valid execution completed. |
| `INCONCLUSIVE` | Evidence exists, but a required gate/device/input/approval is missing or invalid. |
| `PASS` | All mandatory approved gates pass and no approved failure gate triggers. |
| `FAIL` | At least one approved failure gate triggers or a critical invariant is violated. |

Recommendation is separate from result:

- `GO` — proceed to the next bounded experiment or admission review;
- `ITERATE` — change harness/implementation and repeat without changing the gate after seeing results;
- `FALLBACK` — use the documented alternate design or narrow support;
- `NO_GO` — stop the candidate/path;
- `BLOCKED` — obtain a missing owner/legal/license/device decision first.

## 4. Preconditions for every run

- clean, named PoC branch and exact 40-character commit SHA;
- no production application ID/signing or unrelated feature code;
- input is synthetic or referenced by an approved consent-governed manifest;
- no real meeting/private audio in Git, LFS or Actions artifacts;
- until controlled non-public storage is configured, input is synthetic and no raw trace/audio is retained;
- device availability is confirmed for the run, without serial/account/private path in the report;
- for the first exploratory capture run, the owner's physical phone is connected and its model, Android API, firmware/build, ABI and RAM are discovered automatically before measurement;
- power, battery, thermal, storage and network starting state are recorded where relevant;
- third-party artifacts have exact version/digest and at least `EVALUATION_APPROVED` rights;
- success/failure gates are frozen for the campaign;
- evidence destination and cleanup/retention are known;
- forbidden logging/content assertions are enabled.

## 5. Machine-readable result fields

The JSON Schema requires the following top-level areas:

| Required area | Content |
|---|---|
| `pocId` | Backlog ID such as `POC-CAPTURE-001`. |
| `applicationVersion` | PoC harness/application version; not a production version claim. |
| `commit` | Exact lowercase Git SHA-1 currently used by the repository. |
| `device` | D-profile, sanitized model, physical/emulator, ABI, RAM and firmware/build. |
| `androidApi` | Integer API level used for the run. |
| `duration` | Planned and actual seconds plus whether the duration completed. |
| `inputData` | Classification, manifest/fixture IDs, digests, language/acoustic slices and consent reference when needed. |
| `metrics` | Typed measurement name/value/unit/method/slice/sample count. |
| `successGates` | Predicates that must pass, each with `Proposed` or `Approved`. |
| `failureGates` | Predicates that trigger failure, each with status and observed outcome. |
| `result` | `NOT_RUN`, `INCONCLUSIVE`, `PASS` or `FAIL`, with rationale and evaluated gate-set version. |
| `errors` | Stable redacted error code, stage, count and retryability; never content. |
| `battery` | Start/end charge, mWh when available, baseline ratio, power state and source. |
| `temperature` | Start/end/max temperature when safely available and Android thermal states. |
| `memory` | Peak PSS/RSS/native heap and OOM/trim facts. |
| `fileSizes` | Named output bytes and optional digest; no private absolute path. |
| `licenses` | Exact evaluated code/model/data artifact, digest, license and review state. |
| `limitations` | Unsupported slices, missing evidence and threats to validity. |
| `recommendation` | Decision, rationale, fallback and owner action. |
| `evidenceFiles` | Sanitized repository path or opaque controlled-store locator, digest, classification and retention. |

Optional measurements stay optional because not every PoC has audio, battery or native artifacts. The area itself remains required and uses `notApplicableReason` when a measurement is genuinely not applicable.

## 6. Approved Stage 0 gate set v0.1

Every fully specified predicate below has status **Approved for Stage 0** under `OD-05`. The `Status exception` column identifies the only unresolved parts; they remain **Proposed** and must be separate gate instances in result JSON.

| PoC | Approved success gate | Approved failure gate | Status exception | Required fallback |
|---|---|---|---|---|
| `POC-CAPTURE-001` | Valid-state Start and Stop/finalize success ≥99.5% across the planned campaign; zero corruption or whole-session loss; recording remains visible; required one-hour D1–D5 slices complete. | Any whole-session loss, reproducible corruption/unexplained sample gap, hidden recording state, OS/OEM termination on an intended supported class, or energy above an approved capture gate. | Exact numeric sample-gap tolerance is `Proposed`. | Change buffer/checkpoint design or narrow the tested support matrix; never use a hidden workaround. |
| `POC-VAD-001` | Boundary error ≤±0.5 s after the VAD silence decision; 100% timer cancellation when speech resumes before 90 s; onset preserved by pre-roll; physical part ≤600 s plus one frame with 1.5–2.0 s overlap. | False boundaries on required fixtures, wall-clock timer, resume-at-89.9 failure, onset loss, cap failure or missed real-time frame deadline on D1. | None. | Version/tune profiles; compare another VAD/rules while preserving the 90-s product rule. |
| `POC-RECOVERY-001` | 100% authenticated oracle-equal contiguous recovery with `0 <= C <= R <= A`, exactly zero committed-byte loss and tail loss ≤5.000 s on every valid hard kill; durable ninth-family run-key confirmation before publication; exact ordered eight-class KEY taxonomy; 12 strata, 120 base attempts/candidate, ≥100 valid and ≥8 valid/stratum; 46 mandatory fault rows; Phase A 184 injections and full physical 138 injections as separate profiles; zero duplicate/missing deterministic processing intents; no automatic microphone restart. Full PASS additionally requires complete physical D1/D2/D5. | Any committed byte becomes unreadable/unauthenticated, key confirmation/bootstrap is missing/corrupt/mismatched, plaintext validation is performed before decrypt, any returned byte differs from the oracle, any valid tail exceeds five seconds, unsafe path/symlink or silent deletion occurs, DB/file split-brain is unrecoverable, a candidate failure is hidden as invalid, or mic auto-restarts. | Prospective Gate Set/protocol `stage0-v0.6` inherits all unchanged SHA-256-pinned v0.5/v0.4/v0.3 semantics and replaces only effective `KEY-04` with an exact decrypt authentication/AAD failure-only oracle returning `KEY_UNAVAILABLE_KEY_MISMATCH`; successful decrypt malformed/wrong plaintext remains `KCF-07` → `CORRUPT_KEY_CONFIRMATION`. The active matrix has exactly 46 unique IDs and one `KEY-04`; prospective policy and exact governance evidence are closed, while actual graph, implementation verification and distinct accountable Engineering/Security review remain required; v0.1–v0.5 are 15 unchanged superseded audit artifacts; `implementationAllowed=false`; `executionAllowed=false`. | Compare durable-one-segment-lookahead Streaming AEAD with sealed five-second AEAD microfiles/authenticated manifest. Fifteen/30-second variants are observation/post-failure fallbacks only and can never PASS. |
| `POC-ASR-001` | Normalized WER: RU ≤20%, EN ≤18%, mixed ≤28%, noisy/speakerphone ≤35% or explicit low-confidence behavior; timestamp median error ≤500 ms and p95 ≤1.5 s; exact artifact loads/runs in required 16-KiB environment; no OOM/severe thermal. | Required slice exceeds quality gate without honest fallback, artifact fails license/digest/ABI/16-KiB gate, repeated OOM/severe thermal, or no D1 fallback completes reliably. | Exact RTF by D1/D2/D3 tier and maximum PSS/native heap are `Proposed`. | Lighter Vosk/sherpa candidate, process while charging, or separately consented faster-whisper route. |
| `POC-DIAR-001` | Clean 2–4 speaker DER ≤20%; noisy/remote/overlap DER ≤35% or review-flag recall ≥90%; speaker-count MAE ≤0.5 clean/≤1.0 noisy; corrections persist; exact weights have approved evaluation/redistribution path for the chosen route. | DER exceeds its approved gate, overlap is forced to a wrong speaker, correction is lost on rerun, or weight terms/provenance remain unclear. | Maximum acceptable correction operations/minute is `Proposed`. | Server route with consent, local speaker-change hints plus manual labels, or omit exact speaker assignment. |
| `POC-BATTERY-001` | Capture-only overhead ≤1.25× minimal AudioRecord+FGS baseline on each supported physical profile; zero dropped frames and no `SEVERE` thermal state; ≥3 controlled repeats per required slice. | Sustained severe thermal, capture gap, failure of the approved relative baseline gate, invalid uncontrolled comparison or heavy processing compromises capture. | Absolute battery-drain threshold when mWh is unavailable is `Proposed`. | Never run heavy ML during capture; defer to charging; tune fsync/buffer; narrow supported matrix. |
| `POC-DECISION-001` | Final-decision precision ≥0.90 and recall ≥0.80; revision-link precision ≥0.85 and recall ≥0.75; ≥99% auto items have valid source and unsupported factual claims <1%; manual truth survives rerun. | Wrong confirmed task crosses precision gate, unconfirmed late phrase routinely overrides final, invalid source is applied, or user-owned fields are overwritten. | None. | Require review for all decision/task candidates; use rules-only chronology or server only for ambiguity. |
| `POC-SEARCH-001` | p95 <200 ms and p99 <500 ms on the 10k-conversation/1M-transcript reference DB; correct filters/source, safe adversarial query handling and deterministic rebuild. | Latency/storage/update gate fails, query operator injection/crash occurs, index loses canonical mapping or migration/rebuild is non-deterministic. | None. | Per-entity FTS, pagination/ranking changes; FTS5 only with migration evidence; no vector DB by default. |
| `POC-OFFLINE-001` | Zero forbidden network attempts; approved local capture/storage/history/search/tasks/copy/export flows work without account/GMS/network; installed local model works; queued cloud work resumes exactly once. | Login, GMS, DNS, remote config or provider availability blocks local core; retry loop drains battery; reconnect duplicates/corrupts state. | None. | Remove dependency; ship safe local defaults/catalog snapshot and an approved side-load path. |
| `POC-VPN-001` | Eventual completion after valid connectivity; zero duplicate job/result/billing; exact checksum; no automatic region/provider switch; local flow unaffected. | Duplicate cost/result, corrupted upload, consent/region bypass, data sent to wrong endpoint or infinite retry. | None. | Restart under same idempotency key after explicit user action, provider circuit breaker or local-only processing. |

`OD-06` does not weaken the D1–D7 gates. The first campaign may execute only an exploratory slice on the owner's one physical phone after automatic inventory. A triggered approved failure gate still yields `FAIL`; otherwise the missing required matrix slices make the overall result `INCONCLUSIVE`. A global D1–D7 `PASS` and device-support claim are forbidden.

## 7. Unresolved thresholds — Proposed

The following remain explicitly `Proposed` after `OD-05`. They are deliberately not invented in v0.1 and therefore make the affected gate verdict `INCONCLUSIVE` until a value is approved before a new measured campaign:

- exact acceptable RTF by D1/D2/D3 ASR tier;
- maximum peak PSS/native heap by device profile;
- acceptable diarization correction operations per minute;
- acceptable absolute battery drain when mWh is unavailable;
- capture sample-gap tolerance beyond the invariant that unexplained dropped intervals are unacceptable;
- minimum evidence-store retention for raw performance traces.

The first campaign may measure these as baseline, but a threshold created after seeing the same result cannot retrospectively turn it into `PASS`. Freeze a new version and rerun.

## 8. Evidence requirements

### 8.1 Public evidence

Allowed in Git:

- completed JSON result conforming to the schema;
- Markdown summary with aggregate metrics and categorical errors;
- deterministic synthetic fixture manifest/generator source when in scope;
- sanitized charts/tables with no raw content or personal/device identifiers;
- exact code/model/license metadata already approved for public disclosure.

### 8.2 Controlled evidence

Keep outside public Git:

- raw audio, real/purpose-recorded transcript or consent evidence;
- raw Perfetto/Batterystats/crash dump before privacy review;
- device serial, local path, account identifier and network address;
- gated model terms, private artifact, provider credentials or presigned URL.

The JSON report references controlled evidence with an opaque locator and SHA-256. A public evidence entry must set `containsPersonalData` and `containsSecret` to `false`. Under `OD-08`, raw traces or audio cannot be retained until that controlled store is configured; until then, runs use synthetic inputs and publish only sanitized reports plus aggregate metrics.

## 9. Measurement discipline

- Use a fixed fixture/manifest digest and predeclared slices.
- Use monotonic time for durations and boundaries; wall time only labels the run.
- Record warm-up, number of repetitions, aggregation and measurement source.
- Battery comparison uses the same device, brightness, radio, starting temperature and signal; report all repeats.
- Report p50/p95/p99 only with sample count and method.
- Report WER normalization, DER collar/overlap policy and protected evaluation split.
- Preserve failed trials and error categories; do not drop an outlier without a predeclared invalidation reason.
- If instrumentation itself changes the metric materially, record the limitation and rerun with a validated method.

## 10. Error and limitation rules

- Error messages are stable codes plus redacted summaries; no transcript/audio/path/token.
- A harness crash is not an application failure unless the root cause is in the candidate, but it invalidates the run until classified.
- Missing metrics use `null` plus `notApplicableReason`/limitation; do not use zero.
- A device that was unavailable remains a limitation and blocks a full matrix verdict.
- A proposed threshold remains visibly `Proposed` in both success and failure gate arrays.
- Any manual deviation from the protocol is listed under limitations and cannot be hidden in free text.

## 11. License reporting

Every external artifact used by the run appears in `licenses`, including runtime code, native library, model weight, dataset and conversion tool where relevant. An empty array is valid only when the PoC uses repository-owned code and fully synthetic inputs with no external runtime artifact.

`licenseReviewState` values are `PROPOSED`, `EVALUATION_APPROVED`, `ADMISSION_REVIEW`, `ADMITTED`, `REJECTED` and `REVOKED`. Stage 0 execution requires at least `EVALUATION_APPROVED`; this Stage 0A document approves none.

## 12. Approval and versioning

`OD-05` was approved by the Project owner on 4 August 2026 and is recorded in `DEC-020`. Every result must copy `Approved` only into the fully specified gate instances from section 6 and keep every section 7 instance `Proposed`.

1. Never edit a completed result in place—issue a new result/version.
2. Create `stage0-v0.2` for threshold changes and state whether a rerun is required.
3. Approve any previously unresolved threshold before the campaign whose data will be judged by it.
4. Do not use an exploratory one-phone result to approve D1–D7 support.

Critical zero-loss, source-validity, consent and no-silent-overwrite invariants cannot be weakened merely to make a candidate pass.

## 13. Stage 0A exit

The common format and all fully specified Stage 0 gates are approved. `POC-GATES-001` governance preparation is complete; section 7 remains `Proposed` by owner instruction. No PoC was run by this change, and approval does not authorize production code.
