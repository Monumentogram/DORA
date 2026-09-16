@file:Suppress("MagicNumber", "LongMethod", "TooManyFunctions")

package com.monumentogram.dora.poc.recovery.candidate

import android.database.Cursor
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingCheckpointIdentityInput
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingCheckpointRow
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingIdentity
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingJournalClassification
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingJournalReadResult
import com.monumentogram.dora.poc.recovery.contract.RunId
import com.monumentogram.dora.poc.recovery.contract.Sha256Value
import com.monumentogram.dora.poc.recovery.journal.AndroidRecoveryStreamingJournal
import com.monumentogram.dora.poc.recovery.journal.RecoveryJournalSchema
import com.monumentogram.dora.poc.recovery.journal.RecoveryStreamingJournalDatabase
import com.monumentogram.dora.poc.recovery.journal.RecoveryStreamingSqlCodec
import com.monumentogram.dora.poc.recovery.journal.StreamingSqliteCell
import com.monumentogram.dora.poc.recovery.journal.decodeStreamingJournalRow
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryCampaignJournalRowsTest {
    @Test
    fun `valid checkpoint observation is stable without adopting a commit`() {
        val before = observe(listOf(checkpointCells()))
        val after = observe(listOf(checkpointCells()))
        assertEquals(1, before.rowCount)
        assertEquals(before.sha256, after.sha256)
        assertEquals(0, after.addedSince(before))
        assertEquals(0, before.addedSince(after))
    }

    @Test
    fun `filename corruption remains observable while production rejects it`() =
        assertCorruption(
            8,
            StreamingSqliteCell.Text("checkpoints/g-00000000000000000001.missing.ct"),
        )

    @Test
    fun `length corruption remains observable while production rejects it`() =
        assertCorruption(9, StreamingSqliteCell.Integer(2))

    @Test
    fun `digest corruption remains observable while production rejects it`() =
        assertCorruption(10, StreamingSqliteCell.Blob(ByteArray(32)))

    @Test
    fun `range corruption remains observable while production rejects it`() =
        assertCorruption(7, StreamingSqliteCell.Integer(4058))

    @Test
    fun `all SQLite storage classes and invalid digest lengths remain distinct`() {
        val values =
            listOf(
                StreamingSqliteCell.Null,
                StreamingSqliteCell.Integer(1),
                StreamingSqliteCell.Real(1.0),
                StreamingSqliteCell.Text("1"),
                StreamingSqliteCell.Blob(byteArrayOf(1)),
                StreamingSqliteCell.Blob(byteArrayOf(1, 0)),
            )
        val observations = values.map { observe(listOf(checkpointCells().replaced(10, it))) }
        assertEquals(6, observations.map { it.sha256 }.distinct().size)
        assertTrue(observations.all { it.rowCount == 1 })
    }

    @Test
    fun `all columns names and values participate in the observation`() {
        val base = checkpointCells()
        val before = observe(listOf(base))
        base.indices.forEach { column ->
            val changed =
                observe(listOf(base.replaced(column, StreamingSqliteCell.Text("altered"))))
            assertNotEquals("column $column", before.sha256, changed.sha256)
            assertEquals(1, changed.addedSince(before))
        }
        val columns = RecoveryStreamingSqlCodec.CHECKPOINT_COLUMNS.copyOf()
        columns[0] = "different_column"
        assertNotEquals(before.sha256, observe(listOf(base), columns).sha256)
    }

    @Test
    fun `row order is irrelevant but duplicate multiplicity and deletion are visible`() {
        val first = checkpointCells()
        val second = first.replaced(8, StreamingSqliteCell.Text("malformed"))
        val before = observe(listOf(first, second))
        val reordered = observe(listOf(second, first))
        val duplicate = observe(listOf(first, second, first))
        val removed = observe(listOf(first))
        assertEquals(before.sha256, reordered.sha256)
        assertEquals(3, duplicate.rowCount)
        assertEquals(1, duplicate.addedSince(before))
        assertEquals(0, before.addedSince(duplicate))
        assertEquals(1, before.addedSince(removed))
        assertNotEquals(before.sha256, duplicate.sha256)
    }

    @Test
    fun `framing prevents text boundary and storage type collisions`() {
        val columns = arrayOf("a", "b")
        val first =
            observe(
                listOf(listOf(StreamingSqliteCell.Text("a|b"), StreamingSqliteCell.Text("c"))),
                columns,
            )
        val second =
            observe(
                listOf(listOf(StreamingSqliteCell.Text("a"), StreamingSqliteCell.Text("b|c"))),
                columns,
            )
        assertNotEquals(first.sha256, second.sha256)
        val surrogate = observe(listOf(listOf(StreamingSqliteCell.Text("\uD800"))), arrayOf("a"))
        val replacement = observe(listOf(listOf(StreamingSqliteCell.Text("?"))), arrayOf("a"))
        assertNotEquals(surrogate.sha256, replacement.sha256)
    }

    @Test
    fun `float retention preserves exact bits without JSON numeric normalization`() {
        val cases =
            listOf(
                "0000000000000000",
                "8000000000000000",
                "3ff0000000000000",
                "7ff0000000000000",
                "fff0000000000000",
                "7ff8000000000001",
            )
        cases.forEach { bits ->
            val value = Double.fromBits(bits.toULong(16).toLong())
            assertEquals(bits, RecoveryCampaignJournalRows.encodeReal(value))
            assertEquals(
                value.toRawBits(),
                RecoveryCampaignJournalRows.decodeReal(bits).toRawBits(),
            )
        }
        val zero = observe(listOf(listOf(StreamingSqliteCell.Real(0.0))), arrayOf("a"))
        val negativeZero = observe(listOf(listOf(StreamingSqliteCell.Real(-0.0))), arrayOf("a"))
        assertNotEquals(zero.sha256, negativeZero.sha256)
    }

    @Test
    fun `invalid float evidence encoding is rejected`() {
        listOf("0", "000000000000000G", "3FF0000000000000", "00000000000000000").forEach {
            assertTrue(
                runCatching { RecoveryCampaignJournalRows.decodeReal(it) }.exceptionOrNull()
                    is IllegalArgumentException
            )
        }
    }

    @Test
    fun `query is exact run scoped and an actual empty cursor is distinct from a read failure`() {
        val probe = CursorProbe(emptyList(), RecoveryStreamingSqlCodec.CHECKPOINT_COLUMNS)
        val result =
            RecoveryCampaignJournalRows.checkpoints(runId()) { table, selection, arguments ->
                assertEquals(RecoveryJournalSchema.STREAM_CHECKPOINT_TABLE, table)
                assertEquals("run_id=?", selection)
                assertEquals(listOf(runId().toCanonicalString()), arguments.toList())
                probe.cursor
            }
        assertEquals(0, result.rowCount)
        assertTrue(probe.closed)
        val failure = IllegalStateException("database closed")
        val thrown =
            runCatching {
                    RecoveryCampaignJournalRows.checkpoints(runId()) { _, _, _ -> throw failure }
                }
                .exceptionOrNull()
        assertSame(failure, thrown)
    }

    @Test
    fun `cursor failure propagates and closes the cursor`() {
        val failure = IllegalStateException("cursor window unavailable")
        val probe =
            CursorProbe(
                listOf(checkpointCells()),
                RecoveryStreamingSqlCodec.CHECKPOINT_COLUMNS,
                failure,
            )
        val thrown =
            runCatching {
                    RecoveryCampaignJournalRows.checkpoints(runId()) { _, _, _ -> probe.cursor }
                }
                .exceptionOrNull()
        assertSame(failure, thrown)
        assertTrue(probe.closed)
    }

    @Test
    fun `legacy intent observations retain duplicate counts`() {
        val before = RecoveryCampaignJournalRows.identities(listOf("a", "b"))
        val after = RecoveryCampaignJournalRows.identities(listOf("a", "b", "a"))
        assertEquals(3, after.rowCount)
        assertEquals(1, after.addedSince(before))
    }

    private fun assertCorruption(column: Int, value: StreamingSqliteCell) {
        val changed = checkpointCells().replaced(column, value)
        val database =
            Proxy.newProxyInstance(
                RecoveryStreamingJournalDatabase::class.java.classLoader,
                arrayOf(RecoveryStreamingJournalDatabase::class.java),
            ) { _, method, _ ->
                check(method.name == "checkpoints") { "Unexpected semantic journal access" }
                listOf(
                    decodeStreamingJournalRow {
                        RecoveryStreamingSqlCodec.decodeCheckpoint(changed)
                    }
                )
            } as RecoveryStreamingJournalDatabase
        val rejection = AndroidRecoveryStreamingJournal(database).checkpointChain(runId())
        assertTrue(rejection is RecoveryStreamingJournalReadResult.Fatal)
        assertEquals(
            RecoveryStreamingJournalClassification.JOURNAL_STRUCTURAL,
            (rejection as RecoveryStreamingJournalReadResult.Fatal).classification,
        )
        val before = observe(listOf(changed))
        val replay = observe(listOf(changed))
        assertEquals(1, before.rowCount)
        assertEquals(before.sha256, replay.sha256)
        assertEquals(0, replay.addedSince(before))
        val repaired = observe(listOf(checkpointCells()))
        assertEquals(1, repaired.addedSince(before))
        assertEquals(1, before.addedSince(repaired))
    }

    private fun observe(
        rows: List<List<StreamingSqliteCell>>,
        columns: Array<String> = RecoveryStreamingSqlCodec.CHECKPOINT_COLUMNS,
    ): RecoveryCampaignJournalObservation {
        val probe = CursorProbe(rows, columns)
        return RecoveryCampaignJournalRows.checkpoints(runId()) { _, _, _ -> probe.cursor }
            .also {
                assertTrue(probe.closed)
            }
    }

    private fun checkpointCells(): List<StreamingSqliteCell> {
        val input =
            RecoveryStreamingCheckpointIdentityInput(
                runId(),
                1UL,
                2UL,
                8192UL,
                sha("prefix"),
                4056UL,
                "checkpoints/g-00000000000000000001.ct",
                1UL,
                sha("checkpoint"),
                "key-envelopes/checkpoint-g-00000000000000000001.ks",
                1UL,
                sha("key"),
                "stream/stream.ct",
                "key-envelopes/stream.ks",
                1UL,
                sha("stream-key"),
                Sha256Value.ZERO,
            )
        return RecoveryStreamingSqlCodec.encodeCheckpoint(
            RecoveryStreamingCheckpointRow(
                input.runId,
                input.generation,
                input.durableNonFinalSegmentCount,
                input.streamCiphertextPrefixBytes,
                input.streamCiphertextPrefixSha256,
                input.committedEnd,
                input.checkpointRelativeName,
                input.checkpointBytes,
                input.checkpointSha256,
                input.checkpointEnvelopeRelativeName,
                input.checkpointEnvelopeBytes,
                input.checkpointEnvelopeSha256,
                input.streamRelativeName,
                input.streamEnvelopeRelativeName,
                input.streamEnvelopeBytes,
                input.streamEnvelopeSha256,
                input.previousCheckpointSha256,
                RecoveryStreamingIdentity.checkpoint(input),
            )
        )
    }

    private fun runId() = RunId.fromBytes(ByteArray(16) { (it + 1).toByte() })

    private fun sha(text: String) = Sha256Value.calculate(text.toByteArray())

    private fun List<StreamingSqliteCell>.replaced(index: Int, cell: StreamingSqliteCell) =
        toMutableList().also { it[index] = cell }

    private class CursorProbe(
        private val rows: List<List<StreamingSqliteCell>>,
        private val columns: Array<String>,
        private val getterFailure: Throwable? = null,
    ) {
        private var position = -1
        var closed = false
            private set

        val cursor =
            Proxy.newProxyInstance(Cursor::class.java.classLoader, arrayOf(Cursor::class.java)) {
                _,
                method,
                args ->
                when (method.name) {
                    "getColumnNames" -> columns.copyOf()
                    "getColumnCount" -> columns.size
                    "getColumnName" -> columns[args!![0] as Int]
                    "moveToNext" -> (++position < rows.size)
                    "close" -> {
                        closed = true
                        Unit
                    }
                    "isClosed" -> closed
                    else -> {
                        check(!closed && position in rows.indices)
                        getterFailure?.let { throw it }
                        val cell = rows[position][args!![0] as Int]
                        when (method.name) {
                            "getType" ->
                                when (cell) {
                                    StreamingSqliteCell.Null -> Cursor.FIELD_TYPE_NULL
                                    is StreamingSqliteCell.Integer -> Cursor.FIELD_TYPE_INTEGER
                                    is StreamingSqliteCell.Real -> Cursor.FIELD_TYPE_FLOAT
                                    is StreamingSqliteCell.Text -> Cursor.FIELD_TYPE_STRING
                                    is StreamingSqliteCell.Blob -> Cursor.FIELD_TYPE_BLOB
                                }
                            "getLong" -> (cell as StreamingSqliteCell.Integer).value
                            "getDouble" -> (cell as StreamingSqliteCell.Real).value
                            "getString" -> (cell as StreamingSqliteCell.Text).value
                            "getBlob" -> (cell as StreamingSqliteCell.Blob).value.copyOf()
                            else -> error("Unexpected cursor access ${method.name}")
                        }
                    }
                }
            } as Cursor
    }
}
