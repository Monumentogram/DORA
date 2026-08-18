# GOV-OMI-001 Phase B bounded public-content audit

Date: 18 August 2026

Authorities:

- GOV-OMI-PHASE-B-BOUNDED-PUBLIC-SOURCE-CONTENT-AUDIT-AUTH-20260818-01
- GOV-OMI-PHASE-B-CANONICAL-RECOVERY-AUTH-20260818-02
- GOV-OMI-PHASE-B-INCOMPLETE-RETRIEVAL-RECOVERY-AUTH-20260818-03

Result: **INCOMPLETE_TRACKER_FAIL_CLOSED**

Engineering evidence only; not legal advice.

## Outcome

The original blob failure remains preserved in commit e689f5e. The later recovery performed exactly
one authorized retry of that immutable object, completed it successfully and then completed every
remaining allowlisted blob once. The blob audit therefore reached 65 of 65 unique objects and exactly
700,153 decoded bytes.

The first actual tracker request, issue 11769, returned no successful response. That was the second
external request failure across the audit, so recovery paused without retry. The remaining 17
tracker items were not requested. Two earlier local parser errors occurred before any tracker request
and consumed no external budget. A further tracker attempt requires a new exact authority.

This is a DEFER, not a PASS and not evidence of reuse fitness. Formal rights remain BLOCKED_RIGHTS.
Component reuse, security fitness and hazard validation remain INSUFFICIENT_EVIDENCE. No material
became EVALUATION_APPROVED or ADMITTED.

## Preserved recovery chain

The branch history remains linear and unrevised:

1. 349f479 — initial Phase B scope checkpoint;
2. aa2f68f — formatting normalization;
3. f71a2a2 — fail-closed contract/test tree resolution;
4. d2dc88b — adopted 67-path canonical recovery scope;
5. e689f5e — preserved the initial failed-blob terminal evidence;
6. 2140058 — authorized the exact incomplete-retrieval recovery;
7. 9c47368 — froze the schema-compatible tracker query shape.

The file docs/stage0/gov-omi-phase-b-contract-test-resolved-allowlist-v0.1.json remains a
grandfathered, byte-for-byte immutable artifact introduced by f71a2a2. No previously successful
metadata, blob or tracker request was repeated.

## Frozen scope and integrity

The pinned upstream identity remained
BasedHardware/omi@7d99abcc4efb9e46a5853b21fc01289e4b891837, tree
85db621ffd5dc5386bcbd7c87713cc69638be7e3.

- File allowlist: 67 paths, 65 unique blobs and exactly 700,153 decoded bytes.
- File allowlist SHA-256:
  5ce13c2059bbb6dacbee78d7560985cbbe7cab8a54e93c73b1f643ccca277a4a.
- Completed OID/size-set SHA-256:
  27b4677225746d26de8da2be0a1c274f4a38569e223798ef74cc05832b12d13e.
- Tracker allowlist: nine issues and nine Pull Requests.
- Tracker allowlist SHA-256:
  ef74a5642e5bf66eb3df14db915ffb35a964686b551b407ab0fb00e92f65fc64.

Every blob passed base64 decoding, exact allowlisted size, recomputed Git blob OID, strict UTF-8
decoding and explicit NUL rejection. These are the content predicates defined by this audit; no
additional binary heuristic is claimed or required. The result JSON retains one SHA-256 per unique
blob. Complete sanitized interpretations were not retained for the first 48 successful blobs, which
may not be repeated, so content-derived source/license/manifest/test interpretation remains withheld.

## Request ledger

| Metric | Result |
|---|---:|
| Pre-freeze discovery requests outside canonical budget | 3 |
| Grandfathered contract-tree calls outside canonical budget | 2 |
| Canonical identity/preflight requests | 2 |
| Initial blob attempts | 49 |
| Authorized failed-blob retry | 1 |
| Remaining blob attempts | 16 |
| Tracker attempts | 1 |
| Successful tracker responses | 0 |
| Repeated successful requests | 0 |
| Canonical budget consumed | 69 of 86 |
| Canonical budget unexecuted | 17 |
| Total known external requests | 74 |

Canonical arithmetic: 2 + 49 + 1 + 16 + 1 = 69; 69 + 17 = 86.

All-known-request arithmetic:
3 pre-freeze + 4 preserved prior-scope + 49 initial blobs + 1 retry + 16 remaining blobs +
1 tracker attempt = 74.

The original final identity recheck was displaced by the authorized failed-blob retry and was not
run. The failed tracker request was not retried.

## Tracker pause evidence

The failed target was the frozen AUDIO_DATA_LOSS issue 11769
(GOV-OMI-HZ-META-001), using the public GitHub GraphQL issue body/top-level-comments endpoint
category. The sanitized failure class is GH_API_NONZERO_EXIT_NO_SUCCESSFUL_RESPONSE_PARSED.
HTTP status, GraphQL status, Retry-After and rate-limit remainder are unknown because no raw error or
response was persisted. No body, comment, title or author was persisted. No tracker item completed,
no tracker text bytes entered evidence and no content-derived tracker finding was made.

The frozen identity-set SHA-256 remains
ef74a5642e5bf66eb3df14db915ffb35a964686b551b407ab0fb00e92f65fc64.

- Content-incomplete issues: 11769, 11204, 11695, 11762, 11736, 11694, 11812, 11308, 11777.
- Content-incomplete Pull Requests: 7331, 6565, 7099, 7132, 6777, 7091, 7006, 7106, 7322.
- Unrequested after the failure: eight issues and nine Pull Requests.
- Canonical request budget still available: 17.
- Retry authority currently available: none.

## Privacy and publication

Raw upstream content existed only in process memory. It was not written to the worktree, temporary
files, Git, terminal output, PR text, CI logs, artifacts or attachments.

Four of the original 48 completed blobs remain excluded from interpretation because the in-memory
privacy scan detected contact, secret-like or private-endpoint patterns. The retry and remaining 16
blobs added no privacy suppressions. Evidence retains only immutable identifiers, byte counts,
digests and suppression counts; it contains no source line, license text, title, body, comment,
author identity, contact detail, credential or private endpoint.

## Review and delivery gate

The earlier independent 0/0/0 review applies only to the pre-recovery e689f5e evidence and is now
historical. A fresh independent read-only review of the current recovery candidate completed with
`NO_FURTHER_CHANGES_REQUIRED` and P0/P1/P2 counts of 0/0/0. The reviewer is technical and not a
formal Product/Legal/IP or Engineering/Security reviewer.

Live main advanced to ebbc54eacc2d556180f07e2063e08c1c3d1c28a3 and exact-main Android CI run
32156156620 succeeded. Ancestry sync and publication remain deliberately deferred pending the
coordinator's next bounded instruction; the tracker remains paused with no retry authority.

Even after technical review and ancestry sync, the PR must remain Draft because:

- tracker evidence is incomplete after a second external failure;
- Product/Legal/IP reviewer identity and disposition remain null;
- Engineering/Security reviewer identity and disposition remain null.

No build or execution of Omi, clone, archive, diff, patch, copy, port, dependency change, model,
dataset, admission, Alpha work, Sheet update, Ready transition or merge occurred.
