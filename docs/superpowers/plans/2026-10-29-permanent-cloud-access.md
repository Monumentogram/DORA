# Stage 9.3 permanent access implementation plan

> Execute inline using executing-plans and test-driven-development. The owner's
> Stage 9.3 request supplies the design and authorizes implementation/deployment;
> separate approval is required only if a physical APK installation is necessary.

**Goal:** Keep the existing POCO key authorized until explicit revocation, without
calendar shutdown or a permanent bearer token.

**Architecture:** Add an explicit persistent trust marker to the existing SQLite
installation record. An operator-only `trust` command pins the existing owner and
public key, requires a live invitation or explicit expired-invitation recovery,
and refuses revoked/unknown/rebound identities. Reuse existing proof/session flow.
Finite legacy grants remain finite until explicitly migrated. No Android change.

**Tech stack:** Existing Python/FastAPI, SQLite, P-256, Android Keystore.
**Spec:** Owner-provided “DORA — Stage 9.3: Permanent Local & Cloud Access”.

## Constraints and review focus

No new calendar expiry, shared key, permanent bearer, clock changes, APK install,
Keystore/model/storage reset, IAM/TLS/budget relaxation or PR merge. Preserve
encrypted user data. Test migration races/idempotence, expired/revoked/unknown
identities, persisted replay denial, and retention of permanent authority/source
bindings after 90 days. Finite onboarding is not permanent product authority.

## Task 1 — Audit and baseline
- [x] Inspect PR121 checks and exact baseline 7d4cf3f.
- [x] Create branch stage/9.3-permanent-cloud-access in existing isolated checkout.
- [x] Run backend baseline: 58 tests pass.
- [ ] Capture POCO documents/versions/edit hashes/active versions/model/identity.
- [ ] Audit Android/backend/contracts/deployment/release calendar restrictions.

## Task 2 — Permanent authority
Files: backend/dora_cloud/{auth,store,service,__main__}.py,
backend/tests/test_permanent_access.py, backend/tests/test_operator.py.
- [ ] Write tests for `Auth.trust(installation, owner, public_key, recover_expired=False)`;
  run them RED before implementation.
- [ ] Add nullable `trusted_at` column through additive, serialized schema migration.
  Pin key and owner; preserve revocation, legacy expiry and all short security TTLs.
- [ ] Update job authorization and sweep to preserve permanent registrations and
  active owner source bindings; retain ordinary job/audio/consent retention.
- [ ] Expose operator-only trust/recovery command; no network enrollment endpoint.
- [ ] Test 2026-11-02/03/04/05, 2027-01-01, 2030-01-01, 2100-01-01 using injected clocks,
  signed sessions/jobs, invalid proofs, replay, stale timestamps, consent and restart.
- [ ] Run complete backend suite and relevant local/Cloud/persistence guards.

## Task 3 — Runtime and delivery
- [ ] Update API/runbook, ADR, product decisions/backlog/status and result report.
- [ ] Review the complete change independently before deployment.
- [ ] Deploy exact reviewed backend files with guarded old-file checks and rollback;
  migrate only the verified existing POCO public identity; preserve AWS configuration.
- [ ] Verify phone signed API Gateway status retrieval, restart, data/model identity
  preservation and test-identity revocation; no paid job unless necessary.
- [ ] Publish stacked Draft PR, inspect relevant CI, record limits honestly.

## Execution notes
Native worktree creation refused the nested sourceRoot; reused the existing linked
checkout with a new branch. Pre-existing untracked files are excluded from this task.
The provided detailed design and instruction to implement directly take precedence
over skill prompts to repeat design approval. No APK change is currently needed.
