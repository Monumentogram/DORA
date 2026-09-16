package com.monumentogram.dora.poc.recovery.journal

import android.content.Context
import android.content.ContextWrapper
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecoveryJournalConnectionConfigurationTest {
    @Test
    @SdkSuppress(minSdkVersion = 33)
    fun primaryAndConcurrentReaderKeepConfigurationAfterReopen() = withIsolatedContext { context ->
        RecoveryStreamPrefixMigrationVerification.verify(context)
        repeat(2) {
            RecoveryJournalSqliteHelper(context).use { helper ->
                val database = helper.writableDatabase
                assertTrue(database.isWriteAheadLoggingEnabled)
                assertPrimaryAndConcurrentReader(database)
            }
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = 28, maxSdkVersion = 32)
    fun unsupportedPocFailsBeforePreparingJournalDirectory() = withIsolatedContext { context ->
        val failure =
            assertThrows(IllegalStateException::class.java) {
                RecoveryJournalSqliteHelper(context).use { it.writableDatabase }
            }
        assertTrue(failure.message.orEmpty().contains("API 33"))
        assertFalse(File(context.noBackupFilesDir, "poc-recovery").exists())
    }

    private fun assertPrimaryAndConcurrentReader(database: SQLiteDatabase) {
        val executor = Executors.newSingleThreadExecutor()
        val prepared = CountDownLatch(1)
        val readWhilePrimaryHeld = CountDownLatch(1)
        val expected = mapOf("synchronous" to 2L, "wal_autocheckpoint" to 0L, "foreign_keys" to 1L)
        val reader =
            executor.submit<Map<String, Long>> {
                // Preparing a PRAGMA may itself acquire the primary connection. Prepare before
                // the writer transaction; execution below must finish while that primary is held.
                val statements = mutableMapOf<String, android.database.sqlite.SQLiteStatement>()
                try {
                    expected.keys.forEach { name ->
                        statements[name] = database.compileStatement("PRAGMA $name")
                    }
                    prepared.countDown()
                    check(readWhilePrimaryHeld.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
                    statements.mapValues { (_, statement) -> statement.simpleQueryForLong() }
                } finally {
                    prepared.countDown()
                    statements.values.forEach { it.close() }
                }
            }
        try {
            assertTrue(prepared.await(TIMEOUT_SECONDS, TimeUnit.SECONDS))
            database.beginTransactionNonExclusive()
            try {
                // All reads on this thread use the transaction's primary connection.
                assertEquals("wal", scalar(database, "journal_mode"))
                expected.forEach { (name, value) ->
                    assertEquals(value.toString(), scalar(database, name))
                }
                readWhilePrimaryHeld.countDown()
                // Completing before endTransaction proves the reader did not reuse primary.
                assertEquals(expected, reader.get(TIMEOUT_SECONDS, TimeUnit.SECONDS))
                assertTrue(database.inTransaction())
            } finally {
                database.endTransaction()
            }
        } finally {
            readWhilePrimaryHeld.countDown()
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS))
        }
    }

    private fun scalar(database: SQLiteDatabase, name: String): String =
        database.rawQuery("PRAGMA $name", null).use { cursor ->
            check(cursor.moveToFirst())
            cursor.getString(0)
        }

    private fun withIsolatedContext(test: (Context) -> Unit) {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val root = Files.createTempDirectory(target.cacheDir.toPath(), "rec-i3-sqlite-").toFile()
        val context =
            object : ContextWrapper(target) {
                override fun getNoBackupFilesDir(): File = root
            }
        try {
            test(context)
        } finally {
            check(root.deleteRecursively())
        }
    }

    private companion object {
        const val TIMEOUT_SECONDS = 10L
    }
}
