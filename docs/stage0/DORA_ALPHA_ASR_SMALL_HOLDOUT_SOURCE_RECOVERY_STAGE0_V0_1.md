# SMALL exact source recovery and completed preflight — Stage 0 v0.1

Date: 2026-09-25. Task: **5.6C.1.1**.

## Result

**5.6C.1.1 = PASS / EXACT_CORPUS_RECOVERED_FRESH_HOLDOUT_MATERIALIZED_SMALL_PREFLIGHT_READY**.

Resulting **5.6C.1 = PASS / FRESH_48_HOLDOUT_MATERIALIZED_SMALL_MODEL_INIT_PREFLIGHT_READY**.
Both exact historical archives were recovered through official MDC access; the
previously selected fresh 24 RU + 24 EN are fully materialized and frozen. Exact
SMALL initialization on physical POCO M5 succeeds; process/file cleanup verifies.
**ASR inference = 0; whisper_full = 0.**

`5.6C.2 = NOT_STARTED / NOT_AUTHORIZED`; `POC-ASR-001 = BLOCKED / NOT_READY`.
Historical BASE remains `VALID_FAIL / REJECTED_FOR_BOUNDED_ALPHA_QUALITY_GATE`.
This is preparation evidence, with no quality, performance-gate or production PASS.

## Exact official source recovery

Method A: the owner authenticated and completed both downloads through the official
[RU dataset](https://mozilladatacollective.com/datasets/cmu5mg3pr00simh07epeylc55) and
[EN dataset](https://mozilladatacollective.com/datasets/cmu5nqn1h00vwmi07b4dbk085) flow.
Browser automation could navigate the flow but did not confirm completed downloads;
the owner supplied the completed files in Downloads. The agent applied owner-only
file ACLs, moved each file into controlled local storage, and verified the original
Downloads paths absent. No cross-device transfer, access bypass, agent credential
entry, new model download or cloud upload occurred. No terms were accepted by the agent.

| Locale | Complete bytes | Complete archive SHA-256 | Verdict |
| --- | ---: | --- | --- |
| RU | 93031233 | `aa0910f5fef7f24afccf5bca075995f0b7a970d96da72626c80507d629d05a6f` | Exact historical match |
| EN | 544267165 | `390b5a54c9afe0cc01da039ad206248f85682f247dd2b27d4cc0ab9a68e860b6` | Exact historical match |

Historical basenames are recorded in [aggregate evidence](../evidence/poc-asr-001/alpha-asr-small-holdout-source-recovery-stage0-v0.1.json).
Rounded web sizes and the current public package label were not used as identity
proof. Both complete gzip streams passed CRC/length verification before extraction.
All 801 RU and 8709 EN tar members passed bounded release-prefix, regular-file/
directory, traversal, control-character, Windows-path, duplicate/case-collision,
link, sparse-file and parent-file checks. No whole-corpus extraction occurred.

The exact archive digests bind the provider index bytes. RU has 783 provider rows
and 385 test rows; EN has 5898 provider rows and 357 test rows. Exact test membership,
all 742 inventory source paths and their duration bindings match the unchanged
inventory. Index SHA-256 values are
`045b18d249cc06a275217e56962e6bb3abc9ef3d637f6c35831cd19b9eb04c5e` (RU) and
`e7db9c37c200342505bf796bdeef305e5aaf3d52bccff2698bec07eced705f46` (EN).
Prior decode-validity is inherited from exact 5.1C archive identity; audio decode is 0.

## Unchanged selection and exact materialization

The saved private receipt and metadata hashes pass. The unchanged actual metadata
CLI returns PASS/exit 0 and reproduces all prior counts and digests. Its expected
selection computation is verification only; no case or saved metadata is replaced.

| Binding | Result |
| --- | --- |
| Candidate inventory SHA-256 | `ad454b162fab7dea96c28612a374fcae79040346dae5ee2916b3c4482b3ec041` |
| Original48 manifest SHA-256 | `5d9e12fa76e9db54bc69bbd22396dbec733348db97bba353361ac701af64061c` |
| Selection contract SHA-256 | `4db7c7d67aac03504a1233f0f59cfe1bedee414917f28e2c915d337d404638ff` |
| Combined exclusions SHA-256 | `5061c65e15f6c45fcfc532fc110bde3318af05cf000890bad3e2656a03b94e4f` |
| Eligible pool SHA-256 | `758b9b917eadb53bc93b32ed029a66ea344bff15998c17138fcc6b62d72a6fad` |
| Unchanged metadata manifest SHA-256 | `695f1e147fe968c8f75e7bea5e7a6edfd20f50e939c0bdbc2064d3a7db7d1947` |

Counts remain 742 candidates, 332 uniquely excluded, 190 eligible RU, 220 eligible
EN and selected 24 RU + 24 EN. Exclusion reasons remain 284 base-ineligible,
48 original-source, 48 original-audio, 48 prior-use and 0 duplicate-audio; reasons
overlap. No separately authorized development/tuning set exists; development
participant disjointness remains not applicable.

Exactly **24 RU + 24 EN audio files and exact reference files** were materialized.
All source and copied audio byte counts/hashes and reference hashes match frozen
inventory metadata. Source identity, locale, duration, selection key and rank match
for all 48. Reference TSV quoting is parsed without text normalization; only selected
reference fields are retained/decoded. Nonselected transcription and participant
fields are discarded without decoding. No original48 audio member is opened.
No quality/convenience replacement, extra candidate copy or transcoding occurs.

Fully materialized manifest SHA-256:
`2cb05132de945fe8a1b281e79efd8efa15d9b9b94c0326f6572a594e0c74e9bb`.
Transfer index SHA-256: `95c26be0501172196587181b3f46705b65237bde879a0e3ee8eb748cee2e1a87`; 97 bound files verified.

## Immutable private freeze and retention

Freeze completed at **2026-09-25T14:06:46.641267+00:00**, before any device access.
Canonical SHA-256: `eb088082b2cde6d2bc2649e6cb6d67a92d3a68ad447a8a7331eec58c2794bcaa`.
The record binds exact 5.6B governance commit
`dd1468dcaace9699e64b1945f83a9b532205d435` / Git blob
`961ed67b3da21967497a9ed5c1be176f27fcde27`, selection/exclusion/pool/materialized
manifest/transfer digests, 48-case count, all model/runtime/profile/resource pins,
data authority, deletion deadline and completion timestamp. Private records were
exclusively created and made read-only; ASR inference before and after freeze is 0.

Source locator: `controlled:alpha-asr-small-source-5.6c11-v0.1`.
Acceptance locator: `controlled:alpha-asr-small-5.6c1-v0.1`.
Both are `LOCAL_PRIVATE_CONTROLLED_STORAGE`; owner-only ACLs and absence of reparse
entries were verified for every entry. Storage is outside Git/worktrees, sync/public/
nonadministrative shared roots and separate from model storage. The existing five
acceptance metadata records are unchanged; 99 materialization files and six source
files are newly retained. Copy classes include two archives, two member metadata
records, source owner/lifecycle receipts, 48 audio, 48 references, materialized
manifest, transfer index and freeze. Temporary device copies are removed.

Deletion deadline remains **2026-10-25**, earlier when no longer needed. No extension
or public/cloud sharing is approved. The public evidence contains only aggregate
counts and digests; no private paths, case IDs, references, participant identities,
per-case audio hashes, hypotheses or device serial are published.

## SMALL init-only POCO evidence

SMALL `ggml-small-q5_1.bin`: **190085487 bytes**, SHA-256
`ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb`,
HF revision `f281eb45af861ab5e5297d23694b7d46e090c02c`;
`controlled:alpha-asr-model-5.6a1-v0.1`. Host rehash passes before transfer.
Runtime remains `whisper.cpp v1.9.4`, commit
`927cfce34f31707e17f2bff35c349632fb9e2c3a`. All six unchanged native/preflight files
match the historically admitted 5.3C manifest, digest
`fd3e789904f9f3baca28db793d4526cb29b60f105a72a034445378bf07a58b82`. All seven transferred file sizes and hashes match.

Decoding profile: `cd53fda162402675f54875de470b01c303f0ede9a2e0cefd6f1056e8fa381d39`.
Measurement protocol: `0f1e456fb4b4b0da28a209abe94e42fe7a0ca599e5c5c794db16a6abe478b116`.
Resource gates: `dora-alpha-asr-small-poco-m5-resource-gates-v0.1`,
SHA-256 `fdfb56b56429c07a99538c92d87f9ea8570c723cc67f5e5eb02915d97557ccd1`.

| Device fact | Observation |
| --- | --- |
| Physical model / code / ABI | POCO M5 / 22071219CG / arm64-v8a |
| Android / API / runtime page size | 14 / 34 / 4096 bytes |
| RAM total | 3849269248 bytes |
| RAM available, start / end | 1626849280 / 1636593664 bytes |
| Storage available, start / end before cleanup | 95815184384 / 95597256704 bytes |
| Power | USB powered; battery 89%; charging |
| Thermal status start / end | 0 / 0 (NONE) |

The first host preparation stopped before transfer/init because its task-local
memory parser rejected Windows ADB CRLF. A memory-only diagnostic identified the
cause; synthetic LF/CRLF tests reproduced and verified the correction. The pinned
runtime, native source, model, selector, profiles and gates were unchanged.
This is not a model-init failure or quality retry. Both preparation records are retained
as sanitized evidence, with two model rehashes and only **one native initialization**.

The unchanged native executable ran **init** only: successful exact model context
initialization, immediate `whisper_free` and process exit. Diagnostic host-observed
wall time is **422 ms**, including ADB overhead. It is not a campaign cold-load result.
No audio was transferred; `whisper_full` was never called. Scoped PID ownership,
process absence, removal of the task-owned model/native directory and directory
absence were verified. No settings changed; no stale device files are retained.
No RTF, memory/PSS, thermal campaign, 16-KiB device-runtime or production gate is passed.

## Exact operation accounting

| Category | Count |
| --- | ---: |
| Official archives downloaded / same-host controlled moves | 2 / 2 |
| Whole-archive hash / gzip / safe-tar checks | 2 / 2 / 2 |
| Complete archive bytes hashed | 637298398 |
| Selected audio extracted / copied / source-hashed | 48 / 48 / 48 |
| Selected reference records read / written / source-hashed | 48 / 48 / 48 |
| Audio / reference copy hash passes, including transfer index | 96 / 96 |
| Provider-index hash checks / metadata scans / header-only inspection | 2 / 2 / 1 |
| SMALL host rehash / native host file hash checks | 2 / 12 |
| Device access sessions (two preparations and one diagnostic) | 3 |
| Model / native / audio transfers | 1 / 6 / 0 |
| Device hash checks / context init / context release | 7 / 1 / 1 |
| Original48 audio members opened / old reference fields decoded or retained | 0 / 0 |
| Old private reference files / hypotheses / per-case results opened | 0 / 0 / 0 |
| Audio decode / ASR inference / whisper_full / original48 regression inference | 0 / 0 / 0 / 0 |
| Tuning / threshold changes / model downloads / 5.6C.2 attempts | 0 / 0 / 0 / 0 |

Whole-archive hashing/decompression and provider-index raw hashing are counted
separately from selected content use. No old48 audio member or reference-text field
is selected for content access. No private corpus bytes are used in host unit tests.

## Validation and publication

Branch: `chat/alpha-asr-runner-scope`. Local and freshly fetched starting HEAD:
`aff983095fc62b815278313db384356653c7c0eb`; tracked/index state was clean.

14 successor + 21 historical validator tests pass; applicable ASR host suite:
**113 PASS**. Actual private CLI, exact archives, source membership and 48/48
materialization integrity pass. Local byte-TSV controls pass four variants, and
independent review passes 1000 synthetic TSV cases. Eight failure-classification
controls and two LF/CRLF telemetry tests pass. One review finding in task-local
preflight error classification was corrected and rereviewed; no selector defect exists.

Strict JSON, Markdown/references/privacy, Stage 00, diff whitespace, exact four-file
delta and 62 protected historical byte/hash/Git-blob checks are recorded in evidence.
The historical blocked 5.6C.1 report/evidence and both validator files remain unchanged;
status/backlog retain all prior bytes with additive amendments. No Android/Gradle
suite or campaign acceptance is inferred from the host checks or init observation.

Publication: exactly one atomic commit, push only to the existing branch, no PR,
merge, PR #86 modification, Recovery work or CI dispatch. Stop after push/readback;
5.6C.2 is not authorized.
