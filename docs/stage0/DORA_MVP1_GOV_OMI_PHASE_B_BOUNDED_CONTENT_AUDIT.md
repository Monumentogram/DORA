# Dora MVP 1 — GOV-OMI-001 Phase B bounded public-content audit

Authority: GOV-OMI-PHASE-B-BOUNDED-PUBLIC-SOURCE-CONTENT-AUDIT-AUTH-20260818-01  
Version: gov-omi-phase-b-bounded-content-audit-v0.1  
Date: 18 August 2026  
State: AUTHORIZED_BOUNDED_MECHANICAL_AUDIT; retrieval is permitted only after this frozen checkpoint  
Policy boundary: DORA_MVP1_IP_ASSET_POLICY.md; engineering evidence, not legal advice

## 1. Purpose and authority boundary

This record freezes the complete request and publication boundary before any Phase B upstream
content request. It permits a small, read-only mechanical audit of public material already selected
from the merged Phase A metadata. It does not authorize a legal, security, reuse, port, evaluation or
admission conclusion.

Literal placeholders such as [имя] assign nobody. Product/Legal/IP and Engineering/Security
reviewer identity, capacity, signature/equivalent, timestamp and disposition remain null and pending.

Allowed after this record is locally committed and its machine companion passes parity checks:

- exact immutable Git commit/tree identity recheck;
- exact Git tree metadata for the two Phase-A-pinned contract/test tree objects;
- exact public blob retrieval within the static or deterministically resolved allowlists below;
- the selected public issue and Pull Request bodies plus bounded first-page comments/reviews;
- sanitized content-free mechanical evidence and a Draft Pull Request.

Forbidden:

- clone, archive, release-asset, binary, model, dataset or LFS download;
- patch, diff, Pull Request file list or commit-history retrieval;
- build, execution, import, copying, porting, adaptation or dependency/product admission;
- changing Dora code, Android dependencies, architecture, active stage or any PoC verdict;
- assigning or impersonating a Product/Legal/IP or Engineering/Security reviewer;
- Ready-for-review, merge, Backlog, Stage Status, Sheet or report-relay changes.

## 2. Pre-edit and pre-query checkpoint

| Field | Frozen value |
|---|---|
| Dora repository | Monumentogram/DORA |
| Branch | codex/gov-omi-001-phase-b-bounded-content-audit |
| Base commit | 4a1dacacc52926ef3608a5952a762f00b8dafaa9 |
| Base tree | 23d0fe235acff4e4693b6eabcedad1e58b8d0816 |
| Base parent | 97df1dc029328de16d7b2cc9f4aadcefc043bbbd |
| Checkpoint time | 2026-08-18T14:50:00Z |
| Tracked worktree | clean |
| Staged worktree | clean |
| Upstream requests before checkpoint | zero |

The Phase A report's Dora base 97df1dc029328de16d7b2cc9f4aadcefc043bbbd / tree
4b4ac11c07a0465c22a6f7840c40d809955c92f2 is immutable historical collection provenance and is
not rewritten. This Phase B branch starts from the later protected integration commit above.

## 3. Immutable upstream identity

| Field | Frozen value |
|---|---|
| Canonical repository | BasedHardware/omi |
| Commit | 7d99abcc4efb9e46a5853b21fc01289e4b891837 |
| Commit tree | 85db621ffd5dc5386bcbd7c87713cc69638be7e3 |
| Phase A tree metadata SHA-256 | 007d030a21e72c07c89f36359c0e5ded355147c85b5b6284ae0858e5e5058e90 |
| Root entry count | 48 |
| Recursive entry count | 13,341, untruncated in Phase A |
| Contracts tree | contracts / 8f75b76071a25b45ff1227b72888c0d9468888fa / 7 Phase A prefix entries |
| Contract-tests tree | contract_tests / d10af828ca4c088e4f3c011de4bc75dc011ef2e2 / 4 Phase A prefix entries |

No moving branch, tag, release or latest identifier may substitute for these objects.

## 4. Static blob allowlist

Only the following ten Phase-A-recorded blobs may be requested directly. Every response must match
the expected Git OID and decoded size; Git blob SHA-1 framing and a local SHA-256 are recomputed in
memory before analysis.

| Category | Path | Git blob OID | Bytes |
|---|---|---|---:|
| rights | LICENSE | beb9b3553922522ad2466040709a4a76c05d81ca | 1,084 |
| security | SECURITY.md | 3b541d7704e77fbc2be195a400588453073d74ff | 3,413 |
| mobile manifest | app/pubspec.yaml | 0fd1d42b3367ae21e1fc62f85f21c5f64a42e352 | 6,211 |
| mobile lock | app/pubspec.lock | 343b04ffa58f2c700c944899b0ba877f5c934796 | 75,797 |
| Android manifest | app/android/app/build.gradle | 969fc35017e5b8b6dba80ad6f6769e4f063b95eb | 5,586 |
| Android native control | app/android/app/src/main/cpp/CMakeLists.txt | e58117c7cbe53ba4204fe394f263498ca2ee84d7 | 594 |
| backend manifest | backend/pyproject.toml | e047144e4753267f3041f369724f72d02354e8a9 | 301 |
| backend dependencies | backend/requirements.txt | 0ffa99904132158a87cff837890e7b0cadec148a | 4,460 |
| diarizer dependencies | backend/diarizer/requirements.txt | df89c93a6b9086d11c09acfe4dca05742bee025c | 2,452 |
| ASR dependencies | backend/parakeet/requirements.txt | 10e7698b495460333e1149f1da51bc4c0c930478 | 892 |

Static decoded bytes are exactly 100,790. Other license/NOTICE, manifest, app, backend, SDK,
firmware, web, desktop, plugin and infrastructure paths remain outside this Phase B allowlist.

## 5. Contract/test deterministic resolution

Before retrieving contract or test content, exactly two tree-metadata requests may resolve the
contents of the pinned contracts and contract_tests tree objects. The response is accepted only if:

- the returned tree OID equals the requested OID and truncated is false;
- contracts resolves to at most 7 total entries and contract_tests to at most 4;
- every content candidate is a regular blob with mode 100644 or 100755;
- there is no submodule, symlink or unresolved nested tree;
- at most 11 blobs resolve, every blob is at most 65,536 bytes and their total is at most 131,072;
- the exact resolved path/OID/size list is written to and committed as
  docs/stage0/gov-omi-phase-b-contract-test-resolved-allowlist-v0.1.json before any of those blob
  contents are requested.

If any predicate fails, contract/test blob retrieval is skipped. The static allowlist may continue
only if the failure does not indicate immutable snapshot drift.

## 6. Issue and Pull Request allowlist

The deterministic selection is the first issue and first Pull Request example recorded by Phase A
for each of its nine hazard classes. Titles are not copied. These live tracker objects are identified
by stable repository/type/number and are explicitly not part of the immutable source tree.

| Hazard | Issue | Pull Request |
|---|---:|---:|
| GOV-OMI-HZ-META-001 AUDIO_DATA_LOSS | 11769 | 7331 |
| GOV-OMI-HZ-META-002 MIC_STATE_PERMISSION | 11204 | 6565 |
| GOV-OMI-HZ-META-003 LIFECYCLE_CRASH | 11695 | 7099 |
| GOV-OMI-HZ-META-004 ROUTE_BLE_INTERRUPTION | 11762 | 7132 |
| GOV-OMI-HZ-META-005 DUPLICATE_RETRY_ORDER | 11736 | 6777 |
| GOV-OMI-HZ-META-006 OFFLINE_SYNC | 11694 | 7091 |
| GOV-OMI-HZ-META-007 DELETE_PRIVACY_SECURITY | 11812 | 7006 |
| GOV-OMI-HZ-META-008 USER_TRUTH_STALE_WRITE | 11308 | 7106 |
| GOV-OMI-HZ-META-009 STT_PROVIDER_FAILURE | 11777 | 7322 |

For each issue, one item request and one first-page issue-comment request are allowed. For each Pull
Request, one item request, one first-page issue-comment request, one first-page review-comment
request and one first-page review request are allowed. Each list uses per_page=5 and page=1 only;
there is no pagination. Patch, diff, files, commits and checks endpoints remain forbidden.

## 7. Request and byte budgets

| Budget | Cap |
|---|---:|
| identity/root metadata requests | 2 |
| contract/test tree metadata requests | 2 |
| static blob requests | 10 |
| resolved contract/test blob requests | 11 maximum |
| issue/PR body/comment/review requests | 54 |
| total logical requests | 79 maximum |
| retry | at most one per request, only transient transport, HTTP 429 or 5xx |
| total transport attempts including retries | 158 maximum |
| decoded static blob bytes | 100,790 exact |
| decoded resolved contract/test bytes | 131,072 maximum |
| decoded all blob bytes | 262,144 maximum |
| one issue/PR body | 131,072 UTF-8 bytes maximum |
| one comment/review body | 65,536 UTF-8 bytes maximum |
| one tracker item body+bounded discussion | 524,288 UTF-8 bytes maximum |
| all tracker text | 8,388,608 UTF-8 bytes maximum |
| one API JSON response | 2,097,152 bytes maximum |
| aggregate API JSON responses | 33,554,432 bytes maximum |

An over-cap item is not truncated into evidence. It is discarded from analysis and recorded only as
OVER_CAP with content absent. Missing, deleted, private/auth-gated, rate-limited-after-retry or
schema-invalid responses are recorded as unavailable and do not receive a conclusion.

## 8. Zero-raw-logging and redaction rules

Raw license, source, manifest, test, security-policy, issue, comment or review text may exist only in
process memory for bounded parsing. It must not be written to the Dora worktree, temporary files,
Git objects, terminal/stdout/stderr, tool output, PR text, CI logs, Actions artifacts or attachments.

Public evidence may retain only:

- upstream repository, immutable commit/tree, allowlisted relative path, Git OID, expected/observed
  byte count, recomputed SHA-1 result and SHA-256;
- tracker type/number, canonical public URL, state/time metadata, Phase A title digest comparison,
  bounded counts and SHA-256 digests of bodies/comment sets;
- content-free mechanical booleans/counts such as parse success, dependency count, native-control
  marker count, assertion count, reporting-channel-present, reproduction-detail-present and
  fix-reference-present;
- explicit cap, unavailable, drift and suppression states.

Evidence must not retain source lines, excerpts, code identifiers, test names, manifest values,
license wording, issue titles, bodies, comments, review text, author/login/name/email/avatar,
contact details, local paths, credentials, tokens, URLs containing query data, or content-derived
personal information. Secret-like or personal-data patterns cause the affected content to be
suppressed; only a category/count and suppression flag may remain.

## 9. Mechanical findings only

The writer may verify object integrity, parse format, count declarations/assertions, detect bounded
category markers and map evidence to an existing Dora hazard class. The writer may not decide
license compatibility, source quality, security fitness, bug validity, fix validity, reuse, porting,
evaluation approval or admission.

All reviewer fields remain null. Overall rights remain BLOCKED_RIGHTS. Component and hazard
conclusions remain INSUFFICIENT_EVIDENCE. No item may become REUSE_CANDIDATE, PORT_CANDIDATE,
LEARN_ONLY, REJECT, EVALUATION_APPROVED, ADMISSION_REVIEW or ADMITTED in this task.

## 10. Stop conditions

Stop the affected work, and stop the whole audit on identity or scope drift, if any of these occur:

- Dora base/head/tree, authority ID, upstream repository/commit/tree or Phase A source hashes drift;
- a request, path, object ID or tracker number falls outside the frozen allowlist;
- any request/attempt/response/content budget would be exceeded;
- an immutable blob OID, expected size or Git blob SHA-1 framing check fails;
- a tree is truncated, exceeds its entry/byte cap or exposes a symlink/submodule/unresolved tree;
- raw content would be logged, persisted, committed, attached or quoted;
- potential secret/private/personal content cannot be reduced safely to a suppression count;
- reviewer identity/capacity/signature/disposition is inferred from [имя] or any other placeholder;
- build, execution, patch/diff, copying, porting, dependency or product admission is attempted;
- Backlog/Stage Status overlap becomes unavoidable;
- a new Dora P0/P1/P2 finding, validation failure, red CI or protection drift appears.

## 11. Dora file allowlist and delivery

Exactly five new Dora files are permitted on this branch:

1. docs/stage0/DORA_MVP1_GOV_OMI_PHASE_B_BOUNDED_CONTENT_AUDIT.md
2. docs/stage0/gov-omi-phase-b-bounded-content-audit-v0.1.json
3. docs/stage0/gov-omi-phase-b-contract-test-resolved-allowlist-v0.1.json
4. docs/evidence/gov-omi-001/phase-b-bounded-content-audit-v0.1.json
5. docs/evidence/gov-omi-001/phase-b-bounded-content-audit-report.md

The first two files are the immutable pre-query plan. File 3 is committed after tree metadata and
before any contract/test blob request. Files 4–5 contain sanitized results. Local validation,
explicit-file commits, push and a Draft PR are allowed. Ready and merge are not authorized.

