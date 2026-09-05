# DORA MVP-1 PoC Recovery I3 Streaming Option A Scope — Stage 0 v0.1

## Owner decision and immutable inputs

This scope implements the separate Project-owner Option A decision recorded by `DEC-045` on
2026-09-05. It is anchored to decision commit `7ec8b845c2fd0194097eefc024eed1ca7cae2e9a`, tree
`6e17e9deeaac7ef7fa2fc7afe09eb16418f9554f`, which contains the decision record, Product Decisions
registry update, Backlog update and Stage Status update.

The immutable proof basis remains final commit `19807f6ea8166fff972e1537beeb131def8bfa1d`, tree
`8920855c9b8642162e5df16204d796d51db4fa86`, proof-scope commit
`477b5e354c8649fb0202fb039758526c4d41de03`, and all 27 final proof observations. The final CLEAN
review identities are Markdown SHA-256
`93f763a93e0fdb79a1658f84194abf5162d6e2a7f1038478a97b4d7f37080f11` and JSON SHA-256
`4c7cc94087262ae46c13dbba823d04c5103671963f58d6f86937a798d8c4bd04`. The prepared external
decision packet `rec-i3-streaming-after-proof-decision-packet.md` has raw SHA-256
`becb78d81e8bea5fdccb2e109b6b8eeada6f47353399222900bd0af5756e0db3`.

`DEC-044`, `OD-15`, the active Recovery Gate Set v0.6 and protocol v0.6 remain governing and
unchanged. This scope is a bounded synthetic/non-metric implementation preparation under `OD-15`.

## Exact semantic contract

The controller must retain independently authenticated, pre-existing, contiguous oracle-equal bytes
in `R` even when a later public read terminates in authentication failure. `R` may exceed durable
checkpoint `C`; `C` must not advance. It must never adopt metadata, semantic commit, processing
intent or other durable meaning from unauthenticated bytes. The unauthenticated or corrupt remainder
must be classified for bounded quarantine without implementing persistence mechanics.

The invariant is `0 <= C <= R <= A`; all returned bytes are authenticated. The 8,160-byte /
0.255-second design bound is unchanged. Physical-source extent is a bounded observed candidate byte
sequence supplied by the harness policy; an arbitrary post-crash append is not trusted. For
`TRU-03`, preserved prefix means the same recovered byte values and authentication, not necessarily
an identical endpoint after a pre-existing authentic tail becomes readable. A same-file suffix must
not silently promote a whole object.

## Eligible next implementation

No production streaming recovery/controller file exists at this decision head. The existing main
sources provide only the pinned crypto boundary, while the existing streaming artifacts are JVM
tests. Therefore the next slice may own only these exact paths:

1. Create `android/poc/recovery/src/test/kotlin/com/monumentogram/dora/poc/recovery/crypto/RecoveryStreamingAuthenticatedTailController.kt` as a deterministic synthetic host/JVM controller seam.
2. Modify `android/poc/recovery/src/test/kotlin/com/monumentogram/dora/poc/recovery/crypto/RecoveryStreamingBoundProofTest.kt` to prove the Option A accounting and failure-classification boundary against that seam.

The new controller remains test-source-only. It may consume the existing typed public streaming
boundary and deterministic in-memory fixture. It may expose explicit `A`, `C`, `R`, authenticated
returned bytes and a bounded remainder classification. It must not write files, create a database,
open Android APIs, issue process signals, schedule faults, emit evidence JSON or introduce a new
dependency.

Root must review this scope before code begins. If repository structure or a governing source
changes before that review, a successor scope is required instead of expanding these paths.

## Exclusions and nonclaims

This scope excludes every production source path, Android runtime code, schema, SQLite journal,
candidate storage, quarantine implementation, reconciliation, protocol/Gate Set JSON or Markdown,
ADR acceptance, Gradle/build/dependency/lock/R8 configuration, evidence JSON, workflow, common
reconciliation and all files outside the two future-owned paths above.

It authorizes no device or emulator execution, preflight, process death, hard kill, fault campaign,
durability campaign, measurement, persistence proof, package/R8 claim, PASS/READY verdict,
production admission, push, PR, merge, cherry-pick or rebase. Option B is rejected only for this
scope and remains preserved historically. All ten `REC-RDY` blockers and every existing campaign or
admission flag remain unchanged.
