package com.monumentogram.dora.audio.persistence.journal

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update

/** Derived immutable provenance; retained independently of removable Recovery source rows. */
@Entity(
    tableName = "original_audio_reference",
    foreignKeys =
        [
            ForeignKey(
                entity = AssetEntity::class,
                parentColumns = ["assetId"],
                childColumns = ["assetId"],
                onDelete = ForeignKey.RESTRICT,
            )
        ],
    indices = [Index(value = ["digest"], unique = true)],
)
internal data class OriginalAudioReferenceEntity(
    @PrimaryKey val assetId: String,
    val version: Int,
    val digest: String,
    val frames: Long,
    val unavailableReason: String?,
)

@Entity(
    tableName = "vault_binding",
    indices = [Index(value = ["ownerId", "vaultId"], unique = true)],
)
internal data class VaultBindingEntity(
    @PrimaryKey val singleton: Int = 1,
    val ownerId: String,
    val vaultId: String,
)

@Entity(
    tableName = "audio_asset",
    foreignKeys =
        [
            ForeignKey(
                entity = VaultBindingEntity::class,
                parentColumns = ["ownerId", "vaultId"],
                childColumns = ["ownerId", "vaultId"],
                onDelete = ForeignKey.RESTRICT,
            )
        ],
    indices =
        [
            Index(value = ["ownerId", "vaultId"]),
            Index(value = ["recordingId", "assetId", "sessionId"], unique = true),
        ],
)
internal data class AssetEntity(
    @PrimaryKey val assetId: String,
    val recordingId: String,
    val sessionId: String,
    val ownerId: String,
    val vaultId: String,
    val finalized: Boolean = false,
)

@Entity(
    tableName = "physical_source",
    primaryKeys = ["assetId", "physicalId"],
    foreignKeys =
        [
            ForeignKey(
                entity = AssetEntity::class,
                parentColumns = ["assetId"],
                childColumns = ["assetId"],
                onDelete = ForeignKey.RESTRICT,
            )
        ],
    indices = [Index(value = ["assetId", "physicalId", "physicalFirstFrame"], unique = true)],
)
internal data class PhysicalEntity(
    val assetId: String,
    val physicalId: String,
    val physicalFirstFrame: Long,
)

/** Claims survive deletion: an occupied Recovery namespace can never be assigned again. */
@Entity(
    tableName = "unit_claim",
    foreignKeys =
        [
            ForeignKey(
                entity = AssetEntity::class,
                parentColumns = ["assetId"],
                childColumns = ["assetId"],
                onDelete = ForeignKey.RESTRICT,
            ),
            ForeignKey(
                entity = PhysicalEntity::class,
                parentColumns = ["assetId", "physicalId", "physicalFirstFrame"],
                childColumns = ["assetId", "physicalId", "physicalFirstFrame"],
                onDelete = ForeignKey.RESTRICT,
            ),
        ],
    indices =
        [
            Index(value = ["assetId", "ordinal"], unique = true),
            Index(value = ["assetId", "firstFrame"], unique = true),
            Index(value = ["assetId", "physicalId", "physicalFirstFrame"]),
        ],
)
internal data class UnitClaimEntity(
    @PrimaryKey val runId: String,
    val assetId: String,
    val ordinal: Int,
    val firstFrame: Long,
    val frames: Long,
    val physicalId: String,
    val physicalFirstFrame: Long,
    val sourceFrameOffset: Long,
    val committed: Boolean = false,
)

@Entity(
    tableName = "audio_intent",
    foreignKeys =
        [
            ForeignKey(
                entity = AssetEntity::class,
                parentColumns = ["assetId"],
                childColumns = ["assetId"],
                onDelete = ForeignKey.RESTRICT,
            ),
            ForeignKey(
                entity = UnitClaimEntity::class,
                parentColumns = ["runId"],
                childColumns = ["appendRunId"],
                onDelete = ForeignKey.RESTRICT,
            ),
        ],
    indices = [Index(value = ["appendRunId"], unique = true)],
)
internal data class IntentEntity(
    @PrimaryKey val assetId: String,
    val kind: String,
    val appendRunId: String?,
    val applied: Boolean = false,
)

@Entity(
    tableName = "bootstrap",
    foreignKeys =
        [
            ForeignKey(
                entity = UnitClaimEntity::class,
                parentColumns = ["runId"],
                childColumns = ["runId"],
                onDelete = ForeignKey.RESTRICT,
            )
        ],
    indices = [Index(value = ["aliasHash"], unique = true)],
)
internal data class BootstrapEntity(
    @PrimaryKey val runId: String,
    val candidateId: String,
    val relativeName: String,
    val bytes: Long,
    val digest: String,
    val aliasHash: String,
    val state: String,
)

@Entity(
    tableName = "manifest",
    foreignKeys =
        [
            ForeignKey(
                entity = BootstrapEntity::class,
                parentColumns = ["runId"],
                childColumns = ["runId"],
                onDelete = ForeignKey.RESTRICT,
            )
        ],
    indices = [Index(value = ["runId", "generation", "digest"], unique = true)],
)
internal data class ManifestEntity(
    @PrimaryKey val runId: String,
    val candidateId: String,
    val kind: String,
    val generation: Long,
    val endExclusive: Long,
    val relativeName: String,
    val bytes: Long,
    val digest: String,
    val keyName: String,
    val keyBytes: Long,
    val keyDigest: String,
    val previousDigest: String,
    val state: String,
)

@Entity(
    tableName = "microfile",
    foreignKeys =
        [
            ForeignKey(
                entity = BootstrapEntity::class,
                parentColumns = ["runId"],
                childColumns = ["runId"],
                onDelete = ForeignKey.RESTRICT,
            ),
            ForeignKey(
                entity = ManifestEntity::class,
                parentColumns = ["runId", "generation", "digest"],
                childColumns = ["runId", "generation", "manifestDigest"],
                onDelete = ForeignKey.RESTRICT,
            ),
        ],
    indices =
        [
            Index(value = ["runId", "unitIndex"], unique = true),
            Index(value = ["runId", "generation", "manifestDigest"]),
        ],
)
internal data class MicrofileEntity(
    @PrimaryKey val runId: String,
    val candidateId: String,
    val unitIndex: Long,
    val startInclusive: Long,
    val endExclusive: Long,
    val cadence: Long,
    val relativeName: String,
    val bytes: Long,
    val digest: String,
    val keyName: String,
    val keyBytes: Long,
    val keyDigest: String,
    val generation: Long,
    val manifestDigest: String,
    val processingIntent: String,
    val state: String,
)

/** Ordered finalization sources remain immutable after pending -> finalized transition. */
@Entity(
    tableName = "finalization_source",
    primaryKeys = ["assetId", "ordinal"],
    foreignKeys =
        [
            ForeignKey(
                entity = AssetEntity::class,
                parentColumns = ["assetId"],
                childColumns = ["assetId"],
                onDelete = ForeignKey.RESTRICT,
            ),
            ForeignKey(
                entity = ManifestEntity::class,
                parentColumns = ["runId", "generation", "digest"],
                childColumns = ["runId", "generation", "manifestDigest"],
                onDelete = ForeignKey.RESTRICT,
            ),
        ],
    indices =
        [
            Index(value = ["runId", "generation", "manifestDigest"]),
            Index(value = ["runId"], unique = true),
        ],
)
internal data class FinalSourceEntity(
    val assetId: String,
    val ordinal: Int,
    val runId: String,
    val generation: Long,
    val manifestDigest: String,
    val frames: Long,
)

@Entity(
    tableName = "quarantine_intent",
    foreignKeys =
        [
            ForeignKey(
                entity = UnitClaimEntity::class,
                parentColumns = ["runId"],
                childColumns = ["runId"],
                onDelete = ForeignKey.RESTRICT,
            ),
            ForeignKey(
                entity = BootstrapEntity::class,
                parentColumns = ["runId"],
                childColumns = ["bootstrapRunId"],
                onDelete = ForeignKey.RESTRICT,
            ),
        ],
    indices =
        [
            Index(value = ["runId", "sourceName", "sourceDigest"], unique = true),
            Index(value = ["runId", "destinationName"], unique = true),
            Index(value = ["bootstrapRunId"]),
        ],
)
internal data class QuarantineEntity(
    @PrimaryKey val intentId: String,
    val runId: String,
    val candidateId: String,
    val bootstrapRunId: String?,
    val bootstrapBinding: String,
    val role: String,
    val observed: String,
    val sourceName: String,
    val destinationName: String,
    val sourceBytes: Long,
    val sourceDigest: String,
    val state: String,
)

@Entity(
    tableName = "deletion_tombstone",
    foreignKeys =
        [
            ForeignKey(
                entity = AssetEntity::class,
                parentColumns = ["assetId"],
                childColumns = ["assetId"],
                onDelete = ForeignKey.RESTRICT,
            )
        ],
)
internal data class TombstoneEntity(
    @PrimaryKey val assetId: String,
    val operationId: String,
    val state: String,
)

@Entity(
    tableName = "deletion_target",
    primaryKeys = ["assetId", "runId", "kind", "relativeName"],
    foreignKeys =
        [
            ForeignKey(
                entity = TombstoneEntity::class,
                parentColumns = ["assetId"],
                childColumns = ["assetId"],
                onDelete = ForeignKey.RESTRICT,
            ),
            ForeignKey(
                entity = UnitClaimEntity::class,
                parentColumns = ["runId"],
                childColumns = ["runId"],
                onDelete = ForeignKey.RESTRICT,
            ),
        ],
    indices = [Index(value = ["runId"])],
)
internal data class DeletionTargetEntity(
    val assetId: String,
    val runId: String,
    val kind: String,
    val relativeName: String,
    val digest: String?,
    val completed: Boolean = false,
)

// One DAO keeps the catalog, Recovery and deletion mutations in one transaction boundary.
@Suppress("TooManyFunctions")
@Dao
internal interface AudioJournalDao {
    @Query("SELECT assetId FROM audio_asset WHERE recordingId=:recording ORDER BY assetId")
    fun recordingAssets(recording: String): List<String>

    @Query("SELECT * FROM original_audio_reference WHERE assetId=:id")
    fun originalReference(id: String): OriginalAudioReferenceEntity?

    @Insert fun insert(value: OriginalAudioReferenceEntity)

    @Update fun update(value: OriginalAudioReferenceEntity): Int

    @Query("SELECT * FROM vault_binding ORDER BY singleton")
    fun bindings(): List<VaultBindingEntity>

    @Insert fun insert(value: VaultBindingEntity)

    @Query("SELECT * FROM audio_asset WHERE assetId=:id") fun asset(id: String): AssetEntity?

    @Insert fun insert(value: AssetEntity)

    @Update fun update(value: AssetEntity): Int

    @Query("SELECT * FROM physical_source WHERE assetId=:asset AND physicalId=:id")
    fun physical(asset: String, id: String): PhysicalEntity?

    @Insert fun insert(value: PhysicalEntity)

    @Query("SELECT * FROM unit_claim WHERE assetId=:id ORDER BY ordinal")
    fun claims(id: String): List<UnitClaimEntity>

    @Query("SELECT * FROM unit_claim WHERE runId=:id") fun claim(id: String): UnitClaimEntity?

    @Insert fun insert(value: UnitClaimEntity)

    @Update fun update(value: UnitClaimEntity): Int

    @Query("SELECT * FROM audio_intent WHERE assetId=:id") fun intent(id: String): IntentEntity?

    @Insert fun insert(value: IntentEntity)

    @Update fun update(value: IntentEntity): Int

    @Query("DELETE FROM audio_intent WHERE assetId=:id") fun removeIntent(id: String): Int

    @Query("SELECT * FROM bootstrap WHERE runId=:id") fun bootstrap(id: String): BootstrapEntity?

    @Insert fun insert(value: BootstrapEntity)

    @Query("SELECT * FROM manifest WHERE runId=:id") fun manifest(id: String): ManifestEntity?

    @Insert fun insert(value: ManifestEntity)

    @Query("SELECT * FROM microfile WHERE runId=:id") fun microfile(id: String): MicrofileEntity?

    @Insert fun insert(value: MicrofileEntity)

    @Query("SELECT * FROM finalization_source WHERE assetId=:id ORDER BY ordinal")
    fun finalSources(id: String): List<FinalSourceEntity>

    @Insert fun insert(value: FinalSourceEntity)

    @Query("SELECT * FROM quarantine_intent WHERE intentId=:id")
    fun quarantine(id: String): QuarantineEntity?

    @Query("SELECT * FROM quarantine_intent WHERE runId=:run ORDER BY intentId")
    fun quarantines(run: String): List<QuarantineEntity>

    @Insert fun insert(value: QuarantineEntity)

    @Query("UPDATE quarantine_intent SET state='COMPLETED' WHERE intentId=:id AND state='PENDING'")
    fun completeQuarantine(id: String): Int

    @Query("SELECT * FROM deletion_tombstone WHERE assetId=:id")
    fun tombstone(id: String): TombstoneEntity?

    @Insert fun insert(value: TombstoneEntity)

    @Update fun update(value: TombstoneEntity): Int

    @Query("SELECT * FROM deletion_target WHERE assetId=:id ORDER BY runId,kind,relativeName")
    fun deletionTargets(id: String): List<DeletionTargetEntity>

    @Insert fun insert(value: DeletionTargetEntity)

    @Update fun update(value: DeletionTargetEntity): Int

    @Query("DELETE FROM finalization_source WHERE assetId=:id") fun removeFinalSources(id: String)

    @Query("DELETE FROM quarantine_intent WHERE runId=:run") fun removeQuarantine(run: String)

    @Query("DELETE FROM microfile WHERE runId=:run") fun removeMicrofile(run: String)

    @Query("DELETE FROM manifest WHERE runId=:run") fun removeManifest(run: String)

    @Query("DELETE FROM bootstrap WHERE runId=:run") fun removeBootstrap(run: String)
}

@Database(
    entities =
        [
            VaultBindingEntity::class,
            AssetEntity::class,
            PhysicalEntity::class,
            UnitClaimEntity::class,
            IntentEntity::class,
            BootstrapEntity::class,
            ManifestEntity::class,
            MicrofileEntity::class,
            FinalSourceEntity::class,
            QuarantineEntity::class,
            TombstoneEntity::class,
            DeletionTargetEntity::class,
            OriginalAudioReferenceEntity::class,
        ],
    version = 2,
    exportSchema = true,
)
internal abstract class AudioJournalDatabase : RoomDatabase() {
    abstract fun journal(): AudioJournalDao
}
