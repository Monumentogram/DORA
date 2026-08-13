package com.monumentogram.dora.poc.recovery.contract

import java.security.MessageDigest
import java.util.UUID

class RecoveryContractException(
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)

internal fun contractRequire(
    condition: Boolean,
    lazyMessage: () -> String,
) {
    if (!condition) {
        throw RecoveryContractException(lazyMessage())
    }
}

object RecoveryContract {
    const val PROTOCOL_ID = "poc-recovery-protocol-stage0-v0.6"
    const val SCHEMA_VERSION = 1
    const val MAX_PROTOCOL_ID_BYTES = 96
    const val MAX_CANDIDATE_ID_BYTES = 64
    const val MAX_LP16_ASCII_BYTES = 65_535
    const val MAX_MANIFEST_ENTRIES = 721
    const val MAX_MANIFEST_PLAINTEXT_BYTES = 524_288
    const val MAX_PLAINTEXT_BYTES_PER_RUN = 115_200_000UL
    const val U32_MAX = 4_294_967_295UL

    internal const val MAX_GENERAL_RECORD_BYTES = MAX_MANIFEST_PLAINTEXT_BYTES
}

enum class RecoveryCandidate(val contractId: String) {
    STREAM("REC-STREAM-TINK"),
    MICROFILE("REC-MICROFILE-TINK");

    companion object {
        fun fromContractId(value: String): RecoveryCandidate =
            entries.singleOrNull { it.contractId == value }
                ?: throw RecoveryContractException("Unsupported Recovery candidate: $value")
    }
}

class RunId private constructor(bytes: ByteArray) {
    private val value = bytes.copyOf()

    fun toByteArray(): ByteArray = value.copyOf()

    fun toCanonicalString(): String {
        val reader = BoundedBinaryReader(value, SIZE_BYTES)
        val mostSignificantBits = reader.readU64().toLong()
        val leastSignificantBits = reader.readU64().toLong()
        reader.requireFinished()
        return UUID(mostSignificantBits, leastSignificantBits).toString()
    }

    override fun equals(other: Any?): Boolean = other is RunId && value.contentEquals(other.value)

    override fun hashCode(): Int = value.contentHashCode()

    override fun toString(): String = toCanonicalString()

    companion object {
        const val SIZE_BYTES = 16

        fun fromBytes(bytes: ByteArray): RunId {
            contractRequire(bytes.size == SIZE_BYTES) {
                "Run ID must contain exactly $SIZE_BYTES bytes"
            }
            return RunId(bytes)
        }

        fun fromCanonicalString(value: String): RunId {
            contractRequire(value == value.lowercase()) {
                "Run ID must use canonical lowercase UUID text"
            }
            contractRequire(value.length == CANONICAL_TEXT_LENGTH) {
                "Run ID must use canonical UUID text"
            }
            val uuid =
                try {
                    UUID.fromString(value)
                } catch (exception: IllegalArgumentException) {
                    throw RecoveryContractException(
                        "Run ID must use canonical UUID text",
                        exception,
                    )
                }
            contractRequire(uuid.toString() == value) { "Run ID must use canonical UUID text" }
            val writer = BoundedBinaryWriter(SIZE_BYTES)
            writer.writeU64(uuid.mostSignificantBits.toULong())
            writer.writeU64(uuid.leastSignificantBits.toULong())
            return RunId(writer.toByteArray())
        }

        private const val CANONICAL_TEXT_LENGTH = 36
    }
}

class Sha256Value private constructor(bytes: ByteArray) {
    private val value = bytes.copyOf()

    fun toByteArray(): ByteArray = value.copyOf()

    fun toLowercaseHex(): String =
        value.joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and BYTE_MASK) }

    fun isZero(): Boolean = value.all { it == 0.toByte() }

    override fun equals(other: Any?): Boolean =
        other is Sha256Value && value.contentEquals(other.value)

    override fun hashCode(): Int = value.contentHashCode()

    override fun toString(): String = toLowercaseHex()

    companion object {
        const val SIZE_BYTES = 32

        val ZERO: Sha256Value = Sha256Value(ByteArray(SIZE_BYTES))

        fun fromBytes(bytes: ByteArray): Sha256Value {
            contractRequire(bytes.size == SIZE_BYTES) {
                "SHA-256 value must contain exactly $SIZE_BYTES bytes"
            }
            return Sha256Value(bytes)
        }

        fun calculate(bytes: ByteArray): Sha256Value =
            fromBytes(MessageDigest.getInstance("SHA-256").digest(bytes))

        fun fromLowercaseHex(value: String): Sha256Value {
            contractRequire(value.length == HEX_LENGTH && value.all { it in HEX_DIGITS }) {
                "SHA-256 hexadecimal value must be 64 lowercase hexadecimal characters"
            }
            val bytes =
                ByteArray(SIZE_BYTES) { index ->
                    val startIndex = index * HEX_BYTE_CHARACTERS
                    value
                        .substring(startIndex, startIndex + HEX_BYTE_CHARACTERS)
                        .toInt(radix = HEX_RADIX)
                        .toByte()
                }
            return Sha256Value(bytes)
        }

        private const val HEX_LENGTH = 64
        private const val HEX_DIGITS = "0123456789abcdef"
        private const val HEX_BYTE_CHARACTERS = 2
        private const val HEX_RADIX = 16
        private const val BYTE_MASK = 0xff
    }
}

object RecoveryBinaryPrimitives {
    fun encodeU16(value: Int): ByteArray =
        BoundedBinaryWriter(U16_BYTES).apply { writeU16(value) }.toByteArray()

    fun decodeU16(bytes: ByteArray): Int =
        BoundedBinaryReader(bytes, U16_BYTES).run {
            val result = readU16()
            requireFinished()
            result
        }

    fun encodeU32(value: ULong): ByteArray =
        BoundedBinaryWriter(U32_BYTES).apply { writeU32(value) }.toByteArray()

    fun decodeU32(bytes: ByteArray): ULong =
        BoundedBinaryReader(bytes, U32_BYTES).run {
            val result = readU32()
            requireFinished()
            result
        }

    fun encodeU64(value: ULong): ByteArray =
        BoundedBinaryWriter(U64_BYTES).apply { writeU64(value) }.toByteArray()

    fun decodeU64(bytes: ByteArray): ULong =
        BoundedBinaryReader(bytes, U64_BYTES).run {
            val result = readU64()
            requireFinished()
            result
        }

    fun encodeLp16Ascii(
        value: String,
        maximumBytes: Int = RecoveryContract.MAX_LP16_ASCII_BYTES,
    ): ByteArray =
        BoundedBinaryWriter(RecoveryContract.MAX_LP16_ASCII_BYTES + U16_BYTES)
            .apply {
                writeLp16Ascii(value, maximumBytes)
            }
            .toByteArray()

    fun decodeLp16Ascii(
        bytes: ByteArray,
        maximumBytes: Int = RecoveryContract.MAX_LP16_ASCII_BYTES,
    ): String =
        BoundedBinaryReader(bytes, RecoveryContract.MAX_LP16_ASCII_BYTES + U16_BYTES).run {
            val result = readLp16Ascii(maximumBytes)
            requireFinished()
            result
        }

    fun checkedAddU64(
        left: ULong,
        right: ULong,
    ): ULong {
        contractRequire(ULong.MAX_VALUE - left >= right) { "U64 addition overflow" }
        return left + right
    }

    fun checkedMultiplyU64(
        left: ULong,
        right: ULong,
    ): ULong {
        contractRequire(left == 0UL || right <= ULong.MAX_VALUE / left) {
            "U64 multiplication overflow"
        }
        return left * right
    }

    private const val U16_BYTES = 2
    private const val U32_BYTES = 4
    private const val U64_BYTES = 8
}

internal class BoundedBinaryWriter(private val maximumBytes: Int) {
    private var bytes = ByteArray(minOf(INITIAL_CAPACITY, maximumBytes.coerceAtLeast(1)))
    private var size = 0

    init {
        contractRequire(maximumBytes >= 0) { "Writer maximum must not be negative" }
    }

    fun writeMagic(value: String) {
        contractRequire(value.length == MAGIC_BYTES) {
            "Magic must contain exactly $MAGIC_BYTES ASCII bytes"
        }
        writeBytes(canonicalAsciiBytes(value, MAGIC_BYTES))
    }

    fun writeU16(value: Int) {
        contractRequire(value in 0..U16_MAX) { "Value does not fit U16" }
        ensureCapacity(U16_BYTES)
        bytes[size++] = (value ushr BITS_PER_BYTE).toByte()
        bytes[size++] = value.toByte()
    }

    fun writeU32(value: ULong) {
        contractRequire(value <= RecoveryContract.U32_MAX) { "Value does not fit U32" }
        writeUnsigned(value, U32_BYTES)
    }

    fun writeU64(value: ULong) {
        writeUnsigned(value, U64_BYTES)
    }

    fun writeLp16Ascii(
        value: String,
        maximumLength: Int = RecoveryContract.MAX_LP16_ASCII_BYTES,
    ) {
        val ascii = canonicalAsciiBytes(value, maximumLength)
        writeU16(ascii.size)
        writeBytes(ascii)
    }

    fun toByteArray(): ByteArray = bytes.copyOf(size)

    private fun writeUnsigned(
        value: ULong,
        byteCount: Int,
    ) {
        ensureCapacity(byteCount)
        for (offset in byteCount - 1 downTo 0) {
            bytes[size++] = (value shr (offset * BITS_PER_BYTE)).toByte()
        }
    }

    internal fun writeBytes(value: ByteArray) {
        ensureCapacity(value.size)
        value.copyInto(bytes, destinationOffset = size)
        size += value.size
    }

    private fun ensureCapacity(additionalBytes: Int) {
        contractRequire(additionalBytes >= 0 && size <= maximumBytes - additionalBytes) {
            "Encoded value exceeds the bounded writer maximum of $maximumBytes bytes"
        }
        val required = size + additionalBytes
        if (required <= bytes.size) {
            return
        }
        var newCapacity = bytes.size.coerceAtLeast(1)
        while (newCapacity < required) {
            newCapacity = minOf(maximumBytes, newCapacity * CAPACITY_GROWTH)
        }
        bytes = bytes.copyOf(newCapacity)
    }

    private fun canonicalAsciiBytes(
        value: String,
        maximumLength: Int,
    ): ByteArray {
        contractRequire(maximumLength in 0..RecoveryContract.MAX_LP16_ASCII_BYTES) {
            "ASCII bound does not fit LP16"
        }
        contractRequire(value.length <= maximumLength) { "ASCII value exceeds its byte bound" }
        val result = ByteArray(value.length)
        value.forEachIndexed { index, character ->
            contractRequire(character.code in ASCII_MIN..ASCII_MAX && character != NUL) {
                "LP16 value must contain canonical 7-bit ASCII without NUL"
            }
            result[index] = character.code.toByte()
        }
        return result
    }

    private companion object {
        const val INITIAL_CAPACITY = 128
        const val CAPACITY_GROWTH = 2
        const val MAGIC_BYTES = 8
        const val U16_BYTES = 2
        const val U32_BYTES = 4
        const val U64_BYTES = 8
        const val U16_MAX = 65_535
        const val BITS_PER_BYTE = 8
        const val ASCII_MIN = 1
        const val ASCII_MAX = 0x7f
        const val NUL = '\u0000'
    }
}

internal fun BoundedBinaryWriter.writeRunId(value: RunId) {
    writeBytes(value.toByteArray())
}

internal fun BoundedBinaryWriter.writeSha256(value: Sha256Value) {
    writeBytes(value.toByteArray())
}

internal class BoundedBinaryReader(
    private val bytes: ByteArray,
    maximumBytes: Int,
) {
    private var offset = 0

    init {
        contractRequire(maximumBytes >= 0 && bytes.size <= maximumBytes) {
            "Encoded value exceeds the bounded reader maximum of $maximumBytes bytes"
        }
    }

    fun expectMagic(expected: String) {
        contractRequire(expected.length == MAGIC_BYTES) {
            "Magic must contain exactly $MAGIC_BYTES ASCII bytes"
        }
        val actual = readBytes(MAGIC_BYTES)
        contractRequire(actual.contentEquals(expected.map { it.code.toByte() }.toByteArray())) {
            "Unexpected binary record magic"
        }
    }

    fun readU16(): Int {
        val value = readUnsigned(U16_BYTES)
        return value.toInt()
    }

    fun readU32(): ULong = readUnsigned(U32_BYTES)

    fun readU64(): ULong = readUnsigned(U64_BYTES)

    fun readLp16Ascii(maximumLength: Int = RecoveryContract.MAX_LP16_ASCII_BYTES): String {
        contractRequire(maximumLength in 0..RecoveryContract.MAX_LP16_ASCII_BYTES) {
            "ASCII bound does not fit LP16"
        }
        val length = readU16()
        contractRequire(length <= maximumLength) { "LP16 value exceeds its byte bound" }
        val value = readBytes(length)
        contractRequire(
            value.all { byte -> (byte.toInt() and BYTE_MASK) in ASCII_MIN..ASCII_MAX }
        ) {
            "LP16 value must contain canonical 7-bit ASCII without NUL"
        }
        return value.map { it.toInt().toChar() }.joinToString(separator = "")
    }

    fun readRunId(): RunId = RunId.fromBytes(readBytes(RunId.SIZE_BYTES))

    fun readSha256(): Sha256Value = Sha256Value.fromBytes(readBytes(Sha256Value.SIZE_BYTES))

    fun requireFinished() {
        contractRequire(offset == bytes.size) { "Trailing bytes are forbidden" }
    }

    private fun readUnsigned(byteCount: Int): ULong {
        val value = readBytes(byteCount)
        return value.fold(0UL) { result, byte ->
            (result shl BITS_PER_BYTE) or (byte.toInt() and BYTE_MASK).toULong()
        }
    }

    private fun readBytes(count: Int): ByteArray {
        contractRequire(count >= 0 && offset <= bytes.size - count) { "Truncated binary record" }
        val result = bytes.copyOfRange(offset, offset + count)
        offset += count
        return result
    }

    private companion object {
        const val MAGIC_BYTES = 8
        const val U16_BYTES = 2
        const val U32_BYTES = 4
        const val U64_BYTES = 8
        const val BITS_PER_BYTE = 8
        const val BYTE_MASK = 0xff
        const val ASCII_MIN = 1
        const val ASCII_MAX = 0x7f
    }
}

internal fun BoundedBinaryWriter.writeContractIdentity(
    candidate: RecoveryCandidate,
    runId: RunId,
) {
    writeLp16Ascii(RecoveryContract.PROTOCOL_ID, RecoveryContract.MAX_PROTOCOL_ID_BYTES)
    writeLp16Ascii(candidate.contractId, RecoveryContract.MAX_CANDIDATE_ID_BYTES)
    writeRunId(runId)
}

internal fun BoundedBinaryReader.readAndValidateContractIdentity(): Pair<RecoveryCandidate, RunId> {
    val protocolId = readLp16Ascii(RecoveryContract.MAX_PROTOCOL_ID_BYTES)
    contractRequire(protocolId == RecoveryContract.PROTOCOL_ID) {
        "Protocol identity does not match the active contract"
    }
    val candidate =
        RecoveryCandidate.fromContractId(readLp16Ascii(RecoveryContract.MAX_CANDIDATE_ID_BYTES))
    return candidate to readRunId()
}

internal fun BoundedBinaryReader.readAndValidateSchema() {
    contractRequire(readU16() == RecoveryContract.SCHEMA_VERSION) {
        "Unsupported binary record schema"
    }
}
