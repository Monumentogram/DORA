@file:Suppress(
    "ComplexCondition",
    "CyclomaticComplexMethod",
    "LargeClass",
    "LongMethod",
    "LongParameterList",
    "MagicNumber",
    "ReturnCount",
    "TooGenericExceptionCaught",
    "TooManyFunctions",
)

package com.monumentogram.dora.poc.recovery.journal

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingCheckpointRow
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingExistingEvidence
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingJournal
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingJournalClassification
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingJournalReadResult
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingJournalResult
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingOutcomeAttempt
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingOutcomeRow
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingRangeRow
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingRejectedObservationInput
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingRowValidation
import com.monumentogram.dora.poc.recovery.contract.RunId
import com.monumentogram.dora.poc.recovery.contract.Sha256Value
import com.monumentogram.dora.poc.recovery.contract.StreamBoundaryResult
import com.monumentogram.dora.poc.recovery.contract.StreamRangeCertainty
import java.util.Locale

internal sealed interface StreamingSqliteCell {
    data object Null : StreamingSqliteCell

    data class Integer(val value: Long) : StreamingSqliteCell

    data class Real(val value: Double) : StreamingSqliteCell

    data class Text(val value: String) : StreamingSqliteCell

    class Blob(value: ByteArray) : StreamingSqliteCell {
        val value = value.copyOf()

        override fun equals(other: Any?): Boolean =
            other is Blob && value.contentEquals(other.value)

        override fun hashCode(): Int = value.contentHashCode()
    }
}

internal object RecoveryStreamingSqlCodec {
    val CHECKPOINT_COLUMNS =
        arrayOf(
            "run_id",
            "candidate_id",
            "publication_kind",
            "generation",
            "durable_non_final_segment_count",
            "stream_ciphertext_prefix_bytes",
            "stream_ciphertext_prefix_sha256",
            "committed_end",
            "checkpoint_relative_name",
            "checkpoint_bytes",
            "checkpoint_sha256",
            "checkpoint_key_envelope_relative_name",
            "checkpoint_key_envelope_bytes",
            "checkpoint_key_envelope_sha256",
            "stream_ciphertext_relative_name",
            "stream_key_envelope_relative_name",
            "stream_key_envelope_bytes",
            "stream_key_envelope_sha256",
            "previous_checkpoint_sha256",
            "checkpoint_identity",
            "state",
        )

    val OUTCOME_COLUMNS =
        arrayOf(
            "outcome_id",
            "run_id",
            "candidate_id",
            "checkpoint_generation",
            "checkpoint_identity",
            "checkpoint_context_end",
            "checkpoint_prefix_bytes",
            "checkpoint_artifact_state",
            "source_witness_id",
            "witness_capability_state",
            "controller_snapshot_sha256",
            "oracle_identity_sha256",
            "oracle_plaintext_sha256",
            "accepted_end",
            "source_relative_name",
            "pre_fault_source_bytes",
            "pre_fault_source_sha256",
            "observed_source_bytes",
            "observed_source_sha256",
            "pre_fault_source_match_state",
            "checkpoint_intersection_state",
            "decision",
            "diagnostic_branch",
            "terminal_outcome",
            "recovered_end",
            "recovered_beyond_checkpoint_bytes",
            "tail_loss_bytes",
            "returned_plaintext_sha256",
            "remainder_boundary_bytes",
            "remainder_certainty",
            "rejected_candidate_end",
            "rejected_completed_plaintext_sha256",
            "rejected_oracle_prefix_sha256",
            "rejected_oracle_prefix_equal",
            "rejected_compared_end",
            "rejected_first_mismatch_offset",
            "rejected_equal_prefix_sha256",
            "rejected_expected_oracle_byte",
            "rejected_observed_plaintext_byte",
            "rejected_observed_tail_loss_bytes",
            "rejected_boundary_result",
            "rejected_boundary_bytes",
            "rejected_observation_sha256",
            "required_range_start",
            "required_range_certainty",
            "diagnostic_stage",
            "diagnostic_classification",
            "metadata_adopted",
            "semantic_commit_adopted",
            "processing_intent_adopted",
            "state",
        )

    val RANGE_COLUMNS =
        arrayOf(
            "range_intent_id",
            "outcome_id",
            "run_id",
            "candidate_id",
            "outcome_decision",
            "outcome_branch",
            "outcome_terminal",
            "outcome_classification",
            "source_relative_name",
            "source_bytes",
            "source_sha256",
            "range_start",
            "range_end",
            "range_sha256",
            "boundary_certainty",
            "disposition",
            "state",
        )

    fun encodeCheckpoint(row: RecoveryStreamingCheckpointRow): List<StreamingSqliteCell> =
        listOf(
            text(row.runId.toCanonicalString()),
            text(row.candidateId),
            text(row.publicationKind),
            integer(row.generation),
            integer(row.durableNonFinalSegmentCount),
            integer(row.streamCiphertextPrefixBytes),
            blob(row.streamCiphertextPrefixSha256),
            integer(row.committedEnd),
            text(row.checkpointRelativeName),
            integer(row.checkpointBytes),
            blob(row.checkpointSha256),
            text(row.checkpointKeyEnvelopeRelativeName),
            integer(row.checkpointKeyEnvelopeBytes),
            blob(row.checkpointKeyEnvelopeSha256),
            text(row.streamCiphertextRelativeName),
            text(row.streamKeyEnvelopeRelativeName),
            integer(row.streamKeyEnvelopeBytes),
            blob(row.streamKeyEnvelopeSha256),
            blob(row.previousCheckpointSha256),
            blob(row.checkpointIdentity),
            text(row.state),
        )

    fun encodeOutcome(row: RecoveryStreamingOutcomeRow): List<StreamingSqliteCell> {
        val rejected = row.rejectedObservation
        return listOf(
            blob(row.outcomeId),
            text(row.runId.toCanonicalString()),
            text(row.candidateId),
            integer(row.checkpointGeneration),
            blob(row.checkpointIdentity),
            integer(row.checkpointContextEnd),
            integer(row.checkpointPrefixBytes),
            text(row.checkpointArtifactState),
            blob(row.sourceWitnessId),
            text(row.witnessCapabilityState),
            blob(row.controllerSnapshotSha256),
            blob(row.oracleIdentitySha256),
            blob(row.oraclePlaintextSha256),
            integer(row.acceptedEnd),
            text(row.sourceRelativeName),
            integer(row.preFaultSourceBytes),
            blob(row.preFaultSourceSha256),
            integer(row.observedSourceBytes),
            blob(row.observedSourceSha256),
            text(row.preFaultSourceMatch.name),
            text(row.checkpointIntersection.name),
            text(row.decision.name),
            text(row.diagnosticBranch.name),
            text(row.terminal.name),
            nullableInteger(row.recoveredEnd),
            nullableInteger(row.recoveredBeyondCheckpointBytes),
            nullableInteger(row.tailLossBytes),
            nullableBlob(row.returnedPlaintextSha256),
            nullableInteger(row.remainderBoundaryBytes),
            nullableText(row.remainderCertainty?.name),
            nullableInteger(rejected?.candidateEnd),
            nullableBlob(rejected?.completedPlaintextSha256),
            nullableBlob(rejected?.oraclePrefixSha256),
            rejected?.oraclePrefixEqual?.let { StreamingSqliteCell.Integer(if (it) 1 else 0) }
                ?: StreamingSqliteCell.Null,
            nullableInteger(rejected?.comparedEnd),
            nullableInteger(rejected?.firstMismatchOffset),
            nullableBlob(rejected?.equalPrefixSha256),
            rejected?.expectedOracleByte?.let { StreamingSqliteCell.Integer(it.toLong()) }
                ?: StreamingSqliteCell.Null,
            rejected?.observedPlaintextByte?.let { StreamingSqliteCell.Integer(it.toLong()) }
                ?: StreamingSqliteCell.Null,
            nullableInteger(rejected?.observedTailLossBytes),
            nullableText(rejected?.boundaryResult?.name),
            nullableInteger(rejected?.boundaryBytes),
            nullableBlob(row.rejectedObservationSha256),
            nullableInteger(row.requiredRangeStart),
            nullableText(row.requiredRangeCertainty?.name),
            text(row.diagnosticStage.name),
            text(row.diagnosticClassification.name),
            StreamingSqliteCell.Integer(if (row.metadataAdopted) 1 else 0),
            StreamingSqliteCell.Integer(if (row.semanticCommitAdopted) 1 else 0),
            StreamingSqliteCell.Integer(if (row.processingIntentAdopted) 1 else 0),
            text(row.state),
        )
    }

    fun encodeRange(row: RecoveryStreamingRangeRow): List<StreamingSqliteCell> =
        listOf(
            blob(row.rangeIntentId),
            blob(row.outcomeId),
            text(row.runId.toCanonicalString()),
            text(row.candidateId),
            text(row.decision.name),
            text(row.diagnosticBranch.name),
            text(row.terminal.name),
            text(row.classification.name),
            text(row.sourceRelativeName),
            integer(row.sourceBytes),
            blob(row.sourceSha256),
            integer(row.rangeStart),
            integer(row.rangeEnd),
            blob(row.rangeSha256),
            text(row.certainty.name),
            text(row.disposition),
            text(row.state),
        )

    fun decodeCheckpoint(cells: List<StreamingSqliteCell>): RecoveryStreamingCheckpointRow {
        val row = CellReader(cells, CHECKPOINT_COLUMNS)
        return RecoveryStreamingCheckpointRow(
            runId = row.runId(0),
            candidateId = row.ascii(1, 64),
            publicationKind = row.ascii(2, 64),
            generation = row.uLong(3),
            durableNonFinalSegmentCount = row.uLong(4),
            streamCiphertextPrefixBytes = row.uLong(5),
            streamCiphertextPrefixSha256 = row.sha256(6),
            committedEnd = row.uLong(7),
            checkpointRelativeName = row.ascii(8, 512),
            checkpointBytes = row.uLong(9),
            checkpointSha256 = row.sha256(10),
            checkpointKeyEnvelopeRelativeName = row.ascii(11, 512),
            checkpointKeyEnvelopeBytes = row.uLong(12),
            checkpointKeyEnvelopeSha256 = row.sha256(13),
            streamCiphertextRelativeName = row.ascii(14, 512),
            streamKeyEnvelopeRelativeName = row.ascii(15, 512),
            streamKeyEnvelopeBytes = row.uLong(16),
            streamKeyEnvelopeSha256 = row.sha256(17),
            previousCheckpointSha256 = row.sha256(18),
            checkpointIdentity = row.sha256(19),
            state = row.ascii(20, 64),
        )
    }

    fun decodeOutcome(cells: List<StreamingSqliteCell>): RecoveryStreamingOutcomeRow {
        val row = CellReader(cells, OUTCOME_COLUMNS)
        val rejected = decodeRejected(row)
        return RecoveryStreamingOutcomeRow(
            outcomeId = row.sha256(0),
            runId = row.runId(1),
            candidateId = row.ascii(2, 64),
            checkpointGeneration = row.uLong(3),
            checkpointIdentity = row.sha256(4),
            checkpointContextEnd = row.uLong(5),
            checkpointPrefixBytes = row.uLong(6),
            checkpointArtifactState = row.ascii(7, 64),
            sourceWitnessId = row.sha256(8),
            witnessCapabilityState = row.ascii(9, 64),
            controllerSnapshotSha256 = row.sha256(10),
            oracleIdentitySha256 = row.sha256(11),
            oraclePlaintextSha256 = row.sha256(12),
            acceptedEnd = row.uLong(13),
            sourceRelativeName = row.ascii(14, 512),
            preFaultSourceBytes = row.uLong(15),
            preFaultSourceSha256 = row.sha256(16),
            observedSourceBytes = row.uLong(17),
            observedSourceSha256 = row.sha256(18),
            preFaultSourceMatch = row.enum(19),
            checkpointIntersection = row.enum(20),
            decision = row.enum(21),
            diagnosticBranch = row.enum(22),
            terminal = row.enum(23),
            recoveredEnd = row.nullableULong(24),
            recoveredBeyondCheckpointBytes = row.nullableULong(25),
            tailLossBytes = row.nullableULong(26),
            returnedPlaintextSha256 = row.nullableSha256(27),
            remainderBoundaryBytes = row.nullableULong(28),
            remainderCertainty = row.nullableEnum<StreamRangeCertainty>(29),
            rejectedObservation = rejected,
            rejectedObservationSha256 = row.nullableSha256(42),
            requiredRangeStart = row.nullableULong(43),
            requiredRangeCertainty = row.nullableEnum<StreamRangeCertainty>(44),
            diagnosticStage = row.enum(45),
            diagnosticClassification = row.enum(46),
            metadataAdopted = row.boolean(47),
            semanticCommitAdopted = row.boolean(48),
            processingIntentAdopted = row.boolean(49),
            state = row.ascii(50, 64),
        )
    }

    fun decodeRange(cells: List<StreamingSqliteCell>): RecoveryStreamingRangeRow {
        val row = CellReader(cells, RANGE_COLUMNS)
        return RecoveryStreamingRangeRow(
            rangeIntentId = row.sha256(0),
            outcomeId = row.sha256(1),
            runId = row.runId(2),
            candidateId = row.ascii(3, 64),
            decision = row.enum(4),
            diagnosticBranch = row.enum(5),
            terminal = row.enum(6),
            classification = row.enum(7),
            sourceRelativeName = row.ascii(8, 512),
            sourceBytes = row.uLong(9),
            sourceSha256 = row.sha256(10),
            rangeStart = row.uLong(11),
            rangeEnd = row.uLong(12),
            rangeSha256 = row.sha256(13),
            certainty = row.enum(14),
            disposition = row.ascii(15, 64),
            state = row.ascii(16, 64),
        )
    }

    fun validateParentChild(
        outcome: RecoveryStreamingOutcomeRow,
        range: RecoveryStreamingRangeRow,
    ) = RecoveryStreamingRowValidation.validateParentChild(outcome, range)

    fun sameCheckpoint(
        left: RecoveryStreamingCheckpointRow,
        right: RecoveryStreamingCheckpointRow,
    ) = sameCells(encodeCheckpoint(left), encodeCheckpoint(right))

    fun sameOutcome(left: RecoveryStreamingOutcomeRow, right: RecoveryStreamingOutcomeRow) =
        sameCells(encodeOutcome(left), encodeOutcome(right))

    fun sameRange(left: RecoveryStreamingRangeRow, right: RecoveryStreamingRangeRow) =
        sameCells(encodeRange(left), encodeRange(right))

    private fun decodeRejected(row: CellReader): RecoveryStreamingRejectedObservationInput? {
        val requiredIndexes = listOf(30, 31, 32, 33, 34, 39, 40)
        val optionalIndexes = listOf(35, 36, 37, 38, 41)
        if (row.isNull(30)) {
            require((requiredIndexes + optionalIndexes).all(row::isNull)) {
                "Rejected observation is partially null"
            }
            return null
        }
        require(requiredIndexes.none(row::isNull)) { "Rejected observation is partially null" }
        return RecoveryStreamingRejectedObservationInput(
            candidateEnd = requireNotNull(row.nullableULong(30)),
            completedPlaintextSha256 = requireNotNull(row.nullableSha256(31)),
            oraclePrefixSha256 = requireNotNull(row.nullableSha256(32)),
            oraclePrefixEqual = requireNotNull(row.nullableBoolean(33)),
            comparedEnd = requireNotNull(row.nullableULong(34)),
            firstMismatchOffset = row.nullableULong(35),
            equalPrefixSha256 = row.nullableSha256(36),
            expectedOracleByte = row.nullableUByte(37),
            observedPlaintextByte = row.nullableUByte(38),
            observedTailLossBytes = requireNotNull(row.nullableULong(39)),
            boundaryResult = requireNotNull(row.nullableEnum<StreamBoundaryResult>(40)),
            boundaryBytes = row.nullableULong(41),
        )
    }

    private fun sameCells(
        left: List<StreamingSqliteCell>,
        right: List<StreamingSqliteCell>,
    ): Boolean = left.size == right.size && left.indices.all { left[it] == right[it] }

    private fun integer(value: ULong): StreamingSqliteCell.Integer {
        require(value <= Long.MAX_VALUE.toULong())
        return StreamingSqliteCell.Integer(value.toLong())
    }

    private fun nullableInteger(value: ULong?) = value?.let(::integer) ?: StreamingSqliteCell.Null

    private fun text(value: String) = StreamingSqliteCell.Text(value)

    private fun nullableText(value: String?) = value?.let(::text) ?: StreamingSqliteCell.Null

    private fun blob(value: Sha256Value) = StreamingSqliteCell.Blob(value.toByteArray())

    private fun nullableBlob(value: Sha256Value?) = value?.let(::blob) ?: StreamingSqliteCell.Null

    private class CellReader(
        private val cells: List<StreamingSqliteCell>,
        columns: Array<String>,
    ) {
        init {
            require(cells.size == columns.size) { "SQLite projection cardinality is invalid" }
        }

        fun isNull(index: Int) = cells[index] is StreamingSqliteCell.Null

        fun uLong(index: Int): ULong {
            val value =
                (cells[index] as? StreamingSqliteCell.Integer)?.value
                    ?: throw IllegalArgumentException("Column $index is not INTEGER")
            require(value >= 0) { "Column $index is negative" }
            return value.toULong()
        }

        fun nullableULong(index: Int): ULong? = if (isNull(index)) null else uLong(index)

        fun ascii(index: Int, maximum: Int): String {
            val value =
                (cells[index] as? StreamingSqliteCell.Text)?.value
                    ?: throw IllegalArgumentException("Column $index is not TEXT")
            require(value.length <= maximum && value.all { it.code in 1..0x7f }) {
                "Column $index is not bounded ASCII"
            }
            return value
        }

        fun runId(index: Int): RunId = RunId.fromCanonicalString(ascii(index, 64))

        fun sha256(index: Int): Sha256Value {
            val value =
                (cells[index] as? StreamingSqliteCell.Blob)?.value
                    ?: throw IllegalArgumentException("Column $index is not BLOB")
            require(value.size == Sha256Value.SIZE_BYTES) { "Column $index is not SHA-256" }
            return Sha256Value.fromBytes(value)
        }

        fun nullableSha256(index: Int): Sha256Value? = if (isNull(index)) null else sha256(index)

        fun boolean(index: Int): Boolean =
            requireNotNull(nullableBoolean(index)) { "Column $index is NULL" }

        fun nullableBoolean(index: Int): Boolean? =
            nullableULong(index)?.let {
                require(it <= 1UL) { "Column $index is not Boolean" }
                it == 1UL
            }

        fun nullableUByte(index: Int): UByte? =
            nullableULong(index)?.let {
                require(it <= UByte.MAX_VALUE.toULong()) { "Column $index is not U8" }
                it.toUByte()
            }

        inline fun <reified T : Enum<T>> enum(index: Int): T {
            val value = ascii(index, 64)
            return enumValues<T>().singleOrNull { it.name == value }
                ?: throw IllegalArgumentException("Column $index has unknown enum value")
        }

        inline fun <reified T : Enum<T>> nullableEnum(index: Int): T? =
            if (isNull(index)) null else enum<T>(index)
    }
}

internal interface RecoveryStreamingJournalDatabase {
    fun checkpoints(runId: RunId): List<RecoveryStreamingCheckpointRow>

    fun outcomeById(id: Sha256Value): List<RecoveryStreamingOutcomeRow>

    fun outcomesByWitness(
        runId: RunId,
        checkpointIdentity: Sha256Value,
        witnessId: Sha256Value,
    ): List<RecoveryStreamingOutcomeRow>

    fun rangesByOutcome(outcomeId: Sha256Value): List<RecoveryStreamingRangeRow>

    fun activeRanges(runId: RunId, source: String): List<RecoveryStreamingRangeRow>

    fun rangeByIntentId(id: Sha256Value): List<RecoveryStreamingRangeRow>

    fun rangesBySourceTuple(row: RecoveryStreamingRangeRow): List<RecoveryStreamingRangeRow>

    fun beginTransactionNonExclusive(): RecoveryStreamingJournalTransaction
}

internal interface RecoveryStreamingJournalTransaction {
    val provenRolledBack: Boolean

    fun insertCheckpoint(row: RecoveryStreamingCheckpointRow)

    fun insertOutcome(row: RecoveryStreamingOutcomeRow)

    fun insertRange(row: RecoveryStreamingRangeRow)

    fun setSuccessful()

    fun end()
}

private class AndroidSqliteRecoveryStreamingJournalDatabase(private val database: SQLiteDatabase) :
    RecoveryStreamingJournalDatabase {
    override fun checkpoints(runId: RunId): List<RecoveryStreamingCheckpointRow> =
        query(
            RecoveryJournalSchema.STREAM_CHECKPOINT_TABLE,
            RecoveryStreamingSqlCodec.CHECKPOINT_COLUMNS,
            "run_id=? AND candidate_id=?",
            arrayOf(runId.toCanonicalString(), CANDIDATE),
            "generation ASC",
            RecoveryStreamingSqlCodec::decodeCheckpoint,
        )

    override fun outcomeById(id: Sha256Value): List<RecoveryStreamingOutcomeRow> =
        query(
            RecoveryJournalSchema.STREAM_OUTCOME_TABLE,
            RecoveryStreamingSqlCodec.OUTCOME_COLUMNS,
            "hex(outcome_id)=?",
            arrayOf(id.sqliteHex()),
            null,
            RecoveryStreamingSqlCodec::decodeOutcome,
        )

    override fun outcomesByWitness(
        runId: RunId,
        checkpointIdentity: Sha256Value,
        witnessId: Sha256Value,
    ): List<RecoveryStreamingOutcomeRow> =
        query(
            RecoveryJournalSchema.STREAM_OUTCOME_TABLE,
            RecoveryStreamingSqlCodec.OUTCOME_COLUMNS,
            "run_id=? AND candidate_id=? AND hex(checkpoint_identity)=? AND hex(source_witness_id)=?",
            arrayOf(
                runId.toCanonicalString(),
                CANDIDATE,
                checkpointIdentity.sqliteHex(),
                witnessId.sqliteHex(),
            ),
            null,
            RecoveryStreamingSqlCodec::decodeOutcome,
        )

    override fun rangesByOutcome(outcomeId: Sha256Value): List<RecoveryStreamingRangeRow> =
        query(
            RecoveryJournalSchema.STREAM_RANGE_TABLE,
            RecoveryStreamingSqlCodec.RANGE_COLUMNS,
            "hex(outcome_id)=?",
            arrayOf(outcomeId.sqliteHex()),
            null,
            RecoveryStreamingSqlCodec::decodeRange,
        )

    override fun activeRanges(runId: RunId, source: String): List<RecoveryStreamingRangeRow> =
        query(
            RecoveryJournalSchema.STREAM_RANGE_TABLE,
            RecoveryStreamingSqlCodec.RANGE_COLUMNS,
            "run_id=? AND candidate_id=? AND source_relative_name=? AND state='ACTIVE'",
            arrayOf(runId.toCanonicalString(), CANDIDATE, source),
            "range_start ASC,range_end ASC,range_intent_id ASC",
            RecoveryStreamingSqlCodec::decodeRange,
        )

    override fun rangeByIntentId(id: Sha256Value): List<RecoveryStreamingRangeRow> =
        query(
            RecoveryJournalSchema.STREAM_RANGE_TABLE,
            RecoveryStreamingSqlCodec.RANGE_COLUMNS,
            "hex(range_intent_id)=?",
            arrayOf(id.sqliteHex()),
            null,
            RecoveryStreamingSqlCodec::decodeRange,
        )

    override fun rangesBySourceTuple(
        row: RecoveryStreamingRangeRow
    ): List<RecoveryStreamingRangeRow> =
        query(
            RecoveryJournalSchema.STREAM_RANGE_TABLE,
            RecoveryStreamingSqlCodec.RANGE_COLUMNS,
            "run_id=? AND candidate_id=? AND source_relative_name=? AND source_bytes=? AND " +
                "hex(source_sha256)=? AND range_start=? AND range_end=? AND hex(range_sha256)=?",
            arrayOf(
                row.runId.toCanonicalString(),
                row.candidateId,
                row.sourceRelativeName,
                row.sourceBytes.toString(),
                row.sourceSha256.sqliteHex(),
                row.rangeStart.toString(),
                row.rangeEnd.toString(),
                row.rangeSha256.sqliteHex(),
            ),
            null,
            RecoveryStreamingSqlCodec::decodeRange,
        )

    override fun beginTransactionNonExclusive(): RecoveryStreamingJournalTransaction {
        database.beginTransactionNonExclusive()
        return AndroidTransaction(database)
    }

    private fun <T> query(
        table: String,
        columns: Array<String>,
        selection: String,
        selectionArgs: Array<String>,
        orderBy: String?,
        decode: (List<StreamingSqliteCell>) -> T,
    ): List<T> =
        database.query(table, columns, selection, selectionArgs, null, null, orderBy).use { cursor
            ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        decodeStreamingJournalRow {
                            decode(cursor.strictCells(columns))
                        }
                    )
                }
            }
        }

    private class AndroidTransaction(private val database: SQLiteDatabase) :
        RecoveryStreamingJournalTransaction {
        private var markedSuccessful = false
        private var ended = false
        private var endedNormally = false

        override val provenRolledBack: Boolean
            get() = endedNormally && !markedSuccessful

        override fun insertCheckpoint(row: RecoveryStreamingCheckpointRow) {
            database.insertOrThrow(
                RecoveryJournalSchema.STREAM_CHECKPOINT_TABLE,
                null,
                contentValues(
                    RecoveryStreamingSqlCodec.CHECKPOINT_COLUMNS,
                    RecoveryStreamingSqlCodec.encodeCheckpoint(row),
                ),
            )
        }

        override fun insertOutcome(row: RecoveryStreamingOutcomeRow) {
            database.insertOrThrow(
                RecoveryJournalSchema.STREAM_OUTCOME_TABLE,
                null,
                contentValues(
                    RecoveryStreamingSqlCodec.OUTCOME_COLUMNS,
                    RecoveryStreamingSqlCodec.encodeOutcome(row),
                ),
            )
        }

        override fun insertRange(row: RecoveryStreamingRangeRow) {
            database.insertOrThrow(
                RecoveryJournalSchema.STREAM_RANGE_TABLE,
                null,
                contentValues(
                    RecoveryStreamingSqlCodec.RANGE_COLUMNS,
                    RecoveryStreamingSqlCodec.encodeRange(row),
                ),
            )
        }

        override fun setSuccessful() {
            database.setTransactionSuccessful()
            markedSuccessful = true
        }

        override fun end() {
            check(!ended) { "Streaming journal transaction ended twice" }
            ended = true
            database.endTransaction()
            endedNormally = true
        }
    }

    companion object {
        private const val CANDIDATE = "REC-STREAM-TINK"

        private fun contentValues(
            columns: Array<String>,
            cells: List<StreamingSqliteCell>,
        ): ContentValues {
            require(columns.size == cells.size)
            return ContentValues(columns.size).apply {
                columns.indices.forEach { index ->
                    when (val cell = cells[index]) {
                        StreamingSqliteCell.Null -> putNull(columns[index])
                        is StreamingSqliteCell.Integer -> put(columns[index], cell.value)
                        is StreamingSqliteCell.Real -> put(columns[index], cell.value)
                        is StreamingSqliteCell.Text -> put(columns[index], cell.value)
                        is StreamingSqliteCell.Blob -> put(columns[index], cell.value.copyOf())
                    }
                }
            }
        }
    }
}

internal fun <T> decodeStreamingJournalRow(decode: () -> T): T =
    try {
        decode()
    } catch (failure: IllegalArgumentException) {
        throw JournalStructuralException(failure)
    }

private class JournalStructuralException(cause: Throwable) : IllegalStateException(cause) {
    val classification = RecoveryStreamingJournalClassification.JOURNAL_STRUCTURAL
}

private fun Sha256Value.sqliteHex(): String = toLowercaseHex().uppercase(Locale.ROOT)

private fun Cursor.strictCells(columns: Array<String>): List<StreamingSqliteCell> {
    require(columnCount == columns.size) { "Cursor projection cardinality is invalid" }
    return columns.indices.map { index ->
        require(getColumnName(index) == columns[index]) { "Cursor projection order is invalid" }
        when (getType(index)) {
            Cursor.FIELD_TYPE_NULL -> StreamingSqliteCell.Null
            Cursor.FIELD_TYPE_INTEGER -> StreamingSqliteCell.Integer(getLong(index))
            Cursor.FIELD_TYPE_FLOAT -> StreamingSqliteCell.Real(getDouble(index))
            Cursor.FIELD_TYPE_STRING -> StreamingSqliteCell.Text(getString(index))
            Cursor.FIELD_TYPE_BLOB -> StreamingSqliteCell.Blob(getBlob(index))
            else -> throw IllegalArgumentException("Unsupported SQLite cell type")
        }
    }
}

internal class AndroidRecoveryStreamingJournal(
    private val database: RecoveryStreamingJournalDatabase
) : RecoveryStreamingJournal {
    constructor(
        context: Context
    ) : this(
        AndroidSqliteRecoveryStreamingJournalDatabase(
            AndroidRecoveryJournalDatabase.writable(context.applicationContext)
        )
    )

    override fun checkpointChain(
        runId: RunId
    ): RecoveryStreamingJournalReadResult<List<RecoveryStreamingCheckpointRow>> =
        try {
            validateCheckpointChain(database.checkpoints(runId), runId)
        } catch (_: JournalStructuralException) {
            readFatal(RecoveryStreamingJournalClassification.JOURNAL_STRUCTURAL)
        } catch (_: Exception) {
            RecoveryStreamingJournalReadResult.Retry(
                RecoveryStreamingJournalClassification.JOURNAL_OPERATIONAL
            )
        }

    override fun outcomeById(
        outcomeId: Sha256Value
    ): RecoveryStreamingJournalReadResult<RecoveryStreamingOutcomeRow?> =
        try {
            guardedUniqueRead(database.outcomeById(outcomeId)) {
                RecoveryStreamingExistingEvidence.Outcome(it.outcomeId)
            }
        } catch (_: JournalStructuralException) {
            readFatal(RecoveryStreamingJournalClassification.JOURNAL_STRUCTURAL)
        } catch (_: Exception) {
            RecoveryStreamingJournalReadResult.Retry(
                RecoveryStreamingJournalClassification.JOURNAL_OPERATIONAL
            )
        }

    override fun outcomeByWitness(
        runId: RunId,
        checkpointIdentity: Sha256Value,
        witnessId: Sha256Value,
    ): RecoveryStreamingJournalReadResult<RecoveryStreamingOutcomeRow?> =
        try {
            guardedUniqueRead(database.outcomesByWitness(runId, checkpointIdentity, witnessId)) {
                RecoveryStreamingExistingEvidence.Outcome(it.outcomeId)
            }
        } catch (_: JournalStructuralException) {
            readFatal(RecoveryStreamingJournalClassification.JOURNAL_STRUCTURAL)
        } catch (_: Exception) {
            RecoveryStreamingJournalReadResult.Retry(
                RecoveryStreamingJournalClassification.JOURNAL_OPERATIONAL
            )
        }

    override fun rangeByOutcome(
        outcomeId: Sha256Value
    ): RecoveryStreamingJournalReadResult<RecoveryStreamingRangeRow?> =
        try {
            guardedUniqueRead(database.rangesByOutcome(outcomeId)) {
                RecoveryStreamingExistingEvidence.Range(it.rangeIntentId)
            }
        } catch (_: JournalStructuralException) {
            readFatal(RecoveryStreamingJournalClassification.JOURNAL_STRUCTURAL)
        } catch (_: Exception) {
            RecoveryStreamingJournalReadResult.Retry(
                RecoveryStreamingJournalClassification.JOURNAL_OPERATIONAL
            )
        }

    override fun activeRanges(
        runId: RunId,
        sourceRelativeName: String,
    ): RecoveryStreamingJournalReadResult<List<RecoveryStreamingRangeRow>> {
        if (sourceRelativeName != STREAM_SOURCE) {
            return readFatal(RecoveryStreamingJournalClassification.JOURNAL_STRUCTURAL)
        }
        return try {
            val rows = database.activeRanges(runId, sourceRelativeName)
            if (rows.any { it.runId != runId || it.sourceRelativeName != sourceRelativeName }) {
                readFatal(
                    RecoveryStreamingJournalClassification.JOURNAL_STRUCTURAL,
                    rows.map { RecoveryStreamingExistingEvidence.Range(it.rangeIntentId) },
                )
            } else {
                RecoveryStreamingJournalReadResult.Value(
                    rows
                        .sortedWith(
                            compareBy<RecoveryStreamingRangeRow> { it.rangeStart }
                                .thenBy { it.rangeEnd }
                                .thenBy { it.rangeIntentId.toLowercaseHex() }
                        )
                        .toList()
                )
            }
        } catch (_: JournalStructuralException) {
            readFatal(RecoveryStreamingJournalClassification.JOURNAL_STRUCTURAL)
        } catch (_: Exception) {
            RecoveryStreamingJournalReadResult.Retry(
                RecoveryStreamingJournalClassification.JOURNAL_OPERATIONAL
            )
        }
    }

    override fun insertCheckpoint(
        row: RecoveryStreamingCheckpointRow
    ): RecoveryStreamingJournalResult {
        val existing =
            when (val read = checkpointChain(row.runId)) {
                is RecoveryStreamingJournalReadResult.Value -> read.value
                is RecoveryStreamingJournalReadResult.Fatal ->
                    return RecoveryStreamingJournalResult.Fatal(
                        read.classification,
                        read.existingEvidence,
                    )
                is RecoveryStreamingJournalReadResult.Retry ->
                    return RecoveryStreamingJournalResult.Retry(read.classification)
            }
        val sameGeneration = existing.filter { it.generation == row.generation }
        if (sameGeneration.isNotEmpty()) {
            return if (
                sameGeneration.size == 1 &&
                    RecoveryStreamingSqlCodec.sameCheckpoint(sameGeneration.single(), row)
            ) {
                RecoveryStreamingJournalResult.CheckpointReceipt(row.checkpointIdentity, true)
            } else {
                fatalCheckpoints(sameGeneration)
            }
        }
        if (!isValidProposedCheckpoint(existing, row)) {
            return fatal(
                RecoveryStreamingJournalClassification.JOURNAL_STRUCTURAL,
                existing.map {
                    RecoveryStreamingExistingEvidence.Checkpoint(it.checkpointIdentity)
                },
            )
        }
        val transaction =
            try {
                database.beginTransactionNonExclusive()
            } catch (_: Exception) {
                return retry(RecoveryStreamingJournalClassification.JOURNAL_OPERATIONAL)
            }
        var primaryFailure: Throwable? = null
        try {
            transaction.insertCheckpoint(row)
            transaction.setSuccessful()
        } catch (failure: Throwable) {
            primaryFailure = failure
        }
        try {
            transaction.end()
        } catch (endFailure: Throwable) {
            if (primaryFailure == null) primaryFailure = endFailure
            else primaryFailure.addSuppressed(endFailure)
        }
        return resolveCheckpoint(row, transaction, primaryFailure != null)
    }

    override fun persistOutcome(
        attempt: RecoveryStreamingOutcomeAttempt
    ): RecoveryStreamingJournalResult {
        val preflight =
            try {
                reconcile(attempt)
            } catch (failure: JournalStructuralException) {
                return fatal(failure.classification)
            } catch (_: Exception) {
                return retry(
                    RecoveryStreamingJournalClassification.JOURNAL_OPERATIONAL,
                    attempt,
                )
            }
        preflight.toResult(attempt, replayed = true)?.let {
            return it
        }
        val transaction =
            try {
                database.beginTransactionNonExclusive()
            } catch (_: Exception) {
                return retry(
                    RecoveryStreamingJournalClassification.JOURNAL_OPERATIONAL,
                    attempt,
                )
            }
        var primaryFailure: Throwable? = null
        try {
            transaction.insertOutcome(attempt.outcome)
            attempt.range?.let(transaction::insertRange)
            transaction.setSuccessful()
        } catch (failure: Throwable) {
            primaryFailure = failure
        }
        try {
            transaction.end()
        } catch (endFailure: Throwable) {
            if (primaryFailure == null) primaryFailure = endFailure
            else primaryFailure.addSuppressed(endFailure)
        }
        return if (primaryFailure == null) {
            readbackAfterSuccessfulEnd(attempt)
        } else {
            reconcileAfterFailure(attempt, transaction)
        }
    }

    private fun readbackAfterSuccessfulEnd(
        attempt: RecoveryStreamingOutcomeAttempt
    ): RecoveryStreamingJournalResult =
        try {
            when (val state = reconcile(attempt)) {
                is Reconciliation.Exact -> receipt(attempt, replayed = false)
                is Reconciliation.Fatal -> state.result
                Reconciliation.Absent ->
                    fatal(RecoveryStreamingJournalClassification.JOURNAL_STRUCTURAL)
            }
        } catch (failure: JournalStructuralException) {
            fatal(failure.classification)
        } catch (_: Exception) {
            retry(RecoveryStreamingJournalClassification.JOURNAL_OPERATIONAL, attempt)
        }

    private fun reconcileAfterFailure(
        attempt: RecoveryStreamingOutcomeAttempt,
        transaction: RecoveryStreamingJournalTransaction,
    ): RecoveryStreamingJournalResult =
        try {
            when (val state = reconcile(attempt)) {
                is Reconciliation.Exact -> receipt(attempt, replayed = true)
                is Reconciliation.Fatal -> state.result
                Reconciliation.Absent ->
                    if (transaction.provenRolledBack) {
                        RecoveryStreamingJournalResult.Original(attempt.semanticOutcome)
                    } else {
                        retry(
                            RecoveryStreamingJournalClassification.JOURNAL_COMMIT_STATE_UNRESOLVED,
                            attempt,
                        )
                    }
            }
        } catch (failure: JournalStructuralException) {
            fatal(failure.classification)
        } catch (_: Exception) {
            if (transaction.provenRolledBack) {
                retry(RecoveryStreamingJournalClassification.JOURNAL_OPERATIONAL, attempt)
            } else {
                retry(
                    RecoveryStreamingJournalClassification.JOURNAL_COMMIT_STATE_UNRESOLVED,
                    attempt,
                )
            }
        }

    private fun reconcile(attempt: RecoveryStreamingOutcomeAttempt): Reconciliation {
        val intended = attempt.outcome
        val byId = database.outcomeById(intended.outcomeId)
        val byWitness =
            database.outcomesByWitness(
                intended.runId,
                intended.checkpointIdentity,
                intended.sourceWitnessId,
            )
        val children = database.rangesByOutcome(intended.outcomeId)
        val expectedRange = attempt.range
        val byRangeId = expectedRange?.let { database.rangeByIntentId(it.rangeIntentId) }.orEmpty()
        val bySource = expectedRange?.let(database::rangesBySourceTuple).orEmpty()
        val outcomeEvidence =
            (byId + byWitness).map { RecoveryStreamingExistingEvidence.Outcome(it.outcomeId) }
        val rangeEvidence =
            (children + byRangeId + bySource).map {
                RecoveryStreamingExistingEvidence.Range(it.rangeIntentId)
            }
        if (
            byId.size > 1 ||
                byWitness.size > 1 ||
                children.size > 1 ||
                byRangeId.size > 1 ||
                bySource.size > 1
        ) {
            return Reconciliation.Fatal(
                fatal(
                    RecoveryStreamingJournalClassification.JOURNAL_STRUCTURAL,
                    outcomeEvidence + rangeEvidence,
                )
            )
        }
        val idRow = byId.singleOrNull()
        val witnessRow = byWitness.singleOrNull()
        if (idRow == null && witnessRow == null) {
            if (byRangeId.isNotEmpty() || bySource.isNotEmpty()) {
                return Reconciliation.Fatal(
                    fatal(
                        RecoveryStreamingJournalClassification.STREAM_RANGE_QUARANTINE_COLLISION,
                        rangeEvidence,
                    )
                )
            }
            if (children.isNotEmpty()) {
                return Reconciliation.Fatal(
                    fatal(RecoveryStreamingJournalClassification.JOURNAL_STRUCTURAL, rangeEvidence)
                )
            }
            return Reconciliation.Absent
        }
        if (idRow != null && witnessRow != null && idRow.outcomeId != witnessRow.outcomeId) {
            return Reconciliation.Fatal(
                fatal(RecoveryStreamingJournalClassification.JOURNAL_STRUCTURAL, outcomeEvidence)
            )
        }
        if (idRow != null && !RecoveryStreamingSqlCodec.sameOutcome(idRow, intended)) {
            return Reconciliation.Fatal(
                fatal(
                    RecoveryStreamingJournalClassification.JOURNAL_ATTEMPT_CONFLICT,
                    outcomeEvidence,
                )
            )
        }
        if (witnessRow != null && !RecoveryStreamingSqlCodec.sameOutcome(witnessRow, intended)) {
            return Reconciliation.Fatal(
                fatal(
                    RecoveryStreamingJournalClassification.JOURNAL_ATTEMPT_CONFLICT,
                    outcomeEvidence,
                )
            )
        }
        if (idRow == null || witnessRow == null) {
            return Reconciliation.Fatal(
                fatal(RecoveryStreamingJournalClassification.JOURNAL_STRUCTURAL, outcomeEvidence)
            )
        }
        if (expectedRange == null) {
            return if (children.isEmpty()) {
                Reconciliation.Exact
            } else {
                Reconciliation.Fatal(
                    fatal(
                        RecoveryStreamingJournalClassification.JOURNAL_STRUCTURAL,
                        outcomeEvidence + rangeEvidence,
                    )
                )
            }
        }
        val idRange = byRangeId.singleOrNull()
        val sourceRange = bySource.singleOrNull()
        if (
            (idRange != null && !RecoveryStreamingSqlCodec.sameRange(idRange, expectedRange)) ||
                (sourceRange != null &&
                    !RecoveryStreamingSqlCodec.sameRange(sourceRange, expectedRange))
        ) {
            return Reconciliation.Fatal(
                fatal(
                    RecoveryStreamingJournalClassification.STREAM_RANGE_QUARANTINE_COLLISION,
                    rangeEvidence,
                )
            )
        }
        val child =
            children.singleOrNull()
                ?: return Reconciliation.Fatal(
                    fatal(
                        RecoveryStreamingJournalClassification.JOURNAL_STRUCTURAL,
                        outcomeEvidence,
                    )
                )
        return try {
            RecoveryStreamingSqlCodec.validateParentChild(idRow, child)
            if (
                RecoveryStreamingSqlCodec.sameRange(child, expectedRange) &&
                    idRange != null &&
                    sourceRange != null
            ) {
                Reconciliation.Exact
            } else {
                Reconciliation.Fatal(
                    fatal(
                        RecoveryStreamingJournalClassification.JOURNAL_STRUCTURAL,
                        outcomeEvidence + rangeEvidence,
                    )
                )
            }
        } catch (_: IllegalArgumentException) {
            Reconciliation.Fatal(
                fatal(
                    RecoveryStreamingJournalClassification.JOURNAL_STRUCTURAL,
                    outcomeEvidence + rangeEvidence,
                )
            )
        }
    }

    private fun resolveCheckpoint(
        row: RecoveryStreamingCheckpointRow,
        transaction: RecoveryStreamingJournalTransaction,
        failed: Boolean,
    ): RecoveryStreamingJournalResult =
        try {
            val reread = database.checkpoints(row.runId).filter { it.generation == row.generation }
            when {
                reread.size == 1 &&
                    RecoveryStreamingSqlCodec.sameCheckpoint(reread.single(), row) ->
                    RecoveryStreamingJournalResult.CheckpointReceipt(row.checkpointIdentity, failed)
                reread.isNotEmpty() -> fatalCheckpoints(reread)
                failed && transaction.provenRolledBack ->
                    retry(RecoveryStreamingJournalClassification.JOURNAL_OPERATIONAL)
                failed ->
                    retry(RecoveryStreamingJournalClassification.JOURNAL_COMMIT_STATE_UNRESOLVED)
                else -> fatal(RecoveryStreamingJournalClassification.JOURNAL_STRUCTURAL)
            }
        } catch (_: JournalStructuralException) {
            fatal(RecoveryStreamingJournalClassification.JOURNAL_STRUCTURAL)
        } catch (_: Exception) {
            if (failed)
                retry(RecoveryStreamingJournalClassification.JOURNAL_COMMIT_STATE_UNRESOLVED)
            else retry(RecoveryStreamingJournalClassification.JOURNAL_OPERATIONAL)
        }

    private fun validateCheckpointChain(
        rows: List<RecoveryStreamingCheckpointRow>,
        runId: RunId,
    ): RecoveryStreamingJournalReadResult<List<RecoveryStreamingCheckpointRow>> {
        val ordered = rows.sortedBy { it.generation }
        if (ordered.any { it.runId != runId }) {
            return readFatal(
                RecoveryStreamingJournalClassification.JOURNAL_STRUCTURAL,
                checkpointEvidence(ordered),
            )
        }
        val duplicates = ordered.groupBy { it.generation }.values.filter { it.size > 1 }.flatten()
        if (duplicates.isNotEmpty()) {
            return readFatal(
                RecoveryStreamingJournalClassification.STREAM_CHECKPOINT_SPLIT_BRAIN,
                checkpointEvidence(duplicates),
            )
        }
        if (ordered.isEmpty()) return RecoveryStreamingJournalReadResult.Value(emptyList())
        if (
            ordered.first().generation != 1UL ||
                ordered.first().previousCheckpointSha256 != Sha256Value.ZERO
        ) {
            return readFatal(
                RecoveryStreamingJournalClassification.JOURNAL_STRUCTURAL,
                checkpointEvidence(ordered),
            )
        }
        ordered.zipWithNext().forEach { (previous, next) ->
            if (
                next.generation != previous.generation + 1UL ||
                    next.previousCheckpointSha256 != previous.checkpointSha256
            ) {
                return readFatal(
                    RecoveryStreamingJournalClassification.JOURNAL_STRUCTURAL,
                    checkpointEvidence(listOf(previous, next)),
                )
            }
        }
        return RecoveryStreamingJournalReadResult.Value(ordered.toList())
    }

    private fun isValidProposedCheckpoint(
        existing: List<RecoveryStreamingCheckpointRow>,
        row: RecoveryStreamingCheckpointRow,
    ): Boolean =
        if (existing.isEmpty()) {
            row.generation == 1UL && row.previousCheckpointSha256 == Sha256Value.ZERO
        } else {
            val previous = existing.last()
            row.generation == previous.generation + 1UL &&
                row.previousCheckpointSha256 == previous.checkpointSha256
        }

    private fun <T> guardedUniqueRead(
        rows: List<T>,
        evidence: (T) -> RecoveryStreamingExistingEvidence,
    ): RecoveryStreamingJournalReadResult<T?> =
        try {
            if (rows.size > 1) {
                readFatal(
                    RecoveryStreamingJournalClassification.JOURNAL_STRUCTURAL,
                    rows.map(evidence),
                )
            } else {
                RecoveryStreamingJournalReadResult.Value(rows.singleOrNull())
            }
        } catch (_: JournalStructuralException) {
            readFatal(RecoveryStreamingJournalClassification.JOURNAL_STRUCTURAL)
        } catch (_: Exception) {
            RecoveryStreamingJournalReadResult.Retry(
                RecoveryStreamingJournalClassification.JOURNAL_OPERATIONAL
            )
        }

    private fun Reconciliation.toResult(
        attempt: RecoveryStreamingOutcomeAttempt,
        replayed: Boolean,
    ): RecoveryStreamingJournalResult? =
        when (this) {
            Reconciliation.Absent -> null
            Reconciliation.Exact -> receipt(attempt, replayed)
            is Reconciliation.Fatal -> result
        }

    private fun receipt(
        attempt: RecoveryStreamingOutcomeAttempt,
        replayed: Boolean,
    ) =
        RecoveryStreamingJournalResult.Receipt(
            attempt.outcome.outcomeId,
            attempt.range?.rangeIntentId,
            replayed,
        )

    private fun retry(
        classification: RecoveryStreamingJournalClassification,
        attempt: RecoveryStreamingOutcomeAttempt? = null,
    ) =
        RecoveryStreamingJournalResult.Retry(
            classification,
            attempt?.outcome?.outcomeId,
            attempt?.range?.rangeIntentId,
        )

    private fun fatal(
        classification: RecoveryStreamingJournalClassification,
        evidence: List<RecoveryStreamingExistingEvidence> = emptyList(),
    ) = RecoveryStreamingJournalResult.Fatal(classification, evidence.distinct())

    private fun fatalCheckpoints(rows: List<RecoveryStreamingCheckpointRow>) =
        fatal(
            RecoveryStreamingJournalClassification.STREAM_CHECKPOINT_SPLIT_BRAIN,
            checkpointEvidence(rows),
        )

    private fun checkpointEvidence(rows: List<RecoveryStreamingCheckpointRow>) =
        rows.map { RecoveryStreamingExistingEvidence.Checkpoint(it.checkpointIdentity) }.distinct()

    private fun <T> readFatal(
        classification: RecoveryStreamingJournalClassification,
        evidence: List<RecoveryStreamingExistingEvidence> = emptyList(),
    ): RecoveryStreamingJournalReadResult<T> =
        RecoveryStreamingJournalReadResult.Fatal(classification, evidence.distinct())

    private sealed interface Reconciliation {
        data object Absent : Reconciliation

        data object Exact : Reconciliation

        data class Fatal(val result: RecoveryStreamingJournalResult.Fatal) : Reconciliation
    }

    companion object {
        private const val STREAM_SOURCE = "stream/stream.ct"
    }
}
