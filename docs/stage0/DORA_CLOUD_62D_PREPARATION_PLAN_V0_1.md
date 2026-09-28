# DORA 6.2D AWS and owned-corpus preparation plan

**Goal:** Prepare both external prerequisites for the bounded, owner-authorized
Amazon Transcribe evaluation without creating benchmark results prematurely.

**Authority:** Explicit Project Owner task, 2026-09-28. Required starting local
and fetched remote commit: `d8d5c90516b6a9afa96c21e703dfe7481e64e589` on
`chat/alpha-asr-runner-scope`; verified with a clean tree before implementation.
The Owner explicitly delegates design, implementation and independent review;
one atomic preparation commit replaces intermediate implementation commits.

**Architecture:** A private local corpus store and loopback recorder/annotation
UI feed a deterministic validator/manifest builder. Separate AWS preparation
tools bind short-lived identity, minimum task resources, effective opt-out and
retention. Offline evidence and safe aggregate contracts remain public. A new
Phase A can only bind actual verified data and infrastructure after human work.

**Stack:** CPython 3.12 / Unicode 15.0, Python standard library, local browser
audio APIs, AWS CLI v2 and CloudFormation JSON. No Android/runtime changes.

## Immutable constraints

- [Phase A v0.1](DORA_CLOUD_EVALUATION_PHASE_A_V0_1.md) and its frozen listings
  stay byte-identical. No Phase A v0.2 placeholder or premature PASS.
- `eu-central-1`; standard batch file ASR; no provider or region fallback.
- Total ceiling USD 10; Transcribe reservation <=USD 2; ancillary/tax <=USD 8.
- 36 RU + 36 EN candidate tasks; 12 READ + 12 SPONTANEOUS selected per language;
  6 noisy diagnostics per language; reserves follow the frozen source-hash order.
- Human actual-speech references; six selected READ clips and >=100 human-timed
  words per language. No generated timing or AWS-derived gold reference.
- Private source/final WAV, references, timing, exact manifest and attestation
  stay outside every Git worktree. Public outputs are protocols, schemas,
  aggregate counts and whole-inventory/manifest hashes.
- No deployment before authenticated account context; no Organization creation
  or shared governance mutation without direct Owner action.
- Publish, push and refetch a complete successor before first AWS benchmark;
  zero prior results must be established. Do not execute 6.3.

## Implementation and review sequence

1. **AWS preparation** — `tools/cloud62d_aws/`: inspect host without exposing
   credentials, prepare CLI/login, deterministic scoped resources, account and
   effective-policy readback, bounded cost and explicit cleanup. Unit tests must
   reject wrong region, wrong resources and ineffective inherited opt-out.
2. **Corpus authority and reproducibility** — `tools/cloud62d_owned/corpus.py`
   and schema/tests: freeze private authored inventory before recording, validate
   exact PCM WAV and references, select by v0.1 source hashes, choose timing
   subset prospectively, compose long fixtures and hash the private manifest.
3. **Recording and annotation** — local server/UI/launcher in
   `tools/cloud62d_owned/`: explicit attestation, microphone meter, guided capture,
   automatic conversion, actual-speech reference confirmation, waveform and
   manual word timing. Validate path containment and browser request isolation.
4. **Evaluation integration** — preparation evidence and validation: preserve
   frozen core, verify manifest/config/publication prerequisites, retain truthful
   NOT_RUN states and the exact 39-gate accounting. All private artifacts remain
   excluded from public evidence.
5. **Independent review** — cross-review ownership boundaries plus an adversarial
   review of the integrated changes. Fix findings before publication. Run Stage00,
   scoped Cloud/normalizer tests, documentation/JSON parity, links, hashes, DAG,
   changed-file allowlist, privacy scan and `git diff --check`.
6. **Publication** — verify exact remote parent again, stage only the allowlist,
   make one preparation commit, push/refetch exact equality and clean tree.
   Update the existing Google Sheet only after this milestone; read back every
   changed cell while preserving formulas, formatting and historical records.

## Review focus

- Interrupted capture or disk failure preserves original bytes and never produces
  a falsely verified reference or complete manifest.
- Retakes, duplicate sources and invalid recordings cannot manipulate selection
  after results; selection freezes before timing and provider output.
- Loopback HTTP rejects foreign origins, traversal and missing session authority;
  public artifacts never embed private corpus text, paths or participant identity.
- Timing requires every actual word, finite bounded monotonically ordered anchors,
  and an explicit human blind attestation; absence cannot become zero timing.
- SSO, effective inherited opt-out, retention enforcement and full cost remain
  real preflight prerequisites; policy templates cannot count as live PASS.

## Human boundary and resumption

The Owner performs AWS browser/SSO login or an explicit account-governance action,
then records the guided clips and confirms actual words/timings locally. Resume
from the saved private artifacts: validate, select, compose, quote, bind actual
account controls and run all twelve preflights. Only after all frozen conditions
pass publish Phase A v0.2, push/refetch it, and run Phase B. Preserve failures and
all attempts; never adjust thresholds or use results to choose the corpus.
