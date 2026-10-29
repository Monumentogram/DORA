@file:Suppress("MagicNumber", "LongMethod")

package com.monumentogram.dora.transcript

import android.util.Base64
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.monumentogram.dora.MainActivity
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.OriginalAudioStatus
import com.monumentogram.dora.model.alpha.AudioAssetId
import com.monumentogram.dora.model.alpha.RecordingId
import java.io.ByteArrayOutputStream
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Explicit two-process protocol: seed old APK, install -r new APK, then verify; never clears data.
 */
class AlphaUpgradeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val preferences
        get() = context.getSharedPreferences("stage12-upgrade", 0)

    @Test
    fun prepare() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("upgradePhase") == "prepare")
        check(!preferences.contains("recording")) { "Never overwrite an existing fixture" }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val fixture = AlphaFixture(alphaSession(scenario))
            var doc =
                alphaValue(
                    fixture.session.transcripts.finishCloud(
                        fixture.source,
                        fixture.cloud,
                        fixture.result(),
                    )
                )
            doc =
                alphaValue(
                    fixture.session.transcripts.edit(
                        fixture.identity,
                        fixture.cloud,
                        doc.revision,
                        "Выбранная Cloud правка",
                        6,
                    )
                )
            alphaValue(
                fixture.session.transcripts.select(
                    fixture.identity,
                    fixture.cloud,
                    doc.revision,
                    doc.edits.filter { it.versionId == fixture.local }.map { it.id }.toSet(),
                    7,
                )
            )
            assertTrue(
                preferences
                    .edit()
                    .putString("recording", fixture.identity.recordingId.value)
                    .putString("asset", fixture.identity.assetId.value)
                    .putString("session", fixture.identity.sessionId)
                    .putString("local", fixture.local)
                    .putString("cloud", fixture.cloud)
                    .putString("setting", "ru-owner")
                    .commit()
            )
            val cloudIdentity = CloudIdentity(context)
            if (cloudIdentity.endpoint() == null) cloudIdentity.setEndpoint("https://dora.invalid")
            assertTrue(
                preferences
                    .edit()
                    .putString("cloudIdentity", cloudIdentity.publicInvitation())
                    .putString("endpoint", cloudIdentity.endpoint())
                    .commit()
            )
            if (InstrumentationRegistry.getArguments().getString("upgradeModel") == "true") {
                instrumentation.context.assets.open("stage12-model.bin").use {
                    assertTrue(LocalAsrModel(context).install(it))
                }
                assertTrue(preferences.edit().putBoolean("model", true).commit())
            }
        }
    }

    @Test
    fun verify() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("upgradePhase") == "verify")
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val session = alphaSession(scenario)
            val identity =
                AudioIdentity(
                    RecordingId(checkNotNull(preferences.getString("recording", null))),
                    AudioAssetId(checkNotNull(preferences.getString("asset", null))),
                    checkNotNull(preferences.getString("session", null)),
                )
            val local = checkNotNull(preferences.getString("local", null))
            val cloud = checkNotNull(preferences.getString("cloud", null))
            val doc = alphaValue(session.transcripts.load(identity))!!
            assertEquals(2, doc.versions.size)
            assertEquals("Original local", doc.versions.single { it.id == local }.text)
            assertEquals("Cloud proposal", doc.versions.single { it.id == cloud }.text)
            assertEquals("Правка владельца 😀", doc.rendered(local))
            assertEquals("Выбранная Cloud правка", doc.rendered(cloud))
            assertEquals(cloud, doc.activeId)
            assertEquals(2, doc.edits.size)
            assertEquals(1, doc.selections.size)
            assertEquals("ru-owner", preferences.getString("setting", null))
            val cloudIdentity = CloudIdentity(context)
            val original = JSONObject(checkNotNull(preferences.getString("cloudIdentity", null)))
            assertEquals(original.getString("installation_id"), cloudIdentity.installationId)
            assertEquals(preferences.getString("endpoint", null), cloudIdentity.endpoint())
            val publicKey =
                KeyFactory.getInstance("EC")
                    .generatePublic(
                        X509EncodedKeySpec(
                            Base64.decode(
                                original.getString("public_key_spki_base64"),
                                Base64.NO_WRAP,
                            )
                        )
                    )
            val challenge = "Stage12 synthetic upgrade".toByteArray()
            assertTrue(
                Signature.getInstance("SHA256withECDSA").run {
                    initVerify(publicKey)
                    update(challenge)
                    verify(Base64.decode(cloudIdentity.sign(challenge), Base64.NO_WRAP))
                }
            )
            assertTrue(
                alphaValue(session.originals.acquire(identity)) is OriginalAudioStatus.Available
            )
            val audio = ByteArrayOutputStream()
            alphaValue(session.originals.extract(doc.source) { _, bytes -> audio.write(bytes) })
            assertArrayEquals(ByteArray(96000) { (it % 127).toByte() }, audio.toByteArray())
            if (preferences.getBoolean("model", false))
                assertNotNull(LocalAsrModel(context).installed())
        }
    }
}
