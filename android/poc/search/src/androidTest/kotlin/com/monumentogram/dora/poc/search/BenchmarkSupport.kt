package com.monumentogram.dora.poc.search

import android.content.Context
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import com.monumentogram.dora.poc.search.db.SearchPocDatabase
import java.io.File
import java.security.MessageDigest
import kotlin.math.ceil
import kotlin.math.sqrt

data class DistributionStats(
    val count: Int,
    val minMs: Double,
    val p50Ms: Double,
    val p90Ms: Double,
    val p95Ms: Double,
    val p99Ms: Double,
    val maxMs: Double,
    val meanMs: Double,
    val standardDeviationMs: Double,
)

data class MemoryObservation(
    val peakPssMb: Double,
    val peakNativeHeapMb: Double,
    val peakManagedHeapMb: Double,
    val peakRssMb: Double?,
    val sampleCount: Int,
)

data class DatabaseFileSnapshot(
    val databaseBytes: Long,
    val walBytes: Long,
    val shmBytes: Long,
) {
    val totalBytes: Long = databaseBytes + walBytes + shmBytes
}

data class DatabasePreparation(
    val emptyDatabaseCreationMs: Double,
    val conversationInsertMs: Double,
    val transcriptInsertMs: Double,
    val indexBuildMs: Double,
    val checkpointCompactMs: Double,
    val logicalDigestReadMs: Double,
    val totalPreparationMs: Double,
    val beforeCompact: DatabaseFileSnapshot,
    val afterCompact: DatabaseFileSnapshot,
    val afterCompactDatabaseSha256: String,
    val conversationCount: Long,
    val transcriptCount: Long,
    val ftsCount: Long,
    val expectedLogicalDigest: String,
    val databaseLogicalDigest: String,
    val sqliteIntegrity: String,
    val missingCanonicalMappings: Long,
    val missingIndexRows: Long,
    val duplicateCanonicalRows: Long,
)

data class OpenReferenceDatabase(
    val name: String,
    val database: SearchPocDatabase,
    val preparation: DatabasePreparation,
)

object BenchmarkProgress {
    private const val TAG = "POC_SEARCH"

    fun report(message: String) {
        Log.i(TAG, message)
    }
}

object BenchmarkClock {
    inline fun <T> measure(block: () -> T): Pair<T, Double> {
        val started = SystemClock.elapsedRealtimeNanos()
        val result = block()
        val elapsedMs = (SystemClock.elapsedRealtimeNanos() - started) / 1_000_000.0
        return result to elapsedMs
    }
}

object BenchmarkDigests {
    fun sha256(value: String): String = sha256(value.toByteArray(Charsets.UTF_8))

    fun sha256(value: ByteArray): String =
        "sha256:" +
            MessageDigest.getInstance("SHA-256").digest(value).joinToString("") { byte ->
                "%02x".format(byte)
            }

    fun sha256(file: File): String =
        file.inputStream().buffered().use { input ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
            "sha256:" + digest.digest().joinToString("") { byte -> "%02x".format(byte) }
        }

    fun toSha256(digest: MessageDigest): String =
        "sha256:" + digest.digest().joinToString("") { byte -> "%02x".format(byte) }
}

object BenchmarkStatistics {
    fun fromNanoseconds(values: List<Long>): DistributionStats {
        require(values.isNotEmpty())
        val sorted = values.sorted()
        val millis = sorted.map { it / 1_000_000.0 }
        val mean = millis.average()
        val variance = millis.sumOf { value -> (value - mean) * (value - mean) } / millis.size
        return DistributionStats(
            count = millis.size,
            minMs = millis.first(),
            p50Ms = percentile(millis, 0.50),
            p90Ms = percentile(millis, 0.90),
            p95Ms = percentile(millis, 0.95),
            p99Ms = percentile(millis, 0.99),
            maxMs = millis.last(),
            meanMs = mean,
            standardDeviationMs = sqrt(variance),
        )
    }

    private fun percentile(sortedValues: List<Double>, percentile: Double): Double {
        val rank = ceil(percentile * sortedValues.size).toInt().coerceAtLeast(1)
        return sortedValues[rank - 1]
    }
}

class MemorySampler {
    private var peakPssKb: Long = 0
    private var peakNativeHeapBytes: Long = 0
    private var peakManagedHeapBytes: Long = 0
    private var samples: Int = 0

    fun sample() {
        val info = Debug.MemoryInfo()
        Debug.getMemoryInfo(info)
        peakPssKb = maxOf(peakPssKb, info.totalPss.toLong())
        peakNativeHeapBytes = maxOf(peakNativeHeapBytes, Debug.getNativeHeapAllocatedSize())
        val runtime = Runtime.getRuntime()
        peakManagedHeapBytes =
            maxOf(peakManagedHeapBytes, runtime.totalMemory() - runtime.freeMemory())
        samples += 1
    }

    fun observation(): MemoryObservation =
        MemoryObservation(
            peakPssMb = peakPssKb / 1024.0,
            peakNativeHeapMb = peakNativeHeapBytes / MEBIBYTE,
            peakManagedHeapMb = peakManagedHeapBytes / MEBIBYTE,
            peakRssMb = readPeakRssMb(),
            sampleCount = samples,
        )

    private fun readPeakRssMb(): Double? =
        runCatching {
                File("/proc/self/status")
                    .useLines { lines ->
                        lines.firstOrNull { it.startsWith("VmHWM:") }
                    }
                    ?.split(Regex("\\s+"))
                    ?.getOrNull(1)
                    ?.toDouble()
                    ?.div(1024.0)
            }
            .getOrNull()

    companion object {
        private const val MEBIBYTE = 1024.0 * 1024.0
    }
}

object DatabaseFiles {
    fun snapshot(context: Context, databaseName: String): DatabaseFileSnapshot {
        val database = context.getDatabasePath(databaseName)
        return DatabaseFileSnapshot(
            databaseBytes = database.lengthOrZero(),
            walBytes = File(database.path + "-wal").lengthOrZero(),
            shmBytes = File(database.path + "-shm").lengthOrZero(),
        )
    }

    private fun File.lengthOrZero(): Long = if (isFile) length() else 0L
}
