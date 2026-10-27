# Amazon Cloud ASR implementation plan

> Execute with superpowers:subagent-driven-development or superpowers:executing-plans. The supplied Stage 18.1C request is the implementation authority; no new architecture approval is needed.

**Goal:** Recognize one saved recording through a controlled backend and Amazon Transcribe, preserving Stage 9 versions and edits.

**Architecture:** Python service (the existing AWS integration language), SQLite durable control ledger and encrypted payload custody; invited installation proof-of-key authentication; private S3/KMS and explicit Batch jobs. Android reuses protected original audio, encrypted transcript journal, CAS selection and editor. No existing product backend exists on the accepted base.

**Tech stack:** Python, boto3, cryptography, Kotlin/JVM17, Android Keystore, HTTPS, Compose, existing SQLCipher journal.

**Spec:** Owner-supplied Stage18.1C task; docs/product/DORA_ASR_USER_SCENARIOS_V0_1.md; docs/contracts/DORA_ASR_DATA_AND_VERSIONING_CONTRACT_V0_1.md; ADR-0009/0010/0011/0012/0019/0022; accepted 6.2D overlay.

## Global constraints

- Base exactly 98a975f3aff268db605573e1cd666dbc237f3123; PR stacked on codex/parallel-alpha-preview, no unrelated merges.
- MANUAL_ONLY, recording-scoped persisted consent, zero audio before consent, cancellation fences further upload.
- ru-RU/en-US, eu-central-1, PCM16 mono 16000 Hz verified from original media; maximum 300 seconds.
- Private input/output, separate KMS keys; immediate post-ingest cleanup, inherited 24h deadline; no public objects or credentials in Android.
- No live AWS jobs/infrastructure without current verified authorization; synthetic fake adapter tests permitted. AWS_LIVE_RUNTIME = NOT_RUN until real evidence exists.
- No POCO commands, installation, profile changes, Pilot v2 or LONG recording access.
- Preserve all user edits, audio, historical evidence and local offline behavior. No automatic cloud processing.

## Review focus

- Lost HTTP response after paid dispatch must reconcile the same provider job, not start another.
- Cancellation/revocation during upload or restart must fence later bytes and late publication.
- Cross-owner recording/job/source IDs must fail even with valid authentication.
- Local edits/selections during Cloud recognition must survive and remain active.
- Cleanup failure/timeout must remain durable pending work under the original deadline.

## Task 1: backend vertical slice

Files: backend/dora_cloud/{auth,store,service,aws_adapter,http_server}.py, backend/tests/, backend/README.md and safe configuration examples.

- [ ] Add failing tests for signed request auth/replay/expiry, consent, ownership, bounded chunk upload, job lifecycle, idempotency and cleanup.
- [ ] Implement durable owner-scoped recording and job bindings, finite installation grant and proof-bound access sessions.
- [ ] Implement AWS adapter, no arbitrary URLs, fixed private keys, exact media validation, bounded attempts, cost reservation and retention watchdog.
- [ ] Run full backend tests with fake AWS; prepare deployment/preflight guidance without live mutations.
- [ ] Independently review P0/P1 and fix actionable findings.

## Task 2: Android integration

Files: core/audio transcript port/reducer/codec/vault and tests; app/transcript Cloud client, run, setup and existing UI; instrumented tests.

- [ ] Add failing tests for Cloud versions, consent and restart/callback fences.
- [ ] Extend encrypted transcript payload compatibly; durable consent/action identity, Cloud provenance and optional timestamps.
- [ ] Implement protected PCM upload, Keystore signed HTTPS client, manual consent/language/progress/cancel/retry UI and safe invitation provisioning.
- [ ] Run JVM/static/build checks and synthetic emulator tests including persistence, cloud-only and preserved edits.

## Task 3: delivery

- [ ] Independent whole-change P0/P1 review and necessary fixes.
- [ ] Add truthful evidence and status, commit, push, Draft PR and inspect CI. Never merge.
- [ ] Report exact verified boundaries, AWS live status, cost and one next action.
