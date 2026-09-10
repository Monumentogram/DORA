package com.monumentogram.dora.poc.recovery.journal

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import com.monumentogram.dora.poc.recovery.contract.CanonicalSqliteText
import com.monumentogram.dora.poc.recovery.contract.RecoveryQuarantineMigrationRow
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingMigration
import com.monumentogram.dora.poc.recovery.storage.BootstrapPathType
import com.monumentogram.dora.poc.recovery.storage.RecoveryBootstrapPathPolicy
import java.io.File

@Suppress("LargeClass", "MagicNumber", "TooManyFunctions")
internal object RecoveryJournalSchema {
    data class SqliteObject(val type: String, val name: String, val tableName: String)

    enum class V3ToV4Step {
        AFTER_PREFLIGHT,
        AFTER_CREATE_QUARANTINE,
        AFTER_COPY,
        AFTER_DESTINATION_VERIFY,
        AFTER_CREATE_CHECKPOINT,
        AFTER_CREATE_OUTCOME,
        AFTER_CREATE_RANGE,
        AFTER_CREATE_RANGE_INDEX,
        AFTER_DROP_V3,
    }

    fun interface V3ToV4Failpoint {
        fun hit(step: V3ToV4Step)
    }

    val V3_TO_V4_STEPS = V3ToV4Step.entries.toList()
    private val NO_MIGRATION_FAILPOINT = V3ToV4Failpoint {}

    const val VERSION = 4
    const val DATABASE_RELATIVE_NAME = "poc-recovery/v1/recovery-journal-v1.db"
    const val RUN_TABLE = "recovery_run_bootstrap_v1"
    const val UNIT_TABLE = "recovery_microfile_unit_v2"
    const val PUBLICATION_TABLE = "recovery_manifest_publication_v2"
    const val QUARANTINE_V3_TABLE = "recovery_quarantine_intent_v3"
    const val QUARANTINE_TABLE = "recovery_quarantine_intent_v4"
    const val STREAM_CHECKPOINT_TABLE = "recovery_stream_checkpoint_v4"
    const val STREAM_OUTCOME_TABLE = "recovery_stream_outcome_v4"
    const val STREAM_RANGE_TABLE = "recovery_stream_range_quarantine_v4"

    val EXACT_V4_OBJECTS =
        setOf(
            SqliteObject("table", RUN_TABLE, RUN_TABLE),
            SqliteObject("table", UNIT_TABLE, UNIT_TABLE),
            SqliteObject("table", PUBLICATION_TABLE, PUBLICATION_TABLE),
            SqliteObject("table", QUARANTINE_TABLE, QUARANTINE_TABLE),
            SqliteObject("table", STREAM_CHECKPOINT_TABLE, STREAM_CHECKPOINT_TABLE),
            SqliteObject("table", STREAM_OUTCOME_TABLE, STREAM_OUTCOME_TABLE),
            SqliteObject("table", STREAM_RANGE_TABLE, STREAM_RANGE_TABLE),
            SqliteObject("index", "recovery_run_candidate_v2", RUN_TABLE),
            SqliteObject("index", "recovery_stream_active_range_v4", STREAM_RANGE_TABLE),
            SqliteObject("index", "sqlite_autoindex_recovery_run_bootstrap_v1_1", RUN_TABLE),
            SqliteObject("index", "sqlite_autoindex_recovery_microfile_unit_v2_1", UNIT_TABLE),
            SqliteObject("index", "sqlite_autoindex_recovery_microfile_unit_v2_2", UNIT_TABLE),
            SqliteObject(
                "index",
                "sqlite_autoindex_recovery_manifest_publication_v2_1",
                PUBLICATION_TABLE,
            ),
            SqliteObject(
                "index",
                "sqlite_autoindex_recovery_quarantine_intent_v4_1",
                QUARANTINE_TABLE,
            ),
            SqliteObject(
                "index",
                "sqlite_autoindex_recovery_quarantine_intent_v4_2",
                QUARANTINE_TABLE,
            ),
            SqliteObject(
                "index",
                "sqlite_autoindex_recovery_stream_checkpoint_v4_1",
                STREAM_CHECKPOINT_TABLE,
            ),
            SqliteObject(
                "index",
                "sqlite_autoindex_recovery_stream_checkpoint_v4_2",
                STREAM_CHECKPOINT_TABLE,
            ),
            SqliteObject(
                "index",
                "sqlite_autoindex_recovery_stream_checkpoint_v4_3",
                STREAM_CHECKPOINT_TABLE,
            ),
            SqliteObject(
                "index",
                "sqlite_autoindex_recovery_stream_outcome_v4_1",
                STREAM_OUTCOME_TABLE,
            ),
            SqliteObject(
                "index",
                "sqlite_autoindex_recovery_stream_outcome_v4_2",
                STREAM_OUTCOME_TABLE,
            ),
            SqliteObject(
                "index",
                "sqlite_autoindex_recovery_stream_outcome_v4_3",
                STREAM_OUTCOME_TABLE,
            ),
            SqliteObject(
                "index",
                "sqlite_autoindex_recovery_stream_range_quarantine_v4_1",
                STREAM_RANGE_TABLE,
            ),
            SqliteObject(
                "index",
                "sqlite_autoindex_recovery_stream_range_quarantine_v4_2",
                STREAM_RANGE_TABLE,
            ),
            SqliteObject(
                "index",
                "sqlite_autoindex_recovery_stream_range_quarantine_v4_3",
                STREAM_RANGE_TABLE,
            ),
        )

    enum class UpgradePlan {
        V1_TO_V4,
        V2_TO_V4,
        V3_TO_V4,
        REJECT,
    }

    fun upgradePlan(oldVersion: Int, newVersion: Int): UpgradePlan =
        when {
            oldVersion == 1 && newVersion == 4 -> UpgradePlan.V1_TO_V4
            oldVersion == 2 && newVersion == 4 -> UpgradePlan.V2_TO_V4
            oldVersion == 3 && newVersion == 4 -> UpgradePlan.V3_TO_V4
            else -> UpgradePlan.REJECT
        }

    const val CREATE_RUN_TABLE =
        """CREATE TABLE recovery_run_bootstrap_v1 (
        run_id TEXT NOT NULL PRIMARY KEY,
        candidate_id TEXT NOT NULL CHECK(candidate_id IN ('REC-STREAM-TINK','REC-MICROFILE-TINK')),
        key_confirmation_relative_name TEXT NOT NULL CHECK(key_confirmation_relative_name = 'key-confirmation/run.kc'),
        key_confirmation_bytes INTEGER NOT NULL CHECK(key_confirmation_bytes > 0),
        key_confirmation_sha256 BLOB NOT NULL CHECK(length(key_confirmation_sha256) = 32),
        canonical_alias_sha256 BLOB NOT NULL CHECK(length(canonical_alias_sha256) = 32),
        key_confirmation_state TEXT NOT NULL CHECK(key_confirmation_state IN ('VALID','QUARANTINE_PENDING','QUARANTINED'))
    )"""
    const val CREATE_RUN_IDENTITY_INDEX =
        "CREATE UNIQUE INDEX recovery_run_candidate_v2 ON recovery_run_bootstrap_v1(run_id,candidate_id)"
    const val CREATE_UNIT_TABLE =
        """CREATE TABLE recovery_microfile_unit_v2 (
        run_id TEXT NOT NULL, candidate_id TEXT NOT NULL CHECK(candidate_id='REC-MICROFILE-TINK'),
        unit_index INTEGER NOT NULL CHECK(unit_index BETWEEN 0 AND 4294967295),
        plaintext_start INTEGER NOT NULL CHECK(plaintext_start >= 0),
        plaintext_end INTEGER NOT NULL CHECK(plaintext_end > plaintext_start AND plaintext_end <= 115200000 AND plaintext_end - plaintext_start <= cadence_seconds * 32000),
        cadence_seconds INTEGER NOT NULL CHECK(cadence_seconds IN (5,15,30)),
        ciphertext_relative_name TEXT NOT NULL CHECK(ciphertext_relative_name = printf('units/u-%010d.ct',unit_index)), ciphertext_bytes INTEGER NOT NULL CHECK(ciphertext_bytes > 0),
        ciphertext_sha256 BLOB NOT NULL CHECK(length(ciphertext_sha256)=32),
        key_envelope_relative_name TEXT NOT NULL CHECK(key_envelope_relative_name = printf('key-envelopes/u-%010d.ks',unit_index)), key_envelope_bytes INTEGER NOT NULL CHECK(key_envelope_bytes > 0),
        key_envelope_sha256 BLOB NOT NULL CHECK(length(key_envelope_sha256)=32),
        manifest_generation INTEGER NOT NULL CHECK(manifest_generation = unit_index + 1),
        processing_intent_id BLOB NOT NULL UNIQUE CHECK(length(processing_intent_id)=32),
        state TEXT NOT NULL CHECK(state='VALID'),
        PRIMARY KEY(run_id,candidate_id,unit_index),
        FOREIGN KEY(run_id,candidate_id) REFERENCES recovery_run_bootstrap_v1(run_id,candidate_id) ON UPDATE RESTRICT ON DELETE RESTRICT
    )"""
    const val CREATE_PUBLICATION_TABLE =
        """CREATE TABLE recovery_manifest_publication_v2 (
        run_id TEXT NOT NULL, candidate_id TEXT NOT NULL CHECK(candidate_id='REC-MICROFILE-TINK'),
        publication_kind TEXT NOT NULL CHECK(publication_kind='MANIFEST'),
        generation INTEGER NOT NULL CHECK(generation > 0), committed_end INTEGER NOT NULL CHECK(committed_end BETWEEN 1 AND 115200000),
        publication_relative_name TEXT NOT NULL CHECK(publication_relative_name = printf('manifests/g-%020d.ct',generation)), publication_bytes INTEGER NOT NULL CHECK(publication_bytes > 0),
        publication_sha256 BLOB NOT NULL CHECK(length(publication_sha256)=32),
        key_envelope_relative_name TEXT NOT NULL CHECK(key_envelope_relative_name = printf('key-envelopes/manifest-g-%020d.ks',generation)), key_envelope_bytes INTEGER NOT NULL CHECK(key_envelope_bytes > 0),
        key_envelope_sha256 BLOB NOT NULL CHECK(length(key_envelope_sha256)=32),
        previous_publication_sha256 BLOB NOT NULL CHECK(length(previous_publication_sha256)=32),
        state TEXT NOT NULL CHECK(state='VALID'),
        PRIMARY KEY(run_id,candidate_id,generation),
        FOREIGN KEY(run_id,candidate_id) REFERENCES recovery_run_bootstrap_v1(run_id,candidate_id) ON UPDATE RESTRICT ON DELETE RESTRICT
    )"""
    const val CREATE_QUARANTINE_V3_TABLE =
        """CREATE TABLE recovery_quarantine_intent_v3 (
        intent_id BLOB NOT NULL PRIMARY KEY CHECK(length(intent_id)=32),
        run_id TEXT NOT NULL, candidate_id TEXT NOT NULL CHECK(candidate_id='REC-MICROFILE-TINK'),
        bootstrap_binding TEXT NOT NULL CHECK(bootstrap_binding IN ('ABSENT','PRESENT')),
        bootstrap_run_id TEXT, bootstrap_candidate_id TEXT,
        artifact_role TEXT NOT NULL CHECK(artifact_role IN ('KEY_CONFIRMATION','MICROFILE_KEY_ENVELOPE','MICROFILE_CIPHERTEXT','MANIFEST_KEY_ENVELOPE','MANIFEST_CIPHERTEXT','UNKNOWN_REGULAR')),
        observed_state TEXT NOT NULL CHECK(observed_state IN ('TEMP_ONLY','TEMP_AND_FINAL','FINAL_ORPHAN','SQLITE_POINTS_TO_TEMP','UNKNOWN_OR_NON_ALLOWLISTED_NAME')),
        source_relative_name TEXT NOT NULL, destination_relative_name TEXT NOT NULL,
        source_bytes INTEGER NOT NULL CHECK(source_bytes>=0), source_sha256 BLOB NOT NULL CHECK(length(source_sha256)=32),
        state TEXT NOT NULL CHECK(state IN ('PENDING','COMPLETED')),
        CHECK((bootstrap_binding='ABSENT' AND bootstrap_run_id IS NULL AND bootstrap_candidate_id IS NULL) OR
              (bootstrap_binding='PRESENT' AND bootstrap_run_id=run_id AND bootstrap_candidate_id=candidate_id)),
        UNIQUE(run_id,candidate_id,source_relative_name,source_sha256),
        FOREIGN KEY(bootstrap_run_id,bootstrap_candidate_id) REFERENCES recovery_run_bootstrap_v1(run_id,candidate_id) ON UPDATE RESTRICT ON DELETE RESTRICT
    )"""

    const val CREATE_QUARANTINE_TABLE =
        """CREATE TABLE recovery_quarantine_intent_v4 (
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
)"""
    const val CREATE_STREAM_CHECKPOINT_TABLE =
        """CREATE TABLE recovery_stream_checkpoint_v4 (
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
)"""
    const val CREATE_STREAM_OUTCOME_TABLE =
        """CREATE TABLE recovery_stream_outcome_v4 (
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
)"""
    const val CREATE_STREAM_RANGE_TABLE =
        """CREATE TABLE recovery_stream_range_quarantine_v4 (
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
)"""
    const val CREATE_STREAM_ACTIVE_RANGE_INDEX =
        """CREATE INDEX recovery_stream_active_range_v4
ON recovery_stream_range_quarantine_v4
  (run_id,candidate_id,source_relative_name,state,range_start,range_end)"""

    fun createV3(database: SQLiteDatabase) {
        database.execSQL(CREATE_RUN_TABLE)
        database.execSQL(CREATE_RUN_IDENTITY_INDEX)
        database.execSQL(CREATE_UNIT_TABLE)
        database.execSQL(CREATE_PUBLICATION_TABLE)
        database.execSQL(CREATE_QUARANTINE_V3_TABLE)
        requireExactV3(database)
    }

    fun createV4(database: SQLiteDatabase) {
        database.execSQL(CREATE_RUN_TABLE)
        database.execSQL(CREATE_RUN_IDENTITY_INDEX)
        database.execSQL(CREATE_UNIT_TABLE)
        database.execSQL(CREATE_PUBLICATION_TABLE)
        database.execSQL(CREATE_QUARANTINE_TABLE)
        database.execSQL(CREATE_STREAM_CHECKPOINT_TABLE)
        database.execSQL(CREATE_STREAM_OUTCOME_TABLE)
        database.execSQL(CREATE_STREAM_RANGE_TABLE)
        database.execSQL(CREATE_STREAM_ACTIVE_RANGE_INDEX)
        requireExactV4(database)
    }

    fun migrateV1ToV2(database: SQLiteDatabase) {
        requireExactV1(database)
        database.execSQL(CREATE_RUN_IDENTITY_INDEX)
        database.execSQL(CREATE_UNIT_TABLE)
        database.execSQL(CREATE_PUBLICATION_TABLE)
    }

    fun migrateV2ToV3(database: SQLiteDatabase) {
        requireExactV2(database)
        database.execSQL(CREATE_QUARANTINE_V3_TABLE)
        requireExactV3(database)
    }

    private fun requireExactV2(database: SQLiteDatabase, allowV3: Boolean = false) {
        requireExactSql(database, "table", RUN_TABLE, CREATE_RUN_TABLE)
        requireExactSql(database, "index", "recovery_run_candidate_v2", CREATE_RUN_IDENTITY_INDEX)
        requireExactSql(database, "table", UNIT_TABLE, CREATE_UNIT_TABLE)
        requireExactSql(database, "table", PUBLICATION_TABLE, CREATE_PUBLICATION_TABLE)
        val recoveryObjects = mutableListOf<Pair<String, String>>()
        database
            .rawQuery(
                "SELECT type,name FROM sqlite_master " +
                    "WHERE name LIKE 'recovery_%' OR tbl_name LIKE 'recovery_%' " +
                    "ORDER BY type,name",
                null,
            )
            .use { cursor ->
                while (cursor.moveToNext()) recoveryObjects +=
                    cursor.getString(0) to cursor.getString(1)
            }
        val expected =
            mutableListOf(
                "index" to "recovery_run_candidate_v2",
                "index" to "sqlite_autoindex_recovery_manifest_publication_v2_1",
                "index" to "sqlite_autoindex_recovery_microfile_unit_v2_1",
                "index" to "sqlite_autoindex_recovery_microfile_unit_v2_2",
                "index" to "sqlite_autoindex_recovery_run_bootstrap_v1_1",
                "table" to PUBLICATION_TABLE,
                "table" to UNIT_TABLE,
                "table" to RUN_TABLE,
            )
        if (allowV3)
            expected +=
                listOf(
                    "index" to "sqlite_autoindex_recovery_quarantine_intent_v3_1",
                    "index" to "sqlite_autoindex_recovery_quarantine_intent_v3_2",
                    "table" to QUARANTINE_V3_TABLE,
                )
        expected.sortWith(compareBy<Pair<String, String>> { it.first }.thenBy { it.second })
        if (recoveryObjects != expected)
            throw SQLiteException("Recovery journal v2 schema is not exact")
    }

    fun requireExactV3(database: SQLiteDatabase) {
        requireExactV2(database, allowV3 = true)
        requireExactSql(database, "table", QUARANTINE_V3_TABLE, CREATE_QUARANTINE_V3_TABLE)
    }

    fun migrateV3ToV4(
        database: SQLiteDatabase,
        failpoint: V3ToV4Failpoint = NO_MIGRATION_FAILPOINT,
    ) {
        requireExactV3(database)
        require(rowCount(database, "PRAGMA foreign_key_check") == 0) {
            "Recovery journal v3 contains foreign-key violations"
        }
        val sourceRows = readQuarantineRows(database, QUARANTINE_V3_TABLE)
        val sourceDigest = RecoveryStreamingMigration.digest(sourceRows)
        failpoint.hit(V3ToV4Step.AFTER_PREFLIGHT)
        database.execSQL(CREATE_QUARANTINE_TABLE)
        failpoint.hit(V3ToV4Step.AFTER_CREATE_QUARANTINE)
        sourceRows.forEach { row ->
            database.insertOrThrow(QUARANTINE_TABLE, null, row.toContentValues())
        }
        failpoint.hit(V3ToV4Step.AFTER_COPY)
        val destinationRows = readQuarantineRows(database, QUARANTINE_TABLE)
        if (
            destinationRows.size != sourceRows.size ||
                !RecoveryStreamingMigration.digest(destinationRows).contentEquals(sourceDigest) ||
                !RecoveryStreamingMigration.exactRowsEqual(sourceRows, destinationRows)
        ) {
            throw SQLiteException("Recovery journal v3-to-v4 copy digest mismatch")
        }
        failpoint.hit(V3ToV4Step.AFTER_DESTINATION_VERIFY)
        database.execSQL(CREATE_STREAM_CHECKPOINT_TABLE)
        failpoint.hit(V3ToV4Step.AFTER_CREATE_CHECKPOINT)
        database.execSQL(CREATE_STREAM_OUTCOME_TABLE)
        failpoint.hit(V3ToV4Step.AFTER_CREATE_OUTCOME)
        database.execSQL(CREATE_STREAM_RANGE_TABLE)
        failpoint.hit(V3ToV4Step.AFTER_CREATE_RANGE)
        database.execSQL(CREATE_STREAM_ACTIVE_RANGE_INDEX)
        failpoint.hit(V3ToV4Step.AFTER_CREATE_RANGE_INDEX)
        database.execSQL("DROP TABLE $QUARANTINE_V3_TABLE")
        failpoint.hit(V3ToV4Step.AFTER_DROP_V3)
        requireExactV4(database)
    }

    private fun readQuarantineRows(
        database: SQLiteDatabase,
        table: String,
    ): List<RecoveryQuarantineMigrationRow> =
        database.rawQuery(quarantineMigrationQuery(table), null).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(cursor.toQuarantineMigrationRow())
            }
        }

    private fun quarantineMigrationQuery(table: String): String {
        require(table == QUARANTINE_V3_TABLE || table == QUARANTINE_TABLE)
        val columns =
            listOf(
                "run_id",
                "candidate_id",
                "bootstrap_binding",
                "bootstrap_run_id",
                "bootstrap_candidate_id",
                "artifact_role",
                "observed_state",
                "source_relative_name",
                "destination_relative_name",
                "state",
            )
        val textEvidence =
            columns.joinToString(",") { column ->
                "typeof($column) AS ${column}_type, CAST($column AS BLOB) AS ${column}_blob"
            }
        return "SELECT intent_id,run_id,candidate_id,bootstrap_binding,bootstrap_run_id," +
            "bootstrap_candidate_id,artifact_role,observed_state,source_relative_name," +
            "destination_relative_name,source_bytes,source_sha256,state,$textEvidence " +
            "FROM $table ORDER BY intent_id ASC"
    }

    private fun Cursor.toQuarantineMigrationRow(): RecoveryQuarantineMigrationRow =
        try {
            RecoveryQuarantineMigrationRow(
                    intentId = requiredBlob("intent_id"),
                    runId = requiredCanonicalText("run_id", 64),
                    candidateId = requiredCanonicalText("candidate_id", 64),
                    bootstrapBinding = requiredCanonicalText("bootstrap_binding", 16),
                    bootstrapRunId = nullableCanonicalText("bootstrap_run_id", 64),
                    bootstrapCandidateId = nullableCanonicalText("bootstrap_candidate_id", 64),
                    artifactRole = requiredCanonicalText("artifact_role", 64),
                    observedState = requiredCanonicalText("observed_state", 64),
                    sourceRelativeName = requiredCanonicalText("source_relative_name", 512),
                    destinationRelativeName =
                        requiredCanonicalText("destination_relative_name", 512),
                    sourceBytes = requiredLong("source_bytes"),
                    sourceSha256 = requiredBlob("source_sha256"),
                    state = requiredCanonicalText("state", 16),
                )
                .also { RecoveryStreamingMigration.digest(listOf(it)) }
        } catch (failure: IllegalArgumentException) {
            throw SQLiteException("Recovery journal v3 migration preflight failed", failure)
        }

    private fun Cursor.requiredBlob(column: String): ByteArray {
        val index = getColumnIndexOrThrow(column)
        require(!isNull(index) && getType(index) == Cursor.FIELD_TYPE_BLOB) {
            "$column is not a BLOB"
        }
        return getBlob(index)
    }

    private fun Cursor.requiredLong(column: String): Long {
        val index = getColumnIndexOrThrow(column)
        require(!isNull(index) && getType(index) == Cursor.FIELD_TYPE_INTEGER) {
            "$column is not an INTEGER"
        }
        return getLong(index)
    }

    private fun Cursor.requiredCanonicalText(
        column: String,
        maximumBytes: Int,
    ): CanonicalSqliteText {
        val type = getString(getColumnIndexOrThrow("${column}_type"))
        require(type == "text") { "$column is not SQLite TEXT" }
        return CanonicalSqliteText.of(
            getString(getColumnIndexOrThrow(column)),
            getBlob(getColumnIndexOrThrow("${column}_blob")),
            maximumBytes,
        )
    }

    private fun Cursor.nullableCanonicalText(
        column: String,
        maximumBytes: Int,
    ): CanonicalSqliteText? {
        val index = getColumnIndexOrThrow(column)
        val type = getString(getColumnIndexOrThrow("${column}_type"))
        if (isNull(index)) {
            require(type == "null") { "$column null has a non-null SQLite type" }
            return null
        }
        return requiredCanonicalText(column, maximumBytes)
    }

    private fun RecoveryQuarantineMigrationRow.toContentValues() =
        ContentValues().apply {
            put("intent_id", intentId)
            put("run_id", runId.value)
            put("candidate_id", candidateId.value)
            put("bootstrap_binding", bootstrapBinding.value)
            put("bootstrap_run_id", bootstrapRunId?.value)
            put("bootstrap_candidate_id", bootstrapCandidateId?.value)
            put("artifact_role", artifactRole.value)
            put("observed_state", observedState.value)
            put("source_relative_name", sourceRelativeName.value)
            put("destination_relative_name", destinationRelativeName.value)
            put("source_bytes", sourceBytes)
            put("source_sha256", sourceSha256)
            put("state", state.value)
        }

    fun requireExactV4(database: SQLiteDatabase) {
        requireExactSql(database, "table", RUN_TABLE, CREATE_RUN_TABLE)
        requireExactSql(database, "index", "recovery_run_candidate_v2", CREATE_RUN_IDENTITY_INDEX)
        requireExactSql(database, "table", UNIT_TABLE, CREATE_UNIT_TABLE)
        requireExactSql(database, "table", PUBLICATION_TABLE, CREATE_PUBLICATION_TABLE)
        requireExactSql(database, "table", QUARANTINE_TABLE, CREATE_QUARANTINE_TABLE)
        requireExactSql(database, "table", STREAM_CHECKPOINT_TABLE, CREATE_STREAM_CHECKPOINT_TABLE)
        requireExactSql(database, "table", STREAM_OUTCOME_TABLE, CREATE_STREAM_OUTCOME_TABLE)
        requireExactSql(database, "table", STREAM_RANGE_TABLE, CREATE_STREAM_RANGE_TABLE)
        requireExactSql(
            database,
            "index",
            "recovery_stream_active_range_v4",
            CREATE_STREAM_ACTIVE_RANGE_INDEX,
        )
        val objects = mutableListOf<SqliteObject>()
        database
            .rawQuery(
                "SELECT type,name,tbl_name FROM sqlite_master " +
                    "WHERE name LIKE 'recovery_%' OR tbl_name LIKE 'recovery_%'",
                null,
            )
            .use { cursor ->
                while (cursor.moveToNext()) {
                    objects +=
                        SqliteObject(cursor.getString(0), cursor.getString(1), cursor.getString(2))
                }
            }
        if (objects.size != objects.toSet().size || objects.toSet() != EXACT_V4_OBJECTS) {
            throw SQLiteException("Recovery journal v4 schema is not exact")
        }
    }

    private fun requireExactSql(
        database: SQLiteDatabase,
        type: String,
        name: String,
        expected: String,
    ) {
        val actual =
            database
                .rawQuery(
                    "SELECT sql FROM sqlite_master WHERE type=? AND name=?",
                    arrayOf(type, name),
                )
                .use { cursor ->
                    if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
                }
        if (normalizeSql(actual) != normalizeSql(expected)) {
            throw SQLiteException("Recovery journal v2 schema is not exact: $name")
        }
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    private fun requireExactV1(database: SQLiteDatabase) {
        val expectedColumns =
            listOf(
                listOf("0", "run_id", "TEXT", "1", null, "1"),
                listOf("1", "candidate_id", "TEXT", "1", null, "0"),
                listOf("2", "key_confirmation_relative_name", "TEXT", "1", null, "0"),
                listOf("3", "key_confirmation_bytes", "INTEGER", "1", null, "0"),
                listOf("4", "key_confirmation_sha256", "BLOB", "1", null, "0"),
                listOf("5", "canonical_alias_sha256", "BLOB", "1", null, "0"),
                listOf("6", "key_confirmation_state", "TEXT", "1", null, "0"),
            )
        val actualColumns = mutableListOf<List<String?>>()
        database.rawQuery("PRAGMA table_info($RUN_TABLE)", null).use { cursor ->
            val fields = listOf("cid", "name", "type", "notnull", "dflt_value", "pk")
            while (cursor.moveToNext()) {
                actualColumns += fields.map { field ->
                    val index = cursor.getColumnIndexOrThrow(field)
                    if (cursor.isNull(index)) null else cursor.getString(index)
                }
            }
        }
        val tableSql =
            database
                .rawQuery(
                    "SELECT sql FROM sqlite_master WHERE type='table' AND name=?",
                    arrayOf(RUN_TABLE),
                )
                .use { cursor ->
                    if (!cursor.moveToFirst() || cursor.isNull(0)) null else cursor.getString(0)
                }
        val foreignKeyCount = rowCount(database, "PRAGMA foreign_key_list($RUN_TABLE)")
        val triggers =
            database
                .rawQuery(
                    "SELECT name FROM sqlite_master WHERE type='trigger' AND tbl_name=?",
                    arrayOf(RUN_TABLE),
                )
                .use { cursor ->
                    buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
                }
        val indexes = mutableListOf<List<String>>()
        database.rawQuery("PRAGMA index_list($RUN_TABLE)", null).use { cursor ->
            val fields = listOf("seq", "name", "unique", "origin", "partial")
            while (cursor.moveToNext()) {
                indexes += fields.map { cursor.getString(cursor.getColumnIndexOrThrow(it)) }
            }
        }
        val expectedIndexes = listOf(listOf("0", "sqlite_autoindex_${RUN_TABLE}_1", "1", "pk", "0"))
        val exact =
            listOf(
                    actualColumns == expectedColumns,
                    normalizeSql(tableSql) == normalizeSql(CREATE_RUN_TABLE),
                    foreignKeyCount == 0,
                    triggers.isEmpty(),
                    indexes == expectedIndexes,
                )
                .all { it }
        if (!exact) {
            throw SQLiteException("Recovery journal v1 schema is not exact")
        }
    }

    private fun rowCount(database: SQLiteDatabase, sql: String): Int =
        database.rawQuery(sql, null).use { cursor ->
            var count = 0
            while (cursor.moveToNext()) count++
            count
        }

    private fun normalizeSql(value: String?): String? =
        value?.trim()?.replace(Regex("\\s+"), " ")?.replace("( ", "(")?.replace(" )", ")")
}

internal object AndroidRecoveryJournalDatabase {
    @Volatile private var helper: RecoveryJournalSqliteHelper? = null

    fun writable(context: Context): SQLiteDatabase {
        val app = context.applicationContext
        val selected =
            helper
                ?: synchronized(this) {
                    helper ?: RecoveryJournalSqliteHelper(app).also { helper = it }
                }
        return selected.writableDatabase
    }
}

private class RecoveryJournalSqliteHelper(context: Context) :
    SQLiteOpenHelper(
        context,
        databasePath(context).path,
        RecoveryJournalSchema.VERSION,
        SQLiteDatabase.OpenParams.Builder()
            .addOpenFlags(SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING)
            .setSynchronousMode(SQLiteDatabase.SYNC_MODE_FULL)
            .build(),
    ) {

    override fun onConfigure(database: SQLiteDatabase) {
        database.setForeignKeyConstraintsEnabled(true)
        database.rawQuery("PRAGMA wal_autocheckpoint=0", null).use { cursor ->
            check(cursor.moveToFirst() && cursor.getInt(0) == 0) {
                "Recovery journal could not disable WAL auto-checkpointing"
            }
        }
    }

    override fun onCreate(database: SQLiteDatabase) = RecoveryJournalSchema.createV4(database)

    override fun onOpen(database: SQLiteDatabase) {
        super.onOpen(database)
        if (database.version == RecoveryJournalSchema.VERSION) {
            RecoveryJournalSchema.requireExactV4(database)
        }
    }

    override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        when (RecoveryJournalSchema.upgradePlan(oldVersion, newVersion)) {
            RecoveryJournalSchema.UpgradePlan.V1_TO_V4 -> {
                RecoveryJournalSchema.migrateV1ToV2(database)
                RecoveryJournalSchema.migrateV2ToV3(database)
                RecoveryJournalSchema.migrateV3ToV4(database)
            }
            RecoveryJournalSchema.UpgradePlan.V2_TO_V4 -> {
                RecoveryJournalSchema.migrateV2ToV3(database)
                RecoveryJournalSchema.migrateV3ToV4(database)
            }
            RecoveryJournalSchema.UpgradePlan.V3_TO_V4 ->
                RecoveryJournalSchema.migrateV3ToV4(database)
            RecoveryJournalSchema.UpgradePlan.REJECT ->
                throw SQLiteException(
                    "PoC Recovery journal migration is not admitted: $oldVersion -> $newVersion"
                )
        }
    }

    override fun onDowngrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int): Unit =
        throw SQLiteException(
            "PoC Recovery journal downgrade is forbidden: $oldVersion -> $newVersion"
        )

    companion object {
        private const val DIRECTORY_MODE_OWNER_ONLY = 0x1c0

        fun databasePath(context: Context): File {
            val file = File(context.noBackupFilesDir, RecoveryJournalSchema.DATABASE_RELATIVE_NAME)
            val fixed = File(context.noBackupFilesDir, "poc-recovery")
            val version = File(fixed, "v1")
            requireDirectory(context.noBackupFilesDir)
            for (directory in listOf(fixed, version)) when (val type = existingType(directory)) {
                BootstrapPathType.ABSENT -> {
                    Os.mkdir(directory.path, DIRECTORY_MODE_OWNER_ONLY)
                    requireDirectory(directory)
                }
                else -> RecoveryBootstrapPathPolicy.requireDirectoryComponent(type, directory.name)
            }
            for (leaf in
                listOf(
                    file,
                    File("${file.path}-wal"),
                    File("${file.path}-shm"),
                    File("${file.path}-journal"),
                )) RecoveryBootstrapPathPolicy.requireRegularOrAbsentLeaf(
                existingType(leaf),
                leaf.name,
            )
            return file
        }

        private fun requireDirectory(file: File) =
            RecoveryBootstrapPathPolicy.requireDirectoryComponent(existingType(file), file.name)

        private fun existingType(file: File): BootstrapPathType =
            try {
                val mode = Os.lstat(file.path).st_mode
                when {
                    OsConstants.S_ISLNK(mode) -> BootstrapPathType.SYMLINK
                    OsConstants.S_ISREG(mode) -> BootstrapPathType.REGULAR
                    OsConstants.S_ISDIR(mode) -> BootstrapPathType.DIRECTORY
                    else -> BootstrapPathType.OTHER
                }
            } catch (error: ErrnoException) {
                if (error.errno == OsConstants.ENOENT) BootstrapPathType.ABSENT else throw error
            }
    }
}
