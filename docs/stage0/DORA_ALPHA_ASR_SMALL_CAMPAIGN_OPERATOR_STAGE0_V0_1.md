# SMALL campaign operator and evaluation profile — Stage 0 v0.1

Date: 2026-09-25. Task: **5.6C.2A**.

## Result and scope

**5.6C.2A = PASS / SMALL_CAMPAIGN_OPERATOR_AND_EVALUATION_PROFILE_FROZEN**.
This is host/synthetic preparation only. **5.6C.2B = NOT_STARTED / NOT_AUTHORIZED**;
**POC-ASR-001 = BLOCKED / NOT_READY**. No SMALL quality/resource/device conclusion,
production admission or all-device support follows. Historical BASE remains
`VALID_FAIL / REJECTED_FOR_BOUNDED_ALPHA_QUALITY_GATE`.

The owner supplied the complete pre-result design and exact seven-file scope.
The additive implementation separates a pure SMALL profile/evaluator from future
operator I/O, reuses the unchanged normalizer, Java oracle and model-independent
device/native helpers, and leaves historical BASE identities and reports intact.

## Frozen identities

Evaluation profile: `dora-alpha-asr-small-eval-profile-v0.1`.
Canonical SHA-256: `217b5662bfd22520779a2df2551f553cb17e97e11f0273e8238682a5f4b7f69f`.
Operator identity: `dora-alpha-asr-small-campaign-v0.1`.
Canonical source/test mapping SHA-256: `8d5574d0365c3796f8d73d00cd47fbec557d54ce7f28893c883bb45635141b3c`.
Individual checkout byte counts and SHA-256 values are in
[aggregate evidence](../evidence/poc-asr-001/alpha-asr-small-campaign-operator-stage0-v0.1.json).
Canonical JSON uses UTF-8, sorted keys, no insignificant separators and no NaN.

| Binding | Frozen value |
| --- | --- |
| selectedManifestSha256 | `2cb05132de945fe8a1b281e79efd8efa15d9b9b94c0326f6572a594e0c74e9bb` |
| transferIndexSha256 | `95c26be0501172196587181b3f46705b65237bde879a0e3ee8eb748cee2e1a87` |
| privateFreezeSha256 | `eb088082b2cde6d2bc2649e6cb6d67a92d3a68ad447a8a7331eec58c2794bcaa` |
| modelArtifact | `ggml-small-q5_1.bin` |
| modelBytes | `190085487` |
| modelSha256 | `ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb` |
| runtimeSourceCommit | `927cfce34f31707e17f2bff35c349632fb9e2c3a` |
| selectionContractSha256 | `4db7c7d67aac03504a1233f0f59cfe1bedee414917f28e2c915d337d404638ff` |
| governanceCommit | `dd1468dcaace9699e64b1945f83a9b532205d435` |
| sourceRecoveryCommit | `aa4e65cf4510f055260dca6a7e6e8f26916f732e` |
| decodingProfileSha256 | `cd53fda162402675f54875de470b01c303f0ede9a2e0cefd6f1056e8fa381d39` |
| measurementProtocolSha256 | `0f1e456fb4b4b0da28a209abe94e42fe7a0ca599e5c5c794db16a6abe478b116` |
| resourceGateVersion | `dora-alpha-asr-small-poco-m5-resource-gates-v0.1` |
| resourceGateSha256 | `fdfb56b56429c07a99538c92d87f9ea8570c723cc67f5e5eb02915d97557ccd1` |

The candidate profile imports only the model-independent semantic pins and
tokenization functions from 5.3A. It never adopts the BASE profile as current
truth. BASE and every changed nested SMALL field are rejected. The normalizer is
still OpenAI `BasicTextNormalizer(False, False)` at revision
`86098128c0b4f24f0e2aa2994de830614b474227`, source SHA-256 `4742eaa040e0657fa1247a1361e0d856c62317a43326ea59a40c2e9edd8d2c38`.
The unchanged Java oracle source identity is `a2b3e37305f24f91a8ace00f5f824d1d13effda94248c15b31aa31db78c76125`.
CPython 3.12 / Unicode 15.0.0 remains required. Four token streams match 5.3A;
generated texts score identically through the same Java oracle.

## Numerical predicates and evidence completeness

RU normalized aggregate WER is at most 20%; EN at most 18%. Sum oracle error
counts and reference tokens per complete 24-case language; compare integer
cross-products. Raw WER is diagnostic. Timestamp quality is NOT_EVALUABLE.

RTF is inference microseconds divided by decoded-audio microseconds. Require
all 48 valid cases: duration-weighted aggregate at most 2, nearest-rank p95 at
most 4 (rank 46), and per-case maximum at most 6. Exact fractions decide gates;
floating-point presentation never decides acceptance. Median is diagnostic.

Cold model-context load is at most 15000000 microseconds per case, with a fresh
process/context. PSS maximum observed is at most 1610612736 bytes. Native allocated
heap maximum sampled is at most 1342177280 bytes. These separate peaks are never
added. End-to-end duration is diagnostic only.

Memory target interval remains 100 ms, maximum valid gap 1000 ms; thermal delay
remains 1000 ms, maximum gap 3000 ms. Missing samples, coverage gaps, malformed
metrics, ambiguous termination, unfinished accounting or unverified cleanup cannot
produce resource PASS. A valid positive violation survives unrelated missing data.

Any SEVERE (3) or worse thermal observation is decisive. Preserve the current
attempt and stop safely using the unchanged process-ownership/alarm cleanup path;
do not add PID signalling or a temperature threshold. Pre-corpus severe observations
are retained separately and do not increment selected attempt count.

Evidenced OOM/allocation failure is decisive. The physical reader recognizes only
the exact owned-process libc++ `std::bad_alloc` termination line and binds retained
stderr bytes. Generic native codes 13/14, missing outputs and signal 9 are ambiguous.
There is no new system-wide LMK trace collector; generic failures remain incomplete,
never invented OOM evidence or PASS. The pure evaluator also supports an already
positively bound owned-process LMK receipt.

## Frozen attempt policy and disposition

Exactly one primary measured attempt per selected case, RU24 then EN24. No automatic,
quality or result-dependent retry. A permanent exclusive campaign marker prevents
reentry even if interruption occurs before case one. Each attempt is journaled
before execution. Existing/RUNNING attempts block a new start. On timeout, execution
failure, invalid evidence or unverified cleanup, retain the attempt and stop. Any
future retry requires a separate explicit owner decision after evidence review.
Per-case quality is not an acceptance gate and never changes order or triggers retry.

| Execution complete | Quality | Resource | Candidate disposition |
| --- | --- | --- | --- |
| True | PASS | PASS | `PASS / ACCEPTED_FOR_BOUNDED_ALPHA_ON_POCO_M5` |
| True | FAIL | PASS | `VALID_FAIL / REJECTED_FOR_BOUNDED_ALPHA_QUALITY_GATE` |
| True | PASS | FAIL | `VALID_FAIL / REJECTED_FOR_BOUNDED_ALPHA_RESOURCE_GATE` |
| True | FAIL | FAIL | `VALID_FAIL / REJECTED_FOR_BOUNDED_ALPHA_QUALITY_AND_RESOURCE_GATES` |
| False | NOT_EVALUABLE | FAIL | `VALID_FAIL / REJECTED_FOR_BOUNDED_ALPHA_RESOURCE_GATE` |
| False | NOT_EVALUABLE | NOT_EVALUABLE | `INCONCLUSIVE / CAMPAIGN_EVIDENCE_INCOMPLETE` |
| False | FAIL | NOT_EVALUABLE | `VALID_FAIL / REJECTED_FOR_BOUNDED_ALPHA_QUALITY_GATE` |
| False | PASS | PASS | `INCONCLUSIVE / CAMPAIGN_EVIDENCE_INCOMPLETE` |

A complete language's decisive quality failure is retained even when the other
language/resource evidence is incomplete; completeness flags remain explicit.
Absent a decisive failure, any incomplete evidence is INCONCLUSIVE. A full 48-case
execution with valid evidence can have
`PASS / BOUNDED_SMALL_POCO_48_CAMPAIGN_COMPLETE` while the candidate fails quality
or resources. Execution PASS never implies candidate acceptance.

## Future operator boundary

The CLI prints only the content-free profile; all other arguments are rejected
before reading paths. The future measured Python API is
`campaign(config, authorization)`. It requires separate explicit 5.6C.2B owner
authority; its authorization string is a scope guard, not an authentication system.
This task never calls that API with real inputs.

The future private config binds an independently owner-admitted operator commit,
acceptance directory, work directory, SMALL path, six native artifact paths,
clean runtime source, ADB executable and per-selected-case audio/reference paths.
The acceptance directory contains materialized-manifest.json, transfer-index.json
and pre-inference-freeze.json. Config cannot override the frozen profile or gates.

Before first inference, verify exact repository HEAD/parent/branch/clean state,
operator and historical source hashes, all three private canonical identities,
SMALL size/hash/name, runtime/native identities, decoding/measurement/resource
identities, all ordered input bindings, physical POCO M5 and resolved device
decoding parameters. Repeat frozen identity checks before each case and afterward.
Final identity failure and journal failure force execution incomplete.

Whole-canonical document checks are explicit. Only the manifest/transfer-index
readers allow their named self-digest projection; a manifest digest in another
document is a cross-document binding. Actual private schema compatibility is not
claimed here: no private document was opened. An unrecognized live shape or any
identity drift fails closed during separately authorized 5.6C.2B.

The immutable protocol still requires a pinned upstream codec decode-only check
and exactly one generated three-second silent WAV full-inference warmup before
corpus. No per-case warmups/repetitions are added. Future tests simulate this
sequence; actual warmup/model load/inference counts in this task are zero.

## Validation and preservation

New SMALL synthetic tests: **61 PASS**. Unchanged historical ASR tests: **113 PASS**.
Final combined run and ancillary checks are recorded in the aggregate evidence.
Tests cover every required identity mutation and numerical boundary, rank 46,
token/oracle parity, all disposition combinations, attempt stop/replay/cleanup,
malformed evidence retention, and allowlisted public output. Generated filesystem
fixtures exercise the real input verifier; repository guards and device command
methods are mocked without launching Git-controlled private reads or ADB.

Independent review identified and corrected final-verification PASS leakage,
loss of decisive observations across parser/journal errors, missing allocation
evidence, coupled timing predicates, invalid-quality continuation, implicit
document digest projection and identity-test gaps. Each correction has a
synthetic regression. Final review status is recorded in evidence.

64 named historical files carry unchanged checkout bytes/SHA-256/Git blobs.
Additionally, all 590 original tracked files outside the two additive
status/backlog records are checked unchanged, including all Recovery and production
paths. The old status/backlog bytes are retained with an inserted amendment.
Historical 5.3A, BASE 5.4/5.5, 5.6A/A.1/B/C.1/C.1.1, selectors, native source,
decoding/measurement files, Java oracle and native allowlist are preserved.

Every task count is **0**: private corpus/reference access; old hypothesis/result
access; fresh acceptance audio/reference access; model-byte access; model load;
audio decode; ASR inference; whisper_full; device connection/execution; measured
5.6C.2B attempts; threshold changes; candidate tuning. Generated test bytes and fake
device methods are synthetic fixtures, not actual data/model/device operations.
Retention deadline remains **2026-10-25** without extension.

## Publication

Branch: `chat/alpha-asr-runner-scope`. Local and freshly fetched starting remote
HEAD: `aa4e65cf4510f055260dca6a7e6e8f26916f732e`; tracked/index state clean.
Exactly seven changed files: the three new SMALL Python files, this report, its
JSON evidence, and additive status/backlog updates. One atomic commit, one branch
push, no PR/merge, no PR #86 or Recovery modification, no CI dispatch. Actual commit
and remote readback are returned after publication. Stop after push/readback;
5.6C.2B remains NOT_STARTED / NOT_AUTHORIZED.
