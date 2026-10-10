@file:Suppress("MagicNumber", "TooGenericExceptionCaught", "LongMethod", "LongParameterList")

package com.monumentogram.dora.audio.persistence

import android.content.Context
import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.persistence.database.SqlCipherJournalHelperFactory
import com.monumentogram.dora.audio.persistence.journal.JournalSchemaVerifier
import com.monumentogram.dora.audio.persistence.keys.AndroidVaultKeyBackend
import com.monumentogram.dora.audio.persistence.keys.AndroidVaultKeystoreIo
import com.monumentogram.dora.audio.persistence.keys.KeyAccess
import com.monumentogram.dora.audio.persistence.keys.NoLogRecoveryRunAeadBackend
import com.monumentogram.dora.audio.persistence.keys.VaultBundleStorage
import com.monumentogram.dora.audio.persistence.keys.VaultKeystoreIo
import com.monumentogram.dora.audio.persistence.keys.VaultSecretStore
import com.monumentogram.dora.poc.recovery.contract.KeyConfirmationValue
import com.monumentogram.dora.poc.recovery.contract.KeyEnvelopeAad
import com.monumentogram.dora.poc.recovery.contract.KeyEnvelopeTargetKind
import com.monumentogram.dora.poc.recovery.contract.MicrofileAad
import com.monumentogram.dora.poc.recovery.contract.PublicationAad
import com.monumentogram.dora.poc.recovery.contract.PublicationKind
import com.monumentogram.dora.poc.recovery.contract.RecoveryCandidate
import com.monumentogram.dora.poc.recovery.contract.RecoveryManifestCodec
import com.monumentogram.dora.poc.recovery.contract.RecoveryManifestEntry
import com.monumentogram.dora.poc.recovery.contract.RecoveryProcessingIntent
import com.monumentogram.dora.poc.recovery.contract.RecoveryProcessingIntentInput
import com.monumentogram.dora.poc.recovery.contract.RecoveryRelativeNames
import com.monumentogram.dora.poc.recovery.contract.RunId
import com.monumentogram.dora.poc.recovery.contract.Sha256Value
import com.monumentogram.dora.poc.recovery.crypto.KeyConfirmationDecryption
import com.monumentogram.dora.poc.recovery.crypto.RecoveryRunAeadProvider
import com.monumentogram.dora.poc.recovery.crypto.RecoveryTinkRuntime
import java.io.File
import net.zetetic.database.sqlcipher.SQLiteDatabase

/** No runtime, Room, recovery, writer, quarantine, key creation, or original database open. */
internal object ProtectedReadOnlyVault {
    data class Receipt(val authenticatedBlocks: Int, val authenticatedFrames: Long)

    /** Missing signed/private policy denies this entry before any source file is accessed. */
    @Suppress(
        "SwallowedException",
        "UseCheckOrError",
    ) // Discard private message/cause; retain only static stack locations.
    fun acquireAndInspect(
        context: Context,
        destination: File,
        expected: Map<String, ProtectedReadOnlyAcquisition.Artifact>,
        identity: AudioIdentity,
        authorize: () -> Unit,
    ): Receipt =
        try {
            check(DiagnosticBuild.ENABLED) { "Protected read-only verification rejected" }
            authorize()
            val policy = DiagnosticPolicyLoader.load(context)
            check(policy.active && policy.isProtected(identity)) {
                "Protected read-only verification rejected"
            }
            val claims = policy.successorClaims()
            check(claims.isNotEmpty() && claims.all { it.assetId == identity.assetId.value }) {
                "Protected read-only verification rejected"
            }
            val copy =
                ProtectedReadOnlyAcquisition.acquire(context, destination, expected, authorize)
            val result =
                inspect(copy, policy, identity, claims.size, claims.sumOf { it.frames }, authorize)
            check(DiagnosticPolicyLoader.load(context).successorClaims() == claims)
            result
        } catch (failure: Exception) {
            throw IllegalStateException("Protected read-only verification rejected").apply {
                stackTrace = failure.stackTrace
            }
        }

    @Suppress(
        "SwallowedException",
        "UseCheckOrError",
    ) // Discard private message/cause; retain only static stack locations.
    fun inspect(
        copy: ProtectedReadOnlyAcquisition.Copy,
        policy: DiagnosticSourcePolicy,
        identity: AudioIdentity,
        expectedBlocks: Int,
        expectedFrames: Long,
        authorize: () -> Unit,
    ): Receipt =
        try {
            check(DiagnosticBuild.ENABLED && policy.active && policy.isProtected(identity))
            check(expectedBlocks in 1..100_000 && expectedFrames > 0)
            authorize()
            copy.verifySource()
            copy.verifyCopy()
            val context = copy.context
            val root = File(context.noBackupFilesDir, "dora-vault-v1")
            val bundle =
                object : VaultBundleStorage {
                    override fun reserve(selector: ByteArray): Unit = error("Read-only vault")

                    override fun persist(bundle: ByteArray): Unit = error("Read-only vault")

                    override fun readSelector() =
                        ProtectedReadOnlyAcquisition.read(File(root, "selector"), 32)

                    override fun readBundle() =
                        ProtectedReadOnlyAcquisition.read(File(root, "vault.bundle"), 256)
                }
            val existingOnly =
                object : VaultKeystoreIo by AndroidVaultKeystoreIo {
                    override fun generate(alias: String, strongBox: Boolean): Unit =
                        error("Read-only key")

                    override fun remove(alias: String): Unit = error("Read-only key")
                }
            val secrets =
                (VaultSecretStore(bundle, AndroidVaultKeyBackend(context, existingOnly))
                        .openExisting() as KeyAccess.Available)
                    .value
            policy.requireBinding(secrets.ownerId, secrets.vaultId)
            val database = File(root, "journal-${secrets.databaseObjectSelector}.db")
            check(database.canonicalFile == database.absoluteFile)
            val backend =
                NoLogRecoveryRunAeadBackend(context, secrets.vaultId, existingOnly) {
                    error("Read-only key")
                }
            val provider = RecoveryRunAeadProvider(backend)
            val result = secrets.borrowDatabaseSecret { secret ->
                // Constructor admits the native library/logger; no helper is created.
                SqlCipherJournalHelperFactory(context, database, secret).use {
                    SQLiteDatabase.openDatabase(
                            database.path,
                            secret,
                            null,
                            SQLiteDatabase.OPEN_READONLY,
                            null,
                        )
                        .use { db ->
                            verifyDatabase(db, secrets.ownerId, secrets.vaultId)
                            DiagnosticProtectedOwnership.verify(db, policy)
                            verifyCatalog(db, policy, identity, secrets.ownerId, secrets.vaultId)
                            verifyClaims(
                                root,
                                db,
                                identity,
                                expectedBlocks,
                                expectedFrames,
                                provider,
                                authorize,
                            )
                        }
                }
            }
            copy.verifySource()
            authorize()
            result
        } catch (failure: Exception) {
            throw IllegalStateException("Protected read-only verification rejected").apply {
                stackTrace = failure.stackTrace
            }
        } finally {
            if (DiagnosticBuild.ENABLED) copy.verifySource()
        }

    private fun verifyDatabase(db: SupportSQLiteDatabase, owner: String, vault: String) {
        check(db.isReadOnly && db.version == 3)
        // Connection-local only: never allow sensitive query spill to disk.
        db.execSQL("PRAGMA temp_store=MEMORY")
        db.query("PRAGMA temp_store").use { rows ->
            check(rows.moveToFirst() && rows.getInt(0) == 2 && !rows.moveToNext())
        }
        JournalSchemaVerifier.verify(db, 3)
        db.query("SELECT id,identity_hash FROM room_master_table").use { rows ->
            check(
                rows.moveToFirst() &&
                    rows.getInt(0) == 42 &&
                    rows.getString(1) == "14326b90c3941099abb8dc04701d9d5c" &&
                    !rows.moveToNext()
            )
        }
        db.query("PRAGMA cipher_integrity_check").use { rows -> check(!rows.moveToFirst()) }
        db.query("PRAGMA integrity_check").use { rows ->
            check(rows.moveToFirst() && rows.getString(0) == "ok" && !rows.moveToNext())
        }
        val binding = rows(db, "vault_binding").single()
        check(binding["singleton"] == "1")
        check(binding["ownerId"] == owner)
        check(binding["vaultId"] == vault)
    }

    private fun verifyCatalog(
        db: SupportSQLiteDatabase,
        policy: DiagnosticSourcePolicy,
        identity: AudioIdentity,
        owner: String,
        vault: String,
    ) {
        val assets = rows(db, "audio_asset").associateBy { it.getValue("assetId") }
        policy.protectedSources().forEach { source ->
            val asset = checkNotNull(assets[source.assetId.value])
            check(
                asset["recordingId"] == source.recordingId.value &&
                    asset["sessionId"] == source.sessionId &&
                    asset["ownerId"] == owner &&
                    asset["vaultId"] == vault
            )
            check(rows(db, "deletion_tombstone", "assetId", source.assetId.value).isEmpty())
        }
        val asset = checkNotNull(assets[identity.assetId.value])
        check(asset["finalized"] == "0")
        listOf("finalization_source", "deletion_target", "original_audio_reference").forEach { table
            ->
            check(rows(db, table, "assetId", identity.assetId.value).isEmpty())
        }
    }

    private fun verifyClaims(
        root: File,
        db: SupportSQLiteDatabase,
        identity: AudioIdentity,
        expectedBlocks: Int,
        expectedFrames: Long,
        provider: RecoveryRunAeadProvider,
        authorize: () -> Unit,
    ): Receipt {
        val claims =
            rows(db, "unit_claim", "assetId", identity.assetId.value).sortedBy {
                it.long("ordinal")
            }
        check(claims.size == expectedBlocks)
        val intents = rows(db, "audio_intent", "assetId", identity.assetId.value)
        check(
            intents.isEmpty() ||
                (intents.size == 1 &&
                    intents.single()["applied"] == "1" &&
                    intents.single()["kind"] == "APPEND" &&
                    intents.single()["appendRunId"] == claims.last()["runId"])
        )
        var frames = 0L
        val seen = mutableSetOf<String>()
        claims.forEachIndexed { index, claim ->
            authorize()
            verifyClaim(db, identity, claim, index, frames, seen)
            verifyBlock(root, db, claim, provider, authorize)
            frames = Math.addExact(frames, claim.long("frames"))
        }
        check(frames == expectedFrames)
        authorize()
        return Receipt(claims.size, frames)
    }

    private fun verifyClaim(
        db: SupportSQLiteDatabase,
        identity: AudioIdentity,
        claim: Map<String, String>,
        index: Int,
        frames: Long,
        seen: MutableSet<String>,
    ) {
        check(claim.long("ordinal") == index.toLong() && claim.long("firstFrame") == frames)
        check(claim["committed"] == "1" && claim.long("frames") in 1..80_000)
        val run = claim.getValue("runId")
        check(seen.add(run) && rows(db, "unit_claim", "runId", run).single() == claim)
        val physical =
            rows(db, "physical_source", "physicalId", claim.getValue("physicalId")).single()
        check(
            physical["assetId"] == identity.assetId.value &&
                physical["physicalFirstFrame"] == claim["physicalFirstFrame"]
        )
        check(
            Math.addExact(claim.long("physicalFirstFrame"), claim.long("sourceFrameOffset")) ==
                frames
        )
        check(rows(db, "quarantine_intent", "runId", run).isEmpty())
    }

    private fun verifyBlock(
        root: File,
        db: SupportSQLiteDatabase,
        claim: Map<String, String>,
        provider: RecoveryRunAeadProvider,
        authorize: () -> Unit,
    ) {
        val run = RunId.fromCanonicalString(claim.getValue("runId"))
        val runRoot = File(root, "poc-recovery/v1/runs/${run.toCanonicalString()}")
        val bootstrap = rows(db, "bootstrap", "runId", run.toCanonicalString()).single()
        val manifest = rows(db, "manifest", "runId", run.toCanonicalString()).single()
        val unit = rows(db, "microfile", "runId", run.toCanonicalString()).single()
        val candidate = RecoveryCandidate.MICROFILE
        val end = Math.multiplyExact(claim.long("frames"), 2).toULong()
        val zero = Sha256Value.ZERO
        listOf(bootstrap, manifest, unit).forEach {
            check(it["candidateId"] == candidate.contractId && it["state"] == "VALID")
        }
        val confirmation = KeyConfirmationValue(candidate, run)
        check(
            bootstrap["relativeName"] == "key-confirmation/run.kc" &&
                bootstrap["aliasHash"] == confirmation.canonicalAliasSha256.toLowercaseHex()
        )
        val runAead = provider.openExisting(run)
        check(
            runAead.decryptKeyConfirmation(artifact(runRoot, bootstrap, false), confirmation)
                is KeyConfirmationDecryption.Success
        )
        verifyBlockMetadata(manifest, unit, end)
        RecoveryRelativeNames.validateManifestCiphertext(manifest.getValue("relativeName"), 1UL)
        RecoveryRelativeNames.validateManifestKeyEnvelope(manifest.getValue("keyName"), 1UL)
        RecoveryRelativeNames.validateMicrofileCiphertext(unit.getValue("relativeName"), 0UL)
        RecoveryRelativeNames.validateMicrofileKeyEnvelope(unit.getValue("keyName"), 0UL)
        check(
            unit["processingIntent"] ==
                RecoveryProcessingIntent.calculate(
                        RecoveryProcessingIntentInput(
                            candidate,
                            run,
                            0UL,
                            0UL,
                            end,
                            unit.sha("digest"),
                        )
                    )
                    .toLowercaseHex()
        )
        val manifestKey =
            RecoveryTinkRuntime.parseEncryptedAeadKeyset(
                artifact(runRoot, manifest, true),
                runAead,
                KeyEnvelopeAad(
                    candidate,
                    run,
                    KeyEnvelopeTargetKind.MANIFEST,
                    1UL,
                    KeyEnvelopeAad.NOT_APPLICABLE_UNIT_INDEX,
                    0UL,
                    end,
                    0UL,
                    zero,
                ),
            )
        val manifestBytes =
            manifestKey.decryptPublication(
                artifact(runRoot, manifest, false),
                PublicationAad(candidate, run, PublicationKind.MANIFEST, 1UL, 0UL, end, zero),
            )
        val decoded =
            try {
                RecoveryManifestCodec.decode(manifestBytes)
            } finally {
                manifestBytes.fill(0)
            }
        val entry =
            RecoveryManifestEntry(
                0UL,
                0UL,
                end,
                5UL,
                unit.long("bytes").toULong(),
                unit.sha("digest"),
                unit.long("keyBytes").toULong(),
                unit.sha("keyDigest"),
                unit.getValue("relativeName"),
                unit.getValue("keyName"),
            )
        check(
            decoded.candidate == candidate &&
                decoded.runId == run &&
                decoded.generation == 1UL &&
                decoded.previousManifestCiphertextSha256 == zero &&
                decoded.committedEndExclusive == end &&
                decoded.entries == listOf(entry)
        )
        authorize()
        val unitKey =
            RecoveryTinkRuntime.parseEncryptedAeadKeyset(
                artifact(runRoot, unit, true),
                runAead,
                KeyEnvelopeAad(
                    candidate,
                    run,
                    KeyEnvelopeTargetKind.MICROFILE,
                    1UL,
                    0UL,
                    0UL,
                    end,
                    5UL,
                    zero,
                ),
            )
        val pcm =
            unitKey.decryptMicrofile(
                artifact(runRoot, unit, false),
                MicrofileAad(candidate, run, 1UL, 0UL, 0UL, end, 5UL, zero),
            )
        try {
            check(pcm.size.toULong() == end)
            authorize()
        } finally {
            pcm.fill(0)
        }
    }

    private fun verifyBlockMetadata(
        manifest: Map<String, String>,
        unit: Map<String, String>,
        end: ULong,
    ) {
        check(
            manifest.long("generation") == 1L &&
                manifest["kind"] == PublicationKind.MANIFEST.contractId &&
                manifest.long("endExclusive").toULong() == end &&
                manifest["previousDigest"] == Sha256Value.ZERO.toLowercaseHex()
        )
        check(
            unit.long("generation") == 1L &&
                unit.long("unitIndex") == 0L &&
                unit.long("startInclusive") == 0L &&
                unit.long("endExclusive").toULong() == end &&
                unit.long("cadence") == 5L &&
                unit["manifestDigest"] == manifest["digest"]
        )
    }

    private fun artifact(root: File, row: Map<String, String>, key: Boolean): ByteArray {
        val name = row.getValue(if (key) "keyName" else "relativeName")
        check(
            name.split('/').none { it.isEmpty() || it == "." || it == ".." } &&
                '\\' !in name &&
                ':' !in name
        )
        val bytes = ProtectedReadOnlyAcquisition.read(File(root, name), 1024 * 1024)
        check(bytes.size.toLong() == row.long(if (key) "keyBytes" else "bytes"))
        check(Sha256Value.calculate(bytes) == row.sha(if (key) "keyDigest" else "digest"))
        return bytes
    }
}

private fun rows(
    db: SupportSQLiteDatabase,
    table: String,
    column: String? = null,
    value: String? = null,
): List<Map<String, String>> {
    val cursor =
        if (column == null) db.query("SELECT * FROM $table")
        else db.query("SELECT * FROM $table WHERE $column=?", arrayOf(value))
    return cursor.use { readRows(it) }
}

private fun readRows(cursor: Cursor): List<Map<String, String>> = buildList {
    while (cursor.moveToNext()) {
        check(size < 100_000)
        add(
            cursor.columnNames.indices.filterNot(cursor::isNull).associate {
                cursor.getColumnName(it) to cursor.getString(it)
            }
        )
    }
}

private fun Map<String, String>.long(key: String) = getValue(key).toLong()

private fun Map<String, String>.sha(key: String) = Sha256Value.fromLowercaseHex(getValue(key))
