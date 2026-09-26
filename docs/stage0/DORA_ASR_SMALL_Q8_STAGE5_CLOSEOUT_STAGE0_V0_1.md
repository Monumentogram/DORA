# DORA Small q8 — Stage5 terminal closeout

**Stage5: PASS / BOUNDED_ALPHA_LOCAL_ASR_CANDIDATE_ADMITTED**. **POC-ASR-001: PASS / BOUNDED_ALPHA_ONLY**.
Candidate: **PASS / ACCEPTED_FOR_BOUNDED_ALPHA_ON_POCO_M5**. Task5.6D.3, 27 September2026.
One measured campaign invocation; 48 primary attempts, zero retries/replacements.
Cleanup VERIFIED. No further candidate campaign or Stage6 implementation follows in this task.

## Immutable execution

Branch `chat/alpha-asr-runner-scope`; starting HEAD `5106a225b442bf58d4ca72908b43087b4b5a45a8`.
Data commit `15563e9fbcee7821bc9ff3f10520d4168b19b229`; exact clean operator commit `5a134a8e28501369deb3c12385a8bdd205557bd8`.
Before inference the private envelope bound all source/data/model/native/profile/gate identities:
canonical SHA256`1545b6fea075d227053f6251824c41f08e9c0d311d946c93ba81714f29ae2c4c`, whole-file SHA256`33c0d95406af2f054a1bf932aa8c7702d23be20d0c9797c42535d2fce33a2e80`.
Profile SHA256`cdc37f3f24876d19bda792f371caa38e6d7c46115a868f6612ebb53e16deec65`. One durable claim, RU24 then EN24;
one generated3s silence warmup, one primary per case, no automatic retry or replacement.
Unchanged stop precedence and evaluator were used; all partial evidence is retained.

## Quality and resources

| Language | Complete valid | Normalized errors/reference tokens | Threshold | Gate |
|---|---:|---|---:|---|
| RU | 24/24 | 36/191 = 18.848168% | <= 20% | PASS |
| EN | 24/24 | 31/333 = 9.309309% | <= 18% | PASS |

| Resource | Observed | Threshold/required coverage | Gate |
|---|---:|---:|---|
| coldLoadMicros | 696421 | <= 15000000 | PASS |
| evidencedOomEvents | 0 | <= 0 | PASS |
| maximumRtf | 3.3838938888888888 | <= 6.0 | PASS |
| maximumThermalStatus | 0 | < 3 | PASS |
| p95Rtf | 2.3646576305220885 | <= 4.0 | PASS |
| peakNativeHeapBytes | 1026393880 | <= 1342177280 | PASS |
| peakPssBytes | 435043328 | <= 1610612736 | PASS |
| telemetry | 48 | 48 | PASS |
| weightedRtf | 1.1457327275602678 | <= 2.0 | PASS |

Exact rational/integer gates and complete accounting are in [sanitized evidence](../evidence/poc-asr-001/asr-small-q8-stage5-closeout-stage0-v0.1.json).
Missing coverage never becomes PASS. A decisive observed failure remains FAIL when another metric is not evaluable.
Init diagnostic wall time is separate from measured cold load; timestamp quality remains NOT_EVALUABLE.
The private collector ledger's original phrase "no model/corpus reads" was overbroad: private reference metadata
was read and retained text files hashed for reconciliation. The original ledger remains immutable;
the explicit correction is in sanitized evidence. No collector decoding, inference or scoring occurred.

## Data, model and claim limits

The [data admission](DORA_ASR_EXPANDED_HOLDOUT_STAGE0_V0_1.md) binds RU CV27 official test10328→10326,
EN retained SPS5 train1569→1112. Eligible provider keys RU2233/EN224; selectedRU24/EN24,
selected provider keys RU24/EN19; known historical source/audio/provider-key overlap0.
All48 remain consumed, whether executed or not. Cross-corpus real-world participant equality is UNKNOWN.
Exact source references are bound before unchanged normalization; every selected normalized reference has>=1token.
No text repair, transcript-based selection, historical rescoring or relaxed eligibility occurred.

The [q8 admission](DORA_ASR_SMALL_Q8_ADMISSION_STAGE0_V0_1.md) binds264464607bytes,
SHA256`49c8fb02b65e6049d5fa6c04f81f53b867b5ec9540406812c643f177317f779f`,
whisper.cppv1.9.4 commit`927cfce34f31707e17f2bff35c349632fb9e2c3a`, exact accepted ARM82 binaries,
CPU4/default scheduling/flash-attention and unchanged decoding. Static479tensors, init and lifecycle PASS.
POCO M5 Android14/API34/arm64,4KiB actual pages; ELF/APK16KiB checks do not prove actual16KiB-device runtime.
This result qualifies only this bounded fresh holdout/device, not a paired quantization comparison or production admission.
The accepted task model remains only within bounded-evaluation storage and the2026-10-25 retention deadline.

## Verification and unchanged history

Data tests28, tar4, prior-use4, TSV2; operator/evaluator/normalizer tests98 including15newoperator tests PASS.
Stage00 artifact checks7/7, offline Gradle197tasks, four-ABI app APK alignment PASS.
Strict JSON, new local links, private/public boundary, immutable historical bytes and additive status/backlog checks PASS.
Exact-final-HEAD CI is reported separately after push. Whole-file private evidence digests bind the retained audit and measurements.
No audio, reference, hypothesis, participant ID/hash, upstream sample locator or device serial is published.
Historical BASE/SMALL/ARM82 results (including RU73/349 and EN3/24), Recovery0D.6 and PR#86 are unchanged.
Normalizer/oracle, thresholds, decoding and production Android are unchanged. No hidden retries or tuning.

## Stage5 closure and Stage6 handoff

5.6D.1 PASS data;5.6D.2 PASS artifact/init/operator;5.6D.3 terminal result above.
**PASS / BOUNDED_ALPHA_LOCAL_ASR_CANDIDATE_ADMITTED**. This is bounded-alpha admission only.
The closure follows the owner's section11 bounded-alpha decision rule: data, artifact/init/operator,
quality, resource, telemetry and cleanup evidence are reconciled above. This does not close the full
production/general-device POC matrix; actual16KiB runtime and timestamp quality retain their explicit limitations.
Stage6 may consume this bounded POCO admission while preserving all limitations; do not generalize it to production.
The CTO sheet was not edited; use these exact Stage5/5.6D/POC-ASR-001 statuses after independent verification.
