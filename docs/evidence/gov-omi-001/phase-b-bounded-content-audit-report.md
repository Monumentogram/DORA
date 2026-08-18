# GOV-OMI-001 Phase B bounded public-content audit

Date: 18 August 2026

Authorities:

- GOV-OMI-PHASE-B-BOUNDED-PUBLIC-SOURCE-CONTENT-AUDIT-AUTH-20260818-01
- GOV-OMI-PHASE-B-CANONICAL-RECOVERY-AUTH-20260818-02
- GOV-OMI-PHASE-B-INCOMPLETE-RETRIEVAL-RECOVERY-AUTH-20260818-03
- GOV-OMI-PHASE-B-TEXT-CLASSIFICATION-AND-TRACKER-RECOVERY-AUTH-20260818-04
- GOV-OMI-PHASE-B-SANITIZED-SEMANTIC-FINDINGS-AUTH-20260818-05

Result: **INCOMPLETE_AUTH05_PRIVACY_FAIL_CLOSED**

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

AUTH-05 attempted the final sanitized semantic pass. A local command-line failure occurred before
any request and consumed zero budget. The corrected, parser-validated execution then verified 54
immutable blobs and 582,154 bytes. On object 54, the bounded privacy scanner found a new
`PRIVATE_ENDPOINT` signal after OID, size, SHA-256 and text predicates had passed. It persisted no
raw value and stopped immediately. The remaining 11 blobs and all 18 tracker items were not
requested under AUTH-05.

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
9. 66b1d3e — froze the AUTH-04 48-object/18-item/66-request recovery;
10. 4b39087 — preserved complete AUTH-04 evidence and its independent 0/0/0 review;
11. e0666f4 — froze the AUTH-05 65-object/18-item/83-request semantic pass.

The file docs/stage0/gov-omi-phase-b-contract-test-resolved-allowlist-v0.1.json remains a
grandfathered, byte-for-byte immutable artifact introduced by f71a2a2. AUTH-04 repeated only the
explicitly authorized 48 previously successful blob requests; no other successful request was
repeated before AUTH-05. AUTH-05 then repeated exactly 54 previously successful immutable-blob
requests before its mandatory privacy stop; it did not start tracker retrieval.

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
blob. AUTH-05 produced only partial sanitized findings from 49 eligible blobs, excluded four known
privacy-suppressed blobs and stopped before classifying the newly suppressed object. Those partial
findings are not a complete semantic audit.

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
| AUTH-05 local pre-request failures | 1; zero external requests |
| AUTH-05 blob requests | 54 of 65 |
| AUTH-05 tracker requests | 0 of 18 |
| AUTH-05 budget | 54 consumed; 29 unexecuted of 83 |
| Authorized repeats of previously successful requests | 102 |
| Total known external requests | 194 |

Canonical arithmetic: 2 + 49 + 1 + 16 + 1 = 69; 69 + 17 = 86.

AUTH-04 arithmetic: 48 text-classification objects + 18 tracker items = 66.

AUTH-05 arithmetic: 54 executed + 11 unrequested blobs + 18 unrequested tracker items = 83.

All-known-request arithmetic: 140 preserved through AUTH-04 + 54 AUTH-05 requests = 194. The
original final identity recheck remains displaced and was not run.

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

## Partial sanitized findings

Before the AUTH-05 stop, 16 eligible license/notice objects produced mechanical family and
obligation-signal codes. The partial set contains MIT-family, Apache-2-family, BSD-family, Notice and
empty/unknown signals. These are text-classifier facts, not license-compatibility or legal
conclusions; every license recommendation remains `DEFER`.

The partial component pass found bounded patterns for Android audio lifecycle, Bluetooth route
handling, recording finalization, offline sync/reconciliation, STT/VAD resilience, and
privacy/deletion/user authority. Offline sync is recorded only as a non-admitting
`PORT_CANDIDATE`; all other component patterns are `LEARN_ONLY`. The test/security pass found
regression-test, parity-contract, security-reporting and privacy-control patterns, all
`LEARN_ONLY`. Exact supporting allowlisted paths, OIDs and SHA-256 digests are retained in the JSON
evidence; no code or text is retained.

The semantic tracker pass never started. All nine hazard-taxonomy outcomes therefore remain
`INSUFFICIENT_EVIDENCE` with recommendation `DEFER`; the AUTH-04 ten-comment cap on Pull Request
7322 remains an independent limitation.

## Privacy and publication

Raw upstream content existed only in process memory. It was not written to the worktree, temporary
files, Git, terminal output, PR text, CI logs, artifacts or attachments.

Four of the original 48 completed blobs remain excluded from interpretation because the in-memory
privacy scan detected contact, secret-like or private-endpoint patterns. The retry and remaining 16
blobs added no privacy suppressions. AUTH-04 inherited those four suppressions only after exact
SHA-256 equality. All 18 tracker items passed the bounded privacy checks with zero suppression.
AUTH-05 excluded those same four objects, then found one new private-endpoint signal in
`backend/diarizer/requirements.txt` and stopped. Only its OID, expected size, SHA-256, path and
sanitized signal kind are retained. Evidence contains no source line, license text, title, body,
comment, author identity, contact detail, credential or private endpoint value.

## Review and delivery gate

The earlier independent review of the AUTH-03 candidate is historical. A fresh independent
read-only review of the exact AUTH-04 candidate completed with `NO_FURTHER_CHANGES_REQUIRED` and
P0/P1/P2 counts of 0/0/0. A distinct fresh read-only review of the exact terminal AUTH-05 candidate
also completed with `NO_FURTHER_CHANGES_REQUIRED` and P0/P1/P2 counts of 0/0/0. This validates the
honest incomplete evidence record, not reuse fitness. The technical reviewer is not a formal
Product/Legal/IP or Engineering/Security reviewer.

A later independent diff-check review found one P2 formatting defect: four Markdown hard-break
lines contained trailing spaces. That exact defect was remediated under
`GOV-OMI-PHASE-B-DIFF-CHECK-P2-REMEDIATION-AUTH-20260818-06` without changing semantic findings,
request accounting, dispositions, privacy evidence or reviewer placeholders. The prior diff-check
verdict was `CHANGES_REQUIRED` with P0/P1/P2 counts of 0/0/1. At remediation-commit time, fresh
review of the changed bytes was `PENDING`, with counts, reviewer and review time null. A subsequent
independent read-only Codex review of exact commit
`8f59f103a83f133af42b5b89bd674d2a307b6762` and tree
`28120085942a11469f729b46100fd37c7e3b9255` returned terminal `CLEAN` /
`NO_FURTHER_CHANGES_REQUIRED` with P0/P1/P2 counts of 0/0/0. That reviewer is non-formal; Product/
Legal/IP and Engineering/Security identities and dispositions remain null. This closure changes no
semantic finding, request count, disposition or privacy fact.

The latest coordinator-provided main observation is e33f24b40587e7af9c16a2c42e492b6da79e19f3,
with exact-main run 32160072118 still in progress at that checkpoint. Ancestry sync and publication
remain deliberately deferred until AUTH-05 evidence passes independent review and main CI is green.

Even after technical review and ancestry sync, the PR must remain Draft because:

- AUTH-05 stopped at a new privacy signal and did not complete 11 blobs or any tracker semantics;
- Pull Request 7322 tracker evidence remains incomplete at the frozen ten-comment cap;
- Product/Legal/IP reviewer identity and disposition remain null;
- Engineering/Security reviewer identity and disposition remain null.

No build or execution of Omi, clone, archive, diff, patch, copy, port, dependency change, model,
dataset, admission, Alpha work, Sheet update, Ready transition or merge occurred.
