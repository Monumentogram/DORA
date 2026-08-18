# GOV-OMI-001 Phase B bounded public-content audit

Date: 18 August 2026

Authorities:

- GOV-OMI-PHASE-B-BOUNDED-PUBLIC-SOURCE-CONTENT-AUDIT-AUTH-20260818-01
- GOV-OMI-PHASE-B-CANONICAL-RECOVERY-AUTH-20260818-02
- GOV-OMI-PHASE-B-INCOMPLETE-RETRIEVAL-RECOVERY-AUTH-20260818-03
- GOV-OMI-PHASE-B-TEXT-CLASSIFICATION-AND-TRACKER-RECOVERY-AUTH-20260818-04

Result: **BOUNDED_RETRIEVAL_COMPLETE_REVIEW_PENDING**

Engineering evidence only; not legal advice.

## Outcome

The original blob failure remains preserved in commit e689f5e. The later recovery performed exactly
one authorized retry of that immutable object, completed it successfully and then completed every
remaining allowlisted blob once. The blob audit therefore reached 65 of 65 unique objects and exactly
700,153 decoded bytes.

AUTH-04 then re-fetched exactly the earlier 48 successful objects solely to prove the remaining text
classification predicates. All 48 repeated their expected OID, size and SHA-256, decoded as strict
UTF-8, contained no NUL or disallowed Unicode control scalar, and passed the frozen binary heuristic.
No source, license, manifest or test semantic interpretation was performed.

The same authority retried the previously failed issue 11769 request once and requested the other
17 frozen tracker items once each. All 18 responses succeeded within 111,788 UTF-8 bytes. Pull
Request 7322 reports 18 comments, so only the frozen ten-comment tail was inspected and that item is
explicitly incomplete. No pagination was performed.

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
7. 9c47368 — froze the schema-compatible tracker query shape;
8. e3bce72 — preserved the AUTH-03 paused evidence and its independent review;
9. 66b1d3e — froze the AUTH-04 48-object/18-item/66-request recovery.

The file docs/stage0/gov-omi-phase-b-contract-test-resolved-allowlist-v0.1.json remains a
grandfathered, byte-for-byte immutable artifact introduced by f71a2a2. AUTH-04 repeated only the
explicitly authorized 48 previously successful blob requests; no other successful request was
repeated.

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
decoding and explicit NUL rejection. AUTH-04 additionally established zero disallowed Unicode
`Control` scalars other than TAB, LF and CR for the earlier 48-object set. The frozen binary
heuristic therefore passes for all 65 unique blobs. The result JSON retains one SHA-256 per unique
blob, but content-derived source/license/manifest/test interpretation remains withheld because
semantic interpretation was outside AUTH-04.

## Request ledger

| Metric | Result |
|---|---:|
| Pre-freeze discovery requests outside canonical budget | 3 |
| Grandfathered contract-tree calls outside canonical budget | 2 |
| Canonical identity/preflight requests | 2 |
| Initial blob attempts | 49 |
| Authorized failed-blob retry | 1 |
| Remaining blob attempts | 16 |
| Failed tracker attempt before AUTH-04 | 1 |
| AUTH-03 canonical budget consumed at pause | 69 of 86 |
| AUTH-03 canonical budget unexecuted at pause | 17 |
| Requests preserved through AUTH-03 | 74 |
| AUTH-04 text-classification blob requests | 48 of 48 |
| AUTH-04 tracker requests | 18 of 18 |
| AUTH-04 successful responses | 66 of 66 |
| Authorized repeats of previously successful requests | 48 |
| Total known external requests | 140 |

Canonical arithmetic: 2 + 49 + 1 + 16 + 1 = 69; 69 + 17 = 86.

AUTH-04 arithmetic: 48 text-classification objects + 18 tracker items = 66.

All-known-request arithmetic: 74 preserved through AUTH-03 + 66 AUTH-04 requests = 140. The original
final identity recheck remains displaced and was not run.

## Preserved tracker failure and completed recovery

The failed target was the frozen AUDIO_DATA_LOSS issue 11769
(GOV-OMI-HZ-META-001), using the public GitHub GraphQL issue body/top-level-comments endpoint
category. The sanitized failure class is GH_API_NONZERO_EXIT_NO_SUCCESSFUL_RESPONSE_PARSED.
HTTP status, GraphQL status, Retry-After and rate-limit remainder are unknown because no raw error or
response was persisted. No body, comment, title or author was persisted from that failed request.
Both pre-request local parser failures also remain recorded and consumed no external request.

The frozen identity-set SHA-256 remains
ef74a5642e5bf66eb3df14db915ffb35a964686b551b407ab0fb00e92f65fc64.

AUTH-04 completed all nine issues and nine Pull Requests without requesting authors, URLs/links,
patches, diffs or pagination. Evidence stores only item identity, state/timestamps, bounded byte and
comment counts, digests, cap state and privacy state. Across the 18 items, 59 comments existed and
51 were returned. Pull Request 7322 alone exceeded the frozen comment tail (18 total, 10 returned),
so its tracker evidence remains incomplete and no semantic hazard or fix claim is made.

## Privacy and publication

Raw upstream content existed only in process memory. It was not written to the worktree, temporary
files, Git, terminal output, PR text, CI logs, artifacts or attachments.

Four of the original 48 completed blobs remain excluded from interpretation because the in-memory
privacy scan detected contact, secret-like or private-endpoint patterns. The retry and remaining 16
blobs added no privacy suppressions. AUTH-04 inherited those four suppressions only after exact
SHA-256 equality. All 18 tracker items passed the bounded privacy checks with zero suppression.
Evidence retains only immutable identifiers, byte counts, digests and suppression counts; it
contains no source line, license text, title, body, comment, author identity, contact detail,
credential or private endpoint.

## Review and delivery gate

The earlier independent review of the AUTH-03 candidate is historical. A fresh independent
read-only review of the exact AUTH-04 candidate completed with `NO_FURTHER_CHANGES_REQUIRED` and
P0/P1/P2 counts of 0/0/0. The technical reviewer is not a formal Product/Legal/IP or
Engineering/Security reviewer.

Live main advanced to ebbc54eacc2d556180f07e2063e08c1c3d1c28a3 and exact-main Android CI run
32156156620 succeeded. Ancestry sync and publication remain deliberately deferred; the newer live
main and its exact-main CI must be rechecked only after the authorized semantic follow-up is frozen.

Even after technical review and ancestry sync, the PR must remain Draft because:

- Pull Request 7322 tracker evidence is incomplete at the frozen ten-comment cap;
- Product/Legal/IP reviewer identity and disposition remain null;
- Engineering/Security reviewer identity and disposition remain null.

No build or execution of Omi, clone, archive, diff, patch, copy, port, dependency change, model,
dataset, admission, Alpha work, Sheet update, Ready transition or merge occurred.
