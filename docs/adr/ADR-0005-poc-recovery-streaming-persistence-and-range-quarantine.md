# ADR-0005: PoC recovery streaming persistence and retained-range quarantine

- Status: Accepted for bounded Stage 0 REC-I3 governance and implementation under OD-15
- Date: 2026-09-06
- Decision owner: Project owner
- Applies to: `POC-RECOVERY-001`, candidate `REC-STREAM-TINK`
- Baseline: `3c63ab09874f4d089e4363985aa8b5c99900c122` / tree `718eae8d8d619d17c25ac9d025e0e24db3d52f9e`
- Confirmed design packet: SHA-256 `9f8e3a6e4d20faf0d744310b83ca46bd34d5c6798785e1197e6b61fb8ae81011`

## Context

DEC-045 admits independently authenticated, oracle-equal streaming plaintext beyond durable checkpoint C while keeping C unchanged. The combined REC-I3 baseline has the public-Tink streaming boundary but lacks a durable schema for the checkpoint, the sealed recovery decision, rejected observations, and same-file retained ranges. The owner confirmed the exact corrected v5 contract. This ADR records that contract before any persistence source change.

This is a bounded, deterministic, synthetic and non-metric Stage 0 decision. It grants no Recovery preflight, device/emulator execution, process-death or power-loss claim, hard-kill/fault/Phase A/measured campaign, PASS/READY, dependency or production admission, processing/consumer behavior, cross-process denial, range retirement, or merge.

## Decision

The journal remains at `poc-recovery/v1/recovery-journal-v1.db` and advances to SQLite `user_version=4` without destructive fallback or downgrade. Exact v1 bootstrap and v2 unit/publication DDL remain unchanged. Valid v3 whole-object quarantine rows migrate byte-for-byte into candidate-aware `recovery_quarantine_intent_v4`, preserving IDs, destinations, bindings, states, and value bytes. Version 4 adds only `recovery_stream_checkpoint_v4`, `recovery_stream_outcome_v4`, `recovery_stream_range_quarantine_v4`, and `recovery_stream_active_range_v4`; it adds no trigger, view, retirement API, processing-intent table, provider, dependency, scheduler, or production adapter.

A checkpoint proves its own artifact. Proven current-source C additionally requires a validated checkpoint plus `P<=S<=E` and exact SHA-256 checks for `[0,P)` and `[0,S)` from the one opened, non-growing descriptor. The controller witness binds run/candidate/checkpoint identity, oracle identity and A, pre-fault S/digest, and P. It opens `stream/stream.ct` once with `O_RDONLY|O_CLOEXEC|O_NOFOLLOW`, freezes E, verifies bounds and hashes `[0,E)`, and only then may create public Tink. `[S,E)` is untrusted lookahead and carries no temporal-provenance claim.

Only positive completed public reads enter a candidate end; the throwing-call buffer is discarded and only `-1` is authenticated EOF. VALID requires `0<=C<=R<=A`, exact equality to the fixed oracle, and `A-R<=8160`; there is no `R-C` cap. C never advances. No metadata, semantic commit, or processing intent is adopted.

For canonical positive `R=4056+k*4080`, `B(R)=(1+k)*4096`; `B(0)=0`. Authentication failure retains exact `[B(R),E)` when non-empty. `B(R)=E` produces a sealed outcome with null required-range fields and no range row. Authenticated EOF has no remainder. The authenticated prefix remains in the same file without copy, rename, truncation, overwrite, or deletion.

PRE_INTERSECTION is limited to source truncation, checkpoint prefix outside S, and prefix-identity mismatch. It asserts neither C nor R, records `UNPROVEN_OR_MISMATCH`, `CONTEXT_ONLY`, and `NOT_REACHED`, and retains conservative `[0,E)` when non-empty. POST_INTERSECTION is limited to oracle mismatch, recovered below checkpoint, unproved boundary, and tail-bound exceeded. It preserves `VERIFIED_SAME_DESCRIPTOR`, `PROVEN`, proven C, and the exact public terminal; admits no R; stores the complete rejected observation; and uses only the classification-specific range defined by the exact DDL checks below.

Every post-intersection record binds candidateR, completed-candidate and same-extent oracle-prefix digests, equality, compared extent, `A-candidateR`, typed boundary result/bytes, optional deterministic first-mismatch witness, and `rejectedObservationSha256`. Every rejected field contributes to the observation hash, outcome ID, exact readback, and collision comparison. Public evidence excludes plaintext digests, mismatch offsets, and byte values.

Outcome and any required non-empty range are inserted in one `beginTransactionNonExclusive()` transaction. Successful `endTransaction()` and exact readback precede a receipt. Exact complete state is idempotent hash-only replay; replay revalidates source, witness, oracle-side rejected fields, arithmetic, range, and IDs without rerunning Tink or independently recreating historical returned bytes. Same-ID/different-bytes, same-witness/different-ID, range collision, and checkpoint split brain return non-persistable Fatal with references only to readable pre-existing records. An unresolved ambiguous commit with no exact intended state returns Retry.

Every cooperating app open must use one gateway and hold the shared single-process run lease for the descriptor lifetime. It queries sealed outcomes and ACTIVE ranges before open and denies normal intersecting reads. Privileged replay may hash only and may not return bytes or invoke Tink. No cross-process guarantee is claimed and retirement is deferred.

Extent checks are unsigned and conversion-safe: `A<=115200000`, `S<=115654656`, `E<=115662848`, and, only after proving `E>=S`, `E-S<=8192`. The maximum pair `S=115654656,E=115662848` is accepted; one-over, overflow, or narrowing failures reject before public decryption. The 8192-byte synthetic exposure cap does not change the 8160-byte/0.255-second plaintext gate.

REC-STREAM-TINK K12 uses `K12-STREAM-V0.7`: q=2, P=8192, C=4056, A=8137, S=8192, append=1, E=8193, completed reads `[4056,4080]`, R=8136, authentication failure, `B(R)=8192`, and ACTIVE `[8192,8193)`. K12-PERSISTENCE expects one SEALED VALID outcome, one ACTIVE range, unchanged source, zero streaming intents, exact replay, and no new state on collision. K12-CONSUMER is deferred to a separately approved scope. TRU-03 keeps its microfile branch unchanged and replaces only its stream branch with the v0.7 witness/intersection/remainder contract. The effective matrix remains 46 unique IDs, 184 Phase A injections, 138 full-physical injections, and 120 base hard kills per candidate.

## Exact schema and migration contract

## Schema version and exact migration

Select `user_version=4` at unchanged path
`poc-recovery/v1/recovery-journal-v1.db`. Preserve the exact v1/v2 DDL and named
`recovery_run_candidate_v2` index. Version 4 has these four new/replacement tables and one new
explicit index. There are no v4 triggers or views.

### Whole-object quarantine v4

```sql
CREATE TABLE recovery_quarantine_intent_v4 (
  intent_id BLOB NOT NULL PRIMARY KEY CHECK(length(intent_id)=32),
  run_id TEXT NOT NULL,
  candidate_id TEXT NOT NULL
    CHECK(candidate_id IN ('REC-STREAM-TINK','REC-MICROFILE-TINK')),
  bootstrap_binding TEXT NOT NULL CHECK(bootstrap_binding IN ('ABSENT','PRESENT')),
  bootstrap_run_id TEXT,
  bootstrap_candidate_id TEXT,
  artifact_role TEXT NOT NULL,
  observed_state TEXT NOT NULL CHECK(observed_state IN
    ('TEMP_ONLY','TEMP_AND_FINAL','FINAL_ORPHAN','SQLITE_POINTS_TO_TEMP',
     'UNKNOWN_OR_NON_ALLOWLISTED_NAME')),
  source_relative_name TEXT NOT NULL,
  destination_relative_name TEXT NOT NULL,
  source_bytes INTEGER NOT NULL CHECK(source_bytes>=0),
  source_sha256 BLOB NOT NULL CHECK(length(source_sha256)=32),
  state TEXT NOT NULL CHECK(state IN ('PENDING','COMPLETED')),
  CHECK(
    (candidate_id='REC-MICROFILE-TINK' AND artifact_role IN
      ('KEY_CONFIRMATION','MICROFILE_KEY_ENVELOPE','MICROFILE_CIPHERTEXT',
       'MANIFEST_KEY_ENVELOPE','MANIFEST_CIPHERTEXT','UNKNOWN_REGULAR')) OR
    (candidate_id='REC-STREAM-TINK' AND artifact_role IN
      ('KEY_CONFIRMATION','STREAM_KEY_ENVELOPE','STREAM_CIPHERTEXT',
       'CHECKPOINT_KEY_ENVELOPE','CHECKPOINT_CIPHERTEXT','UNKNOWN_REGULAR'))
  ),
  CHECK(
    (bootstrap_binding='ABSENT' AND bootstrap_run_id IS NULL AND
      bootstrap_candidate_id IS NULL) OR
    (bootstrap_binding='PRESENT' AND bootstrap_run_id=run_id AND
      bootstrap_candidate_id=candidate_id)
  ),
  UNIQUE(run_id,candidate_id,source_relative_name,source_sha256),
  FOREIGN KEY(bootstrap_run_id,bootstrap_candidate_id)
    REFERENCES recovery_run_bootstrap_v1(run_id,candidate_id)
    ON UPDATE RESTRICT ON DELETE RESTRICT
)
```

The historical `RecoveryQuarantineIntent` preimage and every migrated v3 `intent_id`, destination,
binding, state and column byte value remain unchanged. `STREAM_CIPHERTEXT` whole-object quarantine
is allowed only before any checkpoint/outcome proves a prefix. Same-file remainders use the range
table below and never Q01-Q05.

### Streaming checkpoint v4

```sql
CREATE TABLE recovery_stream_checkpoint_v4 (
  run_id TEXT NOT NULL,
  candidate_id TEXT NOT NULL CHECK(candidate_id='REC-STREAM-TINK'),
  publication_kind TEXT NOT NULL CHECK(publication_kind='CHECKPOINT'),
  generation INTEGER NOT NULL CHECK(generation BETWEEN 1 AND 9223372036854775807),
  durable_non_final_segment_count INTEGER NOT NULL
    CHECK(durable_non_final_segment_count BETWEEN 0 AND 28236),
  stream_ciphertext_prefix_bytes INTEGER NOT NULL
    CHECK(stream_ciphertext_prefix_bytes=durable_non_final_segment_count*4096),
  stream_ciphertext_prefix_sha256 BLOB NOT NULL
    CHECK(length(stream_ciphertext_prefix_sha256)=32),
  committed_end INTEGER NOT NULL CHECK(
    (durable_non_final_segment_count<2 AND committed_end=0) OR
    (durable_non_final_segment_count>=2 AND
      committed_end=4056+(durable_non_final_segment_count-2)*4080)
  ),
  checkpoint_relative_name TEXT NOT NULL
    CHECK(checkpoint_relative_name=printf('checkpoints/g-%020d.ct',generation)),
  checkpoint_bytes INTEGER NOT NULL CHECK(checkpoint_bytes>0),
  checkpoint_sha256 BLOB NOT NULL CHECK(length(checkpoint_sha256)=32),
  checkpoint_key_envelope_relative_name TEXT NOT NULL CHECK(
    checkpoint_key_envelope_relative_name=
      printf('key-envelopes/checkpoint-g-%020d.ks',generation)),
  checkpoint_key_envelope_bytes INTEGER NOT NULL CHECK(checkpoint_key_envelope_bytes>0),
  checkpoint_key_envelope_sha256 BLOB NOT NULL
    CHECK(length(checkpoint_key_envelope_sha256)=32),
  stream_ciphertext_relative_name TEXT NOT NULL
    CHECK(stream_ciphertext_relative_name='stream/stream.ct'),
  stream_key_envelope_relative_name TEXT NOT NULL
    CHECK(stream_key_envelope_relative_name='key-envelopes/stream.ks'),
  stream_key_envelope_bytes INTEGER NOT NULL CHECK(stream_key_envelope_bytes>0),
  stream_key_envelope_sha256 BLOB NOT NULL
    CHECK(length(stream_key_envelope_sha256)=32),
  previous_checkpoint_sha256 BLOB NOT NULL
    CHECK(length(previous_checkpoint_sha256)=32),
  checkpoint_identity BLOB NOT NULL UNIQUE CHECK(length(checkpoint_identity)=32),
  state TEXT NOT NULL CHECK(state='VALID'),
  PRIMARY KEY(run_id,candidate_id,generation),
  UNIQUE(run_id,candidate_id,generation,checkpoint_identity,committed_end,
         stream_ciphertext_prefix_bytes),
  FOREIGN KEY(run_id,candidate_id)
    REFERENCES recovery_run_bootstrap_v1(run_id,candidate_id)
    ON UPDATE RESTRICT ON DELETE RESTRICT
)
```

The controller enforces genesis/previous-generation digest chaining, strict checkpoint decryption
and parsing, and exact row/readback equality before source open. That proves the checkpoint
artifact. A `VALID` source intersection additionally requires `P<=S<=E`,
`sha256(stream[0,P))` and `sha256(stream[0,S))` on the same descriptor. No checkpoint row alone
creates proven `C`.

### Sealed streaming outcome v4

```sql
CREATE TABLE recovery_stream_outcome_v4 (
  outcome_id BLOB NOT NULL PRIMARY KEY CHECK(length(outcome_id)=32),
  run_id TEXT NOT NULL,
  candidate_id TEXT NOT NULL CHECK(candidate_id='REC-STREAM-TINK'),
  checkpoint_generation INTEGER NOT NULL CHECK(checkpoint_generation>0),
  checkpoint_identity BLOB NOT NULL CHECK(length(checkpoint_identity)=32),
  checkpoint_context_end INTEGER NOT NULL
    CHECK(checkpoint_context_end BETWEEN 0 AND 115200000),
  checkpoint_prefix_bytes INTEGER NOT NULL
    CHECK(checkpoint_prefix_bytes BETWEEN 0 AND 115654656),
  checkpoint_artifact_state TEXT NOT NULL
    CHECK(checkpoint_artifact_state='CRYPTOGRAPHICALLY_VALIDATED'),
  source_witness_id BLOB NOT NULL CHECK(length(source_witness_id)=32),
  witness_capability_state TEXT NOT NULL
    CHECK(witness_capability_state='INTERNALLY_VERIFIED'),
  controller_snapshot_sha256 BLOB NOT NULL CHECK(length(controller_snapshot_sha256)=32),
  oracle_identity_sha256 BLOB NOT NULL CHECK(length(oracle_identity_sha256)=32),
  oracle_plaintext_sha256 BLOB NOT NULL CHECK(length(oracle_plaintext_sha256)=32),
  accepted_end INTEGER NOT NULL CHECK(accepted_end BETWEEN 0 AND 115200000),
  source_relative_name TEXT NOT NULL CHECK(source_relative_name='stream/stream.ct'),
  pre_fault_source_bytes INTEGER NOT NULL
    CHECK(pre_fault_source_bytes BETWEEN 0 AND 115654656),
  pre_fault_source_sha256 BLOB NOT NULL CHECK(length(pre_fault_source_sha256)=32),
  observed_source_bytes INTEGER NOT NULL
    CHECK(observed_source_bytes BETWEEN 0 AND 115662848),
  observed_source_sha256 BLOB NOT NULL CHECK(length(observed_source_sha256)=32),
  pre_fault_source_match_state TEXT NOT NULL CHECK(pre_fault_source_match_state IN
    ('VERIFIED_SAME_DESCRIPTOR','UNPROVEN_OR_MISMATCH')),
  checkpoint_intersection_state TEXT NOT NULL CHECK(checkpoint_intersection_state IN
    ('PROVEN','CONTEXT_ONLY')),
  decision TEXT NOT NULL CHECK(decision IN ('VALID','REJECTED','FATAL')),
  diagnostic_branch TEXT NOT NULL CHECK(diagnostic_branch IN
    ('NONE','PRE_INTERSECTION','POST_INTERSECTION')),
  terminal_outcome TEXT NOT NULL CHECK(terminal_outcome IN
    ('NOT_REACHED','COMPLETED_READ_REJECTED','AUTHENTICATED_EOF',
     'AUTHENTICATION_FAILURE')),
  recovered_end INTEGER,
  recovered_beyond_checkpoint_bytes INTEGER,
  tail_loss_bytes INTEGER,
  returned_plaintext_sha256 BLOB,
  remainder_boundary_bytes INTEGER,
  remainder_certainty TEXT CHECK(remainder_certainty IS NULL OR remainder_certainty IN
    ('EXACT_FORMAT_BOUNDARY','CONSERVATIVE_PROVEN_CHECKPOINT_SUPERSET',
     'CONSERVATIVE_WHOLE_SOURCE')),
  rejected_candidate_end INTEGER,
  rejected_completed_plaintext_sha256 BLOB,
  rejected_oracle_prefix_sha256 BLOB,
  rejected_oracle_prefix_equal INTEGER,
  rejected_compared_end INTEGER,
  rejected_first_mismatch_offset INTEGER,
  rejected_equal_prefix_sha256 BLOB,
  rejected_expected_oracle_byte INTEGER,
  rejected_observed_plaintext_byte INTEGER,
  rejected_observed_tail_loss_bytes INTEGER,
  rejected_boundary_result TEXT CHECK(rejected_boundary_result IS NULL OR
    rejected_boundary_result IN
      ('NOT_EVALUATED_ORACLE_MISMATCH','NOT_APPLICABLE_AUTHENTICATED_EOF',
       'EXACT_FORMAT_BOUNDARY','NON_CANONICAL_CANDIDATE_END',
       'BOUNDARY_EXCEEDS_OBSERVED_SOURCE')),
  rejected_boundary_bytes INTEGER,
  rejected_observation_sha256 BLOB,
  required_range_start INTEGER,
  required_range_certainty TEXT CHECK(required_range_certainty IS NULL OR
    required_range_certainty IN
      ('EXACT_FORMAT_BOUNDARY','CONSERVATIVE_PROVEN_CHECKPOINT_SUPERSET',
       'CONSERVATIVE_WHOLE_SOURCE')),
  diagnostic_stage TEXT NOT NULL CHECK(diagnostic_stage IN
    ('NONE','STREAM_CHECKPOINT','STREAM_SOURCE_EXTENT','STREAM_PAYLOAD_DECRYPT')),
  diagnostic_classification TEXT NOT NULL CHECK(diagnostic_classification IN
    ('NONE','STREAM_CHECKPOINT_PREFIX_OUTSIDE_WITNESS','STREAM_SOURCE_TRUNCATED',
     'STREAM_SOURCE_PREFIX_IDENTITY_MISMATCH','STREAM_RETURNED_BYTE_ORACLE_MISMATCH',
     'STREAM_RECOVERED_BELOW_CHECKPOINT','STREAM_TAIL_BOUND_EXCEEDED',
     'STREAM_REMAINDER_BOUNDARY_UNPROVEN')),
  metadata_adopted INTEGER NOT NULL CHECK(metadata_adopted=0),
  semantic_commit_adopted INTEGER NOT NULL CHECK(semantic_commit_adopted=0),
  processing_intent_adopted INTEGER NOT NULL CHECK(processing_intent_adopted=0),
  state TEXT NOT NULL CHECK(state='SEALED'),
  CHECK(checkpoint_context_end<=accepted_end),
  CHECK(observed_source_bytes<pre_fault_source_bytes OR
        observed_source_bytes-pre_fault_source_bytes<=8192),
  CHECK((required_range_start IS NULL AND required_range_certainty IS NULL) OR
        (required_range_start IS NOT NULL AND required_range_certainty IS NOT NULL AND
         required_range_start>=0 AND required_range_start<observed_source_bytes)),
  CHECK(
    (decision='VALID' AND diagnostic_branch='NONE' AND diagnostic_stage='NONE' AND
      diagnostic_classification='NONE' AND recovered_end IS NOT NULL AND
      recovered_beyond_checkpoint_bytes IS NOT NULL AND tail_loss_bytes IS NOT NULL AND
      returned_plaintext_sha256 IS NOT NULL AND length(returned_plaintext_sha256)=32 AND
      pre_fault_source_match_state='VERIFIED_SAME_DESCRIPTOR' AND
      checkpoint_intersection_state='PROVEN' AND
      checkpoint_prefix_bytes<=pre_fault_source_bytes AND
      pre_fault_source_bytes<=observed_source_bytes AND
      checkpoint_context_end<=recovered_end AND recovered_end<=accepted_end AND
      recovered_beyond_checkpoint_bytes=recovered_end-checkpoint_context_end AND
      tail_loss_bytes=accepted_end-recovered_end AND tail_loss_bytes<=8160 AND
      rejected_candidate_end IS NULL AND rejected_completed_plaintext_sha256 IS NULL AND
      rejected_oracle_prefix_sha256 IS NULL AND rejected_oracle_prefix_equal IS NULL AND
      rejected_compared_end IS NULL AND rejected_first_mismatch_offset IS NULL AND
      rejected_equal_prefix_sha256 IS NULL AND rejected_expected_oracle_byte IS NULL AND
      rejected_observed_plaintext_byte IS NULL AND
      rejected_observed_tail_loss_bytes IS NULL AND rejected_boundary_result IS NULL AND
      rejected_boundary_bytes IS NULL AND rejected_observation_sha256 IS NULL AND
      ((terminal_outcome='AUTHENTICATED_EOF' AND remainder_boundary_bytes IS NULL AND
         remainder_certainty IS NULL AND required_range_start IS NULL AND
         required_range_certainty IS NULL) OR
       (terminal_outcome='AUTHENTICATION_FAILURE' AND
         remainder_boundary_bytes IS NOT NULL AND
         remainder_certainty='EXACT_FORMAT_BOUNDARY' AND
         ((recovered_end=0 AND remainder_boundary_bytes=0) OR
          (recovered_end>=4056 AND (recovered_end-4056)%4080=0 AND
           remainder_boundary_bytes=(1+(recovered_end-4056)/4080)*4096)) AND
         remainder_boundary_bytes<=observed_source_bytes AND
         ((remainder_boundary_bytes=observed_source_bytes AND
            required_range_start IS NULL AND required_range_certainty IS NULL) OR
          (remainder_boundary_bytes<observed_source_bytes AND
            required_range_start IS NOT NULL AND
            required_range_start=remainder_boundary_bytes AND
            required_range_certainty IS NOT NULL AND
            required_range_certainty='EXACT_FORMAT_BOUNDARY'))))) OR
    (decision='FATAL' AND diagnostic_branch='PRE_INTERSECTION' AND
      terminal_outcome='NOT_REACHED' AND recovered_end IS NULL AND
      recovered_beyond_checkpoint_bytes IS NULL AND tail_loss_bytes IS NULL AND
      returned_plaintext_sha256 IS NULL AND remainder_boundary_bytes IS NULL AND
      remainder_certainty IS NULL AND
      rejected_candidate_end IS NULL AND rejected_completed_plaintext_sha256 IS NULL AND
      rejected_oracle_prefix_sha256 IS NULL AND rejected_oracle_prefix_equal IS NULL AND
      rejected_compared_end IS NULL AND rejected_first_mismatch_offset IS NULL AND
      rejected_equal_prefix_sha256 IS NULL AND rejected_expected_oracle_byte IS NULL AND
      rejected_observed_plaintext_byte IS NULL AND
      rejected_observed_tail_loss_bytes IS NULL AND rejected_boundary_result IS NULL AND
      rejected_boundary_bytes IS NULL AND rejected_observation_sha256 IS NULL AND
      ((observed_source_bytes=0 AND required_range_start IS NULL AND
         required_range_certainty IS NULL) OR
       (observed_source_bytes>0 AND required_range_start IS NOT NULL AND
         required_range_start=0 AND required_range_certainty IS NOT NULL AND
         required_range_certainty='CONSERVATIVE_WHOLE_SOURCE')) AND
      pre_fault_source_match_state='UNPROVEN_OR_MISMATCH' AND
      checkpoint_intersection_state='CONTEXT_ONLY' AND
      ((diagnostic_classification='STREAM_CHECKPOINT_PREFIX_OUTSIDE_WITNESS' AND
         diagnostic_stage='STREAM_CHECKPOINT' AND
         pre_fault_source_bytes<=observed_source_bytes AND
         checkpoint_prefix_bytes>pre_fault_source_bytes) OR
       (diagnostic_classification='STREAM_SOURCE_TRUNCATED' AND
         diagnostic_stage='STREAM_SOURCE_EXTENT' AND
         observed_source_bytes<pre_fault_source_bytes) OR
       (diagnostic_classification='STREAM_SOURCE_PREFIX_IDENTITY_MISMATCH' AND
         diagnostic_stage='STREAM_SOURCE_EXTENT' AND
         checkpoint_prefix_bytes<=pre_fault_source_bytes AND
         pre_fault_source_bytes<=observed_source_bytes))) OR
    (diagnostic_branch='POST_INTERSECTION' AND recovered_end IS NULL AND
      recovered_beyond_checkpoint_bytes IS NULL AND tail_loss_bytes IS NULL AND
      returned_plaintext_sha256 IS NULL AND remainder_boundary_bytes IS NULL AND
      remainder_certainty IS NULL AND rejected_candidate_end IS NOT NULL AND
      rejected_candidate_end BETWEEN 0 AND accepted_end AND
      rejected_completed_plaintext_sha256 IS NOT NULL AND
      length(rejected_completed_plaintext_sha256)=32 AND
      rejected_oracle_prefix_sha256 IS NOT NULL AND
      length(rejected_oracle_prefix_sha256)=32 AND
      rejected_oracle_prefix_equal IS NOT NULL AND rejected_oracle_prefix_equal IN (0,1) AND
      rejected_compared_end IS NOT NULL AND rejected_compared_end=rejected_candidate_end AND
      rejected_observed_tail_loss_bytes IS NOT NULL AND
      rejected_observed_tail_loss_bytes=accepted_end-rejected_candidate_end AND
      rejected_boundary_result IS NOT NULL AND rejected_observation_sha256 IS NOT NULL AND
      length(rejected_observation_sha256)=32 AND
      pre_fault_source_match_state='VERIFIED_SAME_DESCRIPTOR' AND
      checkpoint_intersection_state='PROVEN' AND
      checkpoint_prefix_bytes<=pre_fault_source_bytes AND
      pre_fault_source_bytes<=observed_source_bytes AND
      diagnostic_stage='STREAM_PAYLOAD_DECRYPT' AND
      ((decision='FATAL' AND
         diagnostic_classification='STREAM_RETURNED_BYTE_ORACLE_MISMATCH' AND
         terminal_outcome='COMPLETED_READ_REJECTED' AND rejected_candidate_end>0 AND
         rejected_oracle_prefix_equal=0 AND
         rejected_completed_plaintext_sha256<>rejected_oracle_prefix_sha256 AND
         rejected_first_mismatch_offset IS NOT NULL AND
         rejected_first_mismatch_offset BETWEEN 0 AND rejected_candidate_end-1 AND
         rejected_equal_prefix_sha256 IS NOT NULL AND
         length(rejected_equal_prefix_sha256)=32 AND
         rejected_expected_oracle_byte IS NOT NULL AND
         rejected_expected_oracle_byte BETWEEN 0 AND 255 AND
         rejected_observed_plaintext_byte IS NOT NULL AND
         rejected_observed_plaintext_byte BETWEEN 0 AND 255 AND
         rejected_expected_oracle_byte<>rejected_observed_plaintext_byte AND
         rejected_boundary_result='NOT_EVALUATED_ORACLE_MISMATCH' AND
         rejected_boundary_bytes IS NULL AND observed_source_bytes>0 AND
         required_range_start IS NOT NULL AND required_range_start=0 AND
         required_range_certainty IS NOT NULL AND
         required_range_certainty='CONSERVATIVE_WHOLE_SOURCE') OR
       (decision='FATAL' AND
         diagnostic_classification='STREAM_RECOVERED_BELOW_CHECKPOINT' AND
         terminal_outcome IN ('AUTHENTICATED_EOF','AUTHENTICATION_FAILURE') AND
         rejected_candidate_end<checkpoint_context_end AND
         rejected_oracle_prefix_equal=1 AND
         rejected_completed_plaintext_sha256=rejected_oracle_prefix_sha256 AND
         rejected_first_mismatch_offset IS NULL AND
         rejected_equal_prefix_sha256 IS NULL AND rejected_expected_oracle_byte IS NULL AND
         rejected_observed_plaintext_byte IS NULL AND
         ((terminal_outcome='AUTHENTICATED_EOF' AND
            rejected_boundary_result='NOT_APPLICABLE_AUTHENTICATED_EOF' AND
            rejected_boundary_bytes IS NULL AND required_range_start IS NULL AND
            required_range_certainty IS NULL) OR
          (terminal_outcome='AUTHENTICATION_FAILURE' AND observed_source_bytes>0 AND
            required_range_start IS NOT NULL AND required_range_start=0 AND
            required_range_certainty IS NOT NULL AND
            required_range_certainty='CONSERVATIVE_WHOLE_SOURCE' AND
            ((rejected_candidate_end=0 AND
               rejected_boundary_result='EXACT_FORMAT_BOUNDARY' AND
               rejected_boundary_bytes IS NOT NULL AND rejected_boundary_bytes=0) OR
             (rejected_candidate_end>=4056 AND
               (rejected_candidate_end-4056)%4080=0 AND
               rejected_boundary_bytes IS NOT NULL AND
               rejected_boundary_bytes=(1+(rejected_candidate_end-4056)/4080)*4096 AND
               ((rejected_boundary_bytes<=observed_source_bytes AND
                  rejected_boundary_result='EXACT_FORMAT_BOUNDARY') OR
                (rejected_boundary_bytes>observed_source_bytes AND
                  rejected_boundary_result='BOUNDARY_EXCEEDS_OBSERVED_SOURCE'))) OR
             (rejected_candidate_end<>0 AND
               (rejected_candidate_end<4056 OR
                (rejected_candidate_end-4056)%4080<>0) AND
               rejected_boundary_result='NON_CANONICAL_CANDIDATE_END' AND
               rejected_boundary_bytes IS NULL))))) OR
       (decision='FATAL' AND
         diagnostic_classification='STREAM_REMAINDER_BOUNDARY_UNPROVEN' AND
         terminal_outcome='AUTHENTICATION_FAILURE' AND
         rejected_candidate_end>=checkpoint_context_end AND
         rejected_oracle_prefix_equal=1 AND
         rejected_completed_plaintext_sha256=rejected_oracle_prefix_sha256 AND
         rejected_first_mismatch_offset IS NULL AND
         rejected_equal_prefix_sha256 IS NULL AND rejected_expected_oracle_byte IS NULL AND
         rejected_observed_plaintext_byte IS NULL AND observed_source_bytes>0 AND
         ((rejected_candidate_end<>0 AND
            (rejected_candidate_end<4056 OR
             (rejected_candidate_end-4056)%4080<>0) AND
            rejected_boundary_result='NON_CANONICAL_CANDIDATE_END' AND
            rejected_boundary_bytes IS NULL) OR
          (rejected_candidate_end>=4056 AND
            (rejected_candidate_end-4056)%4080=0 AND
            rejected_boundary_bytes IS NOT NULL AND
            rejected_boundary_bytes=(1+(rejected_candidate_end-4056)/4080)*4096 AND
            rejected_boundary_bytes>observed_source_bytes AND
            rejected_boundary_result='BOUNDARY_EXCEEDS_OBSERVED_SOURCE')) AND
         (((CASE WHEN checkpoint_context_end=0 THEN 0 ELSE
              (1+(checkpoint_context_end-4056)/4080)*4096 END)<observed_source_bytes AND
            required_range_start IS NOT NULL AND
            required_range_start=(CASE WHEN checkpoint_context_end=0 THEN 0 ELSE
              (1+(checkpoint_context_end-4056)/4080)*4096 END) AND
            required_range_certainty='CONSERVATIVE_PROVEN_CHECKPOINT_SUPERSET') OR
          ((CASE WHEN checkpoint_context_end=0 THEN 0 ELSE
             (1+(checkpoint_context_end-4056)/4080)*4096 END)>=observed_source_bytes AND
            required_range_start IS NOT NULL AND required_range_start=0 AND
            required_range_certainty='CONSERVATIVE_WHOLE_SOURCE'))) OR
       (decision='REJECTED' AND
         diagnostic_classification='STREAM_TAIL_BOUND_EXCEEDED' AND
         terminal_outcome IN ('AUTHENTICATED_EOF','AUTHENTICATION_FAILURE') AND
         rejected_candidate_end>=checkpoint_context_end AND
         rejected_oracle_prefix_equal=1 AND
         rejected_completed_plaintext_sha256=rejected_oracle_prefix_sha256 AND
         rejected_observed_tail_loss_bytes>8160 AND
         rejected_first_mismatch_offset IS NULL AND
         rejected_equal_prefix_sha256 IS NULL AND rejected_expected_oracle_byte IS NULL AND
         rejected_observed_plaintext_byte IS NULL AND
         ((terminal_outcome='AUTHENTICATED_EOF' AND
            rejected_boundary_result='NOT_APPLICABLE_AUTHENTICATED_EOF' AND
            rejected_boundary_bytes IS NULL AND required_range_start IS NULL AND
            required_range_certainty IS NULL) OR
           (terminal_outcome='AUTHENTICATION_FAILURE' AND
             rejected_boundary_bytes IS NOT NULL AND
             ((rejected_candidate_end=0 AND rejected_boundary_bytes=0) OR
              (rejected_candidate_end>=4056 AND
               (rejected_candidate_end-4056)%4080=0 AND
               rejected_boundary_bytes=(1+(rejected_candidate_end-4056)/4080)*4096)) AND
             rejected_boundary_result='EXACT_FORMAT_BOUNDARY' AND
             rejected_boundary_bytes<=observed_source_bytes AND
             ((rejected_boundary_bytes=observed_source_bytes AND
                required_range_start IS NULL AND required_range_certainty IS NULL) OR
              (rejected_boundary_bytes<observed_source_bytes AND
               required_range_start IS NOT NULL AND
               required_range_start=rejected_boundary_bytes AND
               required_range_certainty='EXACT_FORMAT_BOUNDARY')))))))
  ),
  UNIQUE(run_id,candidate_id,checkpoint_identity,source_witness_id),
  UNIQUE(outcome_id,run_id,candidate_id,observed_source_bytes,
         observed_source_sha256,decision,diagnostic_branch,terminal_outcome,
         diagnostic_classification,required_range_start,required_range_certainty),
  FOREIGN KEY(run_id,candidate_id,checkpoint_generation,checkpoint_identity,
              checkpoint_context_end,checkpoint_prefix_bytes)
    REFERENCES recovery_stream_checkpoint_v4
      (run_id,candidate_id,generation,checkpoint_identity,committed_end,
       stream_ciphertext_prefix_bytes)
    ON UPDATE RESTRICT ON DELETE RESTRICT
)
```

Only a `VALID` row is a persisted recovery result. In that row `checkpoint_context_end` has proven
`C` semantics because the CHECK requires both source states and `P<=S<=E`. `REJECTED` and `FATAL`
rows are immutable audit decisions with no admitted `R`. A `PRE_INTERSECTION` row has a contextual
endpoint and unproved source/intersection states. A `POST_INTERSECTION` row preserves exact
same-descriptor source verification and proven `C`, plus its public terminal and separate rejected
observation. The DDL permits only the matrix classifications. The range foreign key binds
`range_start` and `boundary_certainty` to the exact required values in its parent outcome. Failures
before the exact checkpoint artifact or bounded observed identity, and all
collision/split-brain/journal cases, have no newly persisted row.

### Retained range quarantine v4

```sql
CREATE TABLE recovery_stream_range_quarantine_v4 (
  range_intent_id BLOB NOT NULL PRIMARY KEY CHECK(length(range_intent_id)=32),
  outcome_id BLOB NOT NULL UNIQUE CHECK(length(outcome_id)=32),
  run_id TEXT NOT NULL,
  candidate_id TEXT NOT NULL CHECK(candidate_id='REC-STREAM-TINK'),
  outcome_decision TEXT NOT NULL CHECK(outcome_decision IN ('VALID','REJECTED','FATAL')),
  outcome_branch TEXT NOT NULL CHECK(outcome_branch IN
    ('NONE','PRE_INTERSECTION','POST_INTERSECTION')),
  outcome_terminal TEXT NOT NULL CHECK(outcome_terminal IN
    ('NOT_REACHED','COMPLETED_READ_REJECTED','AUTHENTICATED_EOF',
     'AUTHENTICATION_FAILURE')),
  outcome_classification TEXT NOT NULL CHECK(outcome_classification IN
    ('NONE','STREAM_CHECKPOINT_PREFIX_OUTSIDE_WITNESS','STREAM_SOURCE_TRUNCATED',
     'STREAM_SOURCE_PREFIX_IDENTITY_MISMATCH','STREAM_RETURNED_BYTE_ORACLE_MISMATCH',
     'STREAM_RECOVERED_BELOW_CHECKPOINT','STREAM_TAIL_BOUND_EXCEEDED',
     'STREAM_REMAINDER_BOUNDARY_UNPROVEN')),
  source_relative_name TEXT NOT NULL CHECK(source_relative_name='stream/stream.ct'),
  source_bytes INTEGER NOT NULL CHECK(source_bytes BETWEEN 1 AND 115662848),
  source_sha256 BLOB NOT NULL CHECK(length(source_sha256)=32),
  range_start INTEGER NOT NULL CHECK(range_start>=0),
  range_end INTEGER NOT NULL CHECK(range_end=source_bytes AND range_end>range_start),
  range_sha256 BLOB NOT NULL CHECK(length(range_sha256)=32),
  boundary_certainty TEXT NOT NULL CHECK(boundary_certainty IN
    ('EXACT_FORMAT_BOUNDARY','CONSERVATIVE_PROVEN_CHECKPOINT_SUPERSET',
     'CONSERVATIVE_WHOLE_SOURCE')),
  disposition TEXT NOT NULL
    CHECK(disposition='RETAINED_IN_PLACE_DENY_APP_READS'),
  state TEXT NOT NULL CHECK(state='ACTIVE'),
  CHECK(outcome_terminal<>'AUTHENTICATED_EOF'),
  CHECK(boundary_certainty<>'CONSERVATIVE_WHOLE_SOURCE' OR range_start=0),
  CHECK(
    (outcome_decision='VALID' AND outcome_branch='NONE' AND
      outcome_terminal='AUTHENTICATION_FAILURE' AND outcome_classification='NONE' AND
      boundary_certainty='EXACT_FORMAT_BOUNDARY') OR
    (outcome_decision='FATAL' AND outcome_branch='PRE_INTERSECTION' AND
      outcome_terminal='NOT_REACHED' AND outcome_classification IN
        ('STREAM_CHECKPOINT_PREFIX_OUTSIDE_WITNESS','STREAM_SOURCE_TRUNCATED',
         'STREAM_SOURCE_PREFIX_IDENTITY_MISMATCH') AND
      boundary_certainty='CONSERVATIVE_WHOLE_SOURCE') OR
    (outcome_decision='FATAL' AND outcome_branch='POST_INTERSECTION' AND
      ((outcome_terminal='COMPLETED_READ_REJECTED' AND
         outcome_classification='STREAM_RETURNED_BYTE_ORACLE_MISMATCH' AND
         boundary_certainty='CONSERVATIVE_WHOLE_SOURCE') OR
       (outcome_terminal='AUTHENTICATION_FAILURE' AND
         outcome_classification='STREAM_RECOVERED_BELOW_CHECKPOINT' AND
         boundary_certainty='CONSERVATIVE_WHOLE_SOURCE') OR
       (outcome_terminal='AUTHENTICATION_FAILURE' AND
         outcome_classification='STREAM_REMAINDER_BOUNDARY_UNPROVEN' AND
         boundary_certainty IN
           ('CONSERVATIVE_PROVEN_CHECKPOINT_SUPERSET','CONSERVATIVE_WHOLE_SOURCE')))) OR
    (outcome_decision='REJECTED' AND outcome_branch='POST_INTERSECTION' AND
      outcome_terminal='AUTHENTICATION_FAILURE' AND
      outcome_classification='STREAM_TAIL_BOUND_EXCEEDED' AND
      boundary_certainty='EXACT_FORMAT_BOUNDARY')
  ),
  UNIQUE(run_id,candidate_id,source_relative_name,source_bytes,source_sha256,
         range_start,range_end,range_sha256),
  FOREIGN KEY(outcome_id,run_id,candidate_id,source_bytes,source_sha256,
              outcome_decision,outcome_branch,outcome_terminal,outcome_classification,
              range_start,boundary_certainty)
    REFERENCES recovery_stream_outcome_v4
      (outcome_id,run_id,candidate_id,observed_source_bytes,observed_source_sha256,
       decision,diagnostic_branch,terminal_outcome,diagnostic_classification,
       required_range_start,required_range_certainty)
    ON UPDATE RESTRICT ON DELETE RESTRICT
)
```

```sql
CREATE INDEX recovery_stream_active_range_v4
ON recovery_stream_range_quarantine_v4
  (run_id,candidate_id,source_relative_name,state,range_start,range_end)
```

An empty `[B,E)` creates no range row. In particular, a tail-bound authentication-failure outcome
with `B(candidateR)=E` is sealed `REJECTED` with null required-range fields. Its child range is
prohibited: any child has a non-null start/certainty and cannot satisfy the composite FK to those
null parent fields; independently, this table requires `source_bytes>=1` and
`range_end>range_start`. Version 4 has no UPDATE/DELETE/retirement API for this table. `ACTIVE`
is permanent in v4.

### Exact SQLite object inventory

`requireExactV4` must compare normalized SQL by `(type,name)` and compare the actual and expected
`(type,name,tbl_name)` triples as order-independent sets of equal cardinality. The following table
and index lists define that set; their displayed order is editorial and is not SQLite output order.
Tables are:

```text
recovery_run_bootstrap_v1
recovery_microfile_unit_v2
recovery_manifest_publication_v2
recovery_quarantine_intent_v4
recovery_stream_checkpoint_v4
recovery_stream_outcome_v4
recovery_stream_range_quarantine_v4
```

Indexes are the existing `recovery_run_candidate_v2`, the explicit
`recovery_stream_active_range_v4`, and only these autoindexes:

```text
sqlite_autoindex_recovery_run_bootstrap_v1_1
sqlite_autoindex_recovery_microfile_unit_v2_1
sqlite_autoindex_recovery_microfile_unit_v2_2
sqlite_autoindex_recovery_manifest_publication_v2_1
sqlite_autoindex_recovery_quarantine_intent_v4_1
sqlite_autoindex_recovery_quarantine_intent_v4_2
sqlite_autoindex_recovery_stream_checkpoint_v4_1
sqlite_autoindex_recovery_stream_checkpoint_v4_2
sqlite_autoindex_recovery_stream_checkpoint_v4_3
sqlite_autoindex_recovery_stream_outcome_v4_1
sqlite_autoindex_recovery_stream_outcome_v4_2
sqlite_autoindex_recovery_stream_outcome_v4_3
sqlite_autoindex_recovery_stream_range_quarantine_v4_1
sqlite_autoindex_recovery_stream_range_quarantine_v4_2
sqlite_autoindex_recovery_stream_range_quarantine_v4_3
```

No `recovery_quarantine_intent_v3`, other `recovery_%` object, trigger or view is allowed. The
implementation must materialize the query result into a set, reject duplicate/unexpected/missing
triples, and never compare iteration order. Tests must derive and pin `PRAGMA index_list`,
`foreign_key_list`, `table_info` and normalized `sqlite_master.sql` produced by the production
statements; a mismatch rejects open.

### External executable schema check for this packet

The five SQL blocks above were executed verbatim with `foreign_keys=ON` in bundled host Python
SQLite 3.53.1 against a minimal valid bootstrap parent. All four tables and the explicit index
compiled. Five preserved representative rows also passed with empty `foreign_key_check`: valid
authentication failure with exact range, valid EOF without range, and all three pre-intersection
classes with whole-source ranges. Seven representative post-intersection rows passed: oracle mismatch; below-C
authentication failure; below-C EOF; non-canonical candidate boundary; canonical boundary beyond
E; 8,161-byte tail authentication failure; and 8,161-byte tail EOF. Every required non-empty
range inserted through the composite parent foreign key and `foreign_key_check` returned empty.

The v5 equality tuple also passed: `q=0`, `P=C=S=E=candidateR=0`, `A=8161`, terminal
`AUTHENTICATION_FAILURE`, equal empty candidate/oracle digests, exact boundary `0`,
`STREAM_TAIL_BOUND_EXCEEDED`, and null required-range fields. It inserted one sealed `REJECTED`
outcome, no range row, and had no `foreign_key_check` violation. The direct mutation from a valid
non-empty tail tuple changing the source identity consistently from `E=1` to its exact boundary
`E=0` made the null-range form pass. A fabricated child range for the equality parent, a non-null required start on
that parent, and a null range on the strictly non-empty parent were rejected.

Nine preserved negative mutations and those three equality-specific mutations were rejected:
below-C relabeled as tail; non-canonical boundary relabeled as tail; oracle mismatch relabeled as
tail; mismatch offset equal to candidate end; tail loss changed to exactly 8,160; wrong exact
`B(candidateR)`; a false exceeds-E reason on a non-canonical candidate; candidate changed to C
under the below-C class; and a range start differing from its parent's required start. This is an
external DDL consistency check only. It is not a repository test, Android SQLite result, Tink read,
transaction/fsync/power-loss proof or campaign evidence.

### Upgrade transaction

```text
1 -> 4: requireExactV1; migrateV1ToV2; requireExactV2;
        migrateV2ToV3; requireExactV3; migrateV3ToV4; requireExactV4
2 -> 4: requireExactV2; migrateV2ToV3; requireExactV3;
        migrateV3ToV4; requireExactV4
3 -> 4: requireExactV3; migrateV3ToV4; requireExactV4
all other upgrades and every downgrade: reject; no destructive fallback
```

`migrateV3ToV4` executes inside the `SQLiteOpenHelper` upgrade transaction with no nested app
transaction. Before its first `CREATE`, `DROP`, `INSERT`, `UPDATE`, `DELETE` or `PRAGMA
user_version` mutation, it validates every v3 row, every TEXT value and the complete source copy
digest in a read-only preflight, including every prospective v4 candidate/role/binding/CHECK and FK
condition. Only then: create v4 quarantine; copy validated rows; compute and
compare the destination count/digest; create checkpoint, outcome, range and explicit index; drop
v3; let the framework set the version; run `requireExactV4`. Any later exception rolls back to
exact v3 objects, rows and `user_version=3`; a preflight error leaves the database untouched.

WAL, foreign keys, the shared helper, `synchronous=FULL`, `wal_autocheckpoint=0` and
`beginTransactionNonExclusive()` remain selected. A future implementation must verify their
effective Android behavior; these settings are not current durability proof.

## Exact binary encodings

All identity encodings are versioned, bounded and big-endian. `U8` is one unsigned byte. `U16BE`,
`U32BE` and `U64BE` are fixed-width unsigned integers. Runtime values must be representable in
signed Kotlin/SQLite types before conversion. `SHA256` is exactly 32 raw bytes. `RUN_ID` is exactly
16 raw bytes. `LP16_ASCII(x,max)` is `U16BE(byteLength)||strict-US-ASCII`, with length at most both
`max` and 65,535. `LP16_UTF8(x,max)` is `U16BE(byteLength)||canonical-UTF-8`: require database
encoding UTF-8, SQLite `typeof(x)='text'`, strict decoding of `CAST(x AS BLOB)` without replacement,
no unpaired surrogate or U+0000, and byte-for-byte equality after re-encoding; no Unicode
normalization is applied. `N(T)` is `U8(0)` for null or `U8(1)||T` for present. Booleans are
`U8(0|1)`. Unknown enums and non-canonical encodings are rejected. Identity preimages are capped
at 4,096 bytes.

Bounds are: protocol 96 bytes; candidate 64; source/path 512; artifact role, observed state,
decision, diagnostic branch, terminal, certainty, stage and classification 64 each. Hash the following exact
concatenations:

```text
oracleIdentitySha256 = SHA256(
  LP16_ASCII("DORA_REC_STREAM_ORACLE_V4",96) || LP16_ASCII(protocolId,96) ||
  LP16_ASCII(candidateId,64) ||
  RUN_ID || U64BE(A) || oraclePlaintextSha256)

controllerSnapshotSha256 = SHA256(
  LP16_ASCII("DORA_REC_STREAM_SNAPSHOT_V4",96) || LP16_ASCII(protocolId,96) ||
  LP16_ASCII(candidateId,64) || RUN_ID || U64BE(checkpointGeneration) ||
  checkpointIdentity || U64BE(P) || U64BE(checkpointContextEnd) ||
  oracleIdentitySha256 || U64BE(A) || oraclePlaintextSha256 || U64BE(S) ||
  preFaultSourceSha256)

sourceWitnessId = SHA256(
  LP16_ASCII("DORA_REC_STREAM_WITNESS_V4",96) || LP16_ASCII(protocolId,96) ||
  LP16_ASCII(candidateId,64) || RUN_ID || U64BE(checkpointGeneration) ||
  checkpointIdentity || U64BE(P) || U64BE(checkpointContextEnd) ||
  oracleIdentitySha256 || U64BE(A) || oraclePlaintextSha256 || U64BE(S) ||
  preFaultSourceSha256 || controllerSnapshotSha256)

rejectedObservationSha256 = SHA256(
  LP16_ASCII("DORA_REC_STREAM_REJECTED_OBSERVATION_V4",96) ||
  LP16_ASCII(protocolId,96) || LP16_ASCII(candidateId,64) || RUN_ID ||
  checkpointIdentity || sourceWitnessId || U64BE(E) || observedSourceSha256 ||
  U64BE(rejectedCandidateEnd) || rejectedCompletedPlaintextSha256 ||
  rejectedOraclePrefixSha256 || U8(rejectedOraclePrefixEqual) ||
  U64BE(rejectedComparedEnd) || N(U64BE(rejectedFirstMismatchOffset)) ||
  N(rejectedEqualPrefixSha256[32]) || N(U8(rejectedExpectedOracleByte)) ||
  N(U8(rejectedObservedPlaintextByte)) || U64BE(rejectedObservedTailLossBytes) ||
  LP16_ASCII(rejectedBoundaryResult,64) || N(U64BE(rejectedBoundaryBytes)))
```

`checkpointIdentity` is SHA-256 of, in this order:

```text
LP16_ASCII("DORA_REC_STREAM_CHECKPOINT_V4",96), LP16_ASCII(protocolId,96),
LP16_ASCII(candidateId,64), RUN_ID,
U64BE(generation), U64BE(q), U64BE(q*4096), prefixSha256, U64BE(C),
LP16_ASCII(checkpointRelativeName,512), U64BE(checkpointBytes), checkpointSha256,
LP16_ASCII(checkpointEnvelopeRelativeName,512), U64BE(checkpointEnvelopeBytes), checkpointEnvelopeSha256,
LP16_ASCII(streamRelativeName,512), LP16_ASCII(streamEnvelopeRelativeName,512), U64BE(streamEnvelopeBytes),
streamEnvelopeSha256, previousCheckpointSha256
```

`outcomeId` is SHA-256 of, in exact table-column order excluding `outcome_id` and `state`, with a
leading `LP16_ASCII("DORA_REC_STREAM_OUTCOME_V4",96)||LP16_ASCII(protocolId,96)`. This includes
every rejected-observation field, `rejected_observation_sha256`, `required_range_start` and
`required_range_certainty`. Every nullable outcome integer uses `N(U64BE)`, except the nullable
equality and byte-value columns use `N(U8)`; nullable hashes use `N(SHA256)` and nullable enums use
`N(LP16_ASCII)`. No empty value substitutes for null. Before insert, the controller recomputes the
rejected-observation digest, then computes `outcomeId`; exact readback and every collision
comparison compare every table column byte-for-byte, including these fields.

`rangeIntentId` is SHA-256 of:

```text
LP16_ASCII("DORA_REC_STREAM_RANGE_V4",96), LP16_ASCII(protocolId,96),
LP16_ASCII(candidateId,64), RUN_ID, outcomeId, LP16_ASCII(outcomeDecision,64),
LP16_ASCII(outcomeBranch,64), LP16_ASCII(outcomeTerminal,64),
LP16_ASCII(outcomeClassification,64),
LP16_ASCII(sourceRelativeName,512), U64BE(E), sourceSha256, U64BE(rangeStart),
U64BE(rangeEnd), rangeSha256, LP16_ASCII(boundaryCertainty,64),
LP16_ASCII("RETAINED_IN_PLACE_DENY_APP_READS",64)
```

The v3-to-v4 copy digest uses streaming SHA-256, source and destination independently:

```text
SHA256(
  LP16_ASCII("DORA_RECOVERY_QMIG_V3_V4",96) || U64BE(rowCount) ||
  for each row ORDER BY intent_id ASC using SQLite BLOB byte order:
    U32BE(rowEncodingLength) || rowEncoding
)
```

Each `rowEncoding`, in v3 column order, is:

```text
intent_id[32] || LP16_UTF8(run_id,64) || LP16_UTF8(candidate_id,64) ||
LP16_UTF8(bootstrap_binding,16) || N(LP16_UTF8(bootstrap_run_id,64)) ||
N(LP16_UTF8(bootstrap_candidate_id,64)) || LP16_UTF8(artifact_role,64) ||
LP16_UTF8(observed_state,64) || LP16_UTF8(source_relative_name,512) ||
LP16_UTF8(destination_relative_name,512) || U64BE(source_bytes) ||
SHA256(source_sha256) || LP16_UTF8(state,16)
```

Both source and destination digest passes use the exact `LP16_UTF8` column encoding and bounds
above. A DDL-valid non-ASCII v3 TEXT value is accepted when it is canonical UTF-8 and within its
column bound; its exact Unicode scalar sequence is copied without normalization. An invalid,
over-bound, NUL-containing or non-canonical value rejects during the read-only preflight before
the first schema mutation. Source and destination counts, digests and every copied SQLite value
must match before v3 is dropped.

## Sealed controller outcomes and evidence

The public Kotlin boundary is a closed sealed hierarchy:

```text
PersistenceReceipt(outcomeId, optionalRangeIntentId, replayed)
ExistingEvidenceReference(recordKind, existingId, existingIdentitySha256)
RejectedObservation(candidateEnd, completedPlaintextSha256, oraclePrefixSha256,
                    oraclePrefixEqual, comparedEnd, firstMismatchOffset?,
                    equalPrefixSha256?, expectedOracleByte?, observedPlaintextByte?,
                    observedTailLossBytes, boundaryResult, boundaryBytes?,
                    rejectedObservationSha256)

PersistedValid(receipt, A, C, R, terminal)
Retry(stage, classification, safeExceptionType, attemptedOutcomeId?, attemptedRangeId?,
      existingEvidenceReferences=[])
Rejected(stage, classification, diagnosticBranch?, checkpointIntersectionProven,
         provenCheckpointEnd?, rejectedObservation?, newPersistenceReceipt?,
         existingEvidenceReferences=[])
Fatal(stage, classification, diagnosticBranch?, checkpointIntersectionProven,
      provenCheckpointEnd?, rejectedObservation?, newPersistenceReceipt?,
      existingEvidenceReferences=[])
```

`RejectedObservation` is an internal typed value and is non-null exactly for persisted
`POST_INTERSECTION`; it is null for valid, pre-intersection and every non-persistable result. Its
candidate end is explicitly rejected and cannot be accessed through the admitted-R property.
`PersistenceReceipt` exists only after exact committed readback. `ExistingEvidenceReference`
always names a pre-existing row and never implies that the current failure was inserted. Attempted
IDs on `Retry` are deterministic lookup keys with unknown/absent commit status, not persistence
receipts.

Their behavior is fixed:

| Outcome | SQLite behavior | Evidence behavior |
|---|---|---|
| `PersistedValid` | Insert one `VALID` outcome and its non-empty exact range in one transaction. On exact replay, insert nothing and return exact stored IDs. | Emit sanitized at-least-once event only after successful commit/readback: IDs, A/C/R, derived counts, terminal, range offsets/certainty and enum fields. Exclude returned plaintext digest publicly. |
| `Retry` | Insert no new outcome/range. Used only for operational failure or an ambiguous commit whose exact readback finds neither intended row and cannot prove rollback. | Emit attempted IDs as unknown lookup keys, never receipts. No success, ACTIVE or durable-state claim. |
| `Rejected` | Persist only the matrix's post-intersection `STREAM_TAIL_BOUND_EXCEEDED` row and its rejected observation. An authentication failure inserts its exact range only for `B(candidateR)<E`; exact equality inserts no range child and has null required-range fields. Other rejected classifications are non-persistable. | A persisted post-intersection rejection emits proven C, a sanitized observation-present/boundary-result marker and no R. Non-persistable rejection has null receipt and no ACTIVE claim. |
| `Fatal` | Persist only the three pre-intersection and three post-intersection FATAL matrix rows when their parent/source, rejected-observation and atomic range prerequisites hold. Split brain, identity/unique/range collision, journal structural state and changed replay source never insert a new fatal row/range. | Pre-intersection receipt emits contextual endpoint with `checkpointIntersectionProven=false`; post-intersection receipt emits proven C, a sanitized observation marker and `true`; neither emits R. Collision/split-brain evidence contains only tagged pre-existing references. |

Raw exception messages, paths, plaintext, keys, keysets, ciphertext bytes, raw database/WAL files,
device identifiers, `returned_plaintext_sha256`, both rejected plaintext digests, the equal-prefix
digest, mismatch offset, expected byte and observed byte are excluded from public evidence. Safe
exception type is an allowlisted short class enum, not arbitrary text.

## Transaction, replay and collision rules

Checkpoint publication retains SCHK-01 through SCHK-13. SCHK-01 fsyncs the open append-only stream
descriptor. Checkpoint envelope and checkpoint ciphertext use the selected file/directory fsync
sequence. SCHK-11 inserts the exact checkpoint. Only successful return of SCHK-12
`endTransaction()` creates semantic `C`; SCHK-13 is evidence.

Recovery mutates no file or directory. After a valid read or matrix-persistable diagnostic,
one `beginTransactionNonExclusive()` inserts outcome first and its matrix-required non-empty range
second. PRE_INTERSECTION failures require `[0,E)` when `E>0`; POST_INTERSECTION range rules are
classification/terminal-specific, and EOF never has a range. A missing required range or a child
range where the matrix requires none aborts the transaction. Exact tail authentication equality
`B(candidateR)=E` is the required no-range form, not a missing range error.
Successful `endTransaction()` return plus exact readback seals the outcome and activates denial.
There is no filesystem fsync because no directory entry or file content changes. This is an SQLite
journal ordering claim, not device power-loss proof.

Before insertion, compute the intended outcome/range IDs. On any insert/unique/commit ambiguity
while holding the lease:

1. Query by `outcome_id`, then by the unique witness tuple.
2. Recompute the oracle-prefix digest and expected mismatch byte/equal-prefix digest from the
   already validated controller oracle; recompute `rejectedObservationSha256` and `outcomeId`;
   then compare every column, including blobs, rejected observations, required range fields and
   nulls, in table order.
3. If an exact intended outcome exists, query its range and compare the matrix-required presence
   (including zero children for `B(candidateR)=E`) and every column/hash. Exact complete state is idempotent replay and returns the existing
   `PersistenceReceipt(replayed=true)`.
4. If neither intended ID nor unique witness/source tuple exists after an unresolved commit,
   return `Retry/JOURNAL_COMMIT_STATE_UNRESOLVED`. Attempted IDs are not existing references.
5. Same outcome ID with different bytes, or same witness tuple with another outcome ID, returns
   non-persistable `Fatal/JOURNAL_ATTEMPT_CONFLICT`. Roll back the current transaction. Read and
   return the pre-existing conflicting outcome ID only as `ExistingEvidenceReference`.
6. Same range ID with different bytes, same source-range tuple with another ID, required range
   missing from a pre-existing outcome, or an unexpected range (including any child for the
   exact-empty tail form) returns non-persistable
   `Fatal/STREAM_RANGE_QUARANTINE_COLLISION` or `JOURNAL_STRUCTURAL`. Roll back any current
   transaction and reference only the pre-existing outcome/range IDs. Never insert a replacement
   fatal row or range through the conflicted keys.
7. A proven framework rollback with no row returns the original non-persistable semantic outcome;
   an operational error without commit ambiguity returns `Retry/JOURNAL_OPERATIONAL`.

This replay is deliberately hash-only. It rehashes the exact frozen source, revalidates the
checkpoint and controller witness, rechecks the persisted observation's deterministic digest,
oracle-side witness, class arithmetic and range, and returns the same IDs. It does not rerun public
Tink and therefore does not independently re-observe the historical completed-candidate digest or
observed mismatch byte. The sealed row proves integrity and idempotent identity of the recorded
observation, not a second cryptographic execution of the failed read.

Checkpoint split brain is detected before a unique validated parent can be selected. It returns
non-persistable `Fatal/STREAM_CHECKPOINT_SPLIT_BRAIN` with readable conflicting checkpoint IDs as
evidence references and cannot insert an outcome/range FK child.

Whole-object quarantine keeps ADR-0004 Q01 intent commit, Q02 no-overwrite rename, Q03 source-dir
fsync, Q04 destination-dir fsync, Q05 completion commit. Range quarantine never invokes Q01-Q05
and does not call itself `COMPLETED`; its only v4 state is `ACTIVE`.

## Journal-enforced read denial

`ACTIVE` is a policy enforced by cooperating app code, not an OS access-control primitive. Every
eligible app open of `stream/stream.ct`—recovery, replay, inspection and any later consumer—must
use one gateway and this order:

1. acquire the shared run lease and retain it for the descriptor's entire lifetime;
2. validate exact journal schema and run/checkpoint identity;
3. query `ACTIVE` ranges for the run/candidate/path before opening;
4. if none exists, open the bounded attempt normally;
5. if a range exists, deny any normal open that may read an interval intersecting it;
6. for exact replay only, a privileged verifier may open under the same lease, `fstat`, hash
   exactly `[0,E)`, close, compare with the sealed source identity, and return stored metadata. It
   may not invoke public Tink or return source bytes;
7. a size/hash mismatch returns `STREAM_SOURCE_IDENTITY_CHANGED` and never replaces the sealed row.

No cached journal decision may authorize an open. A descriptor opened before range commit is safe
only if all compliant code holds the same lease through its reads and the committing attempt; tests
must prove the gateway cannot release that lease early. A descriptor opened outside the gateway
can bypass the policy. Another process does not share the in-memory run lease, and schema v4 adds
no kernel file lock or OS deny rule. Therefore the v4 guarantee is single-process, cooperating-app
journal enforcement. Cross-process, external debugger/root access and stale foreign descriptors
are outside the PoC guarantee and remain preflight/design work.

Range retirement, deletion, compaction, physical suffix extraction and a controlled future
consumer that might need quarantined lookahead are deferred. Any retirement requires a new ADR,
forward schema migration and exact crash/replay contract. Version 4 exposes no retirement method.

## Processing-intent prohibition

This slice creates no streaming processing-intent table, calls no processing-intent calculator,
and enqueues nothing. All v4 outcome rows CHECK the three adoption flags are zero. `R>C` is
authenticated recovery accounting only. A later consuming-reconciliation ADR must define how it
obtains plaintext again, whether controlled lookahead access is allowed, its stable identity and
exact result/checkpoint foreign keys. It may not infer meaning from a range or unauthenticated byte.

## Consequences and verification

All v0.1-v0.6 artifacts remain byte-identical audit history. The v0.7 Gate Set and protocol inherit the v0.6 Markdown/Gate/protocol hashes `5ab6d105fe6c94868d77c25d1be065a1688ccb083fcbdc0c3f43096e73909063`, `6a5f1f994e5084836527fded9bdf762ac1ed982cb5022b6da64090a283717755`, and `9108cbffc3dc74a0e2a45868bf0c82b3827cb1e9023e1f0f12c53e7374c07a3d` and enumerate only their explicit streaming-persistence overrides.

The governance commit must be independently reviewed before source work. Implementation must use the exact scope document, red-first migration/controller/journal tests, host SQLite verification, repository-required Android checks, sanitized immutable evidence, independent critical review, and exact-head CI. Host/JVM evidence cannot establish Android power-loss or physical durability.

Reversal before implementation is a governance successor. Once a v4 database exists, reversal requires a later forward migration; downgrade and v4 retirement are forbidden.
