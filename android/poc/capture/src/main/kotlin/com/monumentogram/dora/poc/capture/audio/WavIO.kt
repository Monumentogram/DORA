@file:Suppress("MagicNumber") // Numeric offsets are the PCM WAV binary format contract.

package com.monumentogram.dora.poc.capture.audio

import com.monumentogram.dora.poc.capture.model.DeletionReceipt
import com.monumentogram.dora.poc.capture.model.WavAnalysis
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.security.MessageDigest

class WavWriter(
    private val partFile: File,
    private val configuration: WavFormat,
) {
    private val output: RandomAccessFile
    private var dataBytes = 0L
    private var closed = false

    init {
        require(partFile.name.endsWith(PART_SUFFIX)) { "WAV working file must use $PART_SUFFIX" }
        val parent = checkNotNull(partFile.parentFile)
        check(parent.mkdirs() || parent.isDirectory) { "Capture directory is unavailable" }
        output = RandomAccessFile(partFile, "rw")
        output.setLength(0L)
        writeHeader(output, configuration, 0L)
    }

    fun write(buffer: ByteArray, offset: Int, count: Int) {
        check(!closed) { "WAV writer is closed" }
        require(count >= 0 && offset >= 0 && offset + count <= buffer.size)
        output.write(buffer, offset, count)
        dataBytes += count
    }

    fun bytesWritten(): Long = dataBytes

    fun fileLength(): Long = HEADER_BYTES + dataBytes

    fun finish(): File {
        if (closed) return finalFileFor(partFile)
        writeHeader(output, configuration, dataBytes)
        output.fd.sync()
        output.close()
        closed = true
        val finalFile = finalFileFor(partFile)
        check(!finalFile.exists() || finalFile.delete()) {
            "Existing finalized WAV cannot be replaced"
        }
        check(partFile.renameTo(finalFile)) { "WAV finalization rename failed" }
        return finalFile
    }

    fun closeLeavingPartial() {
        if (closed) return
        runCatching { output.fd.sync() }
        runCatching { output.close() }
        closed = true
    }

    companion object {
        const val HEADER_BYTES = 44L
        const val PART_SUFFIX = ".wav.part"

        fun finalFileFor(partFile: File): File =
            File(partFile.parentFile, partFile.name.removeSuffix(".part"))

        fun writeHeader(file: RandomAccessFile, format: WavFormat, dataBytes: Long) {
            require(dataBytes in 0..0xFFFF_FFFFL)
            val byteRate = format.sampleRate * format.channelCount * format.bitsPerSample / 8
            val blockAlign = format.channelCount * format.bitsPerSample / 8
            file.seek(0L)
            file.writeBytes("RIFF")
            file.writeLittleEndianInt((36L + dataBytes).toInt())
            file.writeBytes("WAVE")
            file.writeBytes("fmt ")
            file.writeLittleEndianInt(16)
            file.writeLittleEndianShort(1)
            file.writeLittleEndianShort(format.channelCount)
            file.writeLittleEndianInt(format.sampleRate)
            file.writeLittleEndianInt(byteRate)
            file.writeLittleEndianShort(blockAlign)
            file.writeLittleEndianShort(format.bitsPerSample)
            file.writeBytes("data")
            file.writeLittleEndianInt(dataBytes.toInt())
        }

        private fun RandomAccessFile.writeLittleEndianInt(value: Int) {
            write(value and 0xff)
            write(value ushr 8 and 0xff)
            write(value ushr 16 and 0xff)
            write(value ushr 24 and 0xff)
        }

        private fun RandomAccessFile.writeLittleEndianShort(value: Int) {
            write(value and 0xff)
            write(value ushr 8 and 0xff)
        }
    }
}

data class WavFormat(val sampleRate: Int, val channelCount: Int = 1, val bitsPerSample: Int = 16)

object WavAnalyzer {
    fun analyze(file: File): WavAnalysis {
        val header =
            readHeader(file)
                ?: return invalid("Файл отсутствует или WAV-заголовок прочитан не полностью")
        val riff = header.asAscii(0, 4)
        val wave = header.asAscii(8, 4)
        val formatChunk = header.asAscii(12, 4)
        val dataChunk = header.asAscii(36, 4)
        val audioFormat = header.littleEndianShort(20)
        val channels = header.littleEndianShort(22)
        val sampleRate = header.littleEndianInt(24)
        val byteRate = header.littleEndianInt(28)
        val blockAlign = header.littleEndianShort(32)
        val bits = header.littleEndianShort(34)
        val declaredRiffBytes = header.littleEndianUnsignedInt(4)
        val declaredDataBytes = header.littleEndianUnsignedInt(40)
        val actualDataBytes = file.length() - WavWriter.HEADER_BYTES
        val expectedBlockAlign = channels * bits / 8
        val expectedByteRate = sampleRate * expectedBlockAlign
        val valid =
            listOf(
                    riff == "RIFF",
                    wave == "WAVE",
                    formatChunk == "fmt ",
                    dataChunk == "data",
                    audioFormat == 1,
                    channels == 1,
                    bits == 16,
                    sampleRate > 0,
                    blockAlign == expectedBlockAlign,
                    byteRate == expectedByteRate,
                    actualDataBytes % blockAlign.coerceAtLeast(1) == 0L,
                    declaredRiffBytes == file.length() - 8L,
                    declaredDataBytes == actualDataBytes,
                )
                .all { it }
        val reason =
            if (valid) {
                "WAV PCM mono 16-bit валиден"
            } else {
                "Структура или заявленный размер WAV не совпадает с файлом"
            }
        return WavAnalysis(
            valid = valid,
            reason = reason,
            sampleRate = sampleRate.takeIf { it > 0 },
            channelCount = channels.takeIf { it > 0 },
            bitsPerSample = bits.takeIf { it > 0 },
            dataBytes = actualDataBytes.coerceAtLeast(0L),
            sha256 = sha256(file),
        )
    }

    fun analyzeAndDelete(file: File, runId: String, verifiedAtUtc: String): DeletionReceipt {
        val analysis = analyze(file)
        val bytes = file.length().coerceAtLeast(0L)
        val deleted = runCatching { file.delete() }.getOrDefault(false)
        val absent = !file.exists()
        return DeletionReceipt(
            runId = runId,
            verifiedAtUtc = verifiedAtUtc,
            wavWasValid = analysis.valid,
            sha256 = analysis.sha256,
            bytesBeforeDeletion = bytes,
            deletionSucceeded = deleted && absent,
            absenceVerified = absent,
            failureReason = if (deleted && absent) null else "Файл остался в app-private storage",
        )
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(32 * 1024)
        FileInputStream(file).use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read > 0) digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun invalid(reason: String) =
        WavAnalysis(
            valid = false,
            reason = reason,
            sampleRate = null,
            channelCount = null,
            bitsPerSample = null,
            dataBytes = 0,
            sha256 = null,
        )

    private fun readHeader(file: File): ByteArray? {
        if (!file.isFile || file.length() < WavWriter.HEADER_BYTES) return null
        val header = ByteArray(WavWriter.HEADER_BYTES.toInt())
        val read = FileInputStream(file).use { input -> input.read(header) }
        return header.takeIf { read == it.size }
    }

    private fun ByteArray.asAscii(offset: Int, count: Int): String =
        String(this, offset, count, Charsets.US_ASCII)

    private fun ByteArray.littleEndianShort(offset: Int): Int =
        (this[offset].toInt() and 0xff) or ((this[offset + 1].toInt() and 0xff) shl 8)

    private fun ByteArray.littleEndianInt(offset: Int): Int =
        (this[offset].toInt() and 0xff) or
            ((this[offset + 1].toInt() and 0xff) shl 8) or
            ((this[offset + 2].toInt() and 0xff) shl 16) or
            ((this[offset + 3].toInt() and 0xff) shl 24)

    private fun ByteArray.littleEndianUnsignedInt(offset: Int): Long =
        littleEndianInt(offset).toLong() and 0xFFFF_FFFFL
}
