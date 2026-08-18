# GOV-OMI-001 Phase B bounded public-content audit

Date: 18 August 2026

Authorities: GOV-OMI-PHASE-B-BOUNDED-PUBLIC-SOURCE-CONTENT-AUDIT-AUTH-20260818-01 and
GOV-OMI-PHASE-B-CANONICAL-RECOVERY-AUTH-20260818-02

Result: **INCOMPLETE_FAIL_CLOSED**

Engineering evidence only; not legal advice.

## Outcome

The audit stopped at the first failed immutable-blob request. The frozen policy allowed no retry, so
the failed object, the remaining 16 blobs, all 18 issue/Pull Request items and the final identity
recheck were not requested. This is a DEFER, not a PASS and not evidence of reuse fitness.

Formal rights remain BLOCKED_RIGHTS. Component reuse, security fitness and hazard validation remain
INSUFFICIENT_EVIDENCE. No material became EVALUATION_APPROVED or ADMITTED.

## Preserved recovery chain

The canonical recovery retained the linear branch history without reset, rewrite or deletion:

1. 349f479 — initial Phase B scope checkpoint;
2. aa2f68f — formatting normalization;
3. f71a2a2 — fail-closed contract/test tree resolution;
4. d2dc88b — adopted 67-path canonical recovery scope.

The four requests already recorded by f71a2a2 were not repeated. They comprised two identity and two
contract/test tree-metadata requests; no source or tracker content was retained by that batch.

The file docs/stage0/gov-omi-phase-b-contract-test-resolved-allowlist-v0.1.json is a grandfathered,
immutable artifact introduced by f71a2a2. Canonical recovery preserved it byte-for-byte. It is outside
the five-file forward-mutation allowlist and was not edited, replaced or deleted.

## Frozen scope

The pinned upstream identity remained
BasedHardware/omi@7d99abcc4efb9e46a5853b21fc01289e4b891837, tree
85db621ffd5dc5386bcbd7c87713cc69638be7e3.

- File allowlist: 67 paths, 65 unique blobs and exactly 700,153 decoded bytes.
- File allowlist SHA-256:
  5ce13c2059bbb6dacbee78d7560985cbbe7cab8a54e93c73b1f643ccca277a4a.
- Tracker allowlist: nine issues and nine Pull Requests.
- Tracker allowlist SHA-256:
  ef74a5642e5bf66eb3df14db915ffb35a964686b551b407ab0fb00e92f65fc64.

## Blob batch evidence

| Metric | Result |
|---|---:|
| Requests attempted | 49 |
| Successful immutable blobs | 48 |
| Failed requests | 1 |
| Retries | 0 |
| Verified decoded bytes | 543,654 |
| Completed allowlisted paths | 50 |
| Unattempted blobs | 16 |
| Failed plus unattempted expected bytes | 156,499 |
| Tracker requests | 0 |
| Canonical budget consumed | 51 of 86 |
| Canonical budget unexecuted | 35 |
| Grandfathered calls outside canonical budget | 2 |
| All known requests including pre-freeze discovery | 56 |
| Repeated requests | 0 |

Every completed blob passed base64 decoding, exact allowlisted size, recomputed Git blob OID, strict
UTF-8 decoding and an explicit NUL-byte rejection. The result JSON records a SHA-256 for every
completed blob and a canonical digest over the completed OID/size set. A separate binary-content
heuristic was not evidenced, so content-derived mechanical interpretation is unavailable. The
no-retry policy prevents repeat retrieval.

The failing request was attempt 49 for allowlisted object
d62389a3aedd4e1b6d44f4fe66a8d00d0a7af9ce
(docs/doc/hardware/consumer/license.mdx, expected 5,125 bytes). No raw response or error body was
persisted. The failure was not retried.

## Mechanical interpretation withheld

No content-derived mechanical observations are published. Although the in-memory batch computed
bounded pattern signals, the audit did not retain evidence of a separate binary-content heuristic.
The frozen no-retry rule prevents rebuilding that evidence. The incomplete blob set and entirely
unqueried tracker set independently prevent component, security or hazard conclusions.

## Privacy and publication

Raw upstream content existed only in process memory. It was not written to the worktree, temporary
files, Git, terminal output, PR text, CI logs, artifacts or attachments.

Four completed blobs were excluded from mechanical interpretation because the in-memory privacy scan
detected contact, secret-like or private-endpoint patterns. Only suppression counts and immutable
object identifiers are retained. No author identity, contact detail, title, body, comment, source
line, credential or private endpoint is present in this evidence.

## Review and terminal gate

An independent technical review must confirm zero P0, zero P1 and zero P2 defects in the sanitized
evidence before publication. Even with that review and green CI, the PR must remain Draft because:

- the bounded audit is incomplete and non-retryable under the frozen request policy;
- Product/Legal/IP reviewer identity and disposition remain null;
- Engineering/Security reviewer identity and disposition remain null.

No build, execution, clone, archive, diff, patch, copy, port, dependency change, model, dataset,
admission, Alpha work, Sheet update, Ready transition or merge occurred.
