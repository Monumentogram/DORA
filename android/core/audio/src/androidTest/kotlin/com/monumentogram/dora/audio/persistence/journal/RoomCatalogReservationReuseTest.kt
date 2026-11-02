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
import net.zetetic.database.sqlcipher.SQLiteDatabase
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
                assertEquals(1, fixture.observer.claimLoads)
                assertEquals(1, fixture.observer.postCommitClaimReads)
                assertEquals(1, fixture.observer.postCommitRevisionReads)
                assertEquals(before.copy(pending = intent), journal.catalog.load(audio))
            }
        }
    }

    @Test
    fun directReservationStillLoadsPreconditionAndPostCommitState() {
        Fixture().use { fixture ->
            fixture.journal.catalog.tryAcquire(audio)!!.use {
                assertTrue(fixture.journal.catalog.reserve(StoredAudioAsset(audio), intent))
                assertEquals(1, fixture.observer.claimLoads)
                assertEquals(1, fixture.observer.postCommitClaimReads)
                assertEquals(1, fixture.observer.postCommitRevisionReads)
                assertEquals(
                    StoredAudioAsset(audio, pending = intent),
                    fixture.journal.catalog.load(audio),
                )
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
                fixture.observer.failNextRevisionRead = true
                assertThrows(IllegalStateException::class.java) {
                    journal.catalog.reserve(before, intent)
                }
                assertEquals(1, fixture.observer.revisionFailures)
                assertTrue(journal.catalog.reserve(before, intent))
                assertEquals(1, fixture.observer.claimLoads)
                assertEquals(1, fixture.observer.postCommitClaimReads)
                assertEquals(1, fixture.observer.postCommitRevisionReads)
            }
        }
    }

    @Test
    fun releasingLeaseForcesFreshReservationPrecondition() {
        Fixture().use { fixture ->
            val journal = fixture.journal
            val before = journal.catalog.tryAcquire(audio)!!.use { journal.catalog.load(audio)!! }
            journal.catalog.tryAcquire(audio)!!.use {
                fixture.observer.failNextRevisionRead = true
                assertThrows(IllegalStateException::class.java) {
                    journal.catalog.reserve(before, intent)
                }
                assertEquals(1, fixture.observer.revisionFailures)
                assertTrue(journal.catalog.reserve(before, intent))
                assertEquals(1, fixture.observer.claimLoads)
                assertEquals(1, fixture.observer.postCommitClaimReads)
                assertEquals(1, fixture.observer.postCommitRevisionReads)
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
                fixture.observer.failNextRevisionRead = true
                assertThrows(IllegalStateException::class.java) {
                    journal.catalog.reserve(before, intent)
                }
                assertEquals(1, fixture.observer.revisionFailures)
                assertTrue(journal.catalog.reserve(before, intent))
                assertEquals(1, fixture.observer.claimLoads)
                assertEquals(1, fixture.observer.postCommitClaimReads)
                assertEquals(1, fixture.observer.postCommitRevisionReads)
            }
        }
    }

    @Test
    fun loadInsideRecoveryTransactionDoesNotCreateReusableObservation() {
        Fixture().use { fixture ->
            val journal = fixture.journal
            journal.catalog.tryAcquire(audio)!!.use {
                val before = journal.catalog.load(audio)!!
                val transaction = journal.bootstrapJournal.beginNonExclusive()
                try {
                    assertThrows(UncertainAudioSourceState::class.java) {
                        journal.catalog.load(audio)
                    }
                } finally {
                    transaction.end()
                }
                fixture.observer.failNextRevisionRead = true
                assertThrows(IllegalStateException::class.java) {
                    journal.catalog.reserve(before, intent)
                }
                assertEquals(1, fixture.observer.revisionFailures)
                assertTrue(journal.catalog.reserve(before, intent))
                assertEquals(1, fixture.observer.claimLoads)
                assertEquals(1, fixture.observer.postCommitClaimReads)
                assertEquals(1, fixture.observer.postCommitRevisionReads)
            }
        }
    }

    @Test
    fun callerMutationCannotRewriteValidatedSnapshot() {
        Fixture().use { fixture ->
            val journal = fixture.journal
            journal.catalog.tryAcquire(audio)!!.use {
                val before = journal.catalog.load(audio)!!
                val exposed = before.segments.toMutableList()
                val supplied = before.copy(segments = exposed)
                exposed +=
                    StoredAudioSegment(intent.identity, 2, Sha256Value.calculate(byteArrayOf()))
                assertFalse(journal.catalog.reserve(supplied, intent))
                assertTrue(before.segments.isEmpty())
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
        // Exercise direct reserve before any load/isVerifiedAppendSnapshot can invalidate the
        // lease-local observation. A second encrypted connection advances the durable revision.
        Fixture().use { fixture ->
            val journal = fixture.journal
            journal.catalog.tryAcquire(audio)!!.use {
                val before = journal.catalog.load(audio)!!
                val revision = fixture.finalizeFromAnotherConnection()
                assertThrows(IllegalStateException::class.java) {
                    journal.catalog.reserve(before, intent)
                }
                fixture.assertNoReservationAfterExternalFinalization(revision)
            }
        }
        Fixture().use { fixture ->
            val journal = fixture.journal
            journal.catalog.tryAcquire(audio)!!.use {
                val before = journal.catalog.load(audio)!!
                var mutationRevision = 0L
                fixture.observer.beforeNextClaimLookup = {
                    mutationRevision = fixture.finalizeFromAnotherConnection()
                }
                // The point lookup occurs after reservation precondition acceptance and before
                // transaction entry. Recapturing a later revision would bless the old snapshot.
                assertThrows(IllegalStateException::class.java) {
                    journal.catalog.reserve(before, intent)
                }
                assertTrue(mutationRevision > 0)
                assertEquals(null, fixture.observer.beforeNextClaimLookup)
                fixture.assertNoReservationAfterExternalFinalization(mutationRevision)
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
                fixture.observer.failNextRevisionRead = true
                assertThrows(IllegalStateException::class.java) { journal.catalog.load(audio) }
                assertEquals(1, fixture.observer.revisionFailures)
                assertTrue(journal.catalog.reserve(before, intent))
                assertEquals(1, fixture.observer.claimLoads)
                assertEquals(1, fixture.observer.postCommitClaimReads)
                assertEquals(1, fixture.observer.postCommitRevisionReads)
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
            val committed =
                journal.catalog.tryAcquire(audio)!!.use {
                    val before = journal.catalog.load(audio)!!
                    fixture.observer.failPostCommitReadback = true
                    assertThrows(IllegalStateException::class.java) {
                        journal.catalog.reserve(before, intent)
                    }
                    assertEquals(1, fixture.observer.readbackFailures)
                    fixture.observer.failPostCommitReadback = false
                    assertThrows(IllegalStateException::class.java) {
                        journal.catalog.reserve(before, intent)
                    }
                    assertThrows(UncertainAudioSourceState::class.java) {
                        journal.catalog.load(audio)
                    }
                    before.copy(pending = intent)
                }
            val recovered = fixture.reopen()
            recovered.catalog.tryAcquire(audio)!!.use {
                assertEquals(committed, recovered.catalog.load(audio))
            }
        }
    }

    private inner class Fixture : AutoCloseable {
        private val context = InstrumentationRegistry.getInstrumentation().targetContext
        private val file =
            File(context.noBackupFilesDir, "reservation-reuse-${UUID.randomUUID()}.db")
        private val passphrase = ByteArray(32).also { SecureRandom().nextBytes(it) }
        var authorized = true
        val observer =
            QueryObserver(
                SqlCipherJournalHelperFactory(
                    context,
                    file.canonicalFile,
                    passphrase,
                )
            )
        var journal =
            RoomAudioJournal.open(context, file, observer, id(10), id(11), { check(authorized) })
            private set

        init {
            assertTrue(journal.catalog.create(audio))
            observer.resetObservations()
        }

        fun reopen(): RoomAudioJournal {
            journal.close()
            journal =
                RoomAudioJournal.open(
                    context,
                    file,
                    SqlCipherJournalHelperFactory(context, file.canonicalFile, passphrase),
                    id(10),
                    id(11),
                    { check(authorized) },
                )
            return journal
        }

        fun finalizeFromAnotherConnection(): Long =
            externalDatabase().use { database ->
                val revision = scalar(database, "SELECT revision FROM audio_catalog_revision")
                database.execSQL(
                    "UPDATE audio_asset SET finalized=1 WHERE assetId=?",
                    arrayOf(audio.assetId.value),
                )
                assertEquals(1L, scalar(database, "SELECT finalized FROM audio_asset"))
                Math.addExact(revision, 1L).also {
                    assertEquals(
                        it,
                        scalar(database, "SELECT revision FROM audio_catalog_revision"),
                    )
                }
            }

        fun assertNoReservationAfterExternalFinalization(revision: Long) {
            externalDatabase().use { database ->
                assertEquals(
                    revision,
                    scalar(database, "SELECT revision FROM audio_catalog_revision"),
                )
                assertEquals(1L, scalar(database, "SELECT finalized FROM audio_asset"))
                assertEquals(0L, scalar(database, "SELECT COUNT(*) FROM unit_claim"))
                assertEquals(0L, scalar(database, "SELECT COUNT(*) FROM audio_intent"))
            }
        }

        private fun externalDatabase(): SQLiteDatabase =
            SQLiteDatabase.openDatabase(
                file.canonicalPath,
                passphrase,
                null,
                SQLiteDatabase.OPEN_READWRITE,
                null,
            )

        private fun scalar(database: SQLiteDatabase, sql: String): Long =
            database.rawQuery(sql, emptyArray<String>()).use { cursor ->
                check(cursor.moveToFirst())
                cursor.getLong(0).also { check(!cursor.moveToNext()) }
            }

        override fun close() {
            try {
                journal.close()
            } finally {
                passphrase.fill(0)
            }
        }
    }

    private class QueryObserver(private val delegate: SupportSQLiteOpenHelper.Factory) :
        SupportSQLiteOpenHelper.Factory {
        var claimLoads = 0
        var postCommitClaimReads = 0
        var postCommitRevisionReads = 0
        var revisionFailures = 0
        var readbackFailures = 0
        var beforeNextClaimLookup: (() -> Unit)? = null
        var failPostCommitReadback = false
            set(value) {
                field = value
                if (value) ended = false
            }

        var failNextRevisionRead = false
        private var ended = false
        private var successful = false
        private var reservationClaimLookupObserved = false
        private var awaitingRevisionReadback = false

        fun resetObservations() {
            claimLoads = 0
            postCommitClaimReads = 0
            postCommitRevisionReads = 0
            ended = false
            successful = false
            reservationClaimLookupObserved = false
            awaitingRevisionReadback = false
        }

        override fun create(
            configuration: SupportSQLiteOpenHelper.Configuration
        ): SupportSQLiteOpenHelper {
            val helper = delegate.create(configuration)
            fun wrap(database: SupportSQLiteDatabase) =
                Proxy.newProxyInstance(
                    SupportSQLiteDatabase::class.java.classLoader,
                    arrayOf(SupportSQLiteDatabase::class.java),
                ) { _, method, args ->
                    val sql =
                        (args?.firstOrNull() as? SupportSQLiteQuery)?.sql
                            ?: args?.firstOrNull() as? String
                    beforeInvocation(method.name, sql)
                    val result =
                        try {
                            method.invoke(database, *(args ?: emptyArray()))
                        } catch (failure: InvocationTargetException) {
                            throw failure.targetException
                        }
                    if (method.name == "setTransactionSuccessful") successful = true
                    if (method.name == "endTransaction") ended = successful
                    result
                } as SupportSQLiteDatabase
            return object : SupportSQLiteOpenHelper by helper {
                override val writableDatabase: SupportSQLiteDatabase
                    get() = wrap(helper.writableDatabase)

                override val readableDatabase: SupportSQLiteDatabase
                    get() = wrap(helper.readableDatabase)
            }
        }

        private fun beforeInvocation(method: String, sql: String?) {
            if (method == "beginTransaction" || method == "beginTransactionNonExclusive") {
                ended = false
                successful = false
            }
            if (method == "query") observeQuery(sql)
        }

        private fun observeQuery(sql: String?) {
            when (sql) {
                "SELECT * FROM unit_claim WHERE assetId=? ORDER BY ordinal" -> claimLoads++
                "SELECT revision FROM audio_catalog_revision WHERE singleton=1" ->
                    observeRevisionRead()
                "SELECT * FROM unit_claim WHERE runId=?" -> observeClaimRead()
            }
        }

        private fun observeRevisionRead() {
            if (failNextRevisionRead) {
                failNextRevisionRead = false
                revisionFailures++
                error("Synthetic catalog revision failure")
            }
            if (awaitingRevisionReadback) {
                postCommitRevisionReads++
                awaitingRevisionReadback = false
            }
        }

        private fun observeClaimRead() {
            val beforeLookup = beforeNextClaimLookup
            beforeNextClaimLookup = null
            beforeLookup?.invoke()
            // Room may end bookkeeping transactions after a rollback. Only the lookup
            // following this reserve's precondition lookup is its readback.
            if (ended && reservationClaimLookupObserved) {
                reservationClaimLookupObserved = false
                awaitingRevisionReadback = true
                postCommitClaimReads++
                if (failPostCommitReadback) {
                    readbackFailures++
                    error("Synthetic catalog post-commit readback failure")
                }
            } else reservationClaimLookupObserved = true
        }
    }

    private fun id(value: Long) = UUID(0L, value).toString()
}
