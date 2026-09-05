# DORA MVP-1 PoC Recovery I3 Streaming Bound Proof Scope — Stage 0 v0.1

## Owner decision

The owner selected REC-I3-STREAM-BOUND-001 Option C in parent task
`01a07028-54d4-79b1-8549-126c2ec0b98a`, owner message
`01a0715f-a952-7022-8487-448ad429cae6`, by the user text
“Принимай рекомендуемый вариант”, referring to the immediately preceding explicit
recommendation of Option C.

This authorizes only a deterministic synthetic JVM proof through the existing pinned
public Tink boundary. Options A and B remain unresolved. This scope records no Product
Decision amendment and grants no threshold, protocol, Gate, runtime, campaign, PASS,
READY, production-admission, integration or merge authority.

## Immutable authority

The proof starts from commit `3619a9c1d285e2a1c27133467a6b987d23174570`, tree
`7144805ffa10546ad06c773b70fb7743e57a61c3`.

- `docs/stage0/DORA_MVP1_POC_RECOVERY_GATE_SET_STAGE0_V0_3.md`, blob
  `dec6c537591698dfe80ee8a5a0e4d68a4d33088d`, SHA-256
  `7d24e5aa0c2dd0c65ef8def12e687d39f5d0bfc30a222be51f29bffd02c772a9`:
  §5 line 260 permits recovery beyond C only for independently authenticated bytes in
  one contiguous oracle-equal prefix; TRU-03 line 465 requires appended unauthenticated
  bytes to be rejected/quarantined with the prefix unchanged.
- `docs/stage0/poc-recovery-protocol-stage0-v0.3.json`, blob
  `202a8f183d0ea8cfdb6a8cbd656f604154441612`, SHA-256
  `376c6bec9d6632ff0824465ee890f953445c0843716b8a1b3a044f322d03a0c9`:
  `/fixture/acceptedWatermark`, `/fixture/ackDelayUntilCommitForbidden`, checkpoint
  model, K03, and `/faultCampaign/cases/8` (TRU-03) remain unchanged.
- The pinned cached `com.google.crypto.tink:tink-android:1.23.0` jar has SHA-256
  `c656918451b01c45ce5b20c7b6d4c388f956f61b3a3528e769048c8944c42f9e`.

## Question

Observe, without interpreting policy, how checkpoint-capped and actual-tail public
StreamingAead reads differ at q=1/2/3, before and after checkpoint commit, and after
1-byte, 4,096-byte and 8,192-byte unauthenticated appends. Record which requested
plaintext reads return successfully, which call fails, whether EOF is authenticated,
and the resulting raw A/C/R quantities under each explicitly labeled source assumption.

Physical source assembly and read policy are separate. The proof first appends fault
bytes to candidate physical bytes. A checkpoint-capped reader then selects only the
checkpoint prefix from that assembled source; an actual-tail reader selects the entire
assembled source. Observed completed q remains distinct from attempted ciphertext extent.

## Exact scope

Create only
`android/poc/recovery/src/test/kotlin/com/monumentogram/dora/poc/recovery/crypto/RecoveryStreamingBoundProofTest.kt`.
Use in-memory deterministic synthetic bytes, fresh existing typed streaming keysets, the
exact 16-byte input key / 16-byte derived key / SHA-256 HKDF / 4,096-byte segment suite,
and public `newEncryptingStream` / `newDecryptingStream` only.

The fixture covers:

- the separate q=1 24-byte-header transition;
- q=1/2/3 exact fills and 1-byte/4,080-byte triggers;
- raw A/C/R before and after commit under checkpoint-capped and actual-tail policies;
- completed reads before a later expected cryptographic read failure;
- authenticated final EOF control versus an open non-final EOF failure;
- TRU-03 physical appends of 1, 4,096 and 8,192 deterministic bytes, including capped
  reads after append;
- a raw one-byte partial next-segment extent, where lookahead may authenticate the prior
  segment although completed q has not advanced;
- deterministic in-progress calls with nearly-empty and exact-full buffers, and a partial
  downstream failure, retaining prior accepted A and asserting observed R never exceeds A.

Plaintext is `((index * 31 + 7) and 0xff).toByte()`. Append bytes are
`((index * 17 + 0xa5) and 0xff).toByte()`. Plaintext, ciphertext, AAD, keysets, envelopes
and run wrapping material remain in memory and are never emitted to logs.

## Evidence and exception discipline

The test catches narrowly around public decrypting-stream construction and read calls.
It records a neutral authentication-failure observation only after asserting the pinned
case produced the expected Tink cryptographic exception chain. Unexpected exceptions
fail the test. Oracle equality and accounting checks execute outside the catch. Only
`-1` is authenticated EOF; an unexpected zero return fails rather than looping.

Each observation emits only its case identifier, source policy, completed q, attempted
ciphertext byte count, A, C, R, successful read sizes, terminal outcome and safe
exception/cause class names. The external report may conclude `SUPPORTS_OPTION_A`,
`SUPPORTS_OPTION_B` or `INSUFFICIENT`; it may not select the protocol semantics.

## Owned files and exclusions

This slice owns exactly:

1. this additive scope document;
2. `RecoveryStreamingBoundProofTest.kt`.

It does not modify production source, shared contract/schema/reconciliation/governance,
Gradle, dependencies, locks, manifests, R8, package configuration, host controller,
backlog or status. It performs no device, emulator, preflight, signal, process-death,
fault/metric campaign, network, filesystem/SQLite durability or production execution.

## Nonclaims and review gate

The proof does not decide the meaning of TRU-03 “prefix unchanged,” set a 12,240-byte
threshold, weaken 8,160, treat recovered R as committed C, establish scheduler or
durability behavior, or mint runtime admission. A deliberately incomplete test observer
may supply harness-incompleteness RED; only the later public-API observations are the
experiment. Independent Sol HIGH review of the frozen two-commit candidate is required
before this evidence is routed for the A/B decision. No push, PR, merge, rebase,
cherry-pick or integration is authorized.
