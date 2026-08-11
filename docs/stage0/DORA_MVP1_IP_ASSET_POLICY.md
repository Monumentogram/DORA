# Dora MVP 1 — IP and Asset Policy

Task: `GOV-IP-001`
Version: 1.1
Date: 11 August 2026
Status: active governance policy; no third-party model, weight or binary is admitted by this document
Legal note: this is an engineering control, not legal advice

## 1. Purpose

This policy prevents Dora from copying, downloading, committing, shipping or executing an asset whose origin, rights, integrity or obligations are unclear. It applies to source code, binaries, fonts, icons, images, audio, datasets, model definitions, model weights, prompts and generated assets.

The repository is public. A file that is legal to inspect is not automatically legal to redistribute from GitHub, bundle in an APK, mirror for model download or use commercially.

## 2. Non-negotiable rules

1. **Code, model architecture, weights and training data are separate artifacts.** A permissive code license does not license weights or datasets.
2. **No unknown origin.** “Found online”, a search result or a copied design file is not provenance.
3. **No `latest`.** Every admitted artifact has an exact version/commit, immutable digest and retrievable terms.
4. **No silent redistribution.** Download permission, internal evaluation, commercial use, modification, bundling and public mirroring are checked separately.
5. **No model/native artifact in Stage 0A.** This PR creates policy only and downloads no weights or third-party binaries.
6. **No reference copying.** Visual references provide direction only; production artwork, layout and texture must be original or explicitly licensed.
7. **Attribution ships with the artifact.** Required notices are part of acceptance, not a later release chore.
8. **No private evidence in public Git.** Signed terms, invoices or personal consent stay in controlled storage; the public manifest stores a safe reference and non-sensitive facts.
9. **PoC is not admission.** A PoC may evaluate an artifact only after evaluation rights are clear. Production use still requires an admission ADR and all license/security/ABI gates.
10. **When rights conflict or are ambiguous, stop that artifact.** Use a replaceable port/fallback; do not infer permission.

## 3. Asset states

| State | Meaning | Allowed action |
|---|---|---|
| `PROPOSED` | Candidate named; provenance not complete | metadata research only; no download/commit/execution |
| `EVALUATION_APPROVED` | Exact artifact and evaluation rights reviewed | controlled PoC use under stated restrictions; no product admission |
| `ADMISSION_REVIEW` | PoC evidence exists; production obligations being reviewed | no merge into production dependency graph |
| `ADMITTED` | ADR, license, provenance, digest, notices, security, ABI/runtime and fallback approved | use only in approved scope/version/channel |
| `REJECTED` | Terms, provenance, security, quality or compatibility failed | do not use; record reason and candidate fallback |
| `REVOKED` | Previously admitted artifact is no longer acceptable | disable new activation/distribution; follow removal/rollback plan |

Only named Product/Legal/IP and Engineering/Security reviewers may move an artifact to `EVALUATION_APPROVED` or `ADMITTED`. An engineer may collect public metadata but may not self-approve ambiguous rights.

## 4. Required provenance record

Every external artifact receives one record per exact file or reproducible build output. Minimum fields:

| Field | Requirement |
|---|---|
| `artifactId` | stable Dora identifier, not a download URL |
| `category` | font, icon, image, source, native binary, model definition, model weight, dataset, prompt or tool |
| `name` / `version` | exact upstream name and immutable release/tag/commit |
| `sourceRepository` | canonical upstream origin |
| `sourceArtifactUrl` | exact official download/model-card location, if redistribution is permitted to record publicly |
| `retrievedAt` | date metadata was checked; binary retrieval is separate |
| `sha256` | exact file/archive digest; generated build also records source-tree digest |
| `licenseId` | SPDX identifier when accurate; otherwise exact custom/gated terms reference |
| `licenseTextDigest` | digest of the reviewed terms/text |
| `copyrightNotices` | verbatim notices required for attribution, stored in controlled evidence when necessary |
| `rights` | evaluation, commercial use, modification, redistribution, mirroring, sublicensing and model-output terms, each explicit |
| `restrictions` | attribution, notice placement, use-case, geography, account/gated access, acceptable-use and data restrictions |
| `dependencies` | licenses/terms of runtime, tokenizer, vocabulary, weights and embedded sub-assets |
| `trainingDataStatement` | known source/provenance and unresolved dataset concerns for a model |
| `buildProvenance` | toolchain, commands, source commit, patches and reproducibility status for binaries |
| `platformEvidence` | API/ABI, 16-KiB packaging/runtime, memory/security results where applicable |
| `reviewers` | Product/Legal/IP, Engineering and Security identities/roles with dates |
| `evidenceLocator` | controlled-store reference; never a secret-bearing URL |
| `state` / `decision` | one asset state plus rationale, scope and fallback |

A family-level statement such as “Whisper is MIT” cannot replace a record for the exact chosen weight and runtime artifact.

## 5. Rules by asset category

### 5.1 Fonts

- Record the exact font file, upstream release/commit, digest, family/version metadata and license text.
- Verify Cyrillic and Latin coverage, hinting, variable axes, tabular numerals, Android rendering, size and 200% text behavior.
- Bundle the required OFL/other notice in the repository and product only after the exact artifact is admitted.
- Do not fetch fonts at runtime or depend on a user device having the family.
- A font shown in a reference screenshot is not licensed merely because its name can be guessed.
- Manrope remains a `PROPOSED` candidate under DEC-024 until the exact file and notice pass this gate; system font is the fallback.

### 5.2 Icons

- Prefer original Dora vectors or an exact, documented open icon set.
- Record the icon set version, license and any modification/attribution requirements.
- Do not trace a commercial app icon, logo or screenshot asset.
- A generic metaphor such as microphone or pause may be reused; the concrete drawing/path must be original or licensed.
- Adaptive, monochrome and store icons require trademark/name clearance in addition to copyright provenance.
- Exported SVG/vector sources must not contain hidden author paths, embedded raster references or unlicensed fonts.

### 5.3 Design references, images and textures

- Existing reference filenames in the Design Spec are inspiration only and must not be committed or shipped without documented permission.
- Do not reproduce a reference’s logo, proprietary illustration, bitmap, unique card contour or full composition one-to-one.
- Record source, author/rightsholder, acquisition terms and permitted use for every approved reference copy in controlled design storage.
- Any production texture/illustration is newly created for Dora or licensed explicitly; generated assets retain prompt/tool/version and usage terms.
- A generated image must be checked for embedded marks, copied characters, personal likeness and unsuitable training/license restrictions before admission.

### 5.4 Audio models and VAD

- Treat runtime code, model graph, preprocessing/postprocessing, tokenizer/config and weights as separate assets.
- Verify the exact Silero/sherpa-compatible artifact source, version, digest and weight terms, even if the runtime source license is permissive.
- Record supported sample rate, model input contract and upstream conversion path; a re-export does not erase upstream terms.
- Do not mirror or bundle a model unless redistribution rights are explicit.
- PoC audio/model evaluation rights do not permit production distribution.
- If terms are unclear, use a deterministic fake or another candidate; do not download the weight “just for testing”.

### 5.5 ASR models

- Record whisper.cpp/Vosk/sherpa/faster-whisper runtime separately from Whisper/Vosk/other model weights.
- Pin exact runtime commit/release, model variant, quantization/conversion tool, input weight digest and resulting artifact digest.
- Record whether the quantized artifact was reproduced locally or obtained prebuilt; unknown third-party quantizations are not admissible.
- Verify commercial use, modification and redistribution/mirroring of both original and converted weights.
- Include model card, known language/quality limits, attribution and notices in the public safe manifest.
- Native runtime admission additionally requires reproducible build/provenance, supported ABI/API, 16-KiB ELF/ZIP/runtime evidence, lifecycle/corrupt-input tests and a replaceable engine port.
- No ASR weight or binary is downloaded in Stage 0A.

### 5.6 Diarization models

- Review segmentation and embedding weights independently; a combined pipeline may have several licenses and dataset histories.
- Gated acceptance, account access or click-through terms must be preserved as evidence and must not be bypassed by an unofficial mirror.
- Check attribution and redistribution for pyannote, 3D-Speaker, WeSpeaker or any future exact artifact; source-code license is insufficient.
- Review training datasets and restrictions relevant to commercial/biometric use. Unknown provenance is a blocker, not “low risk”.
- Speaker embeddings are not introduced as reusable identity templates under MVP. A voice-identity feature requires a separate biometric threat/legal decision.
- If license or DER gate fails, fallback is manual speaker labels or a separately consented server route, not a forced identity claim.

### 5.7 Other model weights and prompts

- Each LLM/ASR/diarization weight file has its own digest, terms, quantization provenance, size and compatibility record.
- Model-family branding and a permissive GitHub repository do not substitute for exact Hugging Face/ModelScope/model-card terms.
- Prompt templates authored for Dora are versioned source; copied vendor prompts/examples require provenance review.
- Model output cannot be assumed free of third-party rights or personal data; output used as a shipped asset receives its own review.
- Runtime model download is data-asset activation only after signed catalog verification; executable code download is prohibited.

### 5.8 Third-party source code and binaries

- Pin dependencies and actions to an exact release/digest/commit according to repository policy.
- Record direct and transitive licenses, notices, source-offer obligations, patent clauses and incompatibilities.
- Copyleft/custom licenses are not automatically banned, but they require an explicit architecture/distribution/legal review before use.
- Copying a code snippet from an issue, answer, blog or generated response requires traceable origin and license compatibility; otherwise rewrite from specification.
- Native prebuilts are rejected unless provenance, exact digest, symbols/build details, ABI/API and 16-KiB evidence are complete.
- Security/CVE and maintenance review is independent of license approval.
- Development tools that do not ship still require license/provenance, but distribution obligations are assessed separately.

#### 5.8.1 Stage 0 Android platform SQLite boundary (`OD-11`)

For `POC-SEARCH-001` Stage 0 evaluation only, SQLite supplied inside a pinned Android system image
is a nested platform component rather than a separately downloaded or redistributed Dora artifact.
Its technical provenance is sufficient without extracting and hashing an individual SQLite binary
only when all of the following are recorded together:

- exact Android system-image package/image ID and revision;
- immutable system-image archive digest from the canonical publisher;
- runtime Android build fingerprint, API and primary ABI;
- runtime `sqlite_version()` result;
- confirmation that no separate SQLite/SQLCipher library is downloaded, bundled or redistributed.

The digest scope must be labeled `containing-system-image`; it must not be represented as a digest
of the nested SQLite file. This Stage 0 exception resolves provenance method only. Evaluation rights
and the exact dependency/license/NOTICE review still require the assigned reviewers and an
`EVALUATION_APPROVED` decision.

This boundary does not apply to production admission. Before any production schema or dependency
admission, an independent production Security review and production Legal review must reconsider
the actual shipping platform/device matrix, SBOM/notices and distribution scope. Production Legal
is currently unassigned and blocked. A separately bundled SQLCipher, custom SQLite build or native
prebuilt always requires its own exact artifact digest and normal admission evidence.

### 5.9 Datasets and audio fixtures

- Dataset license and participant consent are separate gates. Both must permit the exact evaluation purpose.
- Record canonical dataset/version, sample manifest digest, license/terms, collection statement, consent/legal basis, permitted uses and retention.
- Public benchmark data cannot be assumed suitable for commercial model evaluation, redistribution or derivative annotations.
- Real meetings are forbidden in Git/Git LFS/Actions and are not authorized by this policy.
- Purpose-recorded data is not a public dataset and may not be used for training without separate research consent.
- Synthetic fixtures record generator/source, seed/version and proof that no confidential/personal content was copied.
- Train/development, test and protected evaluation splits remain immutable and participant-isolated as defined in Dataset Governance.

## 6. License and compatibility review

The reviewer answers each question explicitly:

1. Who owns or licenses the exact artifact?
2. Does the source match the official/canonical publisher?
3. May Dora evaluate it internally?
4. May Dora use it commercially?
5. May Dora modify, convert or quantize it?
6. May Dora redistribute it in APK, model catalog, side-load package or mirror?
7. Are attribution, notice, source offer, branding or share-alike obligations compatible with the intended channel?
8. Are there account/gated terms, acceptable-use restrictions or geographic/export constraints?
9. Does the artifact embed or depend on another asset with different terms?
10. Are training-data provenance or biometric/privacy restrictions unresolved?
11. Can the exact artifact be reproduced and revoked safely?
12. Has Product/Legal/IP approved the actual scope rather than a broader family name?

An unresolved answer sets state to `PROPOSED` or `REJECTED`; it never defaults to permission.

## 7. Attribution and notices

For every admitted distributable asset:

- preserve copyright and license text exactly as required;
- include notices in the source distribution and an in-app `Licenses` surface when required/reasonable;
- connect each notice to exact artifact/version/digest;
- include modifications and Dora build/conversion provenance where terms require it;
- retain model-card attribution and restrictions in Model Manager/details;
- ensure store listing, APK, signed side-load and enterprise packages contain the same required notices;
- verify that localization or UI truncation does not alter required legal text;
- keep a machine-readable notices inventory for SBOM/release generation.

Attribution does not cure missing permission. A link alone does not satisfy a requirement to include a full license or notice.

## 8. Evidence storage

### Public repository may contain

- this policy and sanitized artifact manifests;
- canonical public source links without credentials;
- SPDX/custom license identifiers and non-sensitive obligation summaries;
- exact digests of admitted or synthetic artifacts;
- required distributable license/notice texts after approval;
- build scripts/patches whose own rights are clear;
- redacted decision records and ADRs.

### Controlled private evidence must contain when applicable

- accepted gated/click-through terms and date/account role;
- counsel/procurement approval and contract/DPA references;
- invoices or entitlement evidence;
- participant consent and dataset access records;
- private mirror/access details;
- raw audit reports that contain local paths or other sensitive metadata.

The public manifest points to controlled evidence through an opaque locator. It never contains credentials, personal account data or a signed URL.

## 9. PoC evaluation gate

Before a PoC downloads or executes an external model/native artifact:

1. create an exact provenance record;
2. approve evaluation rights and data-use compatibility;
3. verify upstream digest/signature or reproduce the artifact;
4. inventory all runtime/weight/config/tokenizer files;
5. declare allowed storage and cleanup;
6. run secret/malware/basic parser-size checks in a controlled environment;
7. record ABI/API and 16-KiB plan for any native component;
8. add artifact/license fields to the benchmark report;
9. define a fallback that does not depend on the candidate;
10. delete/revoke the evaluation copy if the candidate is rejected or retention expires.

Passing a benchmark changes evidence, not state to `ADMITTED`.

## 10. Production admission gate

Production admission requires all PoC evidence plus:

- accepted ADR with exact artifact and replaceable port;
- legal/IP approval of intended markets, distribution channels and commercial scope;
- reproducible source build or verified prebuilt provenance;
- dependency lock, SBOM and notices;
- CVE/security/parser/JNI lifecycle review;
- ABI/API/device matrix and 16-KiB package/load/inference evidence;
- performance, battery, memory, quality and corrupt-input gates;
- signed catalog, rollback/revocation and mirror rights;
- tested removal/migration path that preserves user data;
- release checklist and owner sign-off.

## 11. Prohibited actions

- committing or attaching model weights, native libraries, fonts, reference images or datasets “temporarily”;
- accepting a model based only on its repository license or family marketing page;
- using an unofficial mirror to avoid gated terms;
- removing attribution to simplify UI or package size;
- copying a visual reference, logo or production screen one-to-one;
- using real meeting audio because it is already available to a team member;
- training/fine-tuning on evaluation data without separate permission;
- publishing private legal/procurement/participant evidence;
- inventing a digest, license identifier, author or provenance gap;
- treating a successful PoC as dependency admission.

## 12. Category-specific current disposition

| Candidate/category | Current state | Allowed now | Next gate |
|---|---|---|---|
| Manrope font | `PROPOSED` | metadata/glyph test plan only | exact file/digest/OFL notice and design test |
| Material/open icon candidate | `PROPOSED` per exact source | inspect metadata; existing original bootstrap assets remain unchanged | exact set/version/license or original Dora artwork |
| User-provided visual references | `PROPOSED` inspiration only | textual analysis already in baseline | permission for controlled copy or create original assets |
| Silero/sherpa VAD artifacts | `PROPOSED` | architecture research only | exact runtime/weight evaluation approval |
| whisper.cpp/Whisper weights | `PROPOSED` | benchmark design only | exact runtime, weight, quantization and evaluation-right record |
| Vosk/sherpa ASR candidates | `PROPOSED` | benchmark design only | exact artifact-level license/provenance record |
| pyannote/3D-Speaker/WeSpeaker | `PROPOSED` | benchmark design only | gated terms, weights/training-data/redistribution review |
| Qwen/llama.cpp candidates | `PROPOSED` | benchmark design only | exact code/weight/quantization/prompt review |
| Purpose-recorded dataset | not created | consent/governance planning only | owner retention/access decision and signed collection process |
| Synthetic fixtures | permitted when original and non-personal | schema/text/signal generation after scoped task | generator provenance and manifest |

## 13. Ownership and escalation

| Role | Responsibility |
|---|---|
| Product owner | approves product scope, market/channel and risk/fallback; names Legal/IP reviewer |
| Legal/IP reviewer | interprets exact terms, redistribution, attribution and dataset/consent compatibility |
| Engineering owner | verifies exact artifact, build, digest, dependencies, replacement and technical obligations |
| Security owner | reviews supply chain, parser/native risk, signing, revocation and evidence handling |
| Data/research custodian | controls dataset access, split, retention, withdrawal and deletion evidence |
| Release owner | blocks shipment when manifest, notices, SBOM or approval is incomplete |

If no named reviewer is available, model/font/reference evaluation remains `PROPOSED`; engineering does not substitute its own legal approval.

## 14. Exit statement for GOV-IP-001

The repository now has a controlled-reference and asset-provenance policy covering fonts, icons, audio/VAD, ASR, diarization, other model weights, source code, binaries, datasets, design references, licensing, attribution and evidence custody. No artifact is downloaded, committed or admitted by this change.
