# Dora MVP 1 — POC-RECOVERY-001 Gate Set `stage0-v0.1`

Status: **Proposed exact contract — review required; execution prohibited**\
Date: 12 August 2026\
Decision: `DEC-044` / `OD-14`\
Machine protocol: `docs/stage0/poc-recovery-protocol-stage0-v0.1.json`\
Current authorization: `executionAllowed=false`

## 1. Scope and non-scope

This Gate Set governs only a future isolated Stage 0 comparison of:

- `REC-STREAM-TINK`: public Tink `StreamingAead` with Proposed template
  `AES128_GCM_HKDF_4KB`; and
- `REC-MICROFILE-TINK`: Tink `Aead` sealed microfiles with an authenticated manifest and a
  gate-compatible five-second cadence.

It does not admit a dependency, select a production format, finalize `ADR-AUDIO-001`, authorize a
run, define a production database, or permit production `:app` changes. Fifteen- and thirty-second
microfile cadences are non-gating observations/fallbacks and can never PASS this Gate Set.

## 2. Normative fixture and units

The recovery oracle uses deterministic synthetic mono PCM16 at exactly 16,000 samples/second,
little-endian, two bytes/sample. No real audio or microphone is involved.

```text
bytesPerSecond = 16000 * 1 * 2 = 32000
tailGateBytes  = 5.000 * 32000 = 160000
```

The controller assigns every plaintext frame a monotonically increasing byte interval. An input
byte is `writerAccepted` only after the candidate's bounded input operation returns successfully;
an in-progress call at kill time is excluded. Acceptance acknowledgement may not be delayed until
durability merely to reduce measured tail loss.

## 3. Exact definitions

### 3.1 Commit point

A `commit point` for unit `n` is the first instant at which all candidate-applicable durable facts
below are true and the external controller has observed the candidate's public commit event:

1. the complete ciphertext unit was produced by the reviewed public Tink primitive and its
   plaintext start/end offsets are fixed;
2. the ciphertext file was flushed and its file descriptor was synchronously persisted;
3. every required final rename and parent-directory durability step completed;
4. the authenticated manifest/checkpoint generation, when the candidate uses one, was fully
   written, synchronously persisted and published;
5. the PoC-local platform SQLite transaction that records the same run, unit, offsets, file
   identity and manifest/checkpoint generation committed with `synchronous=FULL`; and
6. the candidate-specific checkpoint proof approved by the independent Engineering/Security
   reviewer says that public-API recovery can authenticate that unit without depending on later
   uncommitted bytes.

The sequence is data first, authenticated publication second, SQLite record last. Earlier
intermediate states are deliberately uncommitted and must be reconciled. A log statement, buffer
flush, queued task, SQLite transaction begin, or successful plaintext write alone is not a commit
point. The Streaming AEAD candidate may not claim a segment boundary that the public API cannot
prove recoverable; absent an approved proof, its committed prefix is zero and execution remains
blocked.

### 3.2 Committed prefix

The `committed prefix` is the longest gap-free plaintext interval `[0, C)` composed only of units
whose commit points occurred before the hard kill. A later committed-looking unit after a missing,
unauthenticated or inconsistent unit does not extend the prefix and is quarantined for diagnosis.

### 3.3 Recovered authenticated prefix

The `recovered authenticated prefix` is the longest gap-free interval `[0, R)` that recovery
returns only after Tink authentication, associated-data checks and journal/manifest reconciliation.
No unauthenticated, guessed, zero-filled or duplicate byte may contribute to `R`.

### 3.4 Committed-byte loss

```text
committedLossBytes = max(0, C - min(C, R))
```

The approved gate is `committedLossBytes == 0` on every valid hard kill and every applicable fault
case. Recovery beyond `C` may reduce tail loss only when those bytes independently authenticate;
it never excuses committed-byte loss.

### 3.5 Tail loss

Let `A` be the exclusive end offset of all writer-accepted bytes before the kill and `R` the
exclusive end of the recovered authenticated prefix:

```text
tailLossBytes   = max(0, A - R)
tailLossSeconds = tailLossBytes / 32000.0
```

Every valid hard kill must satisfy `tailLossBytes <= 160000` and
`tailLossSeconds <= 5.000`. Five seconds is not a target average; it is a per-valid-attempt maximum.

### 3.6 Valid hard kill

A hard-kill attempt is `VALID` only when all of the following are evidenced:

1. its immutable attempt ID, candidate, phase, environment, stratum and fixture were scheduled
   before the run;
2. exact build/artifact/protocol identities and sanitized device preflight match the authorization;
3. the target PID was alive and the candidate emitted the assigned stratum barrier;
4. the external controller issued the approved non-graceful `SIGKILL` operation to that PID and
   independently confirmed death before starting recovery;
5. no graceful stop/finalize callback completed after the kill trigger;
6. the external schedule/preflight/controller/kill evidence envelope is complete and schema-valid;
   a missing or malformed candidate recovery output after a confirmed valid kill is retained as a
   candidate failure, not used to invalidate the attempt; and
7. there was no external invalidator such as wrong PID, controller disconnect before signal
   confirmation, device reboot, missing preflight, fixture drift or operator intervention.

A candidate crash, authentication failure, excessive tail loss, unrecoverable split-brain,
duplicate recovery output or other candidate-caused outcome is a valid result and may trigger
`FAIL`; it is never an invalid run.

## 4. Gate predicates

All applicable predicates are conjunctive. No averaging can hide a failing valid attempt.

| Gate | PASS-eligible predicate | Immediate FAIL | INCONCLUSIVE condition |
|---|---|---|---|
| `REC-G01-COMMITTED` | `committedLossBytes == 0` for every valid kill/fault case | any committed byte missing, altered or unauthenticated | fewer than the required valid attempts without a candidate failure |
| `REC-G02-AUTH-PREFIX` | 100% of returned bytes authenticate with exact AAD and form one prefix | unauthenticated/guessed byte accepted; gap hidden | oracle/evidence incomplete |
| `REC-G03-TAIL` | every valid kill has tail loss ≤5.000 s / 160000 bytes | any valid kill exceeds either equivalent limit | accepted-byte watermark unavailable |
| `REC-G04-KILL-COVERAGE` | 120 scheduled base attempts/candidate; ≥100 valid; ≥8 valid/stratum; phase environment minima met | invalid attempt hidden, deleted or relabeled; candidate failure invalidated | count/stratum/environment minimum not met |
| `REC-G05-IDEMPOTENCY` | repeated recovery yields identical prefix, quarantine and exactly-once processing-intent IDs | duplicate/missing intent, non-monotonic prefix, second recovery changes truth | applicable matrix case missing |
| `REC-G06-FAIL-CLOSED` | corruption, truncation, key loss and split-brain are explicitly classified and never silently deleted | corrupt/key-unavailable state exposed as healthy; orphan silently removed | expected classifier cannot be proven |
| `REC-G07-KEYS` | Keystore wrapping, unique run/segment key separation, no secret export/log and all key-loss cases pass | key/keyset/plaintext in Git, logs or evidence; key reuse; key loss misclassified | independent crypto review or key-loss evidence absent |
| `REC-G08-QUARANTINE-CLEANUP` | allowlisted app-private quarantine and cleanup matrix converge without committed-data loss | path escape, silent deletion, committed file removed, cleanup changes verdict | cleanup evidence missing |
| `REC-G09-NO-AUTO-CAPTURE` | microphone never opens/restarts and no recording permission is requested by the PoC | any automatic microphone start/restart | observation unavailable |
| `REC-G10-DEVICE-VERDICT` | full physical D1/D2/D5 campaign satisfies every gate | an approved critical gate fails on any required environment | Phase A only, or D1/D5 unavailable |

`processing-intent IDs` are PoC-local SQLite reconciliation records, not WorkManager jobs. Room,
SQLCipher and WorkManager are forbidden.

## 5. Hard-kill campaign

Each candidate has exactly 120 base attempt IDs: 12 strata × 10. The normative mapping is in the
machine protocol.

| Stratum | Required barrier |
|---|---|
| `K01` | accepted plaintext is buffered; no authenticated unit is complete |
| `K02` | crypto primitive is actively producing the next authenticated unit |
| `K03` | authenticated unit complete/close-or-flush returned; file not yet synced |
| `K04` | ciphertext file sync returned; final publication not started |
| `K05` | final ciphertext publish/rename complete; metadata publication pending |
| `K06` | authenticated manifest/checkpoint temporary generation written; not synced |
| `K07` | manifest/checkpoint temp sync complete; atomic publication pending |
| `K08` | manifest/checkpoint published; SQLite transaction pending |
| `K09` | SQLite transaction open before commit return |
| `K10` | candidate commit point observed; next input not yet accepted |
| `K11` | next unit active after at least one prior commit point |
| `K12` | recovery reconciliation/quarantine is active; recovery process receives hard kill |

Before any execution, the independent reviewer must approve a public-API barrier mapping for both
candidates. Internal Tink hooks, reflection and timing-only sleeps cannot establish a barrier.

### 5.1 Phase A allocation

For every candidate and stratum, base slots `01`–`06` target the pinned API 36 emulator and slots
`07`–`10` target physical D2: 72 emulator plus 48 D2 attempts per candidate. Eligibility requires:

- at least 100 valid hard kills total;
- at least 8 valid per stratum;
- at least 60 valid emulator and 40 valid D2 kills; and
- at least 4 emulator and 2 D2 valid kills in every stratum.

Even if every gate passes, Phase A verdict is `INCONCLUSIVE`. A critical predicate may produce
`FAIL`. `PASS` is forbidden.

### 5.2 Full physical allocation

A later, separately authorized full campaign uses fresh 120 base attempts per candidate. In each
stratum slots `01`–`04` target D1, `05`–`07` D2 and `08`–`10` D5: 48 D1, 36 D2 and 36 D5.
Eligibility requires at least 100 valid total, at least 8 per stratum, at least 40 D1, 30 D2 and
30 D5 valid kills, and at least 2 valid kills from each device profile in every stratum.

Phase A attempts cannot be substituted into the full physical denominator. Procurement of D1/D5
is deferred and not required by this package.

### 5.3 Invalidation and replacement

- Every base attempt remains in the immutable ledger as `VALID`, `INVALID`, or `NOT_RUN`.
- Only pre-enumerated controller/environment invalidators may yield `INVALID`; the exact reason and
  evidence must be recorded. No silent restart or overwritten attempt ID is allowed.
- A valid candidate-caused failure is not replaceable.
- At most one explicit replacement may be scheduled for an eligible invalid base attempt, with ID
  `<base-id>-R1`, the same candidate/environment/stratum and a back-reference to the original.
- Replacements never erase or reduce invalid counts. At most 20 replacements per candidate are
  allowed. Any further campaign requires a new protocol version and owner authorization.
- Gate counts include valid base attempts plus valid eligible replacements, but the contract still
  reports all 120 base dispositions and every replacement separately.

## 6. Candidate-specific commit contract

### 6.1 Streaming AEAD

- Only public `StreamingAead` and public key-management APIs are allowed.
- `AES128_GCM_HKDF_4KB` is Proposed, not approved. The name denotes a 16-byte main key,
  HMAC-SHA256 HKDF, 16-byte derived AES-GCM keys and 4096-byte ciphertext segments in the reviewed
  v1.23.0 source.
- The template helper class is deprecated in v1.23.0; the independent review must approve the
  current public construction path and exact parameters.
- A file flush or observed ciphertext-length increment is not automatically a commit point. The
  reviewer must establish how a prefix is authenticated after truncation using only public API and
  how end-of-file is distinguished from authentication failure. Until then,
  `streamingCheckpointProof.status=PENDING` and execution is blocked.

### 6.2 Sealed microfiles

- Five-second units are exactly 160000 plaintext bytes except the explicit final short unit.
- Each file is completely sealed by public Tink `Aead` before durable publication.
- A unique segment key and AAD bind protocol version, candidate, run ID, segment index, plaintext
  interval, cadence and manifest generation. The authenticated manifest is generation-numbered,
  monotonic and contains only key references, never key bytes.
- The exact AEAD template, wrapping construction and manifest encoding remain pending independent
  review.
- Fifteen- and thirty-second variants set `passEligible=false` even if sampled losses happen to be
  ≤5 seconds.

## 7. Fault, quarantine, idempotency and cleanup matrix

Every applicable row is mandatory. Phase A plans three emulator repetitions and one D2 repetition
per candidate; the full campaign plans one repetition on each of D1, D2 and D5. These are separate
from the hard-kill denominator.

| ID | Injection | Required fail-closed outcome |
|---|---|---|
| `COR-01` | flip a bit in committed ciphertext | authentication failure at affected unit; earlier prefix preserved; affected/later state quarantined |
| `COR-02` | alter authenticated manifest/checkpoint bytes or tag | generation rejected; no unauthenticated fallback; last valid prefix or explicit corruption |
| `COR-03` | alter SQLite offset/hash/file identity | split-brain classification; authenticated data is not relabeled committed; no deletion |
| `TRU-01` | truncate inside uncommitted tail | committed prefix preserved; tail classified and quarantined/discarded only by explicit policy |
| `TRU-02` | truncate inside a committed unit | `FAIL`; committed loss detected, never concealed as a shorter healthy run |
| `TRU-03` | append unauthenticated bytes | appended bytes rejected and quarantined; prefix unchanged |
| `KEY-01` | remove/invalidate Android Keystore wrapping key | `KEY_UNAVAILABLE`, not `CORRUPT`; ciphertext and metadata retained |
| `KEY-02` | remove run-key envelope/reference | run-level `KEY_UNAVAILABLE`; no new key generated under the old reference |
| `KEY-03` | remove one microfile segment key or make a reviewed streaming key/derivation unavailable | key-scoped loss identified; earlier prefix remains; no silent skip across the hole |
| `SPL-01` | file durable, SQLite record absent | uncommitted orphan classified; never promoted without authenticated reconciliation |
| `SPL-02` | SQLite record present, file missing | explicit split-brain and `FAIL` if record was committed; no fabricated bytes |
| `SPL-03` | manifest/checkpoint ahead of SQLite | authenticated generation retained for reconciliation/quarantine; no implicit commit |
| `SPL-04` | SQLite ahead of manifest/checkpoint | explicit split-brain; last jointly durable prefix only |
| `SPL-05` | temporary file/generation remains | deterministic temp classification; final namespace not overwritten blindly |
| `QUA-01` | unknown/orphan file in allowlisted run directory | hash/size/reason recorded, move to app-private quarantine, no open-as-healthy |
| `QUA-02` | process death during quarantine move | next recovery converges to one item without loss or duplicate record |
| `IDE-01` | run recovery twice without mutation | byte-identical prefix and stable classifications/intent IDs |
| `IDE-02` | hard kill during recovery, then recover again | same canonical result as uninterrupted recovery; no duplicate processing intent |
| `CLN-01` | hard kill during explicit cleanup | rerun converges; committed/quarantined evidence is not silently lost |
| `CLN-02` | deny cleanup deletion | explicit retryable cleanup state; verdict and evidence retained |
| `CLN-03` | explicitly delete test keys after evidence retention | subsequent access reports key loss; cleanup receipt contains references only, never key material |

Quarantine and cleanup paths must resolve beneath the exact allowlisted PoC-private root. Public
evidence contains only sanitized JSON, hashes, sizes and classifications. Ciphertext, plaintext,
keysets, database files, WAL/journal files and device-unique identifiers are excluded from Git and
Actions artifacts.

## 8. SQLite boundary

The allowed journal is platform `android.database.sqlite` with a PoC-only schema containing run,
unit, offset, file identity, manifest/checkpoint generation, state and processing-intent ID. It is
used only to inject and reconcile DB/file split-brain. The canonical proposed execution profile is
`PRAGMA synchronous=FULL`; the exact journal mode and durability implementation remain part of the
independent review and must be frozen before execution.

No Room, SQLCipher, WorkManager, production table, migration, repository port or production
dependency is allowed. Emulator and physical platform provenance follow the recovery-only
extension in `DORA_MVP1_IP_ASSET_POLICY.md` and the machine evidence packet.

## 9. Verdict algorithm

1. Any privacy/key leak, automatic microphone start, unauthenticated accepted byte, nonzero
   committed loss or other critical FAIL predicate makes that candidate `FAIL` immediately.
2. Otherwise, unmet valid-count/stratum/environment/fault-evidence requirements produce
   `INCONCLUSIVE`.
3. Phase A is always `INCONCLUSIVE` unless it is `FAIL`.
4. A 15/30-second microfile observation is never PASS-eligible.
5. Full candidate `PASS` requires the separately authorized D1/D2/D5 campaign, all conjunctive
   gates, approved crypto/recovery reviews and no critical failure.
6. Candidate comparison/winner selection and final `ADR-AUDIO-001` remain separate decisions.

## 10. Current blockers

- Proposed decision/Gate Set/protocol package review incomplete;
- independent recovery Engineering/Security reviewer unassigned and approval absent;
- streaming public-API checkpoint proof absent;
- exact microfile AEAD template/manifest/key construction unapproved;
- future harness-resolved dependency graph absent by design because Gradle wiring is prohibited;
- D2 recovery SQLite runtime preflight absent; D1 and D5 unavailable;
- no recovery harness exists; and
- Project owner has not issued a post-review execution authorization.

Therefore `executionAllowed=false` and the fail-closed checker must return non-zero.
