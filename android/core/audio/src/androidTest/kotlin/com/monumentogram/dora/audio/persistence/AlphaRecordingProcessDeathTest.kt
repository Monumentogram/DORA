package com.monumentogram.dora.audio.persistence

import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.os.Process
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.persistence.EncryptedAudioVaultFaultFixture.Companion.success
import com.monumentogram.dora.audio.persistence.LogicalRecoveryRuntimeTest.Companion.id
import com.monumentogram.dora.audio.persistence.LogicalRecoveryRuntimeTest.Companion.recovery
import com.monumentogram.dora.audio.persistence.LogicalRecoveryRuntimeTest.Companion.reference
import com.monumentogram.dora.audio.recording.RecordingPhase
import com.monumentogram.dora.audio.recording.RecordingSession
import com.monumentogram.dora.model.alpha.AudioAssetId
import com.monumentogram.dora.model.alpha.RecordingId
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlphaRecordingProcessDeathTest {
    @Test
    // Historical inventory selector; ADR-0030 now exercises 1799 -> 1800 seconds.
    fun restartPreserves299SecondsAndCannotCapturePast300() {
        val action = InstrumentationRegistry.getArguments().getString("persistenceCrashAction")
        val base = ApplicationProvider.getApplicationContext<Context>()
        val root =
            File(
                base.noBackupFilesDir,
                "alpha-crash-" + if (action == null) UUID.randomUUID() else "host",
            )
        val context =
            object : ContextWrapper(base) {
                override fun getApplicationContext(): Context = this

                override fun getNoBackupFilesDir(): File = root
            }
        val identity = AudioIdentity(RecordingId(id(901)), AudioAssetId(id(902)), id(903))
        if (action == null || action == "PREPARE") {
            check(root.mkdir())
            open(context, true).use { vault ->
                val session = RecordingSession(identity, vault.writer)
                assertTrue(session.start())
                repeat(359) { session.accept(ByteArray(160000) { 7 }) }
                session.accept(ByteArray(128000) { 7 })
                session.pause()
                assertEquals(28_784_000L, session.state.durableFrames)
                if (action != null) {
                    signal("READY")
                    CountDownLatch(1).await(120, TimeUnit.SECONDS)
                    error("Host failed to terminate prepared process")
                }
            }
        }
        require(action == null || action == "VERIFY")
        open(context, false).use { vault ->
            val recovered = recovery(vault, identity)
            assertEquals(28_784_000L, recovered.recoveredFrames)
            assertTrue(recovered.canResume)
            val session = RecordingSession(identity, vault.writer)
            session.restore(recovered.recoveredFrames, recovered.nextOrdinal)
            assertTrue(session.resume())
            session.accept(ByteArray(160000) { 7 })
            assertEquals(28_800_000L, session.state.frames)
            assertFalse(session.canCapture)
            session.requestStop()
            session.confirmStop()
            assertEquals(RecordingPhase.SAVED, session.state.phase)
            val original = reference(vault, identity)
            var frames = 0L
            success(
                vault.originals.extract(original) { first, bytes ->
                    assertEquals(frames, first)
                    assertTrue(bytes.all { it == 7.toByte() })
                    frames += bytes.size / 2
                }
            )
            assertEquals(28_800_000L, frames)
        }
        if (action != null) signal("VERIFIED")
    }

    private fun open(context: Context, create: Boolean) =
        (EncryptedAudioVault.open(context, create, {}, { it() }) as AudioResult.Value).value

    private fun signal(kind: String) =
        InstrumentationRegistry.getInstrumentation()
            .sendStatus(
                2,
                Bundle().apply {
                    putString("stream", "DORA_PERSISTENCE_$kind:ALPHA_299:${Process.myPid()}")
                },
            )
}
