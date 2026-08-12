# Immutable JetBrains annotations 13.0 LICENSE/NOTICE verification

Status: **VERIFIED FOR THE EXACT STAGE 0 GOVERNANCE PACKET**
Verified at: `2026-08-12T14:38:33Z`
Tool: `gh api` GitHub Contents API plus `System.Security.Cryptography.SHA256`
Upstream commit: `f92ce9af0629ee8dcc8743dcc2c1ca297aaacc7c`

| Object | Immutable locator | Bytes | SHA-256 | Result |
|---|---|---:|---|---|
| LICENSE | `https://github.com/JetBrains/intellij-community/blob/f92ce9af0629ee8dcc8743dcc2c1ca297aaacc7c/LICENSE.txt` | 11,358 | `cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30` | match |
| NOTICE | `https://github.com/JetBrains/intellij-community/blob/f92ce9af0629ee8dcc8743dcc2c1ca297aaacc7c/NOTICE.txt` | 127 | `0479f6a86003002dec1da1667f2f8320253c7225c6ffffc05cf7e0988bd8c72c` | match |

The GitHub Contents API returned each object at the exact 40-character commit. The verifier decoded
the API's base64 content in memory and computed SHA-256 independently over the resulting bytes. No
mutable branch locator was used and no upstream byte was written to the repository.

Project-owner / Stage 0 Product/IP disposition for this packet: exact JetBrains annotations
evidence is accepted for governance-package evaluation; the applicable LICENSE and the immutable
upstream NOTICE-preservation requirement are recorded. If the artifact enters a separately
approved future Stage 0 resolved graph, `NOTICE.txt` must be preserved in that future notices
packet. This disposition is not Production Legal, production admission, dependency admission,
redistribution approval, implementation authorization or execution authorization.

Historical finding `F-06` is treated as closed only after this immutable verification. Mutable
branch URLs, placeholder/PENDING values, missing LICENSE/NOTICE digests and missing NOTICE
preservation language are fail-closed validation errors.
