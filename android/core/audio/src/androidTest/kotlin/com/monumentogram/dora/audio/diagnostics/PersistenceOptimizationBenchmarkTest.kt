package com.monumentogram.dora.audio.diagnostics

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Debug
import android.os.Process
import androidx.test.platform.app.InstrumentationRegistry
import com.monumentogram.dora.audio.AudioFormat
import com.monumentogram.dora.audio.AudioStorageUnitIdentity
import com.monumentogram.dora.audio.EncryptedAudioCatalog
import com.monumentogram.dora.audio.PersistenceLatency
import com.monumentogram.dora.audio.persistence.EncryptedAudioVault
import com.monumentogram.dora.audio.persistence.EncryptedAudioVaultFaultFixture
import com.monumentogram.dora.audio.persistence.EncryptedAudioVaultFaultFixture.Companion.id
import com.monumentogram.dora.audio.persistence.EncryptedAudioVaultFaultFixture.Companion.success
import com.monumentogram.dora.poc.recovery.candidate.RecoveryCandidateStorage
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Identical C3 harness for baseline/optimized; synthetic real encryption, no capture. */
class PersistenceOptimizationBenchmarkTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val arguments = InstrumentationRegistry.getArguments()
    private val probe = PersistenceOptimizationProbe()
    private lateinit var output: File

    @Test
    @Suppress("LongMethod", "NestedBlockDepth") // One continuously open vault is the control.
    fun controlledEncryptedGrowth() {
        assumeTrue(arguments.getString("persistenceOptimizationC3") == "true")
        assertTrue("Emulator only", Build.HARDWARE in setOf("ranchu", "goldfish"))
        val base = instrumentation.targetContext
        assertEquals("com.monumentogram.dora.audio.test", base.packageName)
        assertTrue(
            (base.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isDeviceSecure
        )
        assertTrue(Build.VERSION.SDK_INT in setOf(28, 36))
        val variant = arguments.getString("variant")
        require(variant in setOf("baseline", "optimized"))
        val source = checkNotNull(arguments.getString("sourceSha256"))
        require(source.matches(Regex("[0-9a-f]{64}")))
        val maximum = arguments.getString("maxBlocks", "2000").toInt()
        require(maximum in setOf(10, 400, 1000, 2000))
        output = File(base.filesDir, "persistence-c3-${System.currentTimeMillis()}.jsonl")
        check(output.createNewFile())
        emit(
            JSONObject()
                .put("event", "BEGIN")
                .put("api", Build.VERSION.SDK_INT)
                .put("maximum", maximum)
                .put("variant", variant)
                .put("sourceSha256", source)
                .put("pcmBytes", 160000)
                .put("groupBlocks", 120)
        )
        val fixture = EncryptedAudioVaultFaultFixture()
        lateinit var catalog: EncryptedAudioCatalog
        val dependencies =
            EncryptedAudioVault.Dependencies(
                catalog = {
                    catalog = it
                    it
                },
                helperFactory = probe::factory,
                candidateStorage = {
                    probe.wrap(it, RecoveryCandidateStorage::class.java, "candidate")
                },
            )
        val pcm = ByteArray(160000) { (it * 37 + 19).toByte() }
        val physicalIds = mutableMapOf<Int, String>()
        var ordinal = 0
        fun checkCatalog() {
            checkNotNull(catalog.tryAcquire(fixture.audio)).use {
                val snapshot = checkNotNull(catalog.load(fixture.audio))
                assertEquals(ordinal, snapshot.segments.size)
                assertEquals(null, snapshot.pending)
                assertEquals(null, snapshot.finalization)
                snapshot.segments.forEachIndexed { index, segment ->
                    assertEquals(index, segment.identity.ordinal)
                    assertEquals(index * 80000L, segment.identity.firstFrame)
                    assertEquals(80000L, segment.frames)
                }
            }
        }
        fun checkPcm(vault: EncryptedAudioVault) {
            var checked = 0
            success(
                vault.reader.extract(fixture.audio) { frame, bytes ->
                    assertEquals(checked * 80000L, frame)
                    assertTrue(pcm.contentEquals(bytes))
                    checked++
                }
            )
            assertEquals(ordinal, checked)
        }
        fixture.open(dependencies = dependencies).use { vault ->
            success(vault.writer.createLogicalRecording(fixture.audio))
            fun append(measured: Boolean) {
                val group = ordinal / 120
                val physicalStart = group * 9600000L
                val unit =
                    AudioStorageUnitIdentity(
                        fixture.audio,
                        id(),
                        ordinal,
                        ordinal * 80000L,
                        physicalIds.getOrPut(group) { id() },
                        physicalStart,
                        ordinal * 80000L - physicalStart,
                    )
                val action = { success(vault.writer.append(unit, AudioFormat.PCM, pcm)) }
                if (measured) measure("append", ordinal, action) else action()
                ordinal++
            }
            for (target in listOf(10, 400, 1000, 2000).filter { it <= maximum }) {
                while (ordinal < target) {
                    append(false)
                    if (ordinal % 50 == 0)
                        emit(JSONObject().put("event", "PROGRESS").put("blocks", ordinal))
                }
                checkCatalog() // Symmetric unmeasured catalog warmup.
                repeat(3) { measure("catalog_load", ordinal, ::checkCatalog) }
                repeat(7) { append(true) }
                emit(
                    JSONObject()
                        .put("event", "CHECKPOINT")
                        .put("target", target)
                        .put("actualBlocks", ordinal)
                )
            }
            checkCatalog()
            checkPcm(vault)
        }
        fixture.open(create = false, dependencies = dependencies).use { vault ->
            checkCatalog()
            checkPcm(vault)
        }
        pcm.fill(0)
        emit(
            JSONObject()
                .put("event", "COMPLETE")
                .put("blocks", ordinal)
                .put("authenticatedPcmFrames", ordinal * 80000L)
                .put("reopenedAuthenticatedPcmFrames", ordinal * 80000L)
                .put("reopenedCatalogVerified", true)
        )
    }

    private fun measure(kind: String, blocks: Int, action: () -> Unit) {
        var stages = emptyMap<String, Long>()
        probe.start()
        val cpu = Debug.threadCpuTimeNanos()
        val processCpu = Process.getElapsedCpuTime()
        val start = System.nanoTime()
        PersistenceLatency.collect({ stages = it }, action)
        val elapsed = System.nanoTime() - start
        val cpuNanos = Debug.threadCpuTimeNanos() - cpu
        val processCpuMs = Process.getElapsedCpuTime() - processCpu
        probe.finish()
        val stageOrder =
            stages.keys.filter {
                it in setOf("reserve", "bootstrap", "publication", "recovery", "catalog_commit")
            }
        if (kind == "append") {
            assertEquals(
                listOf("reservation", "bootstrap", "publication", "catalogCommit"),
                probe.transactionOrder,
            )
            assertEquals(
                listOf("reserve", "bootstrap", "publication", "recovery", "catalog_commit"),
                stageOrder,
            )
        }
        emit(
            JSONObject()
                .put("event", "MEASURE")
                .put("kind", kind)
                .put("blocksBefore", blocks)
                .put("elapsedNanos", elapsed)
                .put("threadCpuNanos", cpuNanos)
                .put("processCpuMs", processCpuMs)
                .put("fullCatalogLoads", probe.fullCatalogLoads)
                .put("transactionOrder", JSONArray(probe.transactionOrder))
                .put("stageOrder", JSONArray(stageOrder))
                .put("stages", JSONObject(stages as Map<*, *>))
                .put("candidateFsyncCount", probe.candidateFsyncCount)
                .put("candidateParentFsyncCount", probe.candidateParentFsyncCount)
        )
    }

    private fun emit(row: JSONObject) {
        val line = row.toString()
        output.appendText(line + "\n")
        instrumentation.sendStatus(2, Bundle().apply { putString("stream", "C3 $line\n") })
    }
}
