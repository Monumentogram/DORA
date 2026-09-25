# DORA Alpha ASR controlled model storage remediation — Stage 0 v0.1

Task **5.6A.1**, 25 September 2026.

**5.6A.1 = PASS / CONTROLLED_MODEL_STORAGE_ESTABLISHED_SMALL_Q5_1_BYTES_VERIFIED**.

Resulting current stage: **5.6A = PASS / SMALL_Q5_1_ARTIFACT_ADMITTED_PRE_RUN_PACKAGE_READY**.
This is Stage-0 artifact admission only. Quality, device fitness, production admission, private-data
reuse, resource thresholds and 5.6B/5.6C execution are not approved by this result.

## Authority and immutable history

The owner's explicit 5.6A.1 instruction authorizes a new equivalent controlled model-storage
boundary on this host when the historical location cannot be positively identified through
bounded non-content discovery. The old physical directory is not required. Prior bounded
discovery did not identify it; no other device or broad recursive search was used in this task.

Repository `Monumentogram/DORA`, branch `chat/alpha-asr-runner-scope`. Required starting HEAD,
local HEAD and freshly fetched remote HEAD were all
`7aad7c697feb4e2bcde5d5425d33038e2b37c883`; the index and working tree were clean.
The [blocked 5.6A report](DORA_ALPHA_ASR_NEXT_CANDIDATE_ADMISSION_STAGE0_V0_1.md) and its
[evidence](../evidence/poc-asr-001/alpha-asr-next-candidate-admission-stage0-v0.1.json) remain
byte-identical historical records. This new result resolves their storage/actual-byte blocker;
it does not rewrite that attempt or any 5.2, 5.3A/B/C, 5.4 or 5.5 evidence.

## Controlled storage

Storage class: **LOCAL_PRIVATE_CONTROLLED_STORAGE**.
Sanitized locator: **controlled:alpha-asr-model-5.6a1-v0.1**.

The new store is under the current authorized user's local application-data area, separate from
raw/private audio, outside DORA and all its registered worktrees. Eight exact ancestor checks
found neither Git metadata nor reparse points, also excluding an enclosing unrelated Git checkout.
No non-administrative SMB share covers the store. One configured sync root was checked and does
not contain it; HKCU/HKLM registered sync-provider discovery found zero providers, standard
Dropbox root-metadata files were absent, and the standard Google DriveFS configuration directory
was absent. These are bounded host-location checks, not a general audit of every installed program.
No public share, repository symlink/junction, remote attachment or cloud upload was created.

The directory has a protected DACL with inheritance disabled and exactly one explicit Allow rule:
FullControl for the current authorized local user, inherited by files and subdirectories. Owner
and effective allow entries were checked on all 14 store items at final inventory (directory plus
13 files). No other principal has a filesystem Allow entry. The physical location is recorded
only in the owner-controlled local receipt. No absolute local path appears in public evidence.
The scripts and receipts are outside Git; no model is in Git, LFS or Actions artifacts.

## Exact acquisition and final identity

| Field | Verified value |
|---|---|
| Canonical repository | `ggerganov/whisper.cpp` |
| Immutable revision | `f281eb45af861ab5e5297d23694b7d46e090c02c` |
| Filename | `ggml-small-q5_1.bin` |
| Expected and observed bytes | `190085487` |
| Expected SHA-256 | `ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb` |
| Acquisition SHA-256 over complete downloaded bytes | `ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb` |
| Final SHA-256 after atomic promotion | `ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb` |
| Expected/acquisition/final equality | **MATCH** |
| Model downloads / retained complete model copies | **1 / 1** |
| Remaining incomplete/stale staging files | **0** |

Downloaded only from the [exact immutable source](https://huggingface.co/ggerganov/whisper.cpp/resolve/f281eb45af861ab5e5297d23694b7d46e090c02c/ggml-small-q5_1.bin).
The exclusive staging file was inside the controlled store. After completion and file flush,
exact size and a full-file SHA-256 passed before same-directory atomic rename. A separate complete
final-file hash then passed; the static checker also independently rehashed that file. Promotion
left one model copy and no staging file. No fallback, alternate revision or additional model was acquired.

The public provenance/MIT review in 5.6A is reused. Its exact source snapshots were checked against
the recorded hashes. Full OpenAI and ggml MIT license texts and the pinned model card are retained
with the model. Historical converter-build identity remains unpublished; neither reproducibility
nor signed-build attestation is claimed. Original weights were not separately downloaded.

## Static actual-byte identity and pinned-runtime compatibility

A local standard-library checker was created outside Git, reviewed against the pinned loader and
converter, and retained in the controlled store. Checker SHA-256:
`636549690bd11b95c74fe57aaf95753c75e4c327c28abd0e8ce5749ee83fc9a5`.
It reads only the model as binary data, uses bounded little-endian parsing and skips tensor payloads;
it performs no native loading, dequantization, audio processing or inference.

| Actual-byte field | Result |
|---|---|
| GGML magic | `0x67676d6c` |
| Vocabulary capacity / stored entries | `51865` / `50257` |
| Audio context/state/heads/layers | `1500 / 768 / 12 / 12` |
| Text context/state/heads/layers | `448 / 768 / 12 / 12` |
| Mel shape | `80 x 201`; finite filter values |
| Header ftype | `1009`: quantization version 1, file type 9 (Q5_1) |
| Tensors | `479`; all expected names/shapes/types, no duplicates or omissions |
| Tensor types | `284 F32`, `2 F16`, `193 Q5_1` |
| Tensor payload bytes | `189489216` |
| Bounds / truncation / EOF / trailing payload | PASS / 0 / exact / 0 |

Runtime remains **ggml-org/whisper.cpp v1.9.4**, commit
`927cfce34f31707e17f2bff35c349632fb9e2c3a`. The pinned loader's SMALL dimensions, multilingual
vocabulary criterion, ftype version extraction/type mapping and tensor shape/byte expectations
match the actual file. Q5_1 tensor type 7 uses 32 elements per 24-byte block. Version 1 in the
artifact is not mistaken for the pinned quantizer's current emitted version; the loader extracts
the version and supports the observed type/layout. **No pinned-runtime incompatibility was
demonstrated by static inspection.** No runtime code was changed or executed; successful native
loading, quality, memory, latency, device fitness and 16-KiB runtime support remain untested.

## Preserved owner decisions and operation boundary

The prospective fresh 24 RU + 24 EN held-out design and owner-decision records in 5.6A remain
**PROPOSED_NOT_APPROVED / PENDING_OWNER_DECISION**. No selection or materialization occurred.
Private-data reuse and numeric RTF/PSS/native-heap limits still require explicit owner decisions.
The original BASE candidate remains **VALID_FAIL / REJECTED_FOR_BOUNDED_ALPHA_QUALITY_GATE**.

Retained private-data policy remains **ACTIVE_THROUGH_5_5_THEN_DELETE_WITHIN_30_CALENDAR_DAYS**;
assessment completion **2026-09-25**, default deletion deadline **2026-10-25**. No extension,
follow-on reuse or private-data deletion occurred. The model is retained only for authorized
bounded evaluation: the custodian must revoke/delete it on rejection, revocation or evaluation
retention expiry, with a deletion receipt; no indefinite or production reuse is authorized.

All eight prohibited-operation counts are **0**: private corpus content access; retained private
reference access; retained hypothesis/per-case-result access; audio decode; ASR inference;
native model load; device execution; new evaluation subset materialization.
**5.6B = NOT_STARTED / NOT_AUTHORIZED; 5.6C = NOT_STARTED / NOT_AUTHORIZED**.

## Validation and publication

[Machine-readable evidence](../evidence/poc-asr-001/alpha-asr-next-candidate-storage-remediation-stage0-v0.1.json)
records acquisition, final storage checks, parser facts, script identities and 47 protected-file
checkout hashes/Git blob identities. Protected files include the blocked 5.6A pair, 5.4/5.5,
normalizer, Java oracle, runner/native source and production native allowlist.
Applicable checks are strict JSON, Markdown/reference/text/privacy validation, Stage 00's seven
artifact checks, `git diff --check`, protected-byte equality and exact four-file scope. Status and
backlog retain all preceding bytes through additive insertion. Check outcomes are in the evidence.

One atomic commit/push to the existing task branch is authorized. At evidence freeze it is pending;
the handoff supplies the actual commit and fetched remote HEAD without a self-referential hash.
No PR, merge, PR #86 change, Recovery change, production edit, CI dispatch or device test occurred.
No CI success is claimed. Stop after the push; no new campaign follows from this admission.
