@file:Suppress("MagicNumber", "TooManyFunctions")

package com.monumentogram.dora.audio.persistence

import android.content.Context
import com.monumentogram.dora.audio.AudioFormat
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.AudioStorageUnitIdentity
import com.monumentogram.dora.audio.persistence.keys.AndroidVaultBundleStorage
import com.monumentogram.dora.audio.persistence.keys.AndroidVaultKeyBackend
import com.monumentogram.dora.audio.persistence.keys.KeyAccess
import com.monumentogram.dora.audio.persistence.keys.NoLogRecoveryRunAeadBackend
import com.monumentogram.dora.audio.persistence.keys.VaultSecretStore
import com.monumentogram.dora.model.alpha.AudioAssetId
import com.monumentogram.dora.model.alpha.RecordingId
import com.monumentogram.dora.poc.recovery.contract.KeyEnvelopeAad
import com.monumentogram.dora.poc.recovery.contract.KeyEnvelopeTargetKind
import com.monumentogram.dora.poc.recovery.contract.MicrofileAad
import com.monumentogram.dora.poc.recovery.contract.RecoveryCandidate
import com.monumentogram.dora.poc.recovery.contract.RunId
import com.monumentogram.dora.poc.recovery.contract.Sha256Value
import com.monumentogram.dora.poc.recovery.crypto.RecoveryRunAeadProvider
import com.monumentogram.dora.poc.recovery.crypto.RecoveryTinkRuntime
import java.io.File
import java.security.KeyStore
import java.util.UUID
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/** All identifiers and PCM are synthetic. Random vault keys/roots remain platform generated. */
internal class SyntheticPreservationFixture(val blocks: Int = 3, val capBlocks: Int = 2) {
    val real = EncryptedAudioVaultFaultFixture()
    val context = real.context
    val root = File(context.noBackupFilesDir, "dora-vault-v1")
    val units = (0 until blocks).map(::unit)
    private val sources =
        (0 until 46)
            .map {
                AudioIdentity(
                    RecordingId(id("recording-$it")),
                    AudioAssetId(id("asset-$it")),
                    id("session-$it"),
                )
            }
            .toSet() + real.audio
    val secrets by lazy {
        (VaultSecretStore(AndroidVaultBundleStorage(context), AndroidVaultKeyBackend(context))
                .openExisting() as KeyAccess.Available)
            .value
    }
    val databaseName
        get() = "journal-${secrets.databaseObjectSelector}.db"

    val policy
        get() =
            DiagnosticSourcePolicy.protected(sources, units.map { it.unitId }.toSet())
                .boundTo(secrets.ownerId, secrets.vaultId)
                .withSuccessorClaims(
                    units.map { unit ->
                        DiagnosticProtectedClaim(
                            unit.unitId,
                            real.audio.assetId.value,
                            unit.physicalSegmentId,
                            unit.ordinal,
                            unit.firstFrame,
                            FRAMES.toLong(),
                            unit.physicalFirstFrame,
                            unit.sourceFrameOffset,
                        )
                    }
                )

    fun unit(index: Int) =
        AudioStorageUnitIdentity(
            real.audio,
            id("run-$index"),
            index,
            index * FRAMES.toLong(),
            id("physical-${index / capBlocks}"),
            (index / capBlocks) * capBlocks * FRAMES.toLong(),
            (index % capBlocks) * FRAMES.toLong(),
        )

    fun prepare(finalized: Boolean = false) {
        real.open().use { vault ->
            sources.forEach { EncryptedAudioVaultFaultFixture.success(vault.writer.create(it)) }
            units.forEach { unit ->
                val pcm = pcm(unit.ordinal)
                try {
                    EncryptedAudioVaultFaultFixture.success(
                        vault.writer.append(unit, AudioFormat.PCM, pcm)
                    )
                } finally {
                    pcm.fill(0)
                }
            }
            if (finalized)
                EncryptedAudioVaultFaultFixture.success(vault.writer.finalize(real.audio))
        }
    }

    fun acquire(name: String = "preservation-copy") =
        ProtectedReadOnlyAcquisition.acquire(
            context,
            File(context.noBackupFilesDir, name),
            ProtectedReadOnlyAcquisition.inventory(root),
        ) {}

    fun inspect(copy: ProtectedReadOnlyAcquisition.Copy, count: Int = blocks) =
        ProtectedReadOnlyVault.inspect(copy, policy, real.audio, count, count * FRAMES.toLong()) {}

    /** Source writes are fixture preparation only; callers freeze evidence afterwards. */
    fun mutate(sql: String) = database(context, false) { it.execSQL(sql) }

    fun <T> database(selected: Context, readonly: Boolean = true, block: (SQLiteDatabase) -> T): T =
        secrets.borrowDatabaseSecret { secret ->
            SQLiteDatabase.openDatabase(
                    File(selected.noBackupFilesDir, "dora-vault-v1/$databaseName").canonicalPath,
                    secret,
                    null,
                    if (readonly) SQLiteDatabase.OPEN_READONLY else SQLiteDatabase.OPEN_READWRITE,
                    null,
                )
                .use(block)
        }

    fun scalar(db: SQLiteDatabase, sql: String): Long =
        db.query(sql).use {
            check(it.moveToFirst())
            val value = it.getLong(0)
            check(!it.moveToNext())
            value
        }

    /** SHA inventory includes DB, WAL, SHM, bundle, envelopes and all ciphertext, plus absence. */
    fun unchanged(block: () -> Unit) {
        val before = ProtectedReadOnlyAcquisition.inventory(root)
        val aliases = aliases()
        try {
            block()
        } finally {
            assertEquals(
                "SOURCE bytes/names changed",
                before,
                ProtectedReadOnlyAcquisition.inventory(root),
            )
            assertEquals("Keystore aliases changed during inspection", aliases, aliases())
        }
    }

    /**
     * Independent test-only AEAD oracle. It reads only the acquired copy, compares every sample to
     * the generator, and reports only a prefix count. It does not reconcile, finalize or approve a
     * pending source. Production inspect separately verifies manifest/catalog/key confirmation.
     */
    @Suppress("LongMethod") // Keep the independent AEAD oracle and plaintext cleanup together.
    fun assertPcmPrefix(copy: ProtectedReadOnlyAcquisition.Copy, count: Int = blocks) {
        val backend =
            NoLogRecoveryRunAeadBackend(copy.context, secrets.vaultId) { error("No key mutation") }
        val provider = RecoveryRunAeadProvider(backend)
        var authenticated = 0
        var frames = 0L
        database(copy.context) { db ->
            assertTrue(db.isReadOnly)
            val sql =
                "SELECT runId,ordinal,firstFrame,frames FROM unit_claim " +
                    "WHERE assetId=? AND committed=1 ORDER BY ordinal"
            db.query(sql, arrayOf(real.audio.assetId.value)).use { claims ->
                while (claims.moveToNext()) {
                    assertTrue("Unexpected committed tail", authenticated < count)
                    val index = claims.getInt(1)
                    assertEquals(authenticated, index)
                    assertEquals(frames, claims.getLong(2))
                    assertEquals(FRAMES.toLong(), claims.getLong(3))
                    val run = RunId.fromCanonicalString(claims.getString(0))
                    val runRoot =
                        File(
                            copy.context.noBackupFilesDir,
                            "dora-vault-v1/poc-recovery/v1/runs/${run.toCanonicalString()}",
                        )
                    db.query(
                            "SELECT relativeName,keyName FROM microfile WHERE runId=?",
                            arrayOf(run.toCanonicalString()),
                        )
                        .use { row ->
                            check(row.moveToFirst())
                            val end = (FRAMES * 2).toULong()
                            val candidate = RecoveryCandidate.MICROFILE
                            val aead =
                                RecoveryTinkRuntime.parseEncryptedAeadKeyset(
                                    File(runRoot, row.getString(1)).readBytes(),
                                    provider.openExisting(run),
                                    KeyEnvelopeAad(
                                        candidate,
                                        run,
                                        KeyEnvelopeTargetKind.MICROFILE,
                                        1UL,
                                        0UL,
                                        0UL,
                                        end,
                                        5UL,
                                        Sha256Value.ZERO,
                                    ),
                                )
                            val plaintext =
                                aead.decryptMicrofile(
                                    File(runRoot, row.getString(0)).readBytes(),
                                    MicrofileAad(
                                        candidate,
                                        run,
                                        1UL,
                                        0UL,
                                        0UL,
                                        end,
                                        5UL,
                                        Sha256Value.ZERO,
                                    ),
                                )
                            val expected = pcm(index)
                            try {
                                assertArrayEquals("Synthetic PCM oracle", expected, plaintext)
                            } finally {
                                plaintext.fill(0)
                                expected.fill(0)
                            }
                            check(!row.moveToNext())
                        }
                    authenticated++
                    frames += FRAMES
                }
            }
        }
        assertEquals(count, authenticated)
        assertEquals(count * FRAMES.toLong(), frames)
    }

    companion object {
        const val FRAMES = 160

        fun id(label: String): String =
            UUID.nameUUIDFromBytes("synthetic-preservation/$label".toByteArray()).toString()

        fun aliases(): Set<String> {
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            return store.aliases().toList().toSet()
        }

        // Explicit signed PCM16 little-endian oracle, independent of encrypted catalog metadata.
        fun pcm(block: Int): ByteArray =
            ByteArray(FRAMES * 2).also { bytes ->
                repeat(FRAMES) { frame ->
                    val sample = ((block * 997 + frame * 37 + 19) % 65536) - 32768
                    bytes[frame * 2] = sample.toByte()
                    bytes[frame * 2 + 1] = (sample shr 8).toByte()
                }
            }
    }
}
