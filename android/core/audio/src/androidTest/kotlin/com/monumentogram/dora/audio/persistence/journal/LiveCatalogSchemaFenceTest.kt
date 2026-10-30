package com.monumentogram.dora.audio.persistence.journal

import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.model.alpha.AudioAssetId
import com.monumentogram.dora.model.alpha.RecordingId
import java.io.File
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveCatalogSchemaFenceTest {
    @Test
    fun removingRevisionTriggerCannotKeepAnOldSnapshotAvailable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.noBackupFilesDir, "live-schema-${UUID.randomUUID()}.db")
        fun id() = UUID.randomUUID().toString()
        val identity = AudioIdentity(RecordingId(id()), AudioAssetId(id()), id())
        RoomAudioJournal.open(context, file, FrameworkSQLiteOpenHelperFactory(), id(), id(), {})
            .use { journal ->
                assertTrue(journal.catalog.create(identity))
                journal.catalog.tryAcquire(identity)!!.use {
                    val cached = journal.catalog.load(identity)!!
                    assertEquals(null, cached.finalization)
                    android.database.sqlite.SQLiteDatabase.openDatabase(file.path, null, 0).use {
                        external ->
                        external.execSQL("DROP TRIGGER audio_revision_audio_asset_update")
                        external.execSQL(
                            "UPDATE audio_asset SET finalized=1 WHERE assetId=?",
                            arrayOf(identity.assetId.value),
                        )
                    }
                    // The changed flag would otherwise bypass both the row counter and immutable
                    // cache.
                    assertThrows(IllegalStateException::class.java) {
                        journal.catalog.load(identity)
                    }
                }
            }
    }
}
