package com.monumentogram.dora.audio.persistence.database

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import net.zetetic.database.sqlcipher.SQLiteGlobal
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Synthetic actual-SQLCipher tests; never a product key or plaintext database fallback. */
class SqlCipherConnectionPolicyTest {
    @Test
    fun combinedReadReturnsEveryPinnedConnectionPolicyValue() {
        val secret = key()
        try {
            helper(file(), secret).use { opened ->
                opened.writableDatabase
                    .query(
                        "SELECT journal_mode, synchronous, foreign_keys, temp_store " +
                            "FROM pragma_journal_mode, pragma_synchronous, pragma_foreign_keys, pragma_temp_store"
                    )
                    .use {
                        assertTrue(it.moveToFirst())
                        assertEquals(
                            listOf("wal", "2", "1", "2"),
                            (0 until it.columnCount).map(it::getString),
                        )
                        assertFalse(it.moveToNext())
                    }
                opened.writableDatabase.query("PRAGMA wal_autocheckpoint").use {
                    assertTrue(it.moveToFirst())
                    assertEquals("0", it.getString(0))
                    assertFalse(it.moveToNext())
                }
            }
        } finally {
            secret.fill(0)
        }
    }

    @Test
    fun everyConnectionPolicyDriftPermanentlyFencesStatementAndHelper() {
        listOf("synchronous=1", "foreign_keys=0", "temp_store=1", "wal_autocheckpoint=1000")
            .forEach { drift ->
                val secret = key()
                try {
                    helper(file(), secret).use { opened ->
                        val db = opened.writableDatabase
                        val statement =
                            db.compileStatement("INSERT INTO synthetic_metadata VALUES (?)")
                        assertThrows(IllegalStateException::class.java) {
                            db.execSQL("PRAGMA $drift")
                        }
                        assertThrows(IllegalStateException::class.java) {
                            statement.bindString(1, "synthetic")
                        }
                        assertThrows(IllegalStateException::class.java) {
                            statement.executeInsert()
                        }
                        assertThrows(IllegalStateException::class.java) { opened.writableDatabase }
                    }
                } finally {
                    secret.fill(0)
                }
            }
    }

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun key() = ByteArray(32).also { SecureRandom().nextBytes(it) }

    private fun file() =
        File(context.noBackupFilesDir, "policy-${UUID.randomUUID()}.db").canonicalFile

    @Test
    fun abandonedFactoryWipesOwnedSecretWithoutWipingCaller() {
        val caller = key()
        val factory = SqlCipherJournalHelperFactory(context, file(), caller)
        val owned =
            factory.javaClass.getDeclaredField("secret").apply { isAccessible = true }.get(factory)
                as ByteArray
        try {
            assertFalse(owned === caller)
            assertTrue(owned.any { it != 0.toByte() })
            (factory as Any as AutoCloseable).close()
            assertTrue(owned.all { it == 0.toByte() })
            assertTrue(caller.any { it != 0.toByte() })
        } finally {
            caller.fill(0)
        }
    }

    @Test
    fun rejectedConfigurationWipesOwnedSecret() {
        val caller = key()
        val factory = SqlCipherJournalHelperFactory(context, file(), caller)
        val owned =
            factory.javaClass.getDeclaredField("secret").apply { isAccessible = true }.get(factory)
                as ByteArray
        try {
            val configuration =
                SupportSQLiteOpenHelper.Configuration.builder(context)
                    .name(file().path)
                    .callback(
                        object : SupportSQLiteOpenHelper.Callback(1) {
                            override fun onCreate(db: SupportSQLiteDatabase) = Unit

                            override fun onUpgrade(
                                db: SupportSQLiteDatabase,
                                oldVersion: Int,
                                newVersion: Int,
                            ) = Unit
                        }
                    )
                    .build()
            assertThrows(IllegalStateException::class.java) { factory.create(configuration) }
            assertTrue(owned.all { it == 0.toByte() })
        } finally {
            caller.fill(0)
        }
    }

    private fun helper(path: File, secret: ByteArray): SupportSQLiteOpenHelper =
        SqlCipherJournalHelperFactory(context, path, secret)
            .create(
                SupportSQLiteOpenHelper.Configuration.builder(context)
                    .name(path.path)
                    .callback(
                        object : SupportSQLiteOpenHelper.Callback(1) {
                            override fun onCreate(db: SupportSQLiteDatabase) {
                                db.execSQL("CREATE TABLE synthetic_metadata(value TEXT NOT NULL)")
                            }

                            override fun onUpgrade(
                                db: SupportSQLiteDatabase,
                                oldVersion: Int,
                                newVersion: Int,
                            ) = error("Unexpected migration")
                        }
                    )
                    .build()
            )

    @Test
    fun ciphertextAndWalDoNotExposeBoundMetadataAndWrongKeyPreservesDatabase() {
        val path = file()
        val secret = key()
        val canary = "DORA_SYNTHETIC_BOUND_METADATA_82"
        try {
            helper(path, secret).use { opened ->
                val db = opened.writableDatabase
                db.beginTransaction()
                try {
                    db.execSQL("INSERT INTO synthetic_metadata VALUES (?)", arrayOf(canary))
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
                db.query("SELECT value FROM synthetic_metadata").use {
                    assertTrue(it.moveToFirst())
                    assertEquals(canary, it.getString(0))
                }
                listOf(path, File(path.path + "-wal"), File(path.path + "-shm"))
                    .filter { it.exists() }
                    .forEach {
                        assertFalse(it.readBytes().toString(Charsets.ISO_8859_1).contains(canary))
                    }
            }
            val preserved = path.readBytes()
            val wrong = key()
            try {
                helper(path, wrong).use { opened ->
                    val failure =
                        assertThrows(IllegalStateException::class.java) { opened.writableDatabase }
                    assertEquals("Encrypted database operation unavailable", failure.message)
                    assertNull(failure.cause)
                }
            } finally {
                wrong.fill(0)
            }
            assertArrayEquals(preserved, path.readBytes())
            helper(path, secret).use { opened ->
                opened.writableDatabase.query("SELECT value FROM synthetic_metadata").use {
                    assertTrue(it.moveToFirst())
                    assertEquals(canary, it.getString(0))
                }
            }
        } finally {
            secret.fill(0)
        }
    }

    @Test
    fun changedGlobalPoolPolicyFencesEpochEvenAfterPolicyRestored() {
        val secret = key()
        try {
            helper(file(), secret).use { opened ->
                opened.writableDatabase
                try {
                    SQLiteGlobal.setWALConnectionPoolSize(2)
                    assertThrows(IllegalStateException::class.java) { opened.writableDatabase }
                } finally {
                    SQLiteGlobal.setWALConnectionPoolSize(1)
                }
                assertThrows(IllegalStateException::class.java) { opened.writableDatabase }
            }
        } finally {
            secret.fill(0)
        }
    }

    @Test(timeout = 20_000)
    fun connectionOwnerCanReenterHelperWhileAnotherThreadWaitsForConnection() {
        val secret = key()
        val opened = helper(file(), secret)
        val db = opened.writableDatabase
        val acquired = CountDownLatch(1)
        val reenter = CountDownLatch(1)
        val owner = FutureTask {
            db.beginTransaction()
            try {
                acquired.countDown()
                check(reenter.await(10, TimeUnit.SECONDS))
                assertTrue(opened.writableDatabase.inTransaction())
            } finally {
                db.endTransaction()
            }
        }
        val waiter = FutureTask {
            opened.writableDatabase.query("SELECT count(*) FROM synthetic_metadata").use {
                assertTrue(it.moveToFirst())
            }
        }
        val ownerThread = Thread(owner, "policy-connection-owner").apply { isDaemon = true }
        val waiterThread = Thread(waiter, "policy-connection-waiter").apply { isDaemon = true }
        try {
            ownerThread.start()
            assertTrue(acquired.await(5, TimeUnit.SECONDS))
            waiterThread.start()
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            // Observe the actual connection wait before permitting owner reentry;
            // no sleep or race-probability assumption establishes this ordering.
            while (waiterThread.stackTrace.none { it.methodName == "waitForConnection" }) {
                check(System.nanoTime() < deadline) { "Connection wait was not reached" }
                Thread.yield()
            }
            reenter.countDown()
            owner.get(5, TimeUnit.SECONDS)
            waiter.get(5, TimeUnit.SECONDS)
        } finally {
            reenter.countDown()
            if (owner.isDone && waiter.isDone) opened.close()
            secret.fill(0)
        }
    }
}
