# DORA expanded untouched holdout — Stage 0 v0.1

Task **5.6D.1**, 27 September 2026. **PASS / UNTOUCHED_EXPANDED_HOLDOUT_AVAILABLE**.
Starting HEAD `5106a225b442bf58d4ca72908b43087b4b5a45a8`, branch `chat/alpha-asr-runner-scope`.
The owner's autonomous Stage5 closeout instruction separately expands RU source authority;
the previous SPS5 train shortage remains an immutable historical result.

## Source and eligibility

RU uses official Common Voice Scripted Speech27.0, release `cv-corpus-27.0-2026-09-11`, explicit `test`;
EN reuses exact retained SPS5.0 `train`. Both CC0-1.0, MDC consumer constraints,
owner-only storage, bounded internal evaluation, deletion deadline2026-10-25.
The official RU archive was packaged as one download:7093766588bytes,
SHA256`3f269caf4757a7837048153067ca84e29c53fd5dee9394a55e6ecc92afb6055a`. This is an independently pinned downloaded identity, not a claimed publisher checksum.
No SPS blank split was admitted. Official metadata-first alternatives were reviewed; no alternative audio was acquired.

| Sequential remaining pool | RU | EN |
|---|---:|---:|
| complete_expanded_inventory | 10328 | 1569 |
| historical_source | 10328 | 1569 |
| historical_audio_digest | 10328 | 1569 |
| historical_participant | 10328 | 1443 |
| normalized_reference_token_count_at_least_one | 10326 | 1335 |
| existing_raw_source_decode_validity | 10326 | 1335 |
| duration_1000_to_20000_ms_inclusive | 10326 | 1112 |
| all_members_of_duplicate_audio_groups | 10326 | 1112 |

Eligible distinct provider participant keys: RU2233/EN224.
Historical exclusions:144 sources,144 audio digests,55 participants, including unexecuted/invalid selections.
Fresh whole-store reconciliation found no additional consumed identities. Every selected48 is now consumed.
Exact reference SHA binding precedes unchanged production normalization; two RU references normalize empty.
Official Scripted TSV uses literal quotation characters; independent direct-byte parsing confirms every reference.
Source/raw/decode/duration1000..20000ms and all-members duplicate-group rules are unchanged.

## Frozen holdout and limitations

Selected and materialized exactly RU24+EN24,48audio+48reference files; deterministic rank, no manual replacements.
Selected distinct provider keys: RU24/EN19.
Known historical source/audio/provider-key overlap is zero. Scripted and Spontaneous participant namespaces differ:
**cross-corpus real-world participant equality is UNKNOWN**. No identity inference/deanonymization occurred.
This is candidate qualification on a fresh holdout, not a paired causal estimate of quantization improvement.
Whole-file private pins and complete sequential counts are in [sanitized evidence](../evidence/poc-asr-001/asr-expanded-holdout-stage0-v0.1.json).
No upstream sample paths, participant hashes or transcript text are published.

## Verification and next action

28 targeted selector/reference/history tests,4 tar-integrity tests,4 prior-reconciliation tests and2 literal-quote
TSV tests pass. Independent recount agrees; selectedRU24 were independently decoded/probed again;
EN remains byte-identical accepted train evidence. All historical private and tracked evidence bytes were checked.
Initial CSV-format mismatch stopped before selection; the official unquoted format was verified and no text was repaired.
Model downloads/freezes, native loads, ASR inference and device operations in this phase are all0.
Historical BASE/SMALL/ARM82 results, Recovery0D.6, PR#86, normalizer/oracle, decoding and gates remain unchanged.

5.6D.1 data authority is ready. Stage5 and POC-ASR-001 are not yet PASS.
Continue the already-authorized q8 artifact/init/operator phase5.6D.2; no further owner approval is required.
