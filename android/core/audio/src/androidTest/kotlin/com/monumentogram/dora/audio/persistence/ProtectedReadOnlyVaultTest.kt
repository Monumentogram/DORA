package com.monumentogram.dora.audio.persistence

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.persistence.keys.AndroidVaultBundleStorage
import com.monumentogram.dora.audio.persistence.keys.AndroidVaultKeyBackend
import com.monumentogram.dora.audio.persistence.keys.KeyAccess
import com.monumentogram.dora.audio.persistence.keys.VaultSecretStore
import com.monumentogram.dora.model.alpha.AudioAssetId
import com.monumentogram.dora.model.alpha.RecordingId
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProtectedReadOnlyVaultTest {
    private val fixture = EncryptedAudioVaultFaultFixture()

    private fun identity() =
        AudioIdentity(
            RecordingId(EncryptedAudioVaultFaultFixture.id()),
            AudioAssetId(EncryptedAudioVaultFaultFixture.id()),
            EncryptedAudioVaultFaultFixture.id(),
        )

    @Test
    @Suppress(
        "LongMethod"
    ) // One retained encrypted fixture spans pinned startup, readback, restart and corruption
    // proofs.
    fun fullSuccessor394UsesPinnedEntryAndRejectsCatalogOwnershipDrift() {
        val old = (1..47).map { identity() }
        val runs = (0 until 394).map { EncryptedAudioVaultFaultFixture.id() }
        val physical = (0 until 4).map { EncryptedAudioVaultFaultFixture.id() }
        val pcm = ByteArray(160_000) { (it * 37 + 19).toByte() }
        fixture.open().use { vault ->
            (old + fixture.audio).forEach {
                EncryptedAudioVaultFaultFixture.success(vault.writer.create(it))
            }
            runs.forEachIndexed { index, run ->
                val unit =
                    com.monumentogram.dora.audio.AudioStorageUnitIdentity(
                        fixture.audio,
                        run,
                        index,
                        index * 80_000L,
                        physical[index / 100],
                        (index / 100) * 8_000_000L,
                        (index % 100) * 80_000L,
                    )
                EncryptedAudioVaultFaultFixture.success(
                    vault.writer.append(
                        unit,
                        com.monumentogram.dora.audio.AudioFormat.PCM,
                        pcm,
                    )
                )
            }
            val oracle =
                vault.reader.extract(fixture.audio) { _, bytes ->
                    org.junit.Assert.assertArrayEquals(pcm, bytes)
                }
            EncryptedAudioVaultFaultFixture.success(oracle)
        }
        pcm.fill(0)
        val secrets =
            (VaultSecretStore(
                        AndroidVaultBundleStorage(fixture.context),
                        AndroidVaultKeyBackend(fixture.context),
                    )
                    .openExisting() as KeyAccess.Available)
                .value
        val policy = fullPolicy(old, runs, physical, secrets.ownerId, secrets.vaultId)
        val policyBytes = policy.toString().toByteArray()
        File(fixture.context.noBackupFilesDir, DiagnosticPolicyLoader.POLICY_FILE)
            .writeBytes(policyBytes)
        val pin = hash(policyBytes)
        val zip = File(fixture.context.noBackupFilesDir, "synthetic-policy-assets.apk")
        java.util.zip.ZipOutputStream(zip.outputStream()).use {
            it.putNextEntry(java.util.zip.ZipEntry("assets/${DiagnosticPolicyLoader.PIN_ASSET}"))
            it.write(pin.toByteArray())
            it.closeEntry()
        }
        // Only test resource mounting uses reflection; every production protection/crypto path is
        // typed.
        val assets =
            android.content.res.AssetManager::class.java.getDeclaredConstructor().newInstance()
        val cookie =
            android.content.res.AssetManager::class
                .java
                .getMethod("addAssetPath", String::class.java)
                .invoke(assets, zip.path) as Int
        check(cookie != 0)
        val pinned =
            object : android.content.ContextWrapper(fixture.context) {
                override fun getAssets() = assets

                override fun getApplicationContext(): android.content.Context = this
            }
        try {
            val admitted = DiagnosticPolicyLoader.load(pinned)
            assertEquals(394, admitted.successorClaims().size)
            val ordinary = EncryptedAudioVault.open(pinned, false, {}, { it() })
            EncryptedAudioVaultFaultFixture.success(ordinary)
            val fresh = identity()
            val freshPcm = ByteArray(320) { (it * 13).toByte() }
            (ordinary as com.monumentogram.dora.audio.AudioResult.Value).value.use { vault ->
                org.junit.Assert.assertTrue(
                    vault.writer.finalize(fixture.audio)
                        is com.monumentogram.dora.audio.AudioResult.Failed
                )
                EncryptedAudioVaultFaultFixture.success(vault.writer.create(fresh))
                EncryptedAudioVaultFaultFixture.success(
                    vault.writer.append(
                        com.monumentogram.dora.audio.AudioStorageUnitIdentity(
                            fresh,
                            EncryptedAudioVaultFaultFixture.id(),
                            0,
                            0,
                        ),
                        com.monumentogram.dora.audio.AudioFormat.PCM,
                        freshPcm,
                    )
                )
            }
            val reopened = EncryptedAudioVault.open(pinned, false, {}, { it() })
            EncryptedAudioVaultFaultFixture.success(reopened)
            (reopened as com.monumentogram.dora.audio.AudioResult.Value).value.use { vault ->
                val recovered = vault.recordingRecovery(fresh)
                org.junit.Assert.assertTrue(
                    recovered is com.monumentogram.dora.audio.AudioResult.Value &&
                        recovered.value.canResume
                )
                EncryptedAudioVaultFaultFixture.success(vault.writer.finalize(fresh))
                EncryptedAudioVaultFaultFixture.success(
                    vault.reader.extract(fresh) { _, bytes ->
                        org.junit.Assert.assertArrayEquals(freshPcm, bytes)
                    }
                )
                EncryptedAudioVaultFaultFixture.success(vault.deleteAudio(fresh))
            }
            freshPcm.fill(0)
            val root = File(fixture.context.noBackupFilesDir, "dora-vault-v1")
            var expected = ProtectedReadOnlyAcquisition.inventory(root)
            repeat(2) { index ->
                val receipt =
                    ProtectedReadOnlyVault.acquireAndInspect(
                        pinned,
                        File(fixture.context.noBackupFilesDir, "full-copy-$index"),
                        expected,
                        fixture.audio,
                    ) {}
                assertEquals(394, receipt.authenticatedBlocks)
                assertEquals(31_520_000L, receipt.authenticatedFrames)
                assertEquals(expected, ProtectedReadOnlyAcquisition.inventory(root))
            }
            val database = File(root, "journal-${secrets.databaseObjectSelector}.db")
            fun mutate(sql: String, args: Array<Any>) {
                secrets.borrowDatabaseSecret { secret ->
                    com.monumentogram.dora.audio.persistence.database
                        .SqlCipherJournalHelperFactory(pinned, database, secret)
                        .use {
                            net.zetetic.database.sqlcipher.SQLiteDatabase.openDatabase(
                                    database.canonicalPath,
                                    secret,
                                    null,
                                    net.zetetic.database.sqlcipher.SQLiteDatabase.OPEN_READWRITE,
                                    null,
                                )
                                .use { db ->
                                    db.execSQL(
                                        "PRAGMA foreign_keys=OFF"
                                    ) // Deliberately corrupt this synthetic fixture only.
                                    db.execSQL(sql, args)
                                }
                        }
                }
            }
            fun rejectsCopy(name: String) {
                expected = ProtectedReadOnlyAcquisition.inventory(root)
                assertThrows(Exception::class.java) {
                    ProtectedReadOnlyVault.acquireAndInspect(
                        pinned,
                        File(fixture.context.noBackupFilesDir, name),
                        expected,
                        fixture.audio,
                    ) {}
                }
                assertEquals(expected, ProtectedReadOnlyAcquisition.inventory(root))
                val normal = EncryptedAudioVault.open(pinned, false, {}, { it() })
                org.junit.Assert.assertTrue(
                    normal is com.monumentogram.dora.audio.AudioResult.Failed
                )
            }
            mutate(
                "UPDATE bootstrap SET aliasHash=? WHERE runId=?",
                arrayOf("f".repeat(64), runs[0]),
            )
            rejectsCopy("wrong-key-copy")
            mutate(
                "UPDATE bootstrap SET aliasHash=? WHERE runId=?",
                arrayOf(
                    hash("android-keystore://dora.poc.recovery.v1.${runs[0]}".toByteArray()),
                    runs[0],
                ),
            )
            mutate(
                "UPDATE unit_claim SET assetId=? WHERE runId=?",
                arrayOf(old[0].assetId.value, runs[0]),
            )
            rejectsCopy("wrong-owner-copy")
            mutate(
                "UPDATE unit_claim SET assetId=? WHERE runId=?",
                arrayOf(fixture.audio.assetId.value, runs[0]),
            )
            mutate("UPDATE unit_claim SET committed=0 WHERE runId=?", arrayOf(runs.last()))
            rejectsCopy("missing-committed-claim-copy")
        } finally {
            assets.close()
        }
    }

    private fun hash(bytes: ByteArray) =
        java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
            "%02x".format(it)
        }

    @Suppress(
        "LongMethod"
    ) // Keep the full synthetic policy and its exact source/run/physical bindings together.
    private fun fullPolicy(
        old: List<AudioIdentity>,
        runs: List<String>,
        physical: List<String>,
        owner: String,
        vault: String,
    ): org.json.JSONObject {
        fun source(value: AudioIdentity) =
            org.json
                .JSONObject()
                .put("recordingId", value.recordingId.value)
                .put("assetId", value.assetId.value)
                .put("sessionId", value.sessionId)
        fun components(value: AudioIdentity) =
            listOf(value.recordingId.value, value.assetId.value, value.sessionId)
        val previous = old.flatMap(::components)
        val claims =
            org.json.JSONArray().apply {
                runs.forEachIndexed { index, run ->
                    val uri = "android-keystore://dora.poc.recovery.v1.$run"
                    put(
                        org.json
                            .JSONObject()
                            .put("runId", run)
                            .put("assetId", fixture.audio.assetId.value)
                            .put("physicalId", physical[index / 100])
                            .put("ordinal", index)
                            .put("firstFrame", index * 80_000L)
                            .put("frames", 80_000)
                            .put("physicalFirstFrame", (index / 100) * 8_000_000L)
                            .put("sourceFrameOffset", (index % 100) * 80_000L)
                            .put("canonicalKeyUri", uri)
                            .put("canonicalKeyUriSha256", hash(uri.toByteArray()))
                            .put(
                                "physicalKeystoreAlias",
                                "dora.vault.run.v1." +
                                    hash(
                                        "DORA/recovery-physical-alias/v1/$vault/$run".toByteArray()
                                    ),
                            )
                    )
                }
            }
        val physicalRows =
            org.json.JSONArray().apply {
                physical.forEachIndexed { index, value ->
                    put(
                        org.json
                            .JSONObject()
                            .put("assetId", fixture.audio.assetId.value)
                            .put("physicalId", value)
                            .put("physicalFirstFrame", index * 8_000_000L)
                    )
                }
            }
        return org.json
            .JSONObject()
            .put("format", "DORA_PROTECTED_SUCCESSOR_V2")
            .put("snapshotSha256", "a".repeat(64))
            .put("predecessorPolicySha256", "b".repeat(64))
            .put("custodyMappingSha256", "c".repeat(64))
            .put("package", fixture.context.packageName)
            .put("model", android.os.Build.MODEL)
            .put("api", android.os.Build.VERSION.SDK_INT)
            .put("firmware", android.os.Build.VERSION.INCREMENTAL)
            .put("ownerId", owner)
            .put("vaultId", vault)
            .put(
                "predecessorSources",
                org.json.JSONArray().apply { old.forEach { put(source(it)) } },
            )
            .put("additionalSource", source(fixture.audio))
            .put(
                "protectedSources",
                org.json.JSONArray().apply {
                    (old + fixture.audio).forEach { put(source(it)) }
                },
            )
            .put("predecessorIdentifiers", org.json.JSONArray(previous))
            .put(
                "historicalIdentifiers",
                org.json.JSONArray(previous + components(fixture.audio) + runs + physical),
            )
            .put("additionalRunBindings", claims)
            .put("additionalPhysicalSources", physicalRows)
    }

    private fun prepare(): DiagnosticSourcePolicy {
        val sources = (1..47).map { identity() }.toSet() + fixture.audio
        fixture.open().use { vault ->
            sources.forEach { EncryptedAudioVaultFaultFixture.success(vault.writer.create(it)) }
            EncryptedAudioVaultFaultFixture.success(fixture.append(vault))
            fixture.exact(vault)
        }
        val secrets =
            (VaultSecretStore(
                        AndroidVaultBundleStorage(fixture.context),
                        AndroidVaultKeyBackend(fixture.context),
                    )
                    .openExisting() as KeyAccess.Available)
                .value
        return DiagnosticSourcePolicy.protected(sources, setOf(fixture.unit.unitId))
            .boundTo(secrets.ownerId, secrets.vaultId)
    }

    @Test
    fun missingPinnedPolicyDeniesEntryBeforeCreatingCopy() {
        prepare()
        val root = File(fixture.context.noBackupFilesDir, "dora-vault-v1")
        val before = ProtectedReadOnlyAcquisition.inventory(root)
        val destination = File(fixture.context.noBackupFilesDir, "unadmitted-copy")
        assertThrows(Exception::class.java) {
            ProtectedReadOnlyVault.acquireAndInspect(
                fixture.context,
                destination,
                before,
                fixture.audio,
            ) {}
        }
        org.junit.Assert.assertFalse(destination.exists())
        assertEquals(before, ProtectedReadOnlyAcquisition.inventory(root))
    }

    @Test
    fun completeAuthenticationOnEncryptedCopyLeavesSourceIdenticalAcrossRestart() {
        val policy = prepare()
        val root = File(fixture.context.noBackupFilesDir, "dora-vault-v1")
        val before = ProtectedReadOnlyAcquisition.inventory(root)
        repeat(2) { index ->
            val copy =
                ProtectedReadOnlyAcquisition.acquire(
                    fixture.context,
                    File(fixture.context.noBackupFilesDir, "copy-$index"),
                    before,
                ) {}
            val receipt = ProtectedReadOnlyVault.inspect(copy, policy, fixture.audio, 1, 160) {}
            assertEquals(160L, receipt.authenticatedFrames)
            assertEquals(1, receipt.authenticatedBlocks)
            assertEquals(before, ProtectedReadOnlyAcquisition.inventory(root))
        }
    }

    @Test
    fun wrongOwnershipAndRevocationFailWithoutChangingOriginal() {
        val policy = prepare()
        val root = File(fixture.context.noBackupFilesDir, "dora-vault-v1")
        val before = ProtectedReadOnlyAcquisition.inventory(root)
        val copy =
            ProtectedReadOnlyAcquisition.acquire(
                fixture.context,
                File(fixture.context.noBackupFilesDir, "copy"),
                before,
            ) {}
        assertThrows(Exception::class.java) {
            ProtectedReadOnlyVault.inspect(copy, policy, identity(), 1, 160) {}
        }
        assertThrows(Exception::class.java) {
            ProtectedReadOnlyVault.inspect(copy, policy, fixture.audio, 1, 160) { error("REVOKED") }
        }
        var authorizations = 0
        assertThrows(Exception::class.java) {
            ProtectedReadOnlyVault.inspect(copy, policy, fixture.audio, 1, 160) {
                authorizations++
                if (authorizations == 4) error("REVOKED_DURING_AUTHENTICATED_READ")
            }
        }
        assertEquals(4, authorizations)
        assertEquals(before, ProtectedReadOnlyAcquisition.inventory(root))
    }

    @Test
    fun missingRunKeyFailsWithoutGeneratingReplacementOrChangingFiles() {
        val policy = prepare()
        val root = File(fixture.context.noBackupFilesDir, "dora-vault-v1")
        val before = ProtectedReadOnlyAcquisition.inventory(root)
        val copy =
            ProtectedReadOnlyAcquisition.acquire(
                fixture.context,
                File(fixture.context.noBackupFilesDir, "copy"),
                before,
            ) {}
        val secrets =
            (VaultSecretStore(
                        AndroidVaultBundleStorage(fixture.context),
                        AndroidVaultKeyBackend(fixture.context),
                    )
                    .openExisting() as KeyAccess.Available)
                .value
        val text = "DORA/recovery-physical-alias/v1/${secrets.vaultId}/${fixture.unit.unitId}"
        val alias =
            "dora.vault.run.v1." +
                java.security.MessageDigest.getInstance("SHA-256")
                    .digest(text.toByteArray())
                    .joinToString("") { "%02x".format(it) }
        val keys = com.monumentogram.dora.audio.persistence.keys.AndroidVaultKeystoreIo
        keys.remove(alias)
        assertThrows(Exception::class.java) {
            ProtectedReadOnlyVault.inspect(copy, policy, fixture.audio, 1, 160) {}
        }
        org.junit.Assert.assertFalse(keys.exists(alias))
        assertEquals(before, ProtectedReadOnlyAcquisition.inventory(root))
    }

    @Test
    fun changedOrMissingCopiedCiphertextIsRejectedWithoutSourceMutation() {
        val policy = prepare()
        val root = File(fixture.context.noBackupFilesDir, "dora-vault-v1")
        val before = ProtectedReadOnlyAcquisition.inventory(root)
        val copy =
            ProtectedReadOnlyAcquisition.acquire(
                fixture.context,
                File(fixture.context.noBackupFilesDir, "copy"),
                before,
            ) {}
        val encrypted =
            File(copy.context.noBackupFilesDir, "dora-vault-v1").walkTopDown().single {
                it.isFile && it.path.contains("units") && !it.path.contains("key")
            }
        val bytes = encrypted.readBytes()
        encrypted.writeBytes(
            bytes.copyOf().apply { this[lastIndex] = (last().toInt() xor 1).toByte() }
        )
        assertThrows(Exception::class.java) {
            ProtectedReadOnlyVault.inspect(copy, policy, fixture.audio, 1, 160) {}
        }
        check(encrypted.delete())
        assertThrows(Exception::class.java) {
            ProtectedReadOnlyVault.inspect(copy, policy, fixture.audio, 1, 160) {}
        }
        assertEquals(before, ProtectedReadOnlyAcquisition.inventory(root))
    }
}
