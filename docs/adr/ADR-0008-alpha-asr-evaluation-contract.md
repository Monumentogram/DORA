# ADR-0008: Bounded Alpha ASR host evaluation contract

Status: Accepted for Stage 0 task 5.3A only
Date: 24 September 2026
Authority: Project Owner's explicit 5.3A instruction
Related: POC-ASR-001, stage0-v0.1, bounded 5.1/5.2

## Context

I1 Java scoring deliberately consumes pretokenized inputs and caller-matched anchors.
Its mechanics do not establish normalization policy. The 48-clip RU/EN Alpha corpus and exact
model are separately pinned. No inference is authorized here; word timestamp truth is absent.

## Decision

Freeze [the contract](../stage0/DORA_ALPHA_ASR_EVAL_CONTRACT_STAGE0_V0_1.md) under the owner's
explicit pre-inference normalization, result-field and claim instructions.
Use a repo-owned stdlib equivalent of pinned OpenAI BasicTextNormalizer(False, False);
keep raw whitespace tokenization diagnostic. Pin CPython 3.12 / Unicode 15.0.0 and verify
exact regex/NFKC/category/lowercase/whitespace parity with deterministic synthetic fixtures.

Leave Levenshtein and timestamp aggregation in the unchanged Java oracle. Bind RU <=20% and
EN <=18% normalized aggregate WER without mixed/noisy/speakerphone claims. No anchors:
timestamp quality NOT_EVALUABLE and both timestamp gates NOT_EVALUATED.
RTF/PSS/native-heap thresholds remain PROPOSED_NOT_APPROVED.

Freeze the closed private per-attempt schema with immutable profile identities, text hashes,
counts, oracle S/D/I, WER contributions, errors and reserved observations.
Publish only allowlisted aggregate evidence, never per-case text, tokens, hashes, manifests,
selected paths or private locators.

## Consequences

This closes only the bounded Alpha text contract. Python/Unicode drift requires prospective
review. Since the unchanged oracle lacks UNKNOWN acoustics, its mechanical pilot slot is QUIET,
with all acoustic projections suppressed and no acoustic ground-truth claim.

No production dependency, Android/backend/user-facing code, runtime, audio or private manifest
is admitted or accessed. 5.3B/5.3C remain NOT_STARTED, 5.4 NOT_AUTHORIZED,
ASR_INFERENCE NOT_RUN and MODEL_QUALITY NOT_EVALUATED. Earlier evidence remains immutable.
The owner explicitly requires one task-branch commit/push, no PR and no merge.

## Verification

Synthetic Python tests, pinned-source parity, deterministic repeats, unchanged Java source and
tests plus synthetic token handoff, JSON/Markdown checks, privacy/scope scan and git diff checks.
Exact source pins and outcomes are in the sanitized 5.3A evidence.
