# POC-OFFLINE-001 I1 resource-envelope amendment — Stage 0 v0.3

Decision packet ID: `OFF-I1-RESOURCE-ENVELOPE-003`

Machine mirror: [`docs/evidence/poc-offline-001/i1-resource-envelope-amendment-stage0-v0.3.json`](../evidence/poc-offline-001/i1-resource-envelope-amendment-stage0-v0.3.json)

Disposition: `LOCAL_PURE_HOST_RESOURCE_ENVELOPE_DEFINED_REVIEW_REQUIRED`

Artifact state: `ADDITIVE_DECISION_PACKET_COMPLETE_PENDING_INDEPENDENT_ADVISORY_REVIEW`

Backlog truth: `POC-OFFLINE-001 = TODO / NOT_READY / NOT_RUN / NOT_AUTHORIZED`

Recorded: `2026-08-16T23:31:55.2072525+03:00`, `Europe/Moscow`

This packet is an additive amendment to `OFF-I1-SEMANTICS-SCOPE-002`. It resolves only the
resource-envelope question exposed by the prospective handwritten strict decoder. All v0.2
semantics remain byte-semantically inherited except the exact pre-semantic admission gate defined
below. It does not rewrite v0.2, activate implementation, run a test, admit a dependency, change
Android or product behavior, mutate backlog/status, authorize publication, or claim Offline PASS.
A Markdown/JSON semantic mismatch invalidates this packet.

## 1. Immutable parent and authority boundary

The local unpublished stack remains based on PR #28 head
`d164b6372ad3e717762d48a9a3ea7e6551602ab7`, tree
`076c9eeda8180d775c6ec6dbb1dc5cc53aeb7ce6`, whose parent is exact GitHub `main`
`78e9dd07d616989987118f26bb16ebb9932ddb2b`, tree
`f403bf60b86273ee8a2634ed5e8530c9d4af20e4`.

| Immutable parent | Bytes | SHA-256 |
|---|---:|---|
| `docs/stage0/DORA_MVP1_POC_OFFLINE_I1_SEMANTICS_AND_IMPLEMENTATION_SCOPE_STAGE0_V0_2.md` | 46364 | `64a8cd6c7230b104834ca8bdb8a8a3a1c1525277dc8b913487acf2739e67c08e` |
| `docs/evidence/poc-offline-001/i1-semantics-and-implementation-scope-stage0-v0.2.json` | 124165 | `156915ba88f97e9bbf08da02b6ef4c2b475c173f1f9b96e8c70fe50e2a174785` |
| `docs/evidence/poc-offline-001/reviews/off-i1-semantics-scope-002.external-review.json` | 757 | `c2a9f86f6d73a7b2f58fe7980691c07f4c9acca7e87fe517655604011db82f17` |

Applicable authority remains the exact source chain frozen by v0.2: Technical Plan §§21.3, 22
and 28; Product Decisions `DEC-009`, `DEC-012`, `DEC-013`, `DEC-015`, `DEC-017`, `DEC-020`;
accepted ADRs; Test Strategy `TS-UNIT`, `TS-OFFLINE`, `TS-PRIVACY` and Tier A; the Offline
readiness contract; I1 v0.1; and the immutable v0.2 packet plus its clean external advisory-review
record. This amendment introduces no product retention, retry, network, model, provider or
production decision.

## 2. Exact pre-semantic resource envelope

The resource envelope is a pre-semantic admission decision. It is not JSON syntax
classification, is not a replacement for v0.2 semantic classifier categories `001..015`, and
makes no statement about a rejected candidate's latent syntax or semantic defects.

| Parameter | Exact value |
|---|---:|
| `maxCandidateBytesInclusive` | 1048576 UTF-8 input bytes |
| `maxContainerDepthInclusive` | 32 |

Byte length is counted on the original `byte[]` before clone, UTF-8 decode, tokenization or any
semantic inspection. A candidate of exactly 1048576 bytes is admitted to the decoder. A candidate
of 1048577 bytes or more is rejected before decode with `OFF-I1-INVALID-016`.

Container depth is defined independently of Java call-stack depth. A root object or array has
depth 1; entering a child object or array adds one; scalar values add no depth. For a
byte-admitted candidate, containers through depth 32 are admitted. Ordering is exact:

1. the byte-length gate runs first;
2. admitted bytes are decoded as one complete strict UTF-8 buffer and the BOM rule is checked;
3. only after successful full-buffer decode does the JSON parser consume characters in order;
4. a JSON syntax failure observed before an attempted depth-33 entry is
   `OFF-I1-INVALID-013`; the attempted depth-33 entry is `OFF-I1-INVALID-016` and later decoded
   characters are not parsed;
5. if neither envelope gate rejects, the complete ordered v0.2 semantic classifier runs.

Thus malformed UTF-8 anywhere in a byte-admitted buffer, or any forbidden BOM, is
`OFF-I1-INVALID-013` before the container-depth gate. This precedence is intentional and distinct
from the byte-length gate, which does not decode any input byte.

At both inclusive boundaries the resource envelope does not reject; the remaining ordered decode
pipeline applies, including UTF-8/BOM and JSON checks before the v0.2 semantic classifier.
Oversize or excessive depth deliberately preempts every latent `001..015` condition because the
semantic classifier is not entered for an envelope-rejected candidate.

## 3. New diagnostic and exact result

`OFF-I1-INVALID-016` means exactly
`RESOURCE_ENVELOPE_EXCEEDED_BEFORE_SEMANTIC_CLASSIFICATION`. It is the only resource-envelope
diagnostic. It must not be reused for UTF-8, BOM, JSON syntax, catalog, state, relationship,
counter, hash or canonical-byte defects.

The existing diagnostic catalog grows from 19 to 20 values. The semantic classification table
remains exactly 15 ordered rows and is not renumbered; the resource envelope is exactly one
separate pre-semantic row.

For a direct public `decodeSnapshot(byte[])` call, envelope rejection throws the existing typed
`ContractFault` with diagnostic `OFF-I1-INVALID-016`; it returns no `RunState`, creates or mutates
no state or snapshot, and does not mutate the caller-owned input bytes. This amendment does not
change the inherited public API shape.

For a `RESTORE:=` action that receives an envelope-rejected candidate, the step result is exact:

- `selectedRuleId=null`;
- `outcome=INVALID_INPUT`;
- `diagnosticCode=OFF-I1-INVALID-016`;
- prior state, ledgers, replay cache and counters remain byte-for-byte unchanged;
- `flowAppend=null`, `queueAppend=null`, and every counter delta is zero;
- snapshot creation, write or replacement is forbidden and returned snapshot bytes are `null`.

In both entry paths, `OFF-I1-INVALID-016` never becomes part of a valid `RunState`, replay record, queue row,
`lastResult` or canonical snapshot. Therefore the inherited snapshot schema identifier remains
exactly `poc-offline-i1-snapshot-v0.2`.

## 4. Finite valid-domain proof

The selected limits strictly exceed every valid v0.2 canonical snapshot. This is a
schema/catalog proof rather than a measurement of current encoder output.

Exact valid-domain bounds are:

- maximum valid container depth: 5;
- maximum flow rows: 67;
- maximum queue rows: 68, comprising at most one seed row plus at most one append per action;
- maximum replay rows: 68;
- maximum `lastResult` objects: 1;
- fixed snapshot/flow/queue/result schemas: 12/12/19/7 ordered fields;
- fixed replay schema: 11 ordered fields;
- all snapshot strings are ASCII without escaping and at most 64 bytes;
- every signed non-negative int64 token is at most 19 decimal digits;
- the graph contains at most 3587 JSON object members: 12 top-level, five top-level state-vector,
  1474 flow-row and nested-vector, 1292 queue-row, 748 replay-row, and 56 maximal-last-result
  members;
- a conservative 134-byte allowance for each member's local key, colon and scalar token
  contributes at most 480658 bytes; child-member bytes are counted separately;
- every brace, bracket, object separator and array separator contributes at most 8192 additional
  bytes, so the complete conservative bound is at most 488850 bytes and is strictly below 524288
  bytes.

Thus `provedValidCanonicalSnapshotBytesUpperBound=524288`, while the admitted byte ceiling is
1048576 and admitted container depth is 32. A valid snapshot cannot reach either envelope limit.
Any future schema, catalog, string bound, ledger-row bound or action-count expansion invalidates
this proof and requires a new versioned decision before implementation changes.

The prospective exhaustive valid-domain test must materialize and round-trip every valid action
prefix: scenarios 001–025 contribute 25 initial plus 65 post-action snapshots = 90; scenario 026
has 24 eligible row/phase pairs with initial plus two post-action snapshots = 72; total exact
snapshot count = 162. Every one must be below 524288 bytes, have depth at most 5, and decode to a
deeply equal immutable graph with byte-identical canonical re-encoding.

## 5. Remediation input pins and exact local scope

The following are remediation inputs only, not accepted implementation outputs:

| File | Bytes | SHA-256 | Status |
|---|---:|---|---|
| `tools/offline_i1_oracle/src/main/java/com/monumentogram/dora/stage0/offline/i1/OfflineI1Oracle.java` | 213093 | `7eb67b202722339d73ad70f1781a7d5ccddb89e5a1805361aeac2793eb506576` | `REMEDIATION_INPUT_ONLY_NOT_ACCEPTED_OUTPUT` |
| `tools/offline_i1_oracle/src/test/java/com/monumentogram/dora/stage0/offline/i1/OfflineI1OracleTest.java` | 237249 | `7b40cea7586cd89034ad2e3a3ed3ef26eea4374dc9aaa61bffd0f522b81468b2` | `REMEDIATION_INPUT_ONLY_NOT_ACCEPTED_OUTPUT` |

After strict packet validation and a clean independent hash-bound review, the only prospective
implementation scope is these same two Java files in package
`com.monumentogram.dora.stage0.offline.i1`. No helper, generated source, module, Gradle file,
plugin, repository, coordinate, dependency, Android source or workflow may change.

The implementation must add the twentieth diagnostic, route byte overflow and depth-33 entry only
to `OFF-I1-INVALID-016`, preserve all v0.2 `001..015` ordering inside the admitted domain, and add
exact 1048576/1048577-byte, depth-32/depth-33, restore-zero-delta and 162-valid-snapshot tests.
Accepted output hashes belong only to later implementation evidence and are not placeholders in
this packet.

The local toolchain remains exactly Microsoft OpenJDK `17.0.10+7-LTS`, Windows x86_64:

| Tool | SHA-256 |
|---|---|
| `java.exe` | `98fd4a0eec7fa39abbc2b3f55007ed9c8c24ef8fa5d7c04c3895b4c5915ec3f1` |
| `javac.exe` | `0e79806dea4681cf6bb1fd41b4d5ba8c579481cb70a28890fe65813639294ffe` |
| JDK `release` metadata | `f463618c7067d1d7421a2aacdc8a4cfa939e9844436462c1c165cbed20a9769a` |

The v0.2 exact direct `javac`/`java` argv, minimal environment, external validated OS-temp,
cleanup and forbidden-API boundary remain inherited without change. No execution is authorized by
this packet.

## 6. Authority, activation and proof ceiling

All inherited 25 `authorityFlags` remain false. All v0.2 `scopeFlags` remain false, including
validator/test/build execution, data-store/human-data access, dependency, Android integration,
status mutation, publication, formal Security approval, formal reviewer status and PR
advancement. Implementation, execution, device, emulator, network, model, provider, production,
Legal, Security, commit, push, PR, Ready and merge authority remain false.

The only positive scope facts are
`prospectiveLocalImplementationScopeAllowed=true` for the exact two-file remediation and
`activationRequiresCleanIndependentAdvisoryReview=true`. In this immutable packet,
`activationConditionsMet=false`, `implementationCreatedByThisPacket=false` and
`implementationExecutedByThisPacket=false`.

Activation requires strict two-file packet validation plus a separate immutable external review
record at
`docs/evidence/poc-offline-001/reviews/off-i1-resource-envelope-003.external-review.json`.
That record is not part of the packet-authoring scope and is created only after a clean review of
the exact Markdown and JSON bytes. Its exact key order is `packetId`, `mdPath`, `mdBytes`,
`mdSha256`, `jsonPath`, `jsonBytes`, `jsonSha256`, `reviewerModel`, `reviewerOrganization`,
`reviewerRole`, `formalReviewer`, `reviewedAt`, `timezone`, `verdict`, `packetP0`, `packetP1`,
`packetP2`. It requires `packetId=OFF-I1-RESOURCE-ENVELOPE-003`,
`reviewerRole=INDEPENDENT_ADVISORY_REVIEWER`, `formalReviewer=false`, `verdict=CLEAN`, and all
three severity counts zero. Its timezone is exactly `Europe/Moscow`; serialization is strict UTF-8
without BOM, LF, two-space JSON, exactly one final LF, no duplicate, unknown, missing or reordered
key, and no self-hash. Any packet-byte change invalidates it.

The external record activates only this local prospective two-Java-file remediation scope. It
does not authorize execution, commit, push, PR mutation, Ready, merge, publication or formal
approval.

## 7. Acceptance and unchanged program truth

Packet acceptance requires strict duplicate-aware JSON, exact Markdown binding, semantic parity,
the three exact parent pins, two exact remediation-input pins, exact limits and depth definition,
one envelope row, 20 diagnostics, 15 unchanged semantic rows, exact restore behavior, the
schema-based 524288-byte/depth-5 proof, the 162-snapshot prospective test count, inherited
toolchain/scope/flags, UTF-8 without BOM, LF, one final newline, privacy/secret/absolute-path
checks, exact two-file packet-authoring scope, and a clean independent hash-bound advisory review.

This packet may claim only
`ADDITIVE_DECISION_PACKET_COMPLETE_PENDING_INDEPENDENT_ADVISORY_REVIEW`; after a conforming
review it may claim `LOCAL_PURE_HOST_RESOURCE_ENVELOPE_SCOPE_ACTIVE`. It may never claim that the
implementation exists, tests passed, the host oracle is evidence-complete, Offline passed, a
device/network/model/provider was exercised, a dependency or production component was admitted,
or any readiness blocker was closed.

`POC-OFFLINE-001` remains `TODO / NOT_READY / NOT_RUN / NOT_AUTHORIZED`. Every
`OFF-RDY-01..10`, product, integration, device, network, model, data, publication and production
gate remains unchanged. Backlog and Stage Status must not change for this amendment, and no PR is
advanced or merged.
