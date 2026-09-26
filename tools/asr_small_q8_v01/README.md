# Prospective SMALL q8 operator

This additive host binding is preparation only. Import and synthetic tests do not
open a corpus, model or device. It has no CLI execution entrypoint. Do not use it
until the new data authority, q8 artifact/static admission, init/lifecycle checks,
source review and an independently accepted execution envelope have all passed.

`composition(repo, pins)` loads isolated copies of the historical SMALL evaluator
and sequencer. Only model identity, campaign/profile identity and exact data pins
change. The normalizer, oracle, quality/resource gates, CPU4/flash-attention
decoding profile, generated warmup, per-case lifecycle, attempt and stop rules are
inherited. Historical modules are not mutated.

The explicit API is
`campaign(config, freeze_sha, expected_commit, authorization=AUTHORIZATION)`.
The token is a programming guard, not authorization by itself. `expected_commit`
must be independently supplied from the accepted operator commit; it must equal
both the envelope baseCommit and clean repository HEAD. HEAD's parent must be the
envelope dataCommit. No current commit is hard-coded into the operator.

`config` contains exactly repo, bundle, acceptance, work, model, native,
runtimeSource and adb locators. `native` contains the six ARM82 artifact names.
Every accepted invocation uses the same envelope-bound acceptance and work
locations under controlled private storage. A new work locator cannot bypass the
one-shot claim. The work directory must already exist and be empty; the exclusive,
fsynced claim survives interruption. A stopped campaign cannot be resumed or retried.

The private `bundle/execution-freeze.json` must contain:

- operatorId, experimentId, baseCommit, dataCommit, branch, retentionDeadline and
  campaignInvocationBudget=1, matching this module's contracts;
- storage.acceptanceRelativePath and storage.workRelativePath, both relative to
  the controlled private root (keep these out of public evidence);
- sourceFiles: exactly SOURCE_FILES, each with raw bytes and SHA-256;
- privateDocuments: exactly PRIVATE_DOCUMENTS, each with raw bytes/SHA-256 and
  canonicalSha256; these include the historical private snapshot and effective
  prior-use authority in addition to the immutable consumed selection;
- model: exactly MODEL; profileSha256 for composition with the frozen manifest,
  transfer, data-freeze and data-authority canonical digests;
- buildSha256 for bundle/build-summary.json, which must equal the accepted
  ARM82 runtimeBuild record in full;
- isaAdmission identical to the accepted ARM82 executionFreeze.isaAdmission;
- codecSample bytes/SHA-256 for the pinned runtime samples/jfk.mp3.

The envelope's canonical SHA-256 is `freeze_sha`. File hashes and canonical JSON
hashes have distinct purposes; do not substitute one for the other. Source sets
are closed so omitting a mandatory file cannot disable verification. Source
paths are repository-relative, with loaded-module location/hash checks as well.

SOURCE_FILES also requires the public `asr-small-q8-admission-stage0-v0.1.json`.
Before any claim, its successful admission must bind the exact q8 model and data
commit. Its successful init receipt must bind that same model, commit and private
data-freeze raw pin, one init attempt, zero ASR inference and verified cleanup.
Static admission must be PASS for that model with479 tensors and exact EOF. A
present but failing or unrelated admission cannot enable execution.

`verify_inputs` checks all source/data/model/native/runtime/profile/storage
bindings before the claim, before warmup, before each primary case and after the
sequence. It recomputes selection only to compare it with the consumed ledger and
frozen manifest. It never writes or replaces selected rows. A fresh physical POCO
identity check must match the accepted ISA evidence before specialized native code
is called. All remote hashes and cleanup remain handled by the historical Device.

Synthetic verification, from tools, uses Python3.12 with Unicode15:

```text
python -B -m unittest test_alpha_asr_small_q8_binding
```

The synthetic suite covers profile isolation, retained evaluator behavior,
selection/ledger/consumed-authority checks, duplicate references, independent
commit binding, path/pin rejection, one-shot state and inherited stop/verification
cadence. It does not establish real q8 loading, performance, device cleanup,
corpus correctness or actual16KiB-device runtime support. Full repeated checks
rehash model/native bytes and recompute the already frozen selection outside the
measured per-case execution window; no caching bypass is used.
