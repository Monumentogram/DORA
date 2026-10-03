package com.monumentogram.dora.audio.persistence

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.OriginalAudioReference
import com.monumentogram.dora.audio.persistence.database.SqlCipherJournalHelperFactory
import com.monumentogram.dora.audio.persistence.journal.SEGMENTATION_SCHEMA_VERSION
import com.monumentogram.dora.audio.persistence.keys.AndroidVaultBundleStorage
import com.monumentogram.dora.audio.persistence.keys.AndroidVaultKeyBackend
import com.monumentogram.dora.audio.persistence.keys.KeyAccess
import com.monumentogram.dora.audio.persistence.keys.VaultSecretStore
import java.io.File

/** Reads the pre-death reference from its existing encrypted row, never a sidecar. */
internal object LogicalSourceCheckpoint {
    fun read(
        context: Context,
        audio: AudioIdentity,
        corruptMetadata: Boolean = false,
    ): OriginalAudioReference {
        val storage = AndroidVaultBundleStorage(context)
        val secrets =
            (VaultSecretStore(storage, AndroidVaultKeyBackend(context)).openExisting()
                    as KeyAccess.Available)
                .value
        val file = File(storage.vaultDirectory, "journal-${secrets.databaseObjectSelector}.db")
        return secrets.borrowDatabaseSecret { key ->
            val factory = SqlCipherJournalHelperFactory(context, file, key)
            val helper =
                factory.create(
                    SupportSQLiteOpenHelper.Configuration.builder(context)
                        .name(file.path)
                        .callback(
                            object : SupportSQLiteOpenHelper.Callback(SEGMENTATION_SCHEMA_VERSION) {
                                override fun onCreate(db: SupportSQLiteDatabase) =
                                    error("Existing source required")

                                override fun onUpgrade(
                                    db: SupportSQLiteDatabase,
                                    oldVersion: Int,
                                    newVersion: Int,
                                ) = error("No test migration")
                            }
                        )
                        .build()
                )
            try {
                if (corruptMetadata)
                    helper.writableDatabase.execSQL(
                        "UPDATE audio_segmentation SET profileId='invalid-profile' WHERE assetId=?",
                        arrayOf(audio.assetId.value),
                    )
                helper.writableDatabase
                    .query(
                        "SELECT version,digest,frames FROM original_audio_reference WHERE assetId=?",
                        arrayOf(audio.assetId.value),
                    )
                    .use {
                        check(it.moveToFirst())
                        OriginalAudioReference(
                            it.getInt(0),
                            audio,
                            it.getString(1),
                            it.getLong(2),
                        )
                    }
            } finally {
                helper.close()
                factory.close()
            }
        }
    }
}
