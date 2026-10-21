# ADR-RECORDING-013: bounded production Alpha preflight successor

Status: owner-scoped implementation, 2026-10-21; no distribution or installation admission.

The owner requests software/security preflight from exact PR105 HEAD, while explicitly prohibiting user 0 security/storage/package mutation. Production code becomes 5 / `0.1.0-alpha.3`: read-only package metadata reports installed code 4. Debug and isolated package IDs remain distinct.

Preserve the historical Stage7 signing tool and evidence. Use two clean strict release builds with pinned private VAD inputs, owner-local signer outside Git and the new `tools/alpha_security_preflight.py` verifier. Signing uses alias `dora-internal-alpha`, the existing accepted certificate and file-based password input; no key is copied to CI. A locally signed candidate is not distribution acceptance.

Require exact-source clean Git state, production manifest/DEX isolation, release override denial, native alignment, locked dependency inventory/SBOM and two-build payload comparison. The successor scope is enumerated in `alpha-security-preflight-v1.json`. CI runs inherited 4 and isolated 3 jobs on this branch; production artifact checks supplement those jobs. Missing physical or custody evidence remains explicitly blocked.

No runtime authorization, vault format, VAD profile or five-minute cap changes. Windows checkout pins vendor POM line endings to LF, preserving its already admitted SHA256 rather than changing dependency verification.

Restoration stays OPEN until all ten real checks under a separately approved user 0 protocol. Stage8.6 remains NOT_READY, LONG outcomes immutable, battery/PERF-REC-001 deferred and nonblocking. No Stage9, Cloud, Sheet, merge or installation work.
