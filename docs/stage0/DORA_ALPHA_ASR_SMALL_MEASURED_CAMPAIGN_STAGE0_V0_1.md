# SMALL measured campaign — Stage 0 v0.1

Task **5.6C.2B**. Frozen execution result: **INCOMPLETE / BOUNDED_SMALL_POCO_CAMPAIGN_STOPPED**.
Candidate disposition: **VALID_FAIL / REJECTED_FOR_BOUNDED_ALPHA_RESOURCE_GATE**.

## Execution and immutable identity

Exactly one real campaign API invocation; 19 primary attempts,
19 finalized journal records, 0 running records,
and zero retries. Codec execution and the single generated-silence warmup
completed successfully. The unchanged frozen sequencer determined terminal behavior.

Execution used a separate Git-clean repository on `chat/alpha-asr-runner-scope`,
HEAD `9d92549b504d04e4b9022ce1f4fae793a7c4bdd0`, parent
`5c148a966a927842d15aa470d62b3629f62e354e`. All 77 unique raw-file pins passed
unchanged `verify_repository()`. Frozen raw working-tree representation was reproduced
from independently pin-verified canonical copies, preserving normalized Git content.
A fresh index was initially populated from the frozen tree; no source/history rewrite,
verifier bypass, operator edit, or new operator version occurred. The earlier host
preparation failures and their local evidence remain preserved.

Process-local Windows PowerShell module isolation restored the accepted ACL-query environment.
Two newly created private directories received protected owner-only ACLs; existing ACLs
were unchanged. Full non-measured prelaunch passed repository/storage, 48 bindings,
96 content identities, model/native/runtime/profile/protocol/gates/decoder checks,
Java oracle compilation, physical POCO M5 identity, initial thermal and one-shot state.
The campaign repeated all checks at the frozen points; none were cached away or skipped.

Candidate: `ggml-small-q5_1.bin`, 190085487 bytes,
SHA-256 `ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb`.
Fresh holdout: exactly 24 RU + 24 EN; manifest
`2cb05132de945fe8a1b281e79efd8efa15d9b9b94c0326f6572a594e0c74e9bb`.
No model/data/decoding/normalizer/oracle/gate change, tuning, reselection or rematerialization.

## Quality

| Language | Completed | Observed normalized WER | Gate maximum | Frozen gate |
|---|---:|---|---:|---|
| RU | 19/24 | 17.615176% (65/369) | 20% | NOT_EVALUABLE |
| EN | 0/24 | NOT_EVALUABLE | 18% | NOT_EVALUABLE |

Overall frozen quality: **NOT_EVALUABLE**. Observed WER on fewer than 24
valid cases is a partial observation; it does not establish a complete-language gate.
No per-case average or rounded value replaces the frozen integer comparison.

## Resources

| Metric | Observed | Frozen limit | Gate |
|---|---|---|---|
| Weighted RTF | NOT_EVALUABLE | <=2.0 | NOT_EVALUABLE |
| p95 RTF | NOT_EVALUABLE | <=4.0 | NOT_EVALUABLE |
| Maximum observed RTF | 6.063749 (17681893/2916000) | <=6.0 | FAIL |
| Maximum observed cold load (microseconds) | 324106 | <=15000000 | NOT_EVALUABLE |
| Peak observed PSS (bytes) | 360809472 | <=1610612736 | NOT_EVALUABLE |
| Peak observed native heap (bytes) | 933925816 | <=1342177280 | NOT_EVALUABLE |
| Evidenced OOM events | 0 | <=0 | NOT_EVALUABLE |
| Maximum observed thermal status | 0 | <3 | NOT_EVALUABLE |
| Telemetry-valid primary attempts | 19/48 | Complete coverage | NOT_EVALUABLE |

Overall frozen resource result: **FAIL**.
Decisive frozen resource failures: **RTF_MAXIMUM**.
Observed maxima cover the retained attempts, not unexecuted cases. Weighted/p95 RTF
remain unavailable without the frozen 48-case timing coverage. Missing coverage cannot
create PASS; decisive positive failures retain the unchanged evaluator's meaning.
Thermal/OOM observations include applicable retained pre-corpus safety evidence.
Timestamp quality remains NOT_EVALUABLE. End-to-end timing remains diagnostic only.

## Evidence, cleanup and validation

The immutable terminal aggregate reconciles with the retained journal through the
unchanged frozen evaluator: **PASS**. Candidate disposition is copied verbatim;
infrastructure incompleteness and measured model/resource failure are not interchanged.
Terminal aggregate SHA-256: `b4a51b8474c6c0aa226e626e4eb662bd881d79578a687ca03ee349b272deb568`.

Frozen final device cleanup: **VERIFIED**; per-case cleanup verified for
19 attempts. The host campaign process and read-only observer exited.
The campaign marker, journal, terminal aggregate, private observations and prior failure
evidence remain preserved. Retention deadline stays **2026-10-25**, without extension.
Only sanitized aggregates are published; no corpus, model, per-case identifiers,
transcripts, private paths, device serial or sensitive ACL details are included.

Strict JSON, local references/privacy, exact four-file scope, additive Status/Backlog
preservation and diff hygiene: **PASS**. All 7 Stage00 checks: **PASS**. All 607
unchanged baseline files and 77 frozen raw-file pins retain their exact bytes.
CI is not dispatched: this branch has no matching push trigger and no PR is requested.
No Android implementation/build-input change or Recovery execution occurred.

## Stage effect and next action

5.6C.2B execution: **INCOMPLETE / BOUNDED_SMALL_POCO_CAMPAIGN_STOPPED**. 5.6C.2 measured completion:
**INCOMPLETE**. 5.6C has a recorded terminal candidate
outcome; no wider readiness claim follows. Stage 5 and Group B remain NOT_PASS;
POC-ASR-001 remains BLOCKED / NOT_READY pending the next scoped assessment/admission decision.
Recovery remains **0D.6 = ALPHA CLOSED / FULL OPEN**. PR #86 remains OPEN, DRAFT,
UNMERGED and untouched. No merge, canonical reset, force-push or isolated-checkout push.

Next action: Assess the measured resource rejection and decide a separately scoped, prospectively frozen candidate/runtime experiment. Do not rerun this campaign or tune on this holdout.

## Publication

One atomic result commit is prepared from canonical base
`eb375b01df2ab199291daa4f0460987f25fff6d3` on `chat/alpha-asr-runner-scope`.
Only this report, [aggregate evidence](../evidence/poc-asr-001/alpha-asr-small-measured-campaign-stage0-v0.1.json),
Stage Status and Implementation Backlog change. Earlier reports remain historical.
The actual result commit and normal-push readback are supplied in the task handoff.

Frozen authorities: [v0.3 operator](DORA_ALPHA_ASR_SMALL_CAMPAIGN_EXECUTION_OPERATOR_STAGE0_V0_1.md)
and [evaluation governance](DORA_ALPHA_ASR_SMALL_EVALUATION_GOVERNANCE_STAGE0_V0_1.md).
