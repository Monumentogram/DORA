# Dora MVP 1 — REC-I3 key-confirmation controller first slice

Scope ID: `rec-i3-key-confirmation-controller-stage0-v0.1`\
Backlog: `POC-RECOVERY-001` / partial `REC-I3`\
Date: 5 September 2026\
Authority: `OWNER-AUTH-BATCH-20260819-01` / [OD-15](DORA_MVP1_STAGE0_OWNER_DECISION_OD15.md)\
Implementation base: `da1d9bd13b71d609fe7ec4ea62fe1e984f726040`

## 1. Selected contract and boundary

This additive implementation scope applies the frozen selected key-confirmation portion of
active `poc-recovery-protocol-stage0-v0.6`. It adds no product decision, crypto construction,
classification, threshold or Gate Set change. Exact v0.4/v0.5 inherited semantics apply only where
the active v0.6 protocol does not override them. Historical protocols, evidence and the existing
REC-I1/REC-I2B source, tests, Gradle dependency graph, locks and R8 rules remain byte-preserved.
The experiment decision remains Proposed; OD-15 authorizes implementing these exact semantics
and does not elevate the global experiment, readiness or admission state. Subsequent REC-I3
implementation slices remain under the same current OD-15 authority without a new activation gate.

The slice adds a read-only controller and deterministic host tests inside isolated `:poc:recovery`.
It consumes a supplied run-row/artifact snapshot and the existing REC-I2B typed crypto boundary.
It performs no filesystem scan, SQLite transaction, Keystore mutation, file publication,
quarantine move, microphone operation or process orchestration. Supplied path/type/containment
observations are harness inputs and cannot establish physical filesystem evidence.

Completing this slice is not successful completion of REC-I3. The full bootstrap/writer,
publication, journal, reconciliation, quarantine, external controller and exact implementation
review remain separate unfinished REC-I3 work. No readiness blocker closes and Recovery preflight
remains gated on successful full REC-I3 implementation and review.

## 2. Ordered behavior

The controller evaluates the accepted key-confirmation stages in order:

1. An absent durable run row with a bootstrap remainder returns `INCOMPLETE_KEY_BOOTSTRAP`,
   retaining alias/temp/final observations. An entirely absent state is a neutral absence, never
   valid confirmation or inferred commit.
2. A durable run row without the final confirmation returns `KEY_CONFIRMATION_MISSING`.
3. Exact canonical relative-name, supplied containment/component/leaf type, recorded ciphertext
   length/SHA-256 and canonical alias digest checks precede alias access and decrypt. A mismatch
   returns `CORRUPT_KEY_CONFIRMATION`; opening or decrypting the alias is forbidden on that path.
4. An absent or unusable existing alias returns `KEY_UNAVAILABLE`. The only runtime opening path
   is the already approved `RecoveryRunAeadProvider.openExisting`; there is no generation fallback.
5. A positive typed authentication/AAD failure can establish the effective `KEY-04` result only
   with all eight v0.6 preconditions, including run/ciphertext-bound controlled key-replacement
   provenance. It returns `KEY_UNAVAILABLE_KEY_MISMATCH` through the existing exact routing contract.
6. Successful decrypt followed by malformed or wrong-identity plaintext returns
   `CORRUPT_KEY_CONFIRMATION`, preserving the post-decrypt `KCF-07` boundary.

An unknown or operational failure without enough evidence for an accepted KEY result remains an
explicit non-verdict diagnostic. It is never accepted as valid confirmation, corruption evidence
or a ninth KEY classification. Authentication failure without the exact controlled replacement
provenance does not claim that `KEY-04` was observed. Separate KCF-04/KCF-05 campaign orchestration
and later-envelope reconciliation are outside this first slice.

The success result means only that this supplied confirmation snapshot validated. It grants no
publication, run-readiness, campaign or preflight authority. Mutable caller byte arrays must not
alter a snapshot after construction or change bytes between identity verification and decrypt.

## 3. Acceptance evidence

Host tests must exercise both candidates, the absent/alias/temp/final combinations, each identity
gate and its no-open/no-decrypt ordering, alias failure with no replacement, actual REC-I2B decrypt
success/failure, authenticated malformed/wrong-identity plaintext, KEY-04 provenance binding,
unknown/provider errors, repeated evaluation and defensive byte ownership. Fixtures contain only
fixed synthetic IDs, repository-owned bytes and in-memory test keys. No raw key, ciphertext,
database or audio artifact is published as evidence.

Required local checks are the Recovery JVM suite, formatting, Detekt, Android lint and the existing
REC-I2B crypto-policy check. The final branch also runs applicable documented Stage 00 checks and
the governance validator self-tests. An additive exact successor validator may admit only the
named new controller/test files and this scope/evidence record while preserving the predecessor
freeze and failing on any extra Recovery source or authority elevation.

The [local evidence record](../evidence/poc-recovery-001/rec-i3-key-confirmation-controller-local-evidence-stage0-v0.1.json)
binds source digests, exact commands and outcomes. Build or environment failures are retained as
failures; CI and review status must be reported from live exact-head records. Existing REC-I2B
graph-probe output retains its historical scope and is not relabeled as a completed REC-I3 graph.
Final full REC-I3 graph/locks/verification/package/release-R8 and accountable review remain pending.

## 4. Claim ceiling

`POC-RECOVERY-001` stays `BLOCKED / NOT_READY`; all ten active `REC-RDY` blockers remain open.
`phaseAAllowed=false`, `executionAllowed=false`, `measuredExecutionAllowed=false` and
`productionAdmissionAllowed=false` remain exact. No Recovery hard-kill/fault campaign, Phase A,
measurement, device/emulator execution, PASS, production admission or PR merge is performed by
this task. Conditional merge authority in OD-15 is not exercised by the current task.
