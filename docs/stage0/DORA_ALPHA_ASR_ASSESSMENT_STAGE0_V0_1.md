# DORA Alpha ASR 5.5 assessment — Stage 0 v0.1

25 September 2026. **PASS / CURRENT_MODEL_REJECTED_NEXT_CANDIDATE_REQUIRED**.

The exact current candidate is **VALID_FAIL / REJECTED_FOR_BOUNDED_ALPHA_QUALITY_GATE**.
All 48 retained primary results independently reconcile. No common evaluation defect was demonstrated.
POC-ASR-001 remains **BLOCKED / NOT_READY**: both approved language predicates failed and no alternate-candidate evidence exists.
No new inference, audio decoding, model download, tuning, PR or merge occurred.

## Evidence authority and integrity

The owner authorized task 5.5 only on `chat/alpha-asr-runner-scope`, starting at `59b825b16847401ac94242b74fd142b5347126b3`.
Fetched remote and local HEAD matched exactly; tracked/staged state was clean. This is an additive assessment, not a rewrite of the [5.4 campaign](DORA_ALPHA_ASR_CAMPAIGN_STAGE0_V0_1.md).
The [sanitized assessment JSON](../evidence/poc-asr-001/alpha-asr-assessment-stage0-v0.1.json) records exact byte hashes of all pre-existing public ASR evidence JSON files, the 5.4 report and all nine campaign implementation files, plus protected normalizer/oracle/allowlist identities.
All 59 frozen files and 745 retained private files were checked; original corpus, archive, journal, results and profiles were preserved. Java source uses the original LF Git-blob pin while the unchanged Windows checkout has its separately recorded byte digest.
The journal was opened in SQLite read-only immutable mode and passed integrity_check. The retained freeze precedes first corpus access and completion; every attempt binds that same freeze. Active configuration, source/runtime, model, executable, shared libraries and frozen Java class pins match. The campaign checks those pins before each case and after the loop. No configuration drift or post-drift result is evidenced; this is retained-record/source verification, not a continuous independent historical attestation.
PR [#86](https://github.com/Monumentogram/DORA/pull/86) is open and unmerged at head `b951bc454d550e33669ebf4f276a4b09177a99ca`, base `55940df0c95e919a00708ae57e1b8aa23d89b6de`; its last update remains 18 September 2026. It is not this task branch. Recovery and production native allowlist are unchanged.

## Independent reconciliation

Exactly 48 unique intended cases, 24 RU and 24 EN; one successful primary attempt each. Retries, substitutions of selected cases, failures, invalid attempts, timeouts and cancellations: zero.
For each case, independent traversal resolved the exact reference under the case key and reference SHA, read the original strict UTF-8 hypothesis, ran the unchanged 5.3A preparation, and supplied four fresh token arrays to a freshly compiled unchanged Java oracle/bridge. All text hashes, token arrays/counts, S/D/I, per-file results, terminal results and read-only journal records agreed. Language sums below were recomputed without calling the campaign aggregation helper.

| Locale / stream | Cases | S | D | I | Reference tokens | Hypothesis tokens | Errors | WER | Gate |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---|
| RU / raw | 24 | 116 | 18 | 17 | 390 | 389 | 151 | 38.7179% | Diagnostic only |
| RU / normalized | 24 | 87 | 19 | 16 | 400 | 397 | 122 | 30.5000% | FAIL |
| EN / raw | 24 | 56 | 8 | 2 | 280 | 274 | 66 | 23.5714% | Diagnostic only |
| EN / normalized | 24 | 44 | 8 | 2 | 293 | 287 | 54 | 18.4300% | FAIL |

RU allows at most 80 errors (20% of 400); 122 is 42 above the gate. EN allows at most floor(18% × 293) = 52 errors; 54 is two above. EN is not rounded to PASS. Gates, normalization and scoring are unchanged.

## Common-defect audit

**COMPLETE / NO_COMMON_EVALUATION_DEFECT_DEMONSTRATED.** Confirmed defect: no; affected scope: none demonstrated. High WER is not itself evidence of a harness defect.

| Check | Evidence and finding |
|---|---|
| 1. MP3 decode to mono float32 16 kHz | Frozen native main uses ma_decoder_config_init(ma_format_f32,1,16000); pinned miniaudio implementation, no replacement decoder. Header-only inspection independently accounts for all encoded frames. |
| 2. Channel and sample-rate handling | All 48 retained MP3s are mono 32000 Hz. Default miniaudio conversion produces 16000 Hz; no stereo cancellation or channel selection applies to these inputs. |
| 3. End-of-audio and truncation | The read loop appends got frames before checking MA_AT_END, including the final partial block. The 60-second safety cap cannot affect these <=20-second clips. No malformed/truncated MP3 frame or unparsed audio bytes were found. |
| 4. Decoded frame count | For 48/48, retained decodedFrames equals the exact MPEG frame sample count rescaled from 32000 to 16000 Hz, and decoded duration equals frozen manifest durationMs. Zero frame deficit; no new decode was executed. |
| 5. Locale binding | Manifest, transfer binding, journal identity/request, stored result and fresh scoring preparation agree for all 24 RU and 24 EN; execute validates and forwards the explicit locale. |
| 6. translate=false | Frozen resolved profiles and native parameters both disable translation for RU and EN. |
| 7. detect_language=false | Frozen resolved profiles and native parameters disable language detection; explicit locale is authoritative. |
| 8. no_context=true | Native parameters and both resolved profiles set no_context=true. |
| 9. Fresh process/context | Each execute starts a native process and main initializes/frees its own model context; 48 distinct retained PIDs, nonoverlapping measurement intervals, and 48 verified case cleanups support isolation. |
| 10. initial_prompt=null | initial_prompt and prompt_tokens are null, prompt_n_tokens=0 in the source and frozen profiles. No cross-case prompt is supplied. |
| 11. Segment concatenation | Native writer emits every segment in ascending index with fwrite of exact segment bytes, no separators, trimming or correction. It checks write/close errors. Segment-level bytes are not separately retained, so this point is source-supported, not a new segment replay. |
| 12. Unicode and encoding | Hypothesis is strict UTF-8 from original bytes; reference SHA binds exact UTF-8. Length-framed Java input uses UTF-8 bytes and validates complete consumption. No replacement decoding is used in Python. |
| 13. Reference loading | Independent resolver requires exactly one hash-matching string under the intended case binding; all 48 exact reference hashes, transfer files and original archive match. |
| 14. Normalization | 5.3A source unchanged; CPython 3.12 / Unicode 15.0.0 contract enforced. Fresh 4698-fixture parity against the exact pinned upstream source passed; all 48 fresh preparations equal retained four-stream records. |
| 15. Java token-array bridge | Unchanged Java oracle and bridge freshly compiled for Java 17; all four length-prefixed arrays sent directly from fresh preparation. Original Java suite and synthetic bridge tests passed; all 48 raw/normalized counts match. |
| 16. S/D/I aggregation | Assessment sums fresh oracle S/D/I and reference tokens independently of campaign aggregate(). Both raw and normalized language totals equal public 5.4. Integer gate comparisons remain unchanged. |
| 17. Accidental trimming/insertion | Reference loading, strict byte decoding, native fwrite and fresh token preparation preserve exact text before the specified frozen normalization; fresh and retained text hashes and token arrays agree. |
| 18. Stale output/replay/contamination | Case directories use exclusive creation, native outputs use wx, audio/model transfer hashes are checked, salvage hashes bind retrieved output and cleanup removes each case. Journal, terminal, files and 48 unique intended identities agree with ordinal=1 and no extra case directories. |
| 19. Campaign order/state leak | Journal sequence equals the frozen RU24-then-EN24 manifest; one process/context per case. No context reuse found. Fixed language order still confounds language with time for observational performance comparisons; randomization was not retroactively introduced. |
| 20. Selected-file/reference pairing | All selected audio byte/hash identities and transfer reference keys/locales/upstream bindings match the original selected manifest; no substitutions, retries or pairing mismatch. |

The default pinned resampler is retained, including its built-in conversion behavior; no additional preprocessing/filter, trimming or VAD was introduced. Header inspection used no PCM decode or model invocation. Source/records support the bounded decision; they do not prove the absence of every possible runtime or acoustic limitation.

## Error distribution

Bins use the original frozen manifest duration in integer milliseconds: [1000,5000], (5000,10000], (10000,15000], (15000,20000]. All 48 fit exactly one bin. For this retained corpus, decoded duration also matches the manifest exactly. No per-case row or identifier is published.

| Locale | Duration | Cases | Reference tokens | S | D | I | Normalized WER |
|---|---|---:|---:|---:|---:|---:|---:|
| RU | 1-5 s | 4 | 24 | 8 | 1 | 1 | 41.6667% |
| RU | >5-10 s | 12 | 145 | 40 | 9 | 6 | 37.9310% |
| RU | >10-15 s | 3 | 70 | 15 | 5 | 4 | 34.2857% |
| RU | >15-20 s | 5 | 161 | 24 | 4 | 5 | 20.4969% |
| EN | 1-5 s | 7 | 33 | 12 | 1 | 1 | 42.4242% |
| EN | >5-10 s | 10 | 100 | 15 | 3 | 0 | 18.0000% |
| EN | >10-15 s | 4 | 75 | 14 | 2 | 1 | 22.6667% |
| EN | >15-20 s | 3 | 85 | 3 | 2 | 0 | 5.8824% |

| Locale | Zero-error cases | Above language gate-equivalent per-case ratio | S / D / I shares of errors | Top six errors / total | Top one / top three shares |
|---|---:|---:|---|---|---|
| RU | 0 | 21 | 71.3115% / 15.5738% / 13.1148% | 54/122 (44.2623%) | 9.0164% / 24.5902% |
| EN | 9 | 10 | 81.4815% / 14.8148% / 3.7037% | 30/54 (55.5556%) | 9.2593% / 27.7778% |

Per-case ratios are diagnostics only, never new acceptance gates. Top quartile means the six highest normalized error counts per language; tied counts do not change the sum. RU errors are spread across all 24 cases; EN across 15. Neither one case nor the top three dominate a majority of either language total. EN is more concentrated in the top six, but excluding them is not permitted. No demographic, accent, population or causal model-capacity conclusion follows from these distributions.

## Exact candidate disposition

- Model: `ggml-base-q5_1.bin`, 59707625 bytes.
- Model SHA-256: `422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898`.
- Runtime: `ggml-org/whisper.cpp v1.9.4`, commit `927cfce34f31707e17f2bff35c349632fb9e2c3a`.
- Decoding profile canonical SHA-256: `cd53fda162402675f54875de470b01c303f0ede9a2e0cefd6f1056e8fa381d39`.
- Measurement protocol canonical SHA-256: `0f1e456fb4b4b0da28a209abe94e42fe7a0ca599e5c5c794db16a6abe478b116`.
- Selected manifest SHA-256: `5d9e12fa76e9db54bc69bbd22396dbec733348db97bba353361ac701af64061c`.

**CURRENT_MODEL_QUALITY_RESULT = VALID_FAIL. CURRENT_MODEL_DISPOSITION = REJECTED_FOR_BOUNDED_ALPHA_QUALITY_GATE.**
This applies only to that exact model/runtime/profile and bounded 5.1 corpus. It is not a judgment on all Whisper models, quantizations, whisper.cpp configurations or production language populations. Capacity/quantization as a cause remains a hypothesis.

## Next experiment decision

Technical Plan §15 treats base q5 as a starting hypothesis. The following compares its documented directions; official project documentation was consulted only for architectural context, without selecting/downloading weights or starting admission.

### A. SAME_RUNTIME_HIGHER_CAPACITY_MULTILINGUAL_WHISPER

**Hypothesis:** The exact base/q5 capacity-quality tradeoff limits this bounded result; a higher-capacity artifact may improve quality, with unknown device cost.

**Expected information:** Highest first-test comparability: changes the artifact while keeping runtime architecture and frozen decoding semantics. If quantization also changes, capacity and quantization effects remain confounded; do not claim to isolate either cause.

**Implementation delta:** Separate artifact admission and explicit new candidate identity/profile version for model-bound fields; retain current native adapter and whisper.cpp commit when compatible. Any required code/runtime change must be prospectively documented as a confounder.

**Artifact/license work:** Future exact authoritative source, upstream revision, conversion/quantization provenance, bytes, SHA-256, license/redistribution notices and compatibility/build/ABI/16-KiB checks. No exact downloadable artifact selected or admission started here.

**Resource direction:** Expected increase in storage, computation and working memory from greater capacity; an unmeasured hypothesis. POCO M5 suitability is unknown.

**Question tested:** Quality first; device fitness remains a separate mandatory result.

**Held constant:** Runtime commit, CPU architecture/threads, decoder parameters, locale policy, input conversion, normalizer, Java oracle, RU/EN gates, measurement definitions and controlled device conditions.

**New evidence required:** Prospectively frozen single-candidate protocol, admitted exact artifact, approved resource criteria, explicit data/retention authority and held-out evaluation design; full attempts, quality, timing/memory/thermal, cleanup and privacy evidence.

### B. ALTERNATIVE_LOCAL_SHERPA_ONNX_COMPATIBLE_ASR

**Hypothesis:** An independently chosen local ASR architecture may offer a different RU/EN quality/resource tradeoff.

**Expected information:** Independent family comparison, but runtime, preprocessing, tokenizer and decoding differences make causal attribution harder.

**Implementation delta:** New isolated adapter and family-specific frontend/decoder, output binding, lifecycle and instrumentation validation.

**Artifact/license work:** Admit exact RU/EN-capable weights, graph/tokenizer/config and runtime separately, including source, digest, license/redistribution and Android ABI/16-KiB evidence.

**Resource direction:** Unknown and model/backend dependent; neither smaller nor faster is assumed.

**Question tested:** Quality and local device fitness.

**Held constant:** Consent-governed evaluation design, reference authority, frozen normalization/oracle/gates, device and comparable measurement definitions; model-specific frontend differences explicit.

**New evidence required:** Frontend/decoder synthetic correctness, model-language coverage, frozen adapter/configuration and complete local quality/resource campaign evidence.

### C. VOSK_SMALL_WEAK_DEVICE_FALLBACK

**Hypothesis:** A small language-specific candidate may provide useful processing within a weaker-device budget; improved RU/EN quality is not assumed.

**Expected information:** Tests resource fallback viability, not the leading explanation for the current quality miss.

**Implementation delta:** Separate Vosk adapter and explicit per-language package selection; validate streaming/finalization and exact result text assembly.

**Artifact/license work:** Separate exact RU and EN artifact provenance, bytes/digests and package-specific licenses, runtime/ABI and redistribution review.

**Resource direction:** Lower resource demand is the fallback hypothesis; no DORA measurements or guaranteed savings are available.

**Question tested:** Primarily weak-device fitness/fallback; quality must still be measured against approved criteria.

**Held constant:** Selected language, authorized evaluation data, reference/scoring/gates and device measurement definitions; no unapproved relaxation of RU/EN gates.

**New evidence required:** Each admitted language package must have full result completeness, WER, memory/latency/thermal and finalization evidence; mixed-language behavior needs separate scope.

### D. FASTER_WHISPER_LARGER_WHISPER_HYBRID_REFERENCE

**Hypothesis:** A larger server-side Whisper route, including the plan direction large-v3-turbo, may establish a useful quality reference under a different compute budget.

**Expected information:** Quality reference and opt-in hybrid feasibility; cannot establish local-core fitness or isolate model capacity from runtime differences.

**Implementation delta:** Separate consented server adapter, versioned worker environment, protected transfer/result binding and deletion receipts; runtime-specific defaults frozen explicitly.

**Artifact/license work:** Admit exact model conversion/weights, CTranslate2/faster-whisper runtime, licenses/digests and server data-handling scope independently.

**Resource direction:** Moves heavy inference to server resources; network delay, server memory/compute/cost and client overhead require new measurements.

**Question tested:** Hybrid quality reference and fallback strategy, not an assumed local replacement.

**Held constant:** Exact references, normalizer/oracle and quality gates on an explicitly consented evaluation design; report changed runtime/hardware and transmission boundaries.

**New evidence required:** Explicit cloud/reference consent and retention authority, server identity, quality and operational measurements, transfer/security/deletion receipts; zero upload in 5.5.

Context sources: [whisper.cpp](https://github.com/ggml-org/whisper.cpp), [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx), [Vosk model catalog](https://alphacephei.com/vosk/models), [faster-whisper](https://github.com/SYSTRAN/faster-whisper).

**Recommended next test: A — same whisper.cpp runtime plus one separately admitted higher-capacity multilingual Whisper artifact.** This minimizes confounders and tests the capacity-quality hypothesis. It does not establish that the candidate is better or will pass. Prefer comparable quantization where feasible; changing both capacity and quantization cannot distinguish their separate effects. No exact downloadable artifact is selected. No admission, download or campaign is executed in 5.5.
The 48 clips are already evaluated. No beam/best_of/temperature/prompt/suppression/VAD/language-policy search is authorized. Any authorized reuse is a bounded regression comparison, not a fresh blind holdout. Before tuning or selecting models, define separately consented development/tuning data and an untouched held-out evaluation, preregister the candidate/profile and retain all attempts. Existing reference/scoring semantics remain fixed.

## Performance/memory owner proposal

**PROPOSED_FOR_OWNER_DECISION — not APPROVED.** Existing numeric RTF, PSS and native-heap gates remain **NOT_EVALUATED / PROPOSED_NOT_APPROVED**. Thresholds must be chosen before the next measured comparison to prevent post-result acceptance criteria.

Observed POCO M5 facts: RTF median 0.6756, nearest-rank p95 2.0015, maximum 2.8373, duration-weighted aggregate 0.5901; maximum observed PSS 158107648 bytes; maximum sampled allocated native heap 728918776 bytes. No OOM failure was observed, but no system-wide OOM trace was collected. Thermal NONE → NONE → NONE; SEVERE-or-worse false. Timestamp quality NOT_EVALUABLE.
Propose keeping per-case inference-only RTF = inferenceElapsedMicros / decodedAudioDurationMicros and separately recording cold model-load and end-to-end latency. Owner must designate which of median/p95/max/duration-weighted aggregate are gates and assign numeric limits for the target device tier and user wait-time requirement.
Propose separate ceilings in bytes for maximum observed PSS and allocated native heap, using the same 100-ms decode-through-release sampling and <=1000-ms gap validity. These metrics measure different things: do not add them or use either as a continuous peak. Owner must set OS/background headroom, missing-telemetry/OOM/thermal invalidation policy and any cold-load/end-to-end limits. Baseline maxima alone cannot justify safe thresholds for a larger model. No numeric approval or production promise is inferred, and existing gate semantics are not changed.

## Validation, privacy and retention

Fresh checks: 99 ASR host tests passed; unchanged normalizer passed 4698 pinned-source parity fixtures; original Java oracle suite passed; Stage 00 artifact validation passed. Required offline Android formatting/static analysis, host unit tests, instrumentation compilation, lint and debug assembly passed: 197 tasks, five executed and 192 up-to-date. No connected-device tests or inference ran. CI was not run; no matching task-branch push trigger or PR exists.
The unchanged production APK native verifier passed all four ABI entries for the single allowlisted library; APK zipalign with 16-KiB page checks also passed. These do not admit an ASR runtime into the product.
New inference = 0; new audio decode = 0; downloaded models = 0. Public hypotheses, references, token arrays, per-case rows, sample IDs, private paths and clips = 0. Private reconciliation stays in owner-only storage outside Git. Existing device cleanup remains the verified 5.4 evidence, not a new device observation.
Retention remains **ACTIVE_THROUGH_5_5_THEN_DELETE_WITHIN_30_CALENDAR_DAYS**. With assessment completion on 25 September 2026, the default deletion deadline is **25 October 2026**. **RETENTION_ACTION_REQUIRED:** owner must explicitly authorize any alternate-model data reuse/extension with purpose, scope, copies, access, expiry and deletion receipts; otherwise execute the existing deletion obligation by that deadline. No private data was deleted during this assessment and no extension was silently granted. The next-experiment recommendation alone is not authorization to retain or reuse data beyond the existing obligation.

## Stage state and stop boundary

5.1 retains bounded PASS; 5.2 retains model/source admission PASS for Stage 0 evaluation; 5.3 remains PRE_5_4_PREPARATION_COMPLETE. 5.4 remains PASS / BOUNDED_POCO_48_CLIP_CAMPAIGN_COMPLETE with both quality FAILs unchanged. 5.5 is PASS / CURRENT_MODEL_REJECTED_NEXT_CANDIDATE_REQUIRED. POC-ASR-001 remains BLOCKED / NOT_READY.
Publication scope is four documentation/evidence files in one atomic commit on the authorized branch. Historical status/backlog text is preserved additively. Production admission is false; PR #86 and Recovery are untouched. No PR or merge. Stop after this assessment; do not start another ASR campaign or download another model.
