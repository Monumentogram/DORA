package com.monumentogram.dora.audio.persistence.journal

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.sqlite.db.SupportSQLiteDatabase

/** Durable, connection-independent fence for immutable catalog metadata snapshots. */
@Entity(tableName = "audio_catalog_revision")
internal data class AudioCatalogRevisionEntity(@PrimaryKey val singleton: Int, val revision: Long)

@Suppress(
    "MaxLineLength"
) // Preserve canonical DDL text shared by migration and schema verification.
internal object AudioCatalogRevisionSchema {
    val observedTables =
        listOf(
            "audio_asset",
            "physical_source",
            "unit_claim",
            "audio_intent",
            "bootstrap",
            "microfile",
            "manifest",
            "finalization_source",
            "quarantine_intent",
            "deletion_tombstone",
            "deletion_target",
        )
    val tables: Map<String, String> = buildMap {
        put(
            "audio_catalog_revision",
            "CREATE TABLE IF NOT EXISTS `audio_catalog_revision` (`singleton` INTEGER NOT NULL, `revision` INTEGER NOT NULL, PRIMARY KEY(`singleton`))",
        )
        observedTables.forEach { table ->
            listOf("INSERT", "UPDATE", "DELETE").forEach { operation ->
                val name = "audio_revision_${table}_${operation.lowercase()}"
                put(
                    name,
                    "CREATE TRIGGER IF NOT EXISTS `$name` AFTER $operation ON `$table` BEGIN UPDATE `audio_catalog_revision` SET `revision` = CASE WHEN `revision` < 9223372036854775807 THEN `revision` + 1 ELSE RAISE(ABORT, 'Catalog revision exhausted') END WHERE `singleton` = 1; SELECT CASE WHEN changes() != 1 THEN RAISE(ABORT, 'Catalog revision missing') END; END",
                )
            }
        }
    }

    /** Migration/onCreate only. onOpen must verify existing DDL, never repair missing fences. */
    fun install(database: SupportSQLiteDatabase) {
        tables.values.forEach(database::execSQL)
        database.execSQL(
            "INSERT INTO `audio_catalog_revision` (`singleton`,`revision`) VALUES (1,0)"
        )
    }
}
