package com.monumentogram.dora.audio.diagnostic

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.monumentogram.dora.audio.persistence.DiagnosticPolicyLoader
import com.monumentogram.dora.audio.persistence.EncryptedAudioVaultFaultFixture
import java.security.MessageDigest
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DiagnosticSuccessorLoaderTest {
    private val context = EncryptedAudioVaultFaultFixture().context

    private fun id(n: Long) = UUID(0, n).toString()

    private fun digest(value: String) =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") {
            "%02x".format(it)
        }

    private fun source(n: Long) =
        JSONObject().put("recordingId", id(n)).put("assetId", id(n + 1)).put("sessionId", id(n + 2))

    @Suppress("LongMethod") // Keep the complete synthetic signed-policy shape reviewable together.
    private fun manifest(): JSONObject {
        val previous = JSONArray().apply { repeat(47) { put(source(100L + it * 3)) } }
        val added = source(1000)
        val all = JSONArray(previous.toString()).put(added)
        val ids =
            JSONArray().apply {
                for (index in 0 until all.length()) {
                    val row = all.getJSONObject(index)
                    listOf("recordingId", "assetId", "sessionId").forEach { put(row.getString(it)) }
                }
                repeat(394) { put(id(2000L + it)) }
                repeat(4) { put(id(4000L + it)) }
            }
        val runs =
            JSONArray().apply {
                repeat(394) { n ->
                    val run = id(2000L + n)
                    val uri = "android-keystore://dora.poc.recovery.v1.$run"
                    put(
                        JSONObject()
                            .put("runId", run)
                            .put("assetId", id(1001))
                            .put("physicalId", id(4000L + n / 100))
                            .put("ordinal", n)
                            .put("firstFrame", n * 80000L)
                            .put("frames", 80000)
                            .put("physicalFirstFrame", (n / 100) * 8000000L)
                            .put("sourceFrameOffset", (n % 100) * 80000L)
                            .put("canonicalKeyUri", uri)
                            .put("canonicalKeyUriSha256", digest(uri))
                            .put(
                                "physicalKeystoreAlias",
                                "dora.vault.run.v1." +
                                    digest("DORA/recovery-physical-alias/v1/${id(2)}/$run"),
                            )
                    )
                }
            }
        val physical =
            JSONArray().apply {
                repeat(4) { n ->
                    put(
                        JSONObject()
                            .put("assetId", id(1001))
                            .put("physicalId", id(4000L + n))
                            .put("physicalFirstFrame", n * 8000000L)
                    )
                }
            }
        return JSONObject()
            .put("format", "DORA_PROTECTED_SUCCESSOR_V2")
            .put("snapshotSha256", "a".repeat(64))
            .put("predecessorPolicySha256", "b".repeat(64))
            .put("custodyMappingSha256", "c".repeat(64))
            .put("package", context.packageName)
            .put("model", Build.MODEL)
            .put("api", Build.VERSION.SDK_INT)
            .put("firmware", Build.VERSION.INCREMENTAL)
            .put("ownerId", id(1))
            .put("vaultId", id(2))
            .put("protectedSources", all)
            .put("predecessorSources", previous)
            .put("additionalSource", added)
            .put("historicalIdentifiers", ids)
            .put("predecessorIdentifiers", JSONArray().apply { repeat(141) { put(id(100L + it)) } })
            .put("additionalRunBindings", runs)
            .put("additionalPhysicalSources", physical)
    }

    private fun decode(data: JSONObject) =
        DiagnosticPolicyLoader.decode(context, data.toString().toByteArray())

    @Test
    fun successorIncludesExactPredecessorAndRetainsProtectionOnEveryDecode() {
        repeat(2) { assertEquals(48, decode(manifest()).protectedSources().size) }
    }

    @Test
    fun removedHistoricalIdentityWrongKeyBindingAndOwnershipAreRejected() {
        for (kind in
            listOf("source", "key", "owner", "run", "physical", "extra-id", "physical-reentry")) {
            val data = manifest()
            when (kind) {
                "source" -> data.getJSONArray("protectedSources").put(0, source(9000))
                "key" ->
                    data
                        .getJSONArray("additionalRunBindings")
                        .getJSONObject(0)
                        .put("physicalKeystoreAlias", "wrong")
                "owner" ->
                    data
                        .getJSONArray("additionalRunBindings")
                        .getJSONObject(0)
                        .put("assetId", id(9999))
                "run" ->
                    data
                        .getJSONArray("additionalRunBindings")
                        .getJSONObject(0)
                        .put("runId", id(9999))
                "physical" -> data.getJSONArray("additionalPhysicalSources").remove(0)
                "extra-id" -> data.getJSONArray("historicalIdentifiers").put(id(9999))
                "physical-reentry" ->
                    data
                        .getJSONArray("additionalRunBindings")
                        .getJSONObject(200)
                        .put("physicalId", id(4000))
                        .put("physicalFirstFrame", 0)
                        .put("sourceFrameOffset", 16_000_000L)
            }
            assertThrows(Exception::class.java) { decode(data) }
        }
    }
}
