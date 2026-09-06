@file:Suppress("LongParameterList", "MagicNumber")

package com.monumentogram.dora.poc.recovery.contract

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

class CanonicalSqliteText
private constructor(
    val value: String,
    private val bytes: ByteArray,
) {
    fun encodedBytes(): ByteArray = bytes.copyOf()

    companion object {
        fun of(decoded: String, sqliteBlob: ByteArray, maximumBytes: Int): CanonicalSqliteText {
            require(maximumBytes in 0..UShort.MAX_VALUE.toInt())
            require(sqliteBlob.size <= maximumBytes)
            val decoder =
                StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
            val canonical =
                try {
                    decoder.decode(ByteBuffer.wrap(sqliteBlob)).toString()
                } catch (failure: java.nio.charset.CharacterCodingException) {
                    throw IllegalArgumentException("SQLite TEXT is not canonical UTF-8", failure)
                }
            require('\u0000' !in canonical)
            require(canonical == decoded)
            require(canonical.toByteArray(StandardCharsets.UTF_8).contentEquals(sqliteBlob))
            return CanonicalSqliteText(canonical, sqliteBlob.copyOf())
        }
    }
}

class RecoveryQuarantineMigrationRow(
    intentId: ByteArray,
    val runId: CanonicalSqliteText,
    val candidateId: CanonicalSqliteText,
    val bootstrapBinding: CanonicalSqliteText,
    val bootstrapRunId: CanonicalSqliteText?,
    val bootstrapCandidateId: CanonicalSqliteText?,
    val artifactRole: CanonicalSqliteText,
    val observedState: CanonicalSqliteText,
    val sourceRelativeName: CanonicalSqliteText,
    val destinationRelativeName: CanonicalSqliteText,
    val sourceBytes: Long,
    sourceSha256: ByteArray,
    val state: CanonicalSqliteText,
) {
    val intentId = intentId.copyOf()
    val sourceSha256 = sourceSha256.copyOf()
}

object RecoveryStreamingMigration {
    fun digest(rows: List<RecoveryQuarantineMigrationRow>): ByteArray {
        val ordered = rows.sortedWith { left, right -> compareBlobs(left.intentId, right.intentId) }
        val digest = MessageDigest.getInstance("SHA-256")
        val header = BinaryWriter().lp16Ascii(DOMAIN, 96).u64(ordered.size.toLong()).bytes()
        digest.update(header)
        ordered.forEach { row ->
            val encoded = encode(row)
            digest.update(BinaryWriter().u32(encoded.size).bytes())
            digest.update(encoded)
        }
        return digest.digest()
    }

    private fun encode(row: RecoveryQuarantineMigrationRow): ByteArray {
        require(row.intentId.size == SHA256_BYTES)
        require(row.sourceSha256.size == SHA256_BYTES)
        require(row.sourceBytes >= 0)
        require(row.candidateId.value == "REC-MICROFILE-TINK")
        require(row.bootstrapBinding.value in setOf("ABSENT", "PRESENT"))
        require(row.artifactRole.value in MICROFILE_ROLES)
        require(row.observedState.value in OBSERVED_STATES)
        require(row.state.value in setOf("PENDING", "COMPLETED"))
        if (row.bootstrapBinding.value == "ABSENT") {
            require(row.bootstrapRunId == null && row.bootstrapCandidateId == null)
        } else {
            require(row.bootstrapRunId?.value == row.runId.value)
            require(row.bootstrapCandidateId?.value == row.candidateId.value)
        }
        return BinaryWriter(ROW_MAX_BYTES)
            .raw(row.intentId)
            .lp16Utf8(row.runId, 64)
            .lp16Utf8(row.candidateId, 64)
            .lp16Utf8(row.bootstrapBinding, 16)
            .nullableText(row.bootstrapRunId, 64)
            .nullableText(row.bootstrapCandidateId, 64)
            .lp16Utf8(row.artifactRole, 64)
            .lp16Utf8(row.observedState, 64)
            .lp16Utf8(row.sourceRelativeName, 512)
            .lp16Utf8(row.destinationRelativeName, 512)
            .u64(row.sourceBytes)
            .raw(row.sourceSha256)
            .lp16Utf8(row.state, 16)
            .bytes()
    }

    private fun compareBlobs(left: ByteArray, right: ByteArray): Int {
        val common = minOf(left.size, right.size)
        for (index in 0 until common) {
            val comparison = (left[index].toInt() and 0xff).compareTo(right[index].toInt() and 0xff)
            if (comparison != 0) return comparison
        }
        return left.size.compareTo(right.size)
    }

    private const val DOMAIN = "DORA_RECOVERY_QMIG_V3_V4"
    private const val SHA256_BYTES = 32
    private const val ROW_MAX_BYTES = 4_096
    private val MICROFILE_ROLES =
        setOf(
            "KEY_CONFIRMATION",
            "MICROFILE_KEY_ENVELOPE",
            "MICROFILE_CIPHERTEXT",
            "MANIFEST_KEY_ENVELOPE",
            "MANIFEST_CIPHERTEXT",
            "UNKNOWN_REGULAR",
        )
    private val OBSERVED_STATES =
        setOf(
            "TEMP_ONLY",
            "TEMP_AND_FINAL",
            "FINAL_ORPHAN",
            "SQLITE_POINTS_TO_TEMP",
            "UNKNOWN_OR_NON_ALLOWLISTED_NAME",
        )
}

private class BinaryWriter(private val maximum: Int = Int.MAX_VALUE) {
    private val output = ByteArrayOutputStream()

    fun raw(value: ByteArray) = apply { write(value) }

    fun u32(value: Int) = apply {
        require(value >= 0)
        write(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(value).array())
    }

    fun u64(value: Long) = apply {
        require(value >= 0)
        write(ByteBuffer.allocate(Long.SIZE_BYTES).putLong(value).array())
    }

    fun lp16Ascii(value: String, bound: Int) = apply {
        require(value.all { it.code in 0..0x7f })
        lp16(value.toByteArray(StandardCharsets.US_ASCII), bound)
    }

    fun lp16Utf8(value: CanonicalSqliteText, bound: Int) = apply {
        lp16(value.encodedBytes(), bound)
    }

    fun nullableText(value: CanonicalSqliteText?, bound: Int) = apply {
        if (value == null) {
            write(byteArrayOf(0))
        } else {
            write(byteArrayOf(1))
            lp16Utf8(value, bound)
        }
    }

    fun bytes(): ByteArray = output.toByteArray()

    private fun lp16(value: ByteArray, bound: Int) {
        require(value.size <= bound && value.size <= UShort.MAX_VALUE.toInt())
        write(ByteBuffer.allocate(Short.SIZE_BYTES).putShort(value.size.toShort()).array())
        write(value)
    }

    private fun write(value: ByteArray) {
        require(output.size() + value.size <= maximum)
        output.write(value)
    }
}
