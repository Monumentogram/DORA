package com.monumentogram.dora.audio.persistence.journal

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioCatalogRevisionTest {
    @Test
    fun everyCatalogMutationAdvancesDurableRevisionAcrossConnections() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "audio-revision-${UUID.randomUUID()}.db"
        fun helper() =
            FrameworkSQLiteOpenHelperFactory()
                .create(
                    SupportSQLiteOpenHelper.Configuration.builder(context)
                        .name(name)
                        .callback(
                            object : SupportSQLiteOpenHelper.Callback(1) {
                                override fun onCreate(db: SupportSQLiteDatabase) {
                                    AudioCatalogRevisionSchema.observedTables.forEach {
                                        val extra =
                                            if (it == "deletion_target")
                                                ",assetId TEXT,kind TEXT,completed INTEGER,runId TEXT,relativeName TEXT"
                                            else ""
                                        db.execSQL(
                                            "CREATE TABLE `$it` (`test` INTEGER NOT NULL$extra)"
                                        )
                                    }
                                    AudioCatalogRevisionSchema.install(db)
                                }

                                override fun onUpgrade(
                                    db: SupportSQLiteDatabase,
                                    oldVersion: Int,
                                    newVersion: Int,
                                ) = Unit
                            }
                        )
                        .build()
                )
        helper().use { first ->
            helper().use { second ->
                val reader = first.writableDatabase
                val writer = second.writableDatabase
                fun revision() =
                    reader
                        .query("SELECT revision FROM audio_catalog_revision WHERE singleton=1")
                        .use {
                            check(it.moveToFirst())
                            it.getLong(0)
                        }
                var expected = 0L
                assertEquals(expected, revision())
                AudioCatalogRevisionSchema.observedTables.forEach { table ->
                    writer.execSQL("INSERT INTO `$table` (test) VALUES(1)")
                    assertEquals(++expected, revision())
                    writer.execSQL("UPDATE `$table` SET test=2")
                    assertEquals(++expected, revision())
                    writer.execSQL("DELETE FROM `$table`")
                    assertEquals(++expected, revision())
                }
            }
        }
        context.deleteDatabase(name)
    }
}
