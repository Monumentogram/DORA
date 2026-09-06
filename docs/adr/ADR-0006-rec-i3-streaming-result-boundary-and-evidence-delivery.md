# ADR-0006: REC-I3 streaming result boundary and evidence delivery

- Status: Accepted for bounded Stage 0 REC-I3 governance and later implementation under OD-15
- Date: 2026-09-06
- Decision owner: Project owner
- Applies to: controller result/evidence boundary only; ADR-0005 remains authoritative for durable persistence
- Gate Set/protocol: 'poc-recovery-stage0-v0.8' / 'poc-recovery-protocol-stage0-v0.8'

## Context

ADR-0005 and DEC-046 close durable schema-v4 streaming persistence. The controller still needs a closed non-persistable result vocabulary, strict evidence references, and a receipt-preserving cleanup/delivery rule. This prospective correction precedes v0.8 controller implementation and changes no durable row, DDL, migration, identity preimage, replay hash, K12 rule, source-immutability rule, or Stage 0 authority.

The exact Task 5 predecessor is 'e61d9b043fe83aebb674a126ea6aebce72be085b' / tree '85db58154681b17fe5e5101629bded91d48ca59a'. The five v0.7 files are pinned by SHA-256 in the v0.8 machine contracts. All v0.1–v0.7 artifacts remain immutable and no v0.7 identifier is emitted by v0.8.

## Closed controller boundary

The sealed public variants remain exactly 'PersistedValid', 'Retry', 'Rejected', and 'Fatal'. The following are controller-boundary values only, never columns, DDL values, identity-preimage fields, or serialized 'recovery_stream_outcome_v4' values.

    RecoveryStreamingResultStage =
      LEASE | PREREQUISITE | SOURCE_PROOF | RANGE_ADMISSION | STREAM_READ | JOURNAL

    RecoveryStreamingResultClassification =
      STREAM_CHECKPOINT_MISSING |
      STREAM_CHECKPOINT_STRUCTURAL |
      STREAM_CHECKPOINT_AUTHENTICATION_REJECTED |
      STREAM_CHECKPOINT_AUTHENTICATION_OPERATIONAL |
      STREAM_CHECKPOINT_SPLIT_BRAIN |
      STREAM_SOURCE_WITNESS_MISSING |
      UNSAFE_PATH |
      STREAM_SOURCE_IDENTITY_CHANGED |
      STREAM_SOURCE_EXTENT_LIMIT_EXCEEDED |
      RUN_LEASE_CONTENDED |
      STREAM_ACTIVE_RANGE_DENIED |
      STREAM_ZERO_PROGRESS |
      STREAM_READ_CROSSES_ACCEPTED_END |
      STREAM_PUBLIC_READ_OPERATIONAL |
      JOURNAL_STRUCTURAL |
      JOURNAL_ATTEMPT_CONFLICT |
      STREAM_RANGE_QUARANTINE_COLLISION |
      ARTIFACT_IO_BEFORE_EXACT_SOURCE_HASH |
      JOURNAL_OPERATIONAL |
      JOURNAL_COMMIT_STATE_UNRESOLVED

    SafeExceptionType = NONE | IO | CRYPTO | SQLITE
    PostReceiptCleanup =
      NONE |
      PUBLIC_STREAM_CLOSE_FAILED |
      SOURCE_DESCRIPTOR_CLOSE_FAILED |
      PUBLIC_STREAM_AND_SOURCE_DESCRIPTOR_CLOSE_FAILED
    EvidenceDelivery = DELIVERED | PENDING
    ExistingRecordKind = STREAM_CHECKPOINT | STREAM_OUTCOME | STREAM_RANGE

'SafeExceptionType' is present only on 'Retry'. 'NONE' is required for retry without an exception. Exact retries are:

- 'Retry(LEASE, RUN_LEASE_CONTENDED, NONE)'
- 'Retry(PREREQUISITE, STREAM_CHECKPOINT_AUTHENTICATION_OPERATIONAL, CRYPTO)'
- 'Retry(SOURCE_PROOF, ARTIFACT_IO_BEFORE_EXACT_SOURCE_HASH, IO)'
- 'Retry(STREAM_READ, STREAM_ZERO_PROGRESS, NONE)'
- 'Retry(STREAM_READ, STREAM_PUBLIC_READ_OPERATIONAL, IO|CRYPTO)'
- 'Retry(JOURNAL, JOURNAL_OPERATIONAL, SQLITE)' for known non-ambiguous journal failures
- 'Retry(JOURNAL, JOURNAL_COMMIT_STATE_UNRESOLVED, SQLITE)' only for ambiguous commit/readback state

'Rejected(SOURCE_PROOF, STREAM_SOURCE_EXTENT_LIMIT_EXCEEDED)' carries no exception. Fatal mappings are PREREQUISITE for checkpoint missing, structural, authentication rejected, split brain, witness missing, and unsafe path; SOURCE_PROOF for source identity changed; RANGE_ADMISSION for active range denied; STREAM_READ for read crossing accepted end; and JOURNAL for structural, attempt conflict, and range collision. Fatal and Rejected carry stage/classification only. No unlisted combination is constructible or serializable.

'JOURNAL_AMBIGUOUS_COMMIT_WITH_NO_EXACT_INTENDED_STATE' is the only rejected legacy alias. 'JOURNAL_OPERATIONAL' remains a valid distinct outward result. 'JOURNAL_COMMIT_STATE_UNRESOLVED' remains reserved for ambiguous commit/readback.

## Read and admission guards

A completed zero-byte public read produces one 'Retry(STREAM_READ, STREAM_ZERO_PROGRESS, NONE)': stop, retain no bytes, make no further read, and write no outcome/range.

A completed positive read with 'R + count > A' produces 'Fatal(STREAM_READ, STREAM_READ_CROSSES_ACCEPTED_END)': discard the full buffer, do not prefix-compare/hash it, do not change the fixed 4056/4080 request, and write no outcome/range.

A normal ACTIVE-range denial produces 'Fatal(RANGE_ADMISSION, STREAM_ACTIVE_RANGE_DENIED)': do not open the source/stream, invoke Tink, or write.

## Existing evidence references

Only successfully strict-decoded rows may be referenced. Checkpoint uses 'checkpoint_identity', outcome uses 'outcome_id', and range uses 'range_intent_id' as both existing ID and identity SHA-256. The checkpoint SQLite key remains '(run_id,candidate_id,generation)'. References are immutable, deduplicated, then ordered by record-kind ordinal and unsigned lexicographic order of exactly 32 ID bytes. Malformed, unreadable, or inferred records yield no reference.

Per-class reference kinds are exact: split brain may reference STREAM_CHECKPOINT only; attempt conflict may reference STREAM_OUTCOME only; range collision may reference STREAM_OUTCOME and STREAM_RANGE only; active-range denial may reference strict-decoded ranges and their strict-decoded parent outcomes only. Other non-persistable classes carry no existing-record reference. Attempted unknown IDs are limited to commit-state-unresolved and are never existing references.

## Receipt and evidence

Exact readback creates an immutable internal receipt core/entitlement containing 'outcomeId', optional 'rangeIntentId', and 'replayed'. Its IDs derive the immutable event key. It is not yet the final public receipt.

Close the public stream, then the descriptor. Attempt the sanitized sink exactly once after both closes and before lease release. Only then construct the immutable final public 'PersistenceReceipt(outcomeId, optionalRangeIntentId, replayed, postReceiptCleanup, evidenceDelivery)'. Cleanup records the four closed states; if both close operations fail, public-stream failure is primary. Sink success is DELIVERED; sink failure is PENDING. Close/sink failures preserve the primary PersistedValid, persisted Rejected, or persisted Fatal result and the core IDs.

PENDING is caller-retryable only by reinvoking exact reconciliation. Exact replay re-emits the same event key and creates no row. This is one bounded best-effort attempt per invocation, with no at-least-once or autonomous delivery guarantee and no outbox, scheduler, provider, table, dependency, or background mechanism.

Non-persistable Retry, Rejected, and Fatal make one bounded best-effort sanitized attempt after opened resources close and before lease release. Failure changes neither classification nor control flow and starts no loop. Events exclude paths, exception text/implementation type, stack trace, plaintext, ciphertext, keys/keysets, database/WAL, returned/rejected plaintext digests, mismatch offsets, and expected/observed bytes. ADR-0005 sanitization remains in force.

## Authority

The v0.8 governance commit requires an independent CLEAN review before v0.8 controller implementation or evidence. Local mode remains usable without account, network, GMS, or cloud. This ADR grants no device/emulator/preflight/campaign, durability, PASS/READY, dependency, production, consumer, cross-process, retirement, push, PR, merge, or admission authority.
