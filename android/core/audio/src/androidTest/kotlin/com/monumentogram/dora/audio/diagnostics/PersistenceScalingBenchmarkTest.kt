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
import com.monumentogram.dora.audio.SegmentationKind
import com.monumentogram.dora.audio.SegmentationMetadata
import com.monumentogram.dora.audio.persistence.EncryptedAudioVault
import com.monumentogram.dora.audio.persistence.EncryptedAudioVaultFaultFixture
import com.monumentogram.dora.audio.persistence.EncryptedAudioVaultFaultFixture.Companion.id
import com.monumentogram.dora.audio.persistence.EncryptedAudioVaultFaultFixture.Companion.success
import com.monumentogram.dora.poc.recovery.candidate.RecoveryCandidateStorage
import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in real encryption/durability experiment. Never runs on physical hardware. */
class PersistenceScalingBenchmarkTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val arguments = InstrumentationRegistry.getArguments()
    private lateinit var output: File
    private val probe = PersistenceScalingProbe()

    @Test
    @Suppress("LongMethod", "NestedBlockDepth") // Preserve one vault lifetime across every size.
    fun realEncryptedScaling() {
        assumeTrue(arguments.getString("persistenceScaling") == "true")
        assertTrue(
            "Emulator only",
            Build.HARDWARE in setOf("ranchu", "goldfish"),
        )
        val base = instrumentation.targetContext
        assertTrue("Test package only", base.packageName == "com.monumentogram.dora.audio.test")
        assertTrue(
            (base.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isDeviceSecure
        )
        val maximum = arguments.getString("maxBlocks", "2000").toInt()
        require(maximum in setOf(10, 100, 400, 1000, 2000))
        output = File(base.filesDir, "persistence-scaling-${System.currentTimeMillis()}.jsonl")
        check(output.createNewFile())
        emit(
            JSONObject()
                .put("event", "BEGIN")
                .put("api", Build.VERSION.SDK_INT)
                .put("maximum", maximum)
                .put("pcmBytes", 160000)
                .put(
                    "timingScope",
                    "synchronous real writer; no microphone; no capture admission queue",
                )
                .put("countersScope", "calling-thread API boundaries; nested; not total syscalls")
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
                if (measured) measure("append", ordinal, fixture.context, action) else action()
                ordinal++
            }
            for (size in listOf(10, 100, 400, 1000, 2000).filter { it <= maximum }) {
                while (ordinal < size) {
                    append(false)
                    if (ordinal % 50 == 0)
                        emit(JSONObject().put("event", "PROGRESS").put("blocks", ordinal))
                }
                // Same-state loads separate read/validation cost from adding new files/keys.
                repeat(3) {
                    measure("catalog_load", ordinal, fixture.context) {
                        checkNotNull(catalog.tryAcquire(fixture.audio)).use {
                            assertEquals(
                                ordinal,
                                checkNotNull(catalog.load(fixture.audio)).segments.size,
                            )
                        }
                    }
                }
                repeat(3) {
                    val metadata =
                        SegmentationMetadata(
                            SegmentationKind.SEMANTIC_CLOSE,
                            id(),
                            0,
                            ordinal * 80000L,
                            reason = "STOP",
                        )
                    measure("metadata_insert", ordinal, fixture.context) {
                        success(vault.writer.segmentation(fixture.audio, metadata))
                    }
                    measure("metadata_same_row", ordinal, fixture.context) {
                        success(vault.writer.segmentation(fixture.audio, metadata))
                    }
                }
                repeat(3) { append(true) }
                emit(
                    JSONObject()
                        .put("event", "CHECKPOINT")
                        .put("target", size)
                        .put("actualBlocks", ordinal)
                )
            }
            // Every append already does authenticated exact PCM comparison before catalog commit.
            // A final reader verifies the whole synthetic prefix, including ordered source frames.
            var checked = 0
            val read =
                vault.reader.extract(fixture.audio) { frame, bytes ->
                    assertEquals(checked * 80000L, frame)
                    assertTrue(pcm.contentEquals(bytes))
                    checked++
                }
            success(read)
            assertEquals(ordinal, checked)
        }
        fixture.open(create = false, dependencies = dependencies).use { _ ->
            checkNotNull(catalog.tryAcquire(fixture.audio)).use {
                assertEquals(ordinal, checkNotNull(catalog.load(fixture.audio)).segments.size)
            }
        }
        pcm.fill(0)
        emit(
            JSONObject()
                .put("event", "COMPLETE")
                .put("blocks", ordinal)
                .put("authenticatedPcmFrames", ordinal * 80000L)
                .put("reopenedCatalogVerified", true)
        )
    }

    private fun measure(kind: String, blocks: Int, context: Context, action: () -> Unit) {
        val runtime = Runtime.getRuntime()
        val heapBefore = runtime.totalMemory() - runtime.freeMemory()
        val nativeBefore = Debug.getNativeHeapAllocatedSize()
        val gcBefore = Debug.getRuntimeStat("art.gc.gc-count")?.toLongOrNull()
        var stages = emptyMap<String, Long>()
        probe.start()
        val cpu = Debug.threadCpuTimeNanos()
        val processCpu = Process.getElapsedCpuTime()
        val start = System.nanoTime()
        PersistenceLatency.collect({ stages = it }, action)
        val elapsed = System.nanoTime() - start
        val cpuNanos = Debug.threadCpuTimeNanos() - cpu
        val processCpuMs = Process.getElapsedCpuTime() - processCpu
        val counters = probe.finish()
        val heapAfter = runtime.totalMemory() - runtime.freeMemory()
        val nativeAfter = Debug.getNativeHeapAllocatedSize()
        val gcAfter = Debug.getRuntimeStat("art.gc.gc-count")?.toLongOrNull()
        val files = context.noBackupFilesDir.walkTopDown().filter { it.isFile }.toList()
        emit(
            JSONObject()
                .put("event", "MEASURE")
                .put("kind", kind)
                .put("blocksBefore", blocks)
                .put("elapsedNanos", elapsed)
                .put("threadCpuNanos", cpuNanos)
                .put("processCpuMs", processCpuMs)
                .put("javaHeapBefore", heapBefore)
                .put("javaHeapAfter", heapAfter)
                .put("nativeHeapBefore", nativeBefore)
                .put("nativeHeapAfter", nativeAfter)
                .put("gcCountBefore", gcBefore ?: JSONObject.NULL)
                .put("gcCountAfter", gcAfter ?: JSONObject.NULL)
                .put("filesAfter", files.size)
                .put("bytesAfter", files.sumOf { it.length() })
                .put(
                    "databaseBytesAfter",
                    files.filter { it.name.endsWith(".db") }.sumOf { it.length() },
                )
                .put(
                    "walBytesAfter",
                    files.filter { it.name.endsWith("-wal") }.sumOf { it.length() },
                )
                .put("submittedUnreturnedFramesPeak", if (kind == "append") 80000 else 0)
                .put("submittedUnreturnedFramesAfter", 0)
                .put("captureOutstandingFrames", JSONObject.NULL)
                .put("stages", JSONObject(stages as Map<*, *>))
                .put("boundaries", counters)
        )
    }

    private fun emit(row: JSONObject) {
        val line = row.toString()
        output.appendText(line + "\n")
        instrumentation.sendStatus(2, Bundle().apply { putString("stream", "SCALING $line\n") })
    }
}
