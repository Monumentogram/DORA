package com.monumentogram.dora.audio.diagnostic

import android.os.Build
import android.system.Os
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.monumentogram.dora.audio.persistence.DiagnosticPolicyLoader
import com.monumentogram.dora.audio.persistence.EncryptedAudioVaultFaultFixture
import java.io.File
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DiagnosticPolicyLoaderTest {
    private val fixture = EncryptedAudioVaultFaultFixture()
    private val context = fixture.context

    private fun id() = UUID.randomUUID().toString()

    private fun manifest(): JSONObject =
        JSONObject()
            .put("format", "DORA_PROTECTED_HISTORICAL_V1")
            .put("snapshotSha256", "a".repeat(64))
            .put("package", context.packageName)
            .put("model", Build.MODEL)
            .put("api", Build.VERSION.SDK_INT)
            .put("firmware", Build.VERSION.INCREMENTAL)
            .put("ownerId", id())
            .put("vaultId", id())
            .put("historicalIdentifiers", JSONArray().put(id()))
            .put(
                "protectedSources",
                JSONArray().apply {
                    repeat(47) {
                        put(
                            JSONObject()
                                .put("recordingId", id())
                                .put("assetId", id())
                                .put("sessionId", id())
                        )
                    }
                },
            )

    private fun decode(data: JSONObject) =
        DiagnosticPolicyLoader.decode(context, data.toString().toByteArray())

    private fun rejected(data: JSONObject) {
        assertThrows(Exception::class.java) { decode(data) }
    }

    @Test
    fun exactManifestBindsAll47SourcesAndVault() {
        val data = manifest()
        val policy = decode(data)
        assertEquals(47, policy.protectedSources().size)
        policy.requireBinding(data.getString("ownerId"), data.getString("vaultId"))
        assertThrows(IllegalStateException::class.java) { policy.requireBinding(id(), id()) }
    }

    @Test
    fun deviceAndPackageMismatchesAreRejected() {
        listOf("package", "model", "firmware").forEach { field ->
            rejected(manifest().put(field, "wrong"))
        }
        rejected(manifest().put("api", -1))
    }

    @Test
    fun sourceCardinalityAndDuplicatesAreRejected() {
        val less = manifest()
        less.getJSONArray("protectedSources").remove(46)
        rejected(less)
        val more = manifest()
        more.getJSONArray("protectedSources").put(JSONObject())
        rejected(more)
        val duplicate = manifest()
        val sources = duplicate.getJSONArray("protectedSources")
        sources.put(46, sources.get(0))
        rejected(duplicate)
    }

    @Test
    fun malformedManifestAndIdentifiersAreRejected() {
        assertThrows(Exception::class.java) {
            DiagnosticPolicyLoader.decode(context, "{".toByteArray())
        }
        rejected(manifest().put("historicalIdentifiers", JSONArray().put("*")))
        rejected(manifest().put("snapshotSha256", "invalid"))
    }

    @Test
    fun ordinaryBuildDoesNotActivateUnpinnedPrivatePolicy() {
        assertFalse(DiagnosticPolicyLoader.load(context).active)
        File(context.noBackupFilesDir, DiagnosticPolicyLoader.POLICY_FILE)
            .writeText(manifest().toString())
        assertThrows(Exception::class.java) { DiagnosticPolicyLoader.load(context) }
        assertThrows(Exception::class.java) { DiagnosticPolicyLoader.load(context) }
    }

    @Test
    fun privateFileIsReadFreshAtEveryOpen() {
        val file = File(context.noBackupFilesDir, "synthetic-policy")
        assertNull(DiagnosticPolicyLoader.readPrivateFile(file))
        file.writeText("first")
        assertArrayEquals("first".toByteArray(), DiagnosticPolicyLoader.readPrivateFile(file))
        file.writeText("second")
        assertArrayEquals("second".toByteArray(), DiagnosticPolicyLoader.readPrivateFile(file))
        assertTrue(file.delete())
        assertNull(DiagnosticPolicyLoader.readPrivateFile(file))
    }

    @Test
    fun symlinksIncludingDanglingLinksAreRejected() {
        val link = File(context.noBackupFilesDir, "policy-link")
        val target = File(context.noBackupFilesDir, "policy-target")
        Os.symlink(target.path, link.path)
        assertThrows(Exception::class.java) { DiagnosticPolicyLoader.readPrivateFile(link) }
        target.writeText("synthetic")
        assertThrows(Exception::class.java) { DiagnosticPolicyLoader.readPrivateFile(link) }
    }

    @Test
    fun emptyOversizedAndDirectoryInputsAreRejected() {
        val file = File(context.noBackupFilesDir, "bad-policy")
        file.writeBytes(byteArrayOf())
        assertThrows(Exception::class.java) { DiagnosticPolicyLoader.readPrivateFile(file) }
        file.writeBytes(ByteArray(1024 * 1024 + 1))
        assertThrows(Exception::class.java) { DiagnosticPolicyLoader.readPrivateFile(file) }
        assertThrows(Exception::class.java) {
            DiagnosticPolicyLoader.readPrivateFile(context.noBackupFilesDir)
        }
    }
}
