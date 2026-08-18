# Dora MVP 1 — GOV-OMI-001 Phase B bounded public-content audit

Authority: `GOV-OMI-PHASE-B-BOUNDED-PUBLIC-SOURCE-CONTENT-AUDIT-AUTH-20260818-01`  
Version: `gov-omi-phase-b-bounded-content-audit-v0.1`  
Date: 18 August 2026  
State: `AUTHORIZED_BOUNDED_MECHANICAL_AUDIT`  
Policy boundary: `DORA_MVP1_IP_ASSET_POLICY.md`; engineering evidence, not legal advice

## Purpose and authority ceiling

This record freezes the complete Phase B request, byte, privacy and publication boundary before any
upstream source, license, manifest, test, security-document, issue-body or Pull Request-body content
is requested. It permits a read-only, relevance-bounded mechanical audit of public material selected
from the merged Phase A metadata. It does not authorize build, execution, copying, porting, dependency
admission, a license-compatibility conclusion, a security-acceptance conclusion or a product decision.

Literal placeholders such as `[имя]` assign nobody. Product/Legal/IP and Engineering/Security
reviewer identity, capacity, signature/equivalent, timestamp and disposition remain null and pending.
Consequently, this phase cannot make the branch Ready or merge it.

## Frozen identities

| Field | Exact value |
|---|---|
| Dora repository | `Monumentogram/DORA` |
| Branch | `codex/gov-omi-001-phase-b-bounded-content-audit` |
| Base commit | `4a1dacacc52926ef3608a5952a762f00b8dafaa9` |
| Base tree | `23d0fe235acff4e4693b6eabcedad1e58b8d0816` |
| Omi repository | `BasedHardware/omi` |
| Omi commit | `7d99abcc4efb9e46a5853b21fc01289e4b891837` |
| Omi tree | `85db621ffd5dc5386bcbd7c87713cc69638be7e3` |
| Phase A tree-metadata SHA-256 | `007d030a21e72c07c89f36359c0e5ded355147c85b5b6284ae0858e5e5058e90` |
| Checkpoint time | `2026-08-18T14:55:24.370Z` |
| Content requests before checkpoint | `0` |
| Pre-freeze recursive-tree metadata requests | `3` |

No moving branch, tag, release or latest identifier may substitute for the pinned Omi objects.

## Exact content allowlists

The machine companion is authoritative for all 67 path/OID/size rows. Its canonicalization is UTF-8
without BOM, ordinally sorted rows of
`category<TAB>path<TAB>oid<TAB>size<LF>`, including a terminal LF.

| Category | Paths | Unique blobs | Path-expanded bytes | Unique decoded bytes |
|---|---:|---:|---:|---:|
| `LICENSE_NOTICE` | 21 | 19 | 62,140 | 59,972 |
| `MANIFEST` | 11 | 11 | 97,974 | 97,974 |
| `SOURCE` | 20 | 20 | 376,104 | 376,104 |
| `TEST` | 13 | 13 | 158,546 | 158,546 |
| `SECURITY_DOC` | 2 | 2 | 7,557 | 7,557 |
| **Total** | **67** | **65** | **702,321** | **700,153** |

Allowlist SHA-256:
`5ce13c2059bbb6dacbee78d7560985cbbe7cab8a54e93c73b1f643ccca277a4a`.
Duplicate OIDs are fetched once. Every decoded blob must match its exact size and Git blob SHA-1;
each also receives an evidence SHA-256. No linked path or related file may be discovered or fetched.

The discussion allowlist is exactly nine issues and nine Pull Requests, chosen as the first Phase A
example for each existing hazard taxonomy:

- issues: `11769, 11204, 11695, 11762, 11736, 11694, 11812, 11308, 11777`;
- Pull Requests: `7331, 6565, 7099, 7132, 6777, 7091, 7006, 7106, 7322`.

Canonical discussion allowlist SHA-256:
`ef74a5642e5bf66eb3df14db915ffb35a964686b551b407ab0fb00e92f65fc64`.
For each item, one GraphQL request may return only type/number/state/timestamps/URL, body text and the
ten most recently updated top-level comments. Author identities, reactions, review threads, inline
review comments, files, commits, patches and diffs are forbidden. `totalCount > 10` is recorded as an
explicit incomplete-discussion cap; there is no pagination.

## Request and byte budgets

| Budget | Exact cap |
|---|---:|
| Public-repository identity preflight | 1 request |
| Immutable commit/tree preflight | 1 request |
| Unique blob reads | 65 requests |
| Issue/Pull Request reads | 18 requests |
| Final repository/identity recheck | 1 request |
| **Post-freeze logical and transport requests** | **86; no retries** |
| Unique decoded blob content | **700,153 bytes exactly** |
| One tracker body | 131,072 UTF-8 bytes |
| One tracker comment | 65,536 UTF-8 bytes |
| All tracker bodies/comments | 2,097,152 UTF-8 bytes |
| **All decoded content** | **2,797,305 bytes** |

An over-cap item is not truncated into a conclusion. A blob mismatch stops the whole audit. A tracker
item that is missing or over cap is recorded only as unavailable/over-cap and remains
`INSUFFICIENT_EVIDENCE`. Private, auth-gated or non-public upstream state stops the whole audit.

## Zero-raw-persistence and privacy rules

Raw upstream content may exist only in process memory for bounded parsing. It must not be written to
the worktree, temporary files, Git objects, stdout/stderr, PR text, CI logs, Actions artifacts or
attachments. Only sanitized facts may be persisted:

- immutable identities, allowlisted relative paths/numbers/URLs, sizes, counts and cryptographic
  digests;
- parser success, bounded structural counts and content-free behavioral classifications;
- concise paraphrases that contain no source text, identifiers, issue titles, personal data,
  credentials, private endpoints or contact details;
- explicit missing, cap, suppression, drift and incomplete-pagination states.

No author/login/name/email/avatar is requested. Secret-like, personal or private endpoint content is
suppressed in memory and stops the affected item; evidence retains only a sanitized suppression flag.
No link is followed.

## Allowed findings and mandatory stop conditions

The audit may record `LEARN_ONLY`, `DEFER` or `REJECT` where bounded content directly supports the
classification. `REUSE_CANDIDATE` or `PORT_CANDIDATE` may only be a non-authorizing recommendation
pending named human rights and security review. They never mean `EVALUATION_APPROVED` or `ADMITTED`.
Formal rights stay `BLOCKED_RIGHTS`; unsupported claims stay `INSUFFICIENT_EVIDENCE`.

Stop before further retrieval or publication on any Dora base/branch overlap, Omi visibility or
identity drift, allowlist hash/count/OID/size mismatch, request or byte-cap breach, invalid base64,
non-UTF-8/NUL/binary content, Git OID mismatch, private/secret/personal content, unallowlisted evidence
need, linked/gated terms need, raw-content persistence risk, reviewer inference, active-stage
displacement, build/run/copy/port/admission attempt, new P0/P1/P2 finding, validation failure or red CI.

## Dora publication allowlist

Only these five new sanitized files may be changed on this branch:

1. `docs/stage0/DORA_MVP1_GOV_OMI_PHASE_B_BOUNDED_CONTENT_AUDIT.md`
2. `docs/stage0/gov-omi-phase-b-bounded-content-audit-v0.1.json`
3. `docs/evidence/gov-omi-001/phase-b-bounded-content-audit-v0.1.json`
4. `docs/evidence/gov-omi-001/phase-b-bounded-content-audit-report.md`
5. `docs/evidence/gov-omi-001/phase-b-independent-technical-review.json`

The first two files form the committed pre-content checkpoint. Local validation, explicit-file
commits, push, Draft PR and CI are authorized. Google Sheets, report relay, Alpha, Ready and merge are
out of scope. No Omi code, dependency, binary, model, dataset, asset, service or behavior is admitted.
