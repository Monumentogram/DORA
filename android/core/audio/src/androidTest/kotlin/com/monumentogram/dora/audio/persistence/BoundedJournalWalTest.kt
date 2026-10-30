package com.monumentogram.dora.audio.persistence

import android.database.MatrixCursor
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.SupportSQLiteQuery
import com.monumentogram.dora.audio.AudioFailure
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.persistence.journal.JournalWalCheckpoint
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundedJournalWalTest {
    @Test
    fun malformedNativeCheckpointCountersFailClosed() {
        for (values in
            listOf(
                arrayOf<Any?>(null, null, null),
                arrayOf("0", "0", "0"),
                arrayOf(0.0, 0.0, 0.0),
            )) {
            val database =
                Proxy.newProxyInstance(
                    SupportSQLiteDatabase::class.java.classLoader,
                    arrayOf(SupportSQLiteDatabase::class.java),
                ) { _, method, _ ->
                    when (method.name) {
                        "inTransaction" -> false
                        "query" ->
                            MatrixCursor(arrayOf("busy", "log", "checkpointed")).apply {
                                addRow(values)
                            }
                        else -> error("Unexpected checkpoint call")
                    }
                } as SupportSQLiteDatabase
            assertThrows(IllegalStateException::class.java) {
                JournalWalCheckpoint.perform(database)
            }
        }
    }

    @Test
    fun repeatedDurableAppendsBoundWalWithoutChangingFullOrAutoCheckpointPolicy() {
        val fixture = EncryptedAudioVaultFaultFixture()
        fixture.open().use { vault ->
            value(vault.writer.create(fixture.audio))
            val wal =
                fixture.context.noBackupFilesDir.walkTopDown().first {
                    it.isFile && it.name.endsWith("-wal")
                }
            repeat(256) { ordinal ->
                value(
                    fixture.append(
                        vault,
                        fixture.unit.copy(
                            unitId = EncryptedAudioVaultFaultFixture.id(),
                            ordinal = ordinal,
                            firstFrame = ordinal * 160L,
                            sourceFrameOffset = ordinal * 160L,
                        ),
                    )
                )
                val largestWal = wal.length()
                assertTrue(
                    "WAL must stay within8MiB trigger plus one bounded transaction: $largestWal",
                    largestWal <= 9L * 1024 * 1024,
                )
            }
            var next = 0L
            value(
                vault.reader.extract(fixture.audio) { frame, bytes ->
                    assertEquals(next, frame)
                    assertArrayEquals(fixture.pcm, bytes)
                    next += bytes.size / 2
                }
            )
            assertEquals(40_960L, next)
        }
        fixture.open(false).use { vault ->
            var frames = 0L
            value(
                vault.reader.extract(fixture.audio) { first, bytes ->
                    assertEquals(frames, first)
                    assertArrayEquals(fixture.pcm, bytes)
                    frames += bytes.size / 2
                }
            )
            assertEquals(40_960L, frames)
            value(vault.deleteConfirmed(fixture.audio))
        }
    }

    @Test
    fun incompleteCheckpointFencesWritesAndReopenAuthenticatesTheCommittedPrefix() {
        checkpointFailure("PASSIVE")
    }

    @Test
    fun failedTruncationAfterCompletedPassiveCheckpointFencesFurtherWrites() {
        checkpointFailure("TRUNCATE")
    }

    @Suppress(
        "CyclomaticComplexMethod"
    ) // Share the same driver fault and reopened-prefix proof for both phases.
    private fun checkpointFailure(phase: String) {
        val fixture = EncryptedAudioVaultFaultFixture()
        val fired = AtomicBoolean()
        val dependencies =
            EncryptedAudioVault.Dependencies(
                helperFactory = { delegate ->
                    SupportSQLiteOpenHelper.Factory { config ->
                        val helper = delegate.create(config)
                        fun wrapped(database: SupportSQLiteDatabase): SupportSQLiteDatabase =
                            Proxy.newProxyInstance(
                                SupportSQLiteDatabase::class.java.classLoader,
                                arrayOf(SupportSQLiteDatabase::class.java),
                            ) { _, method, args ->
                                val sql =
                                    when (val query = args?.firstOrNull()) {
                                        is String -> query
                                        is SupportSQLiteQuery -> query.sql
                                        else -> ""
                                    }
                                if (
                                    method.name == "query" &&
                                        sql.contains("wal_checkpoint($phase)") &&
                                        fired.compareAndSet(false, true)
                                ) {
                                    MatrixCursor(arrayOf("busy", "log", "checkpointed")).apply {
                                        addRow(arrayOf(1, 5, 2))
                                    }
                                } else
                                    try {
                                        method.invoke(database, *(args ?: emptyArray()))
                                    } catch (error: InvocationTargetException) {
                                        throw error.targetException
                                    }
                            } as SupportSQLiteDatabase
                        object : SupportSQLiteOpenHelper by helper {
                            override val writableDatabase
                                get() = wrapped(helper.writableDatabase)
                        }
                    }
                }
            )
        var acknowledged = 0
        fixture.open(dependencies = dependencies).use { vault ->
            value(vault.writer.create(fixture.audio))
            for (ordinal in 0 until 256) {
                val result =
                    fixture.append(
                        vault,
                        fixture.unit.copy(
                            unitId = EncryptedAudioVaultFaultFixture.id(),
                            ordinal = ordinal,
                            firstFrame = ordinal * 160L,
                            sourceFrameOffset = ordinal * 160L,
                        ),
                    )
                if (result is AudioResult.Failed) {
                    assertEquals(AudioFailure.UNCERTAIN, result.reason)
                    break
                }
                acknowledged++
            }
            assertTrue("Checkpoint must have been attempted", fired.get())
            assertEquals(
                AudioResult.Failed(AudioFailure.UNCERTAIN),
                vault.sourceState(fixture.audio),
            )
            assertEquals(AudioResult.Failed(AudioFailure.UNCERTAIN), fixture.append(vault))
        }
        fixture.open(false).use { vault ->
            var frames = 0L
            value(
                vault.reader.extract(fixture.audio) { first, bytes ->
                    assertEquals(frames, first)
                    assertArrayEquals(fixture.pcm, bytes)
                    frames += bytes.size / 2
                }
            )
            // A post-commit maintenance failure may leave the just-admitted unit durable.
            assertTrue(frames in acknowledged * 160L..(acknowledged + 1) * 160L)
            value(vault.deleteConfirmed(fixture.audio))
        }
    }

    private fun <T> value(result: AudioResult<T>): T {
        assertTrue("Expected synthetic vault success: $result", result is AudioResult.Value)
        return (result as AudioResult.Value).value
    }
}
