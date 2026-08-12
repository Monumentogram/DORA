# POC-RECOVERY-001 governance remediation v0.4

Reviewed input: `c61603d30c01c72347aa205c247729ad534c2882`
Source: final advisory review for Draft PR #11
Scope: governance/evidence/static validators only
Formal reviewer claimed: **no**
Implementation/execution allowed: **no / no**

This remediation closes the four stable findings in `review-findings-v0.3.json` without modifying
the v0.1–v0.3 Gate Set/protocol artifacts. The active v0.4 contract inherits the exact v0.3
protocol by SHA-256 and adds only the required fail-closed controls.

| Finding | Disposition | Evidence |
|---|---|---|
| `REC-GOV-V03-001` P0 | Closed prospectively: ninth `key-confirmation/run.kc` family, exact separate plaintext/AAD schemas, 13-step durable bootstrap, SQLite identity, taxonomy/reconciliation/K01–K12 prerequisite and 12 fault rows | v0.4 Markdown/Gate Set/protocol |
| `REC-GOV-V03-002` P0 | Closed after independent immutable LICENSE and NOTICE byte verification at JetBrains commit `f92ce9af0629ee8dcc8743dcc2c1ca297aaacc7c` | `jetbrains-annotations-license-notice-verification-2026-08-12.md` |
| `REC-GOV-V03-003` P1 | Closed: claims are limited to the future `:poc:recovery` boundary; existing other-module tooling/test/lint/UTP lockfile occurrences are inventoried and are neither hidden nor treated as recovery admission | `base-lockfile-tooling-inventory-2026-08-12.json` |
| `REC-GOV-V03-004` P1 | Closed: prospective policy and exact governance evidence are closed; future actual graph verification/Product-IP disposition remains open and blocking | OD-14, DEC-044, readiness/review-role evidence |

Historical `F-06` is considered closed only on the immutable LICENSE/NOTICE evidence recorded by
this remediation. That Stage 0 packet disposition is not Production Legal, production admission,
redistribution approval or dependency admission.

`REC-JSR305-EXCLUDE-001` does not assert repository-wide absence. It does not require approval to
use the excluded JSR-305 artifact. A future separately authorized implementation must prove the
artifact absent from every covered recovery configuration and packaged output, pass exact narrow
R8 verification, and obtain a separate Product/IP disposition for the actual graph.

The remaining blockers are intentional: distinct accountable Engineering/Security review; separate
implementation scope; exact future recovery graph/package/R8 evidence; actual-graph Product/IP
disposition; non-metric implementation verification; emulator/D2 preflight; separate execution
authorization; and later Production Legal/Security and D1/D5 where applicable. Nothing here flips
`implementationAllowed=false` or `executionAllowed=false`.
