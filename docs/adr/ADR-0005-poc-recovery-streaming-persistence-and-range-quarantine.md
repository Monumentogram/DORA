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

## Consequences and verification

All v0.1-v0.6 artifacts remain byte-identical audit history. The v0.7 Gate Set and protocol inherit the v0.6 Markdown/Gate/protocol hashes `5ab6d105fe6c94868d77c25d1be065a1688ccb083fcbdc0c3f43096e73909063`, `6a5f1f994e5084836527fded9bdf762ac1ed982cb5022b6da64090a283717755`, and `9108cbffc3dc74a0e2a45868bf0c82b3827cb1e9023e1f0f12c53e7374c07a3d` and enumerate only their explicit streaming-persistence overrides.

The governance commit must be independently reviewed before source work. Implementation must use the exact scope document, red-first migration/controller/journal tests, host SQLite verification, repository-required Android checks, sanitized immutable evidence, independent critical review, and exact-head CI. Host/JVM evidence cannot establish Android power-loss or physical durability.

Reversal before implementation is a governance successor. Once a v4 database exists, reversal requires a later forward migration; downgrade and v4 retirement are forbidden.
