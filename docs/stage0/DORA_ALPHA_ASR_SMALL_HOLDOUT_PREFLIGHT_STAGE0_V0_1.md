# SMALL fresh holdout and model-init preflight — Stage 0 v0.1

Date: 2026-09-25. Task: **5.6C.1**.

## Verdict and blocking prerequisite

**BLOCKED / FRESH_HOLDOUT_SOURCE_UNAVAILABLE_ON_THIS_DEVICE**.

The complete controlled source corpus is on another device, confirmed by the owner.
This host has the full candidate inventory and the transferred original48 subset;
none of the fresh selected audio appears in that transfer index, and the full
candidate-reference file and original archives are absent. The source-availability
block occurs before any fresh byte-integrity check. It is not an insufficient-pool
verdict or a claim of corrupt source bytes.

The successor metadata validator passes. Exactly 24 RU and 24 EN are determined in
metadata; **0 RU and 0 EN are materialized**. The full pre-inference freeze is not
complete. POCO identity, SMALL rehash and model initialization are not attempted
because the required holdout freeze has not happened. ASR inference is **0**.

`5.6C.2 = NOT_STARTED / NOT_AUTHORIZED`; `POC-ASR-001 = BLOCKED / NOT_READY`.
Historical BASE remains `VALID_FAIL / REJECTED_FOR_BOUNDED_ALPHA_QUALITY_GATE`.

## Authority and deterministic metadata selection

Branch: `chat/alpha-asr-runner-scope`. Local and fetched starting HEAD:
`dd1468dcaace9699e64b1945f83a9b532205d435`; tracked/index state was clean before work.
The [5.6B governance](../evidence/poc-asr-001/alpha-asr-small-evaluation-governance-stage0-v0.1.json)
at that commit has Git blob `961ed67b3da21967497a9ed5c1be176f27fcde27`.

Selection contract: `dora-alpha-asr-small-acceptance-selection-v0.1`;
SHA-256 `4db7c7d67aac03504a1233f0f59cfe1bedee414917f28e2c915d337d404638ff`.
The [new validator](../../tools/alpha_asr_small_acceptance_manifest.py) reuses
historical primitives without changing the old validator. It checks the pinned
full inventory and historical manifest before applying exclusions. It accepts no
CLI pin overrides and never opens audio/reference paths.

Inventory canonical SHA-256: `ad454b162fab7dea96c28612a374fcae79040346dae5ee2916b3c4482b3ec041` — PASS, 742 rows.
Original48 manifest canonical SHA-256: `5d9e12fa76e9db54bc69bbd22396dbec733348db97bba353361ac701af64061c` — PASS,
including verification by the unchanged historical validator. Both dataset IDs,
release/version/license and archive identities match accepted 5.1C metadata.
The terms evidence and retrieved terms bytes match their accepted SHA-256 values.
Original archives are absent and were not rehashed. Historical completeness and
decode validation are identity-bound inherited facts; neither is rerun here.

| Exclusion reason | Candidate count |
| --- | ---: |
| Base eligibility | 284 |
| Original source identity | 48 |
| Original audio digest | 48 |
| Explicit prior evaluation ledger | 48 |
| All members of duplicate groups after prior exclusions | 0 |

Reasons overlap; they must not be summed. There are **332 unique excluded** rows,
**190 RU + 220 EN eligible** rows and zero remaining duplicate groups.
The prior-use ledger explicitly records original48 as EVALUATION, with zero TUNING
records. `NO_SEPARATE_DEVELOPMENT_OR_TUNING_SET_EXISTS`; participant-disjointness
against development data is not applicable. No re-identification is performed.
Metadata consistency cannot prove absence of undisclosed prior use.

The exact domain/NUL/locale/NUL/path SHA-256 key, raw-key ordering and exact UTF-8
path tie-break select the first 24 per locale after all exclusions, ordered RU24
then EN24. No manual or quality-based substitution occurs.

| Private metadata binding | SHA-256 |
| --- | --- |
| Original source exclusions | `59486316fb3660f7a897f5eba6e4312dcb9d4fda4b239cc60c1befbd28396e62` |
| Original audio exclusions | `ee4e3b8729d67c80e254ee31f6584fb1ad893e32ae9dd6700ebac075a85db044` |
| Prior-use ledger | `75e21945e36ca5a0497db0364d9c44fc86d99f95c022a9b232527698bf78e296` |
| Duplicate groups | `4f53cda18c2baa0c0354bb5f9a3ecbe5ed12ab4d8e11ba873c2f11161202b945` |
| Combined exclusion set | `5061c65e15f6c45fcfc532fc110bde3318af05cf000890bad3e2656a03b94e4f` |
| Eligible pool | `758b9b917eadb53bc93b32ed029a66ea344bff15998c17138fcc6b62d72a6fad` |
| Selected metadata manifest | `695f1e147fe968c8f75e7bea5e7a6edfd20f50e939c0bdbc2064d3a7db7d1947` |

## Storage and incomplete freeze

Private selection metadata is retained at logical locator
`controlled:alpha-asr-small-5.6c1-v0.1`, in owner-only local storage with a protected
DACL, outside repositories/worktrees, sync roots and nonadministrative shares;
no reparse ancestors were found. No fresh corpus bytes were copied.

The five necessary copy classes are the owner-only storage receipt, prior-use
ledger, exclusion/eligible-pool audit, metadata-only selected manifest and blocked
metadata receipt. Private metadata records are written exclusively and made
read-only. Their receipt digest is
`d5a64882456d6f1f9b3dae44623cbeb9c200e845a0976cd4c0cda766e19eca5f`. It is **not** an acceptance freeze or a
claim that selected byte hashes have been verified. Full manifest/transfer/freeze
evidence needs the fresh audio and reference bytes first.

Deletion deadline: **2026-10-25**, unchanged; delete earlier if no longer required.
No public rows, sample IDs, source paths, per-case audio hashes, references,
hypotheses, participant IDs, device serial or private absolute paths are published.

## Frozen candidate and device boundary

SMALL: `ggml-small-q5_1.bin`, **190085487 bytes**, SHA-256
`ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb`;
HF revision `f281eb45af861ab5e5297d23694b7d46e090c02c`.
Approved locator: `controlled:alpha-asr-model-5.6a1-v0.1`.
No model bytes were read this task; exact rehash remains mandatory before device use.

Runtime: `ggml-org/whisper.cpp v1.9.4`, commit
`927cfce34f31707e17f2bff35c349632fb9e2c3a`, unchanged.
Decoding profile: `cd53fda162402675f54875de470b01c303f0ede9a2e0cefd6f1056e8fa381d39`.
Measurement protocol: `0f1e456fb4b4b0da28a209abe94e42fe7a0ca599e5c5c794db16a6abe478b116`.
Resource gates: `dora-alpha-asr-small-poco-m5-resource-gates-v0.1`,
SHA-256 `fdfb56b56429c07a99538c92d87f9ea8570c723cc67f5e5eb02915d97557ccd1`.

POCO M5 preflight: **NOT_RUN_HOLDOUT_FREEZE_PREREQUISITE_UNMET**. Android/API/ABI,
RAM, free storage, power, thermal and runtime page-size observations are unavailable
because no device connection was made. Model-init attempts: 0; no timing exists.
Cleanup: **NOT_APPLICABLE_NO_DEVICE_STATE_CREATED**; zero task-owned device files,
processes or changed settings. Device-side absence was not queried.
No quality, cold-load, RTF, memory, thermal, 16-KiB runtime or production-admission
claim follows.

## Separate access and operation accounting

Counts represent unique records/files per category, not repeated read calls.

| Category | Count |
| --- | ---: |
| Candidate inventory metadata files / rows | 1 / 742 |
| Provenance, terms and lifecycle metadata files | 6 |
| Original48 manifest metadata files / exclusion rows | 1 / 48 |
| Fresh selected metadata rows | 48 |
| Fresh audio/reference files read, copied or hashed | 0 / 0 |
| Original48 audio / reference text accesses | 0 / 0 |
| Old hypotheses / per-case result accesses | 0 / 0 |
| SMALL model byte reads or hashes | 0 |
| Device connections / transfers / model initialization | 0 / 0 / 0 |
| Audio decode / ASR inference / whisper_full calls | 0 / 0 / 0 |
| Tuning / threshold changes / model downloads | 0 / 0 / 0 |
| Dataset downloads / 5.6C.2 measured attempts | 0 / 0 |

## Validation and publication

Synthetic tests were written first: 14 expected failures before implementation,
then **14 new + 21 unchanged historical = 35 PASS**. Applicable ASR host discovery
is **113 PASS**, generated synthetic fixtures only. Coverage includes deterministic
selection, permutations, every exclusion, duration boundaries, both locale shortages,
UTF-8 tie-breaking, authority/manifest tampering, rank/order/substitution, unknown
fields and content-free CLI output. The actual metadata CLI returns PASS/exit 0.

Strict JSON, Markdown/local references/privacy, Stage 00, diff whitespace, exact
six-file scope and 58 protected historical byte/hash/Git-blob checks are recorded
in the [machine-readable evidence](../evidence/poc-asr-001/alpha-asr-small-holdout-preflight-stage0-v0.1.json).
Independent static validator/test review found no actionable issues.
No Android/Gradle/device test is claimed for this host-only metadata change.

Publication is exactly one atomic commit on the existing branch. No PR, merge,
PR #86 modification, Recovery work, CI dispatch or measured campaign is authorized
or performed. Existing 5.1–5.6B artifacts, runtime sources, allowlist and app remain
unchanged. Work stops after the branch push.
