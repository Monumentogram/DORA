# ASR provider-train holdout availability audit — Stage 0

**BLOCKED / INSUFFICIENT_UNTOUCHED_HOLDOUT_AFTER_TRAIN_AUDIT**. The same exact retained release contains **RU0 / EN1569**
records explicitly marked `train`. After every required exclusion, **RU0 / EN1112** remain,
covering **RU0 / EN224** unique eligible participants. RU24+EN24 cannot be formed.
No ranking, selection, holdout freeze, model experiment freeze, inference or device execution occurred.

Branch: `chat/alpha-asr-runner-scope`. Required start and exact result-commit parent:
`2b50043e5422af1fe3f9af8ef1ac0f57b32044f9`. Fetch and remote readback both matched before audit work.
Machine-readable identities and whole-file private audit pins are in the
[sanitized evidence](../evidence/poc-asr-001/asr-train-holdout-availability-stage0-v0.1.json).

## Authority and exact retained inputs

The Project Owner authorized only a train-partition availability audit of the already-controlled
Common Voice Spontaneous Speech 5.0 RU/EN release `sps-corpus-5.0-2026-09-11`.
Same CC0-1.0 and retained MDC terms, `BOUNDED_INTERNAL_ALPHA_ASR_EVALUATION_ONLY`,
Project Owner / Data Custodian, owner-only controlled storage and retention deadline **2026-10-25**;
no extension, new source, release or corpus download. Terms evidence/material hashes are in JSON.

- **RU** MDC `cmu5mg3pr00simh07epeylc55`: `1789489294410-sps-corpus-5.0-2026-09-11-ru.tar.gz`, 93031233 bytes; SHA-256 `aa0910f5fef7f24afccf5bca075995f0b7a970d96da72626c80507d629d05a6f`.
- **EN** MDC `cmu5nqn1h00vwmi07b4dbk085`: `1789489472613-sps-corpus-5.0-2026-09-11-en.tar.gz`, 544267165 bytes; SHA-256 `390b5a54c9afe0cc01da039ad206248f85682f247dd2b27d4cc0ab9a68e860b6`.

Full archives were rehashed and their provider indexes reconciled with the accepted index hashes.
The retained byte parser and independent standard-library CSV parser agree on complete train
membership and exact reference, participant-key and duration bindings. Participant keys remain private.

| Provider index | Total rows | Explicit train | Explicit dev | Explicit test | Empty split |
|---|---:|---:|---:|---:|---:|
| RU | 783 | 0 | 71 | 385 | 327 |
| EN | 5898 | 1569 | 548 | 357 | 3424 |

An empty provider split is **not** train. Subtracting dev/test from the provider total would
incorrectly admit unassigned rows. Those rows were never candidates. Other partition metadata
was read only to reconcile historical identities; test was not rerun as a candidate campaign.

## Sequential exclusions

The accepted private prior-use audit was bound by its whole-file SHA-256. BASE48, SMALL48 and
all ARM82 48 selections reconcile to **144 source records / 144 audio digests / 55 participants**.
The retained prior-use ledger and four journals reveal no additional identities; any larger
authoritative set would have won. Unexecuted, failed and unscored selections remain consumed.

Counts charge overlapping exclusions only at their first applicable step:

| Check completed | RU remaining | EN remaining |
|---|---:|---:|
| complete_train_inventory | 0 | 1569 |
| historical_source | 0 | 1569 |
| historical_audio_digest | 0 | 1569 |
| historical_participant | 0 | 1443 |
| normalized_reference_token_count_at_least_one | 0 | 1335 |
| existing_raw_source_decode_validity | 0 | 1335 |
| duration_1000_to_20000_ms_inclusive | 0 | 1112 |
| all_members_of_duplicate_audio_groups | 0 | 1112 |

Unique eligible participants: **RU0 / EN224**. Source and audio historical exclusions remove
0/0; participant exclusion removes 0/126; normalized-reference exclusion removes 0/108;
raw/source/decode validity removes 0/0; duration removes 0/223; duplicate groups remove 0/0.
Every remaining eligible source/audio/participant key has zero overlap with the complete historical sets.

All 1569 train references were mechanically bound to exact source-reference SHA-256 before
the unchanged production `alpha_asr_eval_text_contract.normalized_tokens` was called.
`NORMALIZED_REFERENCE_TOKEN_COUNT >= 1` is checked before any possible ranking.
No transcript meaning was inspected and no reference bytes were repaired or rewritten.
The helper reuses the prospective wrapper and frozen hash rank; the historical dev selector is unchanged.

All 1569 train audio members were hashed. Only the 1335 records reaching decode validity
underwent host FFprobe and full FFmpeg decode-to-null checks, using the same pinned binaries,
strict error handling, file-only protocol and one decode thread. All passed, with exact provider/probe
duration agreement. The inclusive 1.0–20.0-second bounds then remove 223 EN records.
Every member of a duplicate-audio group among otherwise eligible candidates would be excluded,
including cross-language groups. No such group remains.

## Outcome and validation

Selection: **RU0 / EN0**. Selected audio/reference materialization: **0 / 0**. Holdout manifest:
**not created**. Ranking: **never called**, independently verified with a failing rank-function trap.
The 1335 temporary host-validation audio copies were deleted; no temporary candidate audio remains.
Private audit reference bytes and identifying inventory remain only in controlled storage.
All ASR inference, Android/device execution, model/dataset downloads and experiment freezes: **0**.

**20 focused synthetic host tests PASS**: 6 train-helper tests, 9 existing prospective-reference
tests and 5 frozen-selector tests. Tests cover first-applicable attribution, both-language shortage
(including empty inventory), anti-reuse, source/audio/participant isolation, exact reference binding,
normalized-empty rejection, inclusive duration bounds, decode validity, all-member duplicate exclusion,
deterministic hash selection on generated fixtures and input immutability.
The first new-test run failed because the helper did not yet exist; after implementation it passed.
An initial combined invocation could not resolve the frozen selector's directory-local import;
running its unchanged tests from their own directory passed all five.

Independent archive parsing and sequential recount PASS; reversed actual inventory produces exactly
the same audit; strict JSON, public/private boundary, local links and `git diff --check` PASS.
All **1732 historical private files** remain byte-identical. Of **625 accepted tracked files**,
only the two status/backlog documents gain a new entry; removing that exact insertion restores
their original bytes. All other historical files remain byte-identical. No broad Android/Gradle or
device test campaign was run for this host-only evidence/tooling change.

Private pool-audit whole-file SHA-256:
`158f888c8e0091cc4df532640748d115df75b64f0d3ca81a6e29f9a240773e7f`.
Private prior-use authority whole-file SHA-256:
`148265d6e39737a523d38a5bf2f88be34cba7888dd22a999366c5024ce4d01a2`.
The evidence JSON also binds inventory, reference bindings, decode audit, authority and verification files.

Historical ARM82 RU **73/349 = 20.916905% VALID_FAIL**, EN **3/24 NOT_EVALUABLE**, BASE/SMALL
results, q5_1 identity, whisper.cpp v1.9.4, thresholds, decoding, thread count, normalizer and oracle
remain unchanged. Recovery 0D.6 and PR #86 are untouched. No PR was created or merged.
This result is not Stage 5 PASS or POC-ASR-001 PASS. Small q8_0 remains unfrozen.

**Exact next action:** STOP: the authorized same-release train audit is exhausted. Project Owner must decide a separately authorized data-authority next step that preserves every isolation rule; no further source/partition audit, q8_0 experiment freeze or execution is authorized by this result.
