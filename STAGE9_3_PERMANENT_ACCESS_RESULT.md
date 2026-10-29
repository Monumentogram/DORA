# DORA — Stage 9.3 Permanent Local & Cloud Access

2026-10-29. **IMPLEMENTED / ACCEPTANCE_IN_PROGRESS** — final CI and post-restart
custody checks pending at publication. Do not interpret this source report as PASS.
The owner-local final receipt/report records subsequent CI and device observations.

## Calendar audit

| Restriction | Finding and resolution |
|---|---|
| POCO invitation 2026-11-04T23:59:59Z | Previously checked per request, session and job. Exact existing key migrated to explicit permanent trust; original date retained as history. |
| Installation cleanup after expiry + 90 days | Trusted identity is retained; active source bindings survive. After revoke, source history uses the first revocation time + 90 days. |
| Local model shutdown 2/3 November | Already removed from installed Preview5 by ADR-0027/Stage9.1; no Android production change required. |
| Owner Preview/Pilot release scripts | `owner_arm82.check_custody_time` still blocked model validation/builds after 2026-11-02 21:00 UTC. Removed gate and callers; exact digest/size/source/signing checks remain. |
| Historical ASR corpus October retention gates | Research-only campaign tools; do not run in the APK/backend/release path. Preserved personal-audio/corpus retention authority. |
| Sessions/proofs/nonces | 900-second sessions, 60-second clock tolerance, durable nonce replay prevention unchanged. |
| Audio/results/consent retention, TLS, AWS credentials | Security/retention lifetimes preserved. EC2 runtime role uses SDK credential refresh; Android has no AWS owner credentials. TLS certificate renewal remains active. |
| Other Alpha/preview/build dates | No additional production calendar cutoff found in Android/backend/deployment/release audit. Audio duration/upload/quota limits are not calendar expiration. |

## Authorization and migration

See ADR-0029 and backend/API.md. Additive nullable `trusted_at` and `revoked_at`
columns preserve existing SQLite records. `trust --invitation PUBLIC_JSON` checks
the existing exact owner/key, rejects unknown/revoked/retired identities and is
idempotent. Legacy expired grants require explicit operator `--recover-expired`
after owner confirmation; there is no network recovery bypass or identity rebinding.

Trusted registration lasts until revoke. Every request still uses the existing
Android Keystore P-256 key and signed method/path/body/time/nonce. Sessions stay
short; the existing client renews them automatically. No APK, permanent bearer,
shared key, IAM change, AWS resource creation or real-clock change was needed.

The existing POCO export matched the live server public key before deployment.
The still-valid invitation was migrated without replacing the installation; its
UUID/key/owner/expiry and existing jobs were preserved. API/watchdog restarted
together; nginx stayed active. Fresh AWS privacy preflight passed. Deployment
verified each old file against baseline and recorded new hashes outside Git.

## Verification

| Date (controlled test clock) | Backend registration/session/Cloud cycle/restart | Local model/release verification |
|---|---|---|
| 2026-11-02 | PASS | PASS |
| 2026-11-03 | PASS | PASS |
| 2026-11-04 | PASS | PASS |
| 2026-11-05 | PASS | PASS |
| 2027-01-01 | PASS | PASS |
| 2030-01-01 | PASS | PASS |
| 2100-01-01 | PASS | PASS |

Backend tests use real P-256 signatures, HTTP handlers, encrypted SQLite and injected
clocks with a synthetic provider. Local Kotlin tests vary filesystem dates and
reopen the real model store; a source guard confirms eligibility has no clock
dependency. Release tests control the former clock source. These are not physical
future-date executions, nor a prediction of provider availability in 2100.

64 backend tests pass (58 baseline plus 6 permanent-access tests). Local JVM
PinnedModelStore tests pass. Host guards: model/release 7, permanent-access 2,
PIN-free 3, transcript versioning 44, Cloud integration runner 2 pass. Invalid
signature, unknown/revoked installation, stale timestamp, replay across backend
restart, session expiry, missing/invalid consent, key/owner mismatch, old-schema
migration and explicit expired-grant recovery are covered. Independent review
found a post-revocation retention issue; its reproducing test failed before the
fix and all tests passed afterward. No unresolved review finding remains.

## Current-time runtime acceptance

One authorized synthetic EN clip (~11.05 s, 353644 bytes) completed one real paid
attempt through API Gateway from unchanged POCO Preview5. A third Cloud/Local
history entry appeared for that document; its Local version remained active.
The server returned a completed result and no mass jobs were initiated. This
checks the changed authorization path; it is not a recognition benchmark.

A separate ephemeral synthetic key obtained HTTP200 with a bounded session.
Replay, invalid signature, stale timestamp, unknown identity, revoked session and
revoked job creation each returned HTTP401 through the real gateway. The test
identity was explicitly revoked; it sent no audio and created no paid jobs.

## Data preservation and remaining acceptance

Pre-change UI capture verified 6 documents / 11 versions, all original-text and
rendered-edit hashes, manual-edit markers and active version labels; historical
4 documents / 7 versions also match prior evidence. Installed package remains
com.monumentogram.dora.alpha.preview, versionCode5. Model UI is ready; unchanged
installed model is not copied/reimported. Keystore identity is checked by public
key matching and successful signed phone traffic; private keys are not exported.

Post-smoke expected inventory is 6 documents / 12 versions, with exactly one added
Cloud result. Final post-restart comparison, cleanup and CI receipts are pending
at this source-report revision. No APK update was performed; preservation across
a physical APK update is inherited from Stage9.1, not newly claimed here.

## Git, CI and handoff

Branch: `stage/9.3-permanent-cloud-access`, stacked on PR121 baseline
`7d4cf3f356e5c0eeeb625ab5cce69d9f021ef19a`; draft PR publication receipt follows.
PR121 backend/software/PIN-free checks were green when implementation proceeded;
emulator/persistence checks were still running and generic android-bootstrap had
an inherited failure. New branch CI must be checked before final PASS.

Changed areas: backend auth/store/service/operator CLI and tests; API/runbook;
owner model/release scripts and tests; Local model test only; Cloud workflow;
ADR-0029, DEC-050, backlog/status and implementation plan. No product Android code,
application ID/signing key, transcript format, model, IAM, TLS, quota or encryption
change. No PR116–121/main merge, Recovery benchmark or Stage10 implementation.

After acceptance, Stage10 DORA Signal can use the unchanged local/cloud transcript
history and explicit active-version contract. Preserve manual cloud consent and
owner-only access. Permanent registration still depends on intact device/server
keys, ordinary service operation, budget availability and explicit non-revocation.
