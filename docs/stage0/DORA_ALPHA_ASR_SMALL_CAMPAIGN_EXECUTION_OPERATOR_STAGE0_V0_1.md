# SMALL execution-capable successor operator — Stage 0 v0.1

Task 5.6C.2A.2: **PASS / EXECUTION_CAPABLE_SMALL_SUCCESSOR_OPERATOR_FROZEN**.
This is a pre-result operator freeze. No measured campaign was executed.
Branch `chat/alpha-asr-runner-scope`; clean starting HEAD/freshly fetched remote
`5c148a966a927842d15aa470d62b3629f62e354e`; immediate parent `bdbdaca73c8e0fd9b297bde560cda4442892bd6d`.
Publication is exactly one additive six-file commit, without PR creation or merge.

## Composition and future authority

Operator: `dora-alpha-asr-small-campaign-operator-v0.3`.
Canonical operator SHA-256: `e07f881b11fce21fed27c93178524279af641abb716343c7333884bdc21fea96`.
Future API token: `OWNER_AUTHORIZED_5.6C.2B_V03`. Its presence in code is **not authorization**.
Wrong/missing authorization is rejected before any input/path access. The ordinary
CLI exposes profile/information only. A later owner/CTO execution task must pin the
independently reviewed published commit.

The future config has exactly **seven keys**: `operatorCommit`, `acceptance`,
`work`, `model`, `native`, `runtimeSource`, `adb`. The six native names are fixed.
Values locate inputs; they do not supply identities. Caller `bindings`, SHA overrides,
and unknown keys are rejected. The config is copied before verification/execution.

v0.3 calls unchanged v0.2 `bind_metadata` on the four fixed metadata documents.
It derives all 48 case paths from admitted materialized bindings, keeps them in a
read-only map, and requires the exact row and position before each case. The new
driver rechecks both current content identities before use. It never invokes v0.1's
broken `verify_inputs`, v0.1's top-level `campaign`, or v0.2's blocked `campaign`.

The historical v0.1 `run_sequence`, `read_attempt`, codec/warmup, partial evidence,
cleanup and safety-evidence methods are reused unchanged, together with the same
device adapter, journal, Java oracle and SMALL evaluator. No normalization, gate,
telemetry interpretation or disposition table is forked.

## Frozen authority and future precheck

| Authority | Canonical SHA-256 |
|---|---|
| Selection | `695f1e147fe968c8f75e7bea5e7a6edfd20f50e939c0bdbc2064d3a7db7d1947` |
| Materialized | `2cb05132de945fe8a1b281e79efd8efa15d9b9b94c0326f6572a594e0c74e9bb` |
| Transfer | `95c26be0501172196587181b3f46705b65237bde879a0e3ee8eb748cee2e1a87` |
| Freeze | `eb088082b2cde6d2bc2649e6cb6d67a92d3a68ad447a8a7331eec58c2794bcaa` |
| Selection contract | `4db7c7d67aac03504a1233f0f59cfe1bedee414917f28e2c915d337d404638ff` |
| Evaluation profile | `217b5662bfd22520779a2df2551f553cb17e97e11f0273e8238682a5f4b7f69f` |
| Decoding | `cd53fda162402675f54875de470b01c303f0ede9a2e0cefd6f1056e8fa381d39` |
| Measurement protocol | `0f1e456fb4b4b0da28a209abe94e42fe7a0ca599e5c5c794db16a6abe478b116` |
| Resource gates | `fdfb56b56429c07a99538c92d87f9ea8570c723cc67f5e5eb02915d97557ccd1` |

Model remains `ggml-small-q5_1.bin`, 190085487 bytes,
SHA-256 `ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb`. Runtime remains whisper.cpp v1.9.4,
commit `927cfce34f31707e17f2bff35c349632fb9e2c3a`. RU normalized aggregate WER <=20%
and EN <=18%; all 5.6B resource gates and evaluator semantics are unchanged.

Future precheck order is repository branch/HEAD/parent/clean tree; exact operator
and transitive/protected hashes; retention; controlled storage; four fixed metadata
reads; unchanged v0.2 validation and derived binding; 96 selected content size/hash
checks; model identity; six native identities; clean pinned runtime; decoding,
protocol, gates and decoder source identity. Only then can a device be constructed.
Reparse paths and work/input overlap fail closed. Model ACL checks use the admitted
store/file boundary; ancestor Git/reparse checks remain separate. The additive v3
private-storage checker retains the historical boundary while making ACL errors
terminating and rejecting a null ACL. Unreadable ACLs fail closed. Frozen public
records keep their finite floating-point fields using
the historical strict JSON reader; the private metadata parser remains unchanged.

The precheck repeats before warmup, before every case and at the end, as in the
historical sequencer. Current-case content identity is also checked by the adapter.
None of these content/model/device steps ran against real inputs in this task.

## One-shot behavior

The durable campaign marker is created exclusively and flushed before measured
work. Any existing marker, journal or terminal result blocks re-entry. Attempts
are journaled before invoking the backend, always ordinal 1, at most once per case.
No automatic, quality or result-dependent retry exists. The codec check runs once,
followed by exactly one generated 3-second silence warmup, without quality scoring.
Physical POCO M5 and per-language decoding checks remain unchanged.

Invalid/incomplete evidence stops the sequence; partial decisive resource failures
remain decisive under the frozen evaluator. Cleanup semantics, native OOM and thermal
interpretation are unchanged. The terminal aggregate is written once with exclusive
creation; a terminal write error is not retried. Setup failure after the marker is
retained as incomplete. No failure marker/journal is deleted to permit replay.

## Verification and real-operation accounting

Generated end-to-end tests: 37 PASS.
SMALL host suite: 136 PASS; all ASR host tests: 249 PASS
(including all 212 historical ASR tests). Stage00: seven checks PASS.
Tests cover the composed actual-schema shape, all 48 derived bindings, authority/config
rejection, repository and source identity, input/model/native/runtime/profile drift,
marker/journal persistence, codec/warmup counts, journal-before-backend ordering,
primary-attempt limits, no retries, cleanup, partial resource failures and terminal writes.
All execution tests use generated files and FakeDevice/FakeOracle; the historical
host suites retain the unchanged Java oracle checks. No unchanged flaky Java retry
was needed. Development RED failures are retained outside Git, followed by GREEN.

Actual metadata compatibility proof is reused from the immutable
[5.6C.2A.1 record](../evidence/poc-asr-001/alpha-asr-small-campaign-schema-remediation-stage0-v0.1.json):
48 cases, 24 RU + 24 EN in frozen rank order, 48 materialized bindings and 97 transfer
records. Byte-identical v0.2 is called directly by v0.3. No actual private package
was reopened: **private metadata reads = 0**. This does not claim that a future real
content/model/device precheck has already passed.

Every forbidden counter against real data/device is **0**: measured attempts,
campaign markers, attempt journals, model byte reads/loads, audio/reference content
reads, decoding, ASR inference, whisper_full, device connections/executions, ADB,
on-device codec/warmup, threshold changes, tuning, reselection, rematerialization,
and private package writes. Retention remains **2026-10-25**, without extension.

All 75 named protected files and 601 immutable baseline files remain
byte/blob unchanged. v0.1's three files, v0.2's two files and the historical BLOCKED
report/evidence are preserved. Prior reports are verbatim; status/backlog changes
are additive. Strict JSON, links, privacy, exact six-file scope and diff checks pass.
Independent review is recorded in the [new aggregate evidence](../evidence/poc-asr-001/alpha-asr-small-campaign-execution-operator-stage0-v0.1.json).
It found one Important private-ACL fail-open issue and no Critical or Minor findings.
Four generated acceptance/work/content subcases reproduced the failure before a
v3-only fix. Regression tests and all required host suites passed after the fix;
no independent post-fix re-review is claimed.

CI was not dispatched; configured push triggers do not match this chat branch.
PR [#86](https://github.com/Monumentogram/DORA/pull/86) was read-only checked: OPEN,
draft, unmerged, untouched. Recovery remains **0D.6 = ALPHA CLOSED / FULL OPEN**.
Stage 5 and Group B are not marked PASS; POC-ASR-001 remains BLOCKED / NOT_READY.
No measured quality/resource conclusion is added.

## Exact transitive source/test freeze

Canonical identity hashes this sorted-key compact UTF-8 file map (bytes plus raw
SHA-256). External runtime and six binary identities remain separately pinned.

| Repository file | Bytes | SHA-256 |
|---|---:|---|
| `tools/alpha_asr_campaign.py` | 7274 | `e0261fadf0c01cdc2e634108f7c464ca07f2ae3baa68ac6884dc06e43e799709` |
| `tools/alpha_asr_campaign_device.py` | 10598 | `e0200b3a063659c0041a2d081a7151a7f57407ae015738e8a9fbdb6fae0cadf2` |
| `tools/alpha_asr_campaign_import.py` | 7920 | `338ed7bcb30917d91951fffb49745c3aec39724f8aade0ae5ccf878f3a60860d` |
| `tools/alpha_asr_campaign_native/CMakeLists.txt` | 1327 | `8e3ec4062b5ca3e2b860dae768f71438e9fccd3151d7f69233f0ff2a8f112ca9` |
| `tools/alpha_asr_campaign_native/CampaignOracle.java` | 1621 | `abc51d8d08651f288ded482816350b680eae07ab3e47c8b9dc0d331d72e04270` |
| `tools/alpha_asr_campaign_native/decode.c` | 229 | `0fc06284bb8bce924bd2cc28c2fd0c9b39cd02f0b06a766d13251b9a0224346c` |
| `tools/alpha_asr_campaign_native/main.cpp` | 7112 | `b642497a5f50676f6b643303ad272e78c6f158359d5a480f2f18f16a779f29bf` |
| `tools/alpha_asr_eval_text_contract.py` | 14735 | `490b4f659aef99da413e68cab5ffa187b1e8fd7229e7d7265367f2d14e577ff0` |
| `tools/alpha_asr_pilot_manifest.py` | 12341 | `dea7a8bdf651372052d89b0338b41a2c5cb7373696335ed8de47adbb8cba64b9` |
| `tools/alpha_asr_small_acceptance_manifest.py` | 9876 | `e0c02989fe50ac51df70a4a577119a9cac831cef04d84dab415454a117ac0d1e` |
| `tools/alpha_asr_small_campaign.py` | 12993 | `a923710537f846e8d6894e45a1feb139c63de8702220c6e21bd5d34723383536` |
| `tools/asr_i1_synthetic_scoring_oracle/src/main/java/com/monumentogram/dora/stage0/asr/i1/AsrSyntheticScoringOracle.java` | 29949 | `b2d2525ff0d71c15fdef931c005975f8610f5c5054e8e1f2b10f0f3740db9903` |
| `tools/run_alpha_asr_campaign.py` | 18870 | `c9b1116e03a8bb1377169c3a8c929ddc1c933f84a7f5a7a587b67f205b14e45f` |
| `tools/run_alpha_asr_small_campaign.py` | 17189 | `72b703f51f34394e8cf0f5f367306cbfc5b805a61eb4a9020efd99c0d47575a7` |
| `tools/run_alpha_asr_small_campaign_v2.py` | 13692 | `5a247f3eba9c9b9699d4597f8925684053b915de99ddf224485a2f91aab0244f` |
| `tools/run_alpha_asr_small_campaign_v3.py` | 15573 | `2020a57547daadad0e295190b07b260dca0146fec2c1b45622536358efaf0f69` |
| `tools/test_alpha_asr_pilot_manifest.py` | 20803 | `b9e00347f9669f573ecffa9b7e67735ef67e25ef03a7ce46dcc8d02e8b11c28c` |
| `tools/test_alpha_asr_small_campaign_v2.py` | 17093 | `13342ef8c9d0005dba18ffed6347e158b30a18c6fe92bf45811c3e4fde8d981b` |
| `tools/test_alpha_asr_small_campaign_v3.py` | 29706 | `a77a8e1b3bf8e8e6028c604e22ffa70cba21f456ac6c929a4035f3fce68c9ab9` |

## Exact changed files

- `tools/run_alpha_asr_small_campaign_v3.py`
- `tools/test_alpha_asr_small_campaign_v3.py`
- `docs/stage0/DORA_ALPHA_ASR_SMALL_CAMPAIGN_EXECUTION_OPERATOR_STAGE0_V0_1.md`
- `docs/evidence/poc-asr-001/alpha-asr-small-campaign-execution-operator-stage0-v0.1.json`
- `docs/DORA_MVP1_STAGE_STATUS.md`
- `docs/DORA_MVP1_IMPLEMENTATION_BACKLOG.md`

No operator-freeze blocker remains. Actual measured execution still requires
independent verification of this commit and a later explicit owner/CTO decision.

**5.6C.2B = BLOCKED / NOT_AUTHORIZED**.
