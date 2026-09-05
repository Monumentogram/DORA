# Dora MVP 1 — POC-RECOVERY-001 prospective Gate Set v0.7

Gate Set: `poc-recovery-stage0-v0.7`  
Protocol: `poc-recovery-protocol-stage0-v0.7`  
Date: 6 September 2026  
Status: **Owner-confirmed governance; persistence implementation separately gated by governance review**  
Decision context: `DEC-044`, `DEC-045`, `DEC-046`, `OD-15`, accepted `ADR-0005`

## Scope and authority

This Gate Set adds the exact owner-confirmed streaming persistence contract for bounded synthetic Stage 0 REC-I3. It is pinned to combined baseline `3c63ab09874f4d089e4363985aa8b5c99900c122` / tree `718eae8d8d619d17c25ac9d025e0e24db3d52f9e` and packet SHA-256 `9f8e3a6e4d20faf0d744310b83ca46bd34d5c6798785e1197e6b61fb8ae81011`. It does not authorize Recovery preflight, device/emulator execution, process-death/fault/measured campaigning, PASS/READY, production/dependency admission, a consumer, cross-process enforcement, retirement, or merge.

The v0.6 package's historical `implementationAllowed=false` remains true for authority granted by that package. Current `OD-15` separately keeps `recI3ImplementationAllowed=true`, `recI3NonMetricVerificationAllowed=true`, and `recI3ConditionalMergeAllowed=true`. This v0.7 governance grants no additional implementation authority. `phaseAAllowed`, `executionAllowed`, `measuredExecutionAllowed`, and `productionAdmissionAllowed` remain false.

## Exact inheritance

The v0.7 package inherits every unchanged v0.6 semantic by exact immutable reference:

| Artifact | SHA-256 |
|---|---|
| `DORA_MVP1_POC_RECOVERY_GATE_SET_STAGE0_V0_6.md` | `5ab6d105fe6c94868d77c25d1be065a1688ccb083fcbdc0c3f43096e73909063` |
| `poc-recovery-gate-set-stage0-v0.6.json` | `6a5f1f994e5084836527fded9bdf762ac1ed982cb5022b6da64090a283717755` |
| `poc-recovery-protocol-stage0-v0.6.json` | `9108cbffc3dc74a0e2a45868bf0c82b3827cb1e9023e1f0f12c53e7374c07a3d` |

All v0.1-v0.6 files remain unchanged. The only v0.7 overrides are active identity/status/locators; the REC-STREAM-TINK witness/intersection/admission model; checkpoint versus proven-C distinction; exact PRE/POST diagnostic matrices and rejected observations; public terminal and B-boundary accounting; candidate-specific ACTIVE retained ranges distinct from whole-object COMPLETED quarantine; zero streaming intent in this persistence phase; exact stream K12 and TRU-03 branches; sanitized evidence fields; collision/split-brain/ambiguous-commit results; and the 8192-byte exposure cap.

## Streaming persistence gate

VALID requires a cryptographically validated checkpoint, `P<=S<=E`, exact same-descriptor hashes of `[0,P)` and `[0,S)`, only completed public-authenticated oracle-equal bytes, `0<=C<=R<=A`, and `A-R<=8160`. C remains unchanged. `[S,E)` is untrusted lookahead.

PRE_INTERSECTION and POST_INTERSECTION use the exact closed matrix in ADR-0005 and the protocol JSON. PRE_INTERSECTION asserts neither C nor R and uses conservative `[0,E)` when non-empty. POST_INTERSECTION preserves proven C but admits no R; every row binds its rejected observation and uses its class-specific range. Authentication failure derives the exact remainder from B. Empty `B=E` and authenticated EOF produce no range row while retaining the sealed decision.

Persist outcome and any required range atomically; exact readback precedes evidence. Replay is hash-only and never reruns Tink. Collisions and split brain create no row/range. Ambiguous commit without exact intended state returns Retry. Source bytes/path remain unchanged. ACTIVE range denial is limited to cooperating opens under the shared single-process run lease; no retirement or cross-process guarantee exists.

## Effective K12 and TRU-03

K12 remains one stratum. `K12-STREAM-V0.7` fixes q=2, P=8192, C=4056, A=8137, S=8192, append=1, E=8193, reads `[4056,4080]`, R=8136, authentication failure, B=8192, exact ACTIVE `[8192,8193)`, pause after successful transaction/readback and before evidence acknowledgement, one SEALED VALID outcome, zero streaming intents, exact replay, and collision with no new state. The current scope is K12-PERSISTENCE; K12-CONSUMER is deferred and requires separate approval.

TRU-03 remains one row. Its microfile branch is unchanged. Its stream branch appends exactly 1, 4096, or 8192 synthetic bytes after witness freeze, then applies the exact v0.7 intersection/admission/diagnostic/range/replay rules without mutating the source or creating processing intent.

## Counts, blockers, and claim ceiling

The effective matrix retains exactly 46 unique IDs. Phase A remains `46*(3 emulator + 1 D2)=184`; full physical remains `46*(D1+D2+D5)=138`; the base hard-kill denominator remains 120 attempts per candidate. Phase A permits only FAIL or INCONCLUSIVE and full PASS still requires D1/D2/D5.

The ten active blockers remain `REC-RDY-01`, `REC-RDY-03` through `REC-RDY-11`; `REC-RDY-02` remains historically closed. `POC-RECOVERY-001` remains `BLOCKED / NOT_READY`. Governance review must be CLEAN before persistence source work. Implementation, evidence, independent implementation review, exact-head CI, preflight, campaign, and any later merge consideration remain separate gates.
