# Dora MVP 1 — Omi upstream reuse and hazard audit task

Task: `GOV-OMI-001`\
Version: `gov-omi-reuse-stage0-v0.1`\
Date: 18 August 2026\
State: `BLOCKED` — task definition and Phase A public-metadata evidence complete; full audit remains gated\
Canonical candidate upstream: `https://github.com/BasedHardware/omi`\
Policy boundary: `DORA_MVP1_IP_ASSET_POLICY.md`; this is an engineering control, not legal advice

## 1. Purpose

`GOV-OMI-001` defines a bounded, reproducible upstream-intelligence audit intended to avoid
rebuilding commodity work that Omi has already solved while preserving Dora's approved product,
privacy and architecture contracts. The audit must collect not only candidate source and protocol
ideas, but also upstream tests, fixed defects, reverted approaches, operational failure modes and
other evidence that can become Dora regression specifications.

The controlling decision rule is:

> Prefer a reusable or portable upstream solution when it satisfies Dora's contracts and carries
> lower total risk. Build a Dora-specific implementation only when a predeclared test can show a
> material advantage on a Dora differentiator, or when upstream fit, rights, security or
> maintainability is insufficient.

Code style, local familiarity or the ability to rewrite a component are not by themselves evidence
that a Dora implementation would be better.

## 2. Authority and current boundary

The project owner first authorized creation of this separate task definition on 18 August 2026,
then authorized the bounded public-metadata-only Phase A through
`GOV-OMI-PHASE-A-PUBLIC-METADATA-AUDIT-AUTH-20260818-01`. Phase A is complete at immutable Omi
commit `7d99abcc4efb9e46a5853b21fc01289e4b891837` / tree
`85db621ffd5dc5386bcbd7c87713cc69638be7e3`. This document still does not authorize source/blob,
archive, issue/PR body/comment, patch/diff, download, execution, copying, porting or admission.

| Authority | Current value |
|---|---|
| `taskDefinitionAllowed` | `true` |
| `phaseAPublicMetadataAuditAllowed` | `true` |
| `phaseAPublicMetadataAuditCompleted` | `true` |
| `auditExecutionAllowed` | `false` beyond the completed Phase A boundary |
| `upstreamCloneOrArchiveDownloadAllowed` | `false` |
| `upstreamBlobOrSourceRetrievalAllowed` | `false` |
| `issueOrPullRequestBodyCommentRetrievalAllowed` | `false` |
| `sourceCopyOrPortAllowed` | `false` |
| `thirdPartyCodeExecutionAllowed` | `false` |
| `binaryModelAssetDownloadAllowed` | `false` |
| `dependencyAdmission` | `false` |
| `productImplementationAllowed` | `false` |
| `activeStageChanged` | `false` |

Phase A collected only canonical public repository/ref/commit/tree-path, release/tag and issue/PR
index metadata under `PROPOSED`. The five sanitized artifacts are under
`docs/evidence/gov-omi-001/`. The recursive tree was complete, but tags were capped at
`1000/1470` and Pull Requests at `5000/8794`; license bytes, manifest contents and issue/PR
content were not retrieved. Phase A therefore uses only `PROPOSED`, `DEFER`,
`BLOCKED_RIGHTS` and `INSUFFICIENT_EVIDENCE`. Any broader retrieval, controlled evaluation or
execution still requires an exact-artifact rights and security gate. No result may silently move
an artifact to `EVALUATION_APPROVED` or `ADMITTED`.

## 3. Dora invariants that upstream cannot change

Every assessment is subordinate to the authoritative Dora documents. In particular, Omi evidence
cannot change these contracts without a separately accepted DEC or ADR:

- local mode remains usable without account, network, GMS or cloud configuration;
- cloud remains optional and disabled until separate explicit consent;
- recording is initiated explicitly by the user and is microphone-only in MVP 1;
- no passive recording, call capture promise or silent microphone restart is introduced;
- Kotlin/Compose remains the Android product baseline;
- local durable state and user edits remain authoritative;
- model or cloud reprocessing may propose a versioned diff but may not silently overwrite user truth;
- source-grounded transcript, decision revision and task semantics remain Dora-owned contracts;
- encrypted crash recovery, deletion scope, privacy logging and no-private-data-in-public-Git rules
  are not weakened;
- RU/EN, offline/no-GMS, device, battery, accessibility and 16-KiB gates remain evidence-based;
- an Omi PoC, test or production result is upstream evidence, not Dora device/support evidence.

## 4. Audit scope

The future audit inventories the complete top-level Omi repository at one immutable snapshot, then
deep-reviews only surfaces relevant to Dora MVP 1 or a named near-term port. At minimum it covers:

1. repository layout, commit/tree identity, releases, license files, manifests, vendored content,
   submodules/LFS references and direct dependency declarations;
2. mobile audio capture, buffering, PCM/Opus framing, background/lifecycle behavior, route changes,
   local transcription, storage, deletion, offline behavior and Android-specific bridges;
3. backend STT/VAD/diarization, provider selection/fallback, retry, idempotency, job/result contracts,
   summary/task/memory processing, deletion and privacy boundaries;
4. SDK and BLE/device protocols where they inform a replaceable future Dora port;
5. tests, open and closed issues, merged and reverted Pull Requests, release notes, security policy,
   regression fixes and path-specific history for the reviewed surfaces;
6. direct evidence of data loss, unauthorized capture, packet loss, duplicate processing or cost,
   provider exhaustion, process death, migration, deletion, offline/sync and user-truth failures;
7. maintenance activity, dependency footprint and upstream coupling relevant to long-term ownership.

Desktop, iOS, web, personas, marketplace, firmware and hardware receive a top-level classification.
They receive a deep review only when a concrete Dora MVP 1 contract or an explicitly scoped future
port makes them relevant.

## 5. Explicit non-goals

`GOV-OMI-001` does not:

- fork, vendor, clone into or merge Omi with the Dora repository;
- build, run or connect an Omi application, backend, firmware, model or external service;
- download or commit source archives, binaries, model weights, datasets, fonts, images or branding;
- adopt Flutter, Firebase, Firestore, Omi authentication, its memory schema or cloud authority;
- select a production dependency, model, provider, schema, prompt, API or device protocol;
- grant trademark, patent, dataset, model-weight or third-party-asset rights from a root code license;
- create a marketing claim that Dora is more capable, safer or more reliable than Omi;
- modify current Stage 0 PoC authority, unblock production work or change the active stage;
- treat an upstream passing test as a Dora PASS.

## 6. Immutable snapshot and reproducible collection

Phase A recorded the canonical repository, immutable commit/tree, retrieval window, default branch,
exact metadata queries, filters, ordering, pagination, counts and caps. The remaining full-audit
requirements are:

- canonical repository owner/name and URL;
- exact commit SHA and tree SHA resolved from the canonical publisher;
- retrieval timestamp and default branch observed at that time;
- root license bytes/digest and the contents of nested license/NOTICE files, only after separate
  exact-scope retrieval authority and rights disposition;
- repository archive or source-tree digest only if later retrieval is explicitly approved;
- GitHub query strings, filters, sort order, pagination, result counts and any API/search cap;
- every unavailable, deleted, truncated or authentication-gated source as an evidence limitation.

`main`, a branch name, a moving release channel or `latest` may not identify the audited snapshot.
The audit may use canonical immutable GitHub URLs and metadata. It must not mirror upstream content
into Dora merely to make evidence convenient.

## 7. Decision vocabulary

Every reviewed unit receives exactly one non-admission disposition:

| Disposition | Meaning |
|---|---|
| `REUSE_CANDIDATE` | Exact source may be worth reusing behind a Dora port, subject to separate rights, security, tests and admission. |
| `PORT_CANDIDATE` | Behavior or algorithm is useful, but stack/boundary differences require a bounded Dora port and provenance. |
| `LEARN_ONLY` | Bugs, tests, contracts or operational lessons are useful; upstream implementation must not enter Dora. |
| `REJECT` | Evidence shows conflict with Dora invariants, unacceptable risk or no relevant value. |
| `DEFER` | Potential value exists outside the current product/stage; no current module or dependency is created. |
| `BLOCKED_RIGHTS` | Rights or provenance are insufficient for source inspection, copying, modification, evaluation or distribution. |
| `INSUFFICIENT_EVIDENCE` | Available evidence cannot support a responsible reuse decision. |

These labels are research conclusions only. `REUSE_CANDIDATE` and `PORT_CANDIDATE` are not IP
states and never mean `EVALUATION_APPROVED` or `ADMITTED`.

## 8. Meaning of “Dora can do better”

A recommendation to implement a component independently must name the Dora differentiator and the
test that could falsify the recommendation. Acceptable dimensions include:

- committed/tail data loss and crash recovery;
- local/offline/no-GMS completion;
- unauthorized network, account or cloud dependency count;
- preservation of source links and authoritative user edits;
- RU/EN/mixed quality and correction burden;
- latency, memory, battery and thermal behavior on the declared device tier;
- security, privacy, deletion and provenance guarantees;
- dependency/native/ABI/16-KiB and maintenance burden;
- replaceability and total integration/ownership cost.

If no measurable user, safety or lifecycle advantage is identified, the default recommendation is
reuse/port evaluation rather than an independent rewrite. If both candidates remain plausible, the
matrix proposes a bounded PoC with frozen inputs and acceptance gates; it does not start two open-ended
implementations.

## 9. Hazard and regression-test conversion

Each applicable upstream defect, fix, revert or incident is recorded with:

- stable hazard ID and Dora severity rationale;
- upstream issue/PR/commit/test URLs anchored to immutable evidence where available;
- observed failure, trigger, affected versions and upstream disposition;
- whether a fix is merged, reverted, partial, anecdotal or unverified;
- Dora applicability and the invariant that could be violated;
- proposed deterministic synthetic fixture and expected outcome;
- target Dora Test Strategy ID and future backlog/PoC owner;
- privacy/logging constraints and required device/network environment;
- decision to add a test specification, map an existing gate or record non-applicability.

No issue title alone proves a failure or fix. Query caps and missing private incident history are
explicit limitations. Exact upstream test code is not copied until rights permit it; the initial
deliverable is a Dora-owned behavioral regression specification using synthetic data.

Priority review covers at least:

- silent or partial audio/data loss;
- hidden/unauthorized microphone state and broken Stop/delete behavior;
- process death, screen-off, route change and BLE/network interruption;
- malformed, missing, duplicated or reordered audio/transcript events;
- STT/provider quota, timeout, fallback-chain and duplicate-economic-effect failures;
- offline/account/GMS coupling and reconnect/idempotency failures;
- stale model results overwriting user edits or authoritative local state;
- schema migration, cache/index divergence, retention and deletion reconciliation;
- credential, transcript, audio or private metadata leakage;
- dependency, native packaging and unsupported-device regressions.

## 10. Required artifacts

The completed Phase A produced the following sanitized governance evidence:

| Artifact | Required content |
|---|---|
| `docs/evidence/gov-omi-001/upstream-snapshot.json` | Exact repository/commit/tree/license/query provenance and limitations. |
| `docs/evidence/gov-omi-001/license-surface-inventory.json` | Root/nested licenses, manifests, vendored/submodule/LFS surfaces and unresolved rights; no legal conclusion invented. |
| `docs/evidence/gov-omi-001/component-matrix.json` | Relevant components, paths, dependencies, Dora fit, decision, rationale, interface, fallback and future owner. |
| `docs/evidence/gov-omi-001/hazard-register.json` | Reproducible upstream hazards mapped to Dora regression specifications and gates. |
| `docs/evidence/gov-omi-001/audit-report.md` | Bounded findings, shortlist, rejected/deferred areas, evidence gaps, recommended next PoCs and no-admission statement. |

Machine-readable records must use stable IDs, deterministic ordering, explicit nulls for unknown
facts and canonical UTF-8/LF serialization. They contain no source-code blobs, secrets, private
issue content, personal data or unapproved assets.

Artifact existence does not complete the full audit: their Phase A records deliberately retain
`BLOCKED_RIGHTS` and `INSUFFICIENT_EVIDENCE` wherever content or capped history was unavailable.

## 11. Transition to READY

Phase A is complete. The full audit remains `BLOCKED` until all of the following are true in a
later owner-scoped task:

1. the owner explicitly authorizes exact source/license/blob or issue/PR body retrieval boundaries;
2. the exact expected branch and files are restated against current `main`;
3. the canonical snapshot/query method and evidence limits are confirmed;
4. any source retrieval beyond public metadata has an exact IP-policy disposition;
5. reviewer roles are named for any conclusion that would go beyond factual metadata research;
6. no active-stage work or shared branch is displaced.

Suggested future branch: a new `codex/gov-omi-001-*` branch from then-current `main`; the
completed Phase A branch is not continuing authority.

## 12. Definition of Done for the audit

`GOV-OMI-001` may become `DONE` only when all conditions below are substantively satisfied;
the presence of five Phase A files alone is insufficient:

- all five required artifacts exist and cross-reference one immutable Omi snapshot;
- the top-level repository is inventoried and every Dora-relevant reviewed unit has one disposition;
- every applicable verified hazard maps to a Dora test/gate proposal or explicit non-applicability;
- every reuse/port candidate names an exact upstream scope, Dora interface, fallback and future
  separately gated backlog/PoC item;
- licenses, transitive/embedded assets and evidence gaps are explicit, with ambiguity failing closed;
- no upstream source, dependency, executable, model, dataset, font, image or brand asset has entered
  the Dora product or dependency graph;
- relevant documentation/JSON validation and Tier A checks for the actual docs-only diff pass;
- backlog/status report the audit outcome without changing another PoC verdict or active stage;
- the final report states that no production or evaluation admission follows.

## 13. Fallbacks

- Unclear rights result in `BLOCKED_RIGHTS`, not inferred permission.
- Missing or capped history results in `INSUFFICIENT_EVIDENCE`, not a completeness claim.
- An architecture conflict results in `LEARN_ONLY` or `REJECT`, not modification of Dora invariants.
- A promising but out-of-stage component results in `DEFER`, not an unused module or SDK.
- If no reusable implementation survives the gates, Dora may implement from its own approved
  specification while retaining the upstream hazard-derived tests and provenance record.
- If the canonical repository or immutable evidence is unavailable, the affected review stops.

## 14. Current exit statement

The task definition and Phase A public-metadata collection are complete. The snapshot is
`7d99abcc4efb9e46a5853b21fc01289e4b891837` / tree
`85db621ffd5dc5386bcbd7c87713cc69638be7e3`. No Omi blob/source, archive, issue/PR content,
binary, model, dataset or asset was downloaded or copied; no upstream test was imported; no
dependency was added; no code was executed; and no Dora architecture, active stage, PoC verdict
or product authority flag changed. The full audit remains blocked on exact rights, content,
reviewer and completeness gates.
