# Dora Stage 0 — VAD, Offline, and Data host regression scope v0.1

Task: `OWNER-AUTH-BATCH-20260819-01`, refined priority §§4–5
Version: 0.1
Baseline: `origin/main@671f594074b37bb2b5c8e4a4c1026de909acf339`
Branch: `codex/stage0-vad-offline-data-host-regression`
Status: implementation boundary frozen before validator, inventory, or workflow code

## 1. Hypothesis

The exact already-merged synthetic pure-host mechanics for VAD P2/P3/P4, Offline I1/I2/I3, and
the Data public-manifest validator and control plane remain reproducible together on current
`main`. A single bounded cross-platform validator can prove that limited regression statement by
pinning the existing source and evidence bytes, compiling with strict Java 17, comparing exact
content-free outputs across declared repeats, requiring `jdeps=java.base` and class major 61,
rejecting forbidden runtime/privacy surfaces, and deleting all runner-owned temporary artifacts.

The hypothesis is intentionally one CI slice: VAD, Offline, and Data mechanics that are already
merged but not all revalidated by a shared current-main workflow. It neither extends those tools
nor creates replacement evidence.

## 2. Included existing packages

| Area | Package | Merged source |
|---|---|---|
| VAD | P2 deterministic frame-timing oracle | PR #33 |
| VAD | P3 deterministic integrated replay | PR #46 |
| VAD | P4 synthetic PCM rotation oracle | PR #49 |
| Offline | I1 semantics host oracle | PR #36 |
| Offline | I2 integrated synthetic harness | PR #37 |
| Offline | I3 static call-ledger validator | PR #48 |
| Data | synthetic public-manifest validator | PR #35 |
| Data | synthetic control-plane validator/dry-run | PR #44 |

The inventory points to the existing repository files and canonical hashes. No merged evidence is
copied, rewritten, reclassified, or upgraded.

## 3. Deterministic fixtures

- VAD P2/P3 use only in-code post-acoustic speech/silence frame labels and injected frame indices.
- VAD P4 uses deterministic generated mono PCM16LE only under a validator-owned temporary root;
  no PCM enters Git or survives a successful or failed validator invocation.
- Offline I1/I2/I3 use only repository-owned content-free state, ledger, digest, and profile
  fixtures in memory.
- The Data public-manifest validator uses only its frozen synthetic profile/schema and transient
  test documents under the validator-owned temporary root.
- The Data control plane uses only its frozen synthetic metadata manifest/schema and one bounded
  owned sentinel lifecycle under the validator-owned temporary root.
- All compiled sources, tests, support contracts, schemas, manifests, reviews, and evidence inputs
  are strict UTF-8 text pinned by canonical LF SHA-256 in a closed inventory.
- Network, device, emulator, Android, model, provider, cloud, and real-data inputs are absent and
  unauthorized.

## 4. Non-goals

- Do not run or duplicate Android, VPN, Recovery, Search, Gradle, emulator, device, APK, native,
  packaging, or product tests.
- Do not include Decision, ASR, Diarization, Battery, Capture, Search, Recovery, or VPN packages in
  this first shared regression slice.
- Do not change any existing source, test, evidence, workflow, contract, schema, manifest,
  dependency, product module, Product Decision, or ADR.
- Do not change `DORA_MVP1_IMPLEMENTATION_BACKLOG.md`, `DORA_MVP1_STAGE_STATUS.md`, readiness
  records, PoC parent states, thresholds, execution authority, or admission decisions.
- Do not claim acoustic VAD quality, real file/device behavior, Offline product integration or
  network absence, a governed dataset, operational consent/storage, model quality, or production
  fitness.
- Do not make the new check a substitute for the protected repository Android CI gate.
- Do not merge this PR in this task; exact-head CI is handed to a separate independent review.

## 5. Acceptance criteria

Success requires all of the following:

1. The inventory is closed to the exact eight package entries above, their exact order, exact
   existing file paths, canonical UTF-8/LF SHA-256 pins, command classes, arguments, expected
   stdout, repeat counts, filesystem profiles, and claim ceiling.
2. Every inventory path is repository-relative, tracked, non-symlink, within the repository,
   strict UTF-8 without BOM/NUL/lone carriage return, and hash-exact after Git CRLF-to-LF text
   normalization.
3. `javac` is major version 17. Each isolated compile uses
   `--release 17 -encoding UTF-8 -Xlint:all -Werror`, exits zero, and emits no stdout or stderr.
4. Every declared command runs with assertions and bytecode verification, exits zero for its exact
   repeat count, emits exactly one declared content-free line using the host-native line ending,
   emits no stderr, and is byte-identical across repeats on that host.
5. Each isolated package closure has recursive module dependencies exactly `java.base`; every
   emitted class file has class major version 61.
6. Main-source and compiled-main scans reject undeclared network, clock, randomness, environment,
   process, thread, reflection, Android, GMS, model, or persistence surfaces. Filesystem APIs are
   allowed only for the frozen Data validator/control-plane and VAD P4 bounded profiles.
7. Public-artifact scans find no private key, credential/token, email, private address/endpoint,
   private locator, or absolute local path. The sole allowed URL is the public JSON Schema draft
   identifier already present in the Data schemas/source.
8. Java temporary properties and process temp variables point to an isolated validator-owned root
   outside the repository. Per-command temporary roots are empty after return, repository status
   is unchanged, no `.class`, PCM, or sentinel appears in the worktree, and the top-level temporary
   root is absent before success is reported.
9. The dedicated workflow checks out the exact event SHA and runs only this validator on relevant
   pull requests to `main`, pushes to `main`, and manual dispatch. It uploads no artifact.
10. The exact implementation commit is pushed to a Draft PR, its dedicated exact-head check is
    green, and the commit is handed to a separate independent reviewer. This task stops before
    Ready or merge.

Any mismatch is a hard failure. The validator never rewrites hashes, evidence, or authority.

## 6. Exact expected tracked files

Only these four files may be added by this slice:

1. `.github/workflows/stage0-vad-offline-data-host-regression.yml`
2. `docs/evidence/stage0-vad-offline-data-host-regression/vad-offline-data-host-regression-inventory-stage0-v0.1.json`
3. `docs/stage0/DORA_MVP1_VAD_OFFLINE_DATA_HOST_REGRESSION_SCOPE_STAGE0_V0_1.md`
4. `tools/run_stage0_vad_offline_data_host_regression.py`

All pre-existing paths remain byte-for-byte unchanged.

## 7. Claim ceiling

The maximum allowed claim is:

`CURRENT_MAIN_VAD_OFFLINE_DATA_SYNTHETIC_HOST_MECHANICS_REVALIDATED`

It means only that the exact eight already-merged, inventory-pinned synthetic host packages passed
the closed v0.1 validator on the tested commit. It is not PoC `PASS`, readiness, completion,
quality, real-data or device evidence, dependency/model/dataset/product admission, or formal
Security, Legal, production, or human approval.
