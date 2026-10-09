package com.monumentogram.dora.audio.persistence.journal

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.SupportSQLiteQuery
import androidx.test.platform.app.InstrumentationRegistry
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.AudioIntent
import com.monumentogram.dora.audio.AudioStorageUnitIdentity
import com.monumentogram.dora.audio.StoredAudioAsset
import com.monumentogram.dora.audio.StoredAudioSegment
import com.monumentogram.dora.audio.persistence.database.SqlCipherJournalHelperFactory
import com.monumentogram.dora.model.alpha.AudioAssetId
import com.monumentogram.dora.model.alpha.RecordingId
import com.monumentogram.dora.poc.recovery.contract.Sha256Value
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Real encrypted journal operations; the decorator observes queries without replacing storage. */
@Suppress("TooManyFunctions") // Keep the reservation lifetime and invalidation controls together.
class RoomCatalogReservationReuseTest {
    private val audio = AudioIdentity(RecordingId(id(1)), AudioAssetId(id(2)), id(3))
    private val intent = AudioIntent.Append(AudioStorageUnitIdentity(audio, id(4), 0, 0), 2)

    @Test
    fun validatedEntrySnapshotAvoidsOneLoadButReservationStillReadsBackAfterCommit() {
        Fixture().use { fixture ->
            val journal = fixture.journal
            journal.catalog.tryAcquire(audio)!!.use {
                val before = journal.catalog.load(audio)!!
                assertTrue(journal.catalog.reserve(before, intent))
                assertEquals(2, fixture.observer.claimLoads)
                assertEquals(before.copy(pending = intent), journal.catalog.load(audio))
            }
        }
    }

    @Test
    fun directReservationStillLoadsPreconditionAndPostCommitState() {
        Fixture().use { fixture ->
            fixture.journal.catalog.tryAcquire(audio)!!.use {
                assertTrue(fixture.journal.catalog.reserve(StoredAudioAsset(audio), intent))
                assertEquals(2, fixture.observer.claimLoads)
            }
        }
    }

    @Test
    fun rejectedAppendConsumesObservationBeforeAnotherReservation() {
        Fixture().use { fixture ->
            val journal = fixture.journal
            journal.catalog.tryAcquire(audio)!!.use {
                val before = journal.catalog.load(audio)!!
                assertFalse(
                    journal.catalog.reserve(
                        before,
                        intent.copy(identity = intent.identity.copy(ordinal = 1)),
                    )
                )
                assertTrue(journal.catalog.reserve(before, intent))
                assertEquals(3, fixture.observer.claimLoads)
            }
        }
    }

    @Test
    fun releasingLeaseForcesFreshReservationPrecondition() {
        Fixture().use { fixture ->
            val journal = fixture.journal
            val before = journal.catalog.tryAcquire(audio)!!.use { journal.catalog.load(audio)!! }
            journal.catalog.tryAcquire(audio)!!.use {
                assertTrue(journal.catalog.reserve(before, intent))
                assertEquals(3, fixture.observer.claimLoads)
            }
        }
    }

    @Test
    fun evenRolledBackRecoveryWriteSessionInvalidatesReservationSnapshot() {
        Fixture().use { fixture ->
            val journal = fixture.journal
            journal.catalog.tryAcquire(audio)!!.use {
                val before = journal.catalog.load(audio)!!
                journal.bootstrapJournal.beginNonExclusive().end()
                assertTrue(journal.catalog.reserve(before, intent))
                assertEquals(3, fixture.observer.claimLoads)
            }
        }
    }

    @Test
    fun loadInsideRecoveryTransactionDoesNotCreateReusableObservation() {
        Fixture().use { fixture ->
            val journal = fixture.journal
            journal.catalog.tryAcquire(audio)!!.use {
                val transaction = journal.bootstrapJournal.beginNonExclusive()
                val before =
                    try {
                        journal.catalog.load(audio)!!
                    } finally {
                        transaction.end()
                    }
                assertTrue(journal.catalog.reserve(before, intent))
                assertEquals(3, fixture.observer.claimLoads)
            }
        }
    }

    @Test
    fun callerMutationCannotRewriteValidatedSnapshot() {
        Fixture().use { fixture ->
            val journal = fixture.journal
            journal.catalog.tryAcquire(audio)!!.use {
                val before = journal.catalog.load(audio)!!
                @Suppress("UNCHECKED_CAST")
                val exposed = before.segments as MutableList<StoredAudioSegment>
                exposed +=
                    StoredAudioSegment(intent.identity, 2, Sha256Value.calculate(byteArrayOf()))
                assertFalse(journal.catalog.reserve(before, intent))
                assertEquals(StoredAudioAsset(audio), journal.catalog.load(audio))
            }
        }
    }

    @Test
    fun tombstoneCannotBeBypassedByPreviouslyValidatedSnapshot() {
        Fixture().use { fixture ->
            val journal = fixture.journal
            journal.catalog.tryAcquire(audio)!!.use {
                val before = journal.catalog.load(audio)!!
                journal.beginDeletion(audio, emptyList())
                assertFalse(journal.catalog.reserve(before, intent))
            }
        }
    }

    @Test
    fun currentAuthorizationIsRequiredEvenForValidatedSnapshot() {
        Fixture().use { fixture ->
            val journal = fixture.journal
            journal.catalog.tryAcquire(audio)!!.use {
                val before = journal.catalog.load(audio)!!
                fixture.authorized = false
                assertThrows(IllegalStateException::class.java) {
                    journal.catalog.reserve(before, intent)
                }
            }
        }
    }

    @Test
    fun failedLoadCannotLeavePriorSnapshotReusable() {
        Fixture().use { fixture ->
            val journal = fixture.journal
            journal.catalog.tryAcquire(audio)!!.use {
                val before = journal.catalog.load(audio)!!
                fixture.observer.failNextClaimLoad = true
                assertThrows(IllegalStateException::class.java) { journal.catalog.load(audio) }
                assertTrue(journal.catalog.reserve(before, intent))
                assertEquals(4, fixture.observer.claimLoads)
            }
        }
    }

    @Test
    fun foreignIdentityAndWrongThreadCannotConsumeValidatedSnapshot() {
        Fixture().use { fixture ->
            val journal = fixture.journal
            journal.catalog.tryAcquire(audio)!!.use {
                val before = journal.catalog.load(audio)!!
                assertThrows(IllegalStateException::class.java) {
                    journal.catalog.reserve(
                        before.copy(identity = audio.copy(sessionId = id(99))),
                        intent,
                    )
                }
                val failure = AtomicReference<Throwable>()
                val thread = Thread {
                    try {
                        journal.catalog.reserve(before, intent)
                    } catch (error: IllegalStateException) {
                        failure.set(error)
                    }
                }
                thread.start()
                thread.join()
                assertTrue(failure.get() is IllegalStateException)
                assertTrue(journal.catalog.reserve(before, intent))
            }
        }
    }

    @Test
    fun failedPostCommitReadbackFencesFurtherReservation() {
        Fixture().use { fixture ->
            val journal = fixture.journal
            journal.catalog.tryAcquire(audio)!!.use {
                val before = journal.catalog.load(audio)!!
                fixture.observer.failPostCommitReadback = true
                assertThrows(IllegalStateException::class.java) {
                    journal.catalog.reserve(before, intent)
                }
                fixture.observer.failPostCommitReadback = false
                assertThrows(IllegalStateException::class.java) {
                    journal.catalog.reserve(before, intent)
                }
                assertEquals(before.copy(pending = intent), journal.catalog.load(audio))
            }
        }
    }

    private inner class Fixture : AutoCloseable {
        private val context = InstrumentationRegistry.getInstrumentation().targetContext
        private val file =
            File(context.noBackupFilesDir, "reservation-reuse-${UUID.randomUUID()}.db")
        var authorized = true
        val observer =
            QueryObserver(
                SqlCipherJournalHelperFactory(
                    context,
                    file.canonicalFile,
                    ByteArray(32).also { SecureRandom().nextBytes(it) },
                )
            )
        val journal =
            RoomAudioJournal.open(context, file, observer, id(10), id(11), { check(authorized) })

        init {
            assertTrue(journal.catalog.create(audio))
            observer.claimLoads = 0
        }

        override fun close() = journal.close()
    }

    private class QueryObserver(private val delegate: SupportSQLiteOpenHelper.Factory) :
        SupportSQLiteOpenHelper.Factory {
        var claimLoads = 0
        var failPostCommitReadback = false
            set(value) {
                field = value
                if (value) ended = false
            }

        var failNextClaimLoad = false
        private var ended = false

        override fun create(
            configuration: SupportSQLiteOpenHelper.Configuration
        ): SupportSQLiteOpenHelper {
            val helper = delegate.create(configuration)
            fun wrap(database: SupportSQLiteDatabase) =
                Proxy.newProxyInstance(
                    SupportSQLiteDatabase::class.java.classLoader,
                    arrayOf(SupportSQLiteDatabase::class.java),
                ) { _, method, args ->
                    if (
                        method.name == "beginTransaction" ||
                            method.name == "beginTransactionNonExclusive"
                    )
                        ended = false
                    val sql =
                        (args?.firstOrNull() as? SupportSQLiteQuery)?.sql
                            ?: args?.firstOrNull() as? String
                    if (
                        method.name == "query" &&
                            sql == "SELECT * FROM unit_claim WHERE assetId=? ORDER BY ordinal"
                    ) {
                        claimLoads++
                        if (failNextClaimLoad) {
                            failNextClaimLoad = false
                            error("Synthetic catalog load failure")
                        }
                        if (ended && failPostCommitReadback)
                            error("Synthetic catalog post-commit readback failure")
                    }
                    val result =
                        try {
                            method.invoke(database, *(args ?: emptyArray()))
                        } catch (failure: InvocationTargetException) {
                            throw failure.targetException
                        }
                    if (method.name == "endTransaction") ended = true
                    result
                } as SupportSQLiteDatabase
            return object : SupportSQLiteOpenHelper by helper {
                override val writableDatabase: SupportSQLiteDatabase
                    get() = wrap(helper.writableDatabase)

                override val readableDatabase: SupportSQLiteDatabase
                    get() = wrap(helper.readableDatabase)
            }
        }
    }

    private fun id(value: Long) = UUID(0L, value).toString()
}
