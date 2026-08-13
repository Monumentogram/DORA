package com.monumentogram.dora.poc.recovery.contract

import kotlin.random.Random
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RecoveryBinaryTest {
    @Test
    fun `unsigned primitives cover their complete representable boundaries`() {
        assertEquals(0, RecoveryBinaryPrimitives.decodeU16(byteArrayOf(0, 0)))
        assertEquals(65_535, RecoveryBinaryPrimitives.decodeU16(hex("ffff")))
        assertArrayEquals(hex("0000"), RecoveryBinaryPrimitives.encodeU16(0))
        assertArrayEquals(hex("ffff"), RecoveryBinaryPrimitives.encodeU16(65_535))

        assertEquals(0UL, RecoveryBinaryPrimitives.decodeU32(hex("00000000")))
        assertEquals(RecoveryContract.U32_MAX, RecoveryBinaryPrimitives.decodeU32(hex("ffffffff")))
        assertArrayEquals(hex("00000000"), RecoveryBinaryPrimitives.encodeU32(0UL))
        assertArrayEquals(
            hex("ffffffff"),
            RecoveryBinaryPrimitives.encodeU32(RecoveryContract.U32_MAX),
        )

        assertEquals(0UL, RecoveryBinaryPrimitives.decodeU64(hex("0000000000000000")))
        assertEquals(ULong.MAX_VALUE, RecoveryBinaryPrimitives.decodeU64(hex("ffffffffffffffff")))
        assertArrayEquals(hex("0000000000000000"), RecoveryBinaryPrimitives.encodeU64(0UL))
        assertArrayEquals(
            hex("ffffffffffffffff"),
            RecoveryBinaryPrimitives.encodeU64(ULong.MAX_VALUE),
        )
    }

    @Test
    fun `unsigned primitive overflows and non-exact encodings are rejected`() {
        assertThrows(RecoveryContractException::class.java) {
            RecoveryBinaryPrimitives.encodeU16(-1)
        }
        assertThrows(RecoveryContractException::class.java) {
            RecoveryBinaryPrimitives.encodeU16(65_536)
        }
        assertThrows(RecoveryContractException::class.java) {
            RecoveryBinaryPrimitives.encodeU32(RecoveryContract.U32_MAX + 1UL)
        }
        assertThrows(RecoveryContractException::class.java) {
            RecoveryBinaryPrimitives.checkedAddU64(ULong.MAX_VALUE, 1UL)
        }
        assertThrows(RecoveryContractException::class.java) {
            RecoveryBinaryPrimitives.checkedMultiplyU64(ULong.MAX_VALUE, 2UL)
        }
        assertThrows(RecoveryContractException::class.java) {
            RecoveryBinaryPrimitives.decodeU16(hex("000000"))
        }
        assertThrows(RecoveryContractException::class.java) {
            RecoveryBinaryPrimitives.decodeU64(hex("00000000000000"))
        }
    }

    @Test
    fun `deterministic seeded primitive round trips preserve random values`() {
        val random = Random(SEED)
        repeat(RANDOM_CASES) {
            val u16 = random.nextInt(0, 65_536)
            assertEquals(
                u16,
                RecoveryBinaryPrimitives.decodeU16(RecoveryBinaryPrimitives.encodeU16(u16)),
            )

            val u32 = random.nextLong(0, RecoveryContract.U32_MAX.toLong() + 1L).toULong()
            assertEquals(
                u32,
                RecoveryBinaryPrimitives.decodeU32(RecoveryBinaryPrimitives.encodeU32(u32)),
            )

            val u64 = random.nextLong().toULong()
            assertEquals(
                u64,
                RecoveryBinaryPrimitives.decodeU64(RecoveryBinaryPrimitives.encodeU64(u64)),
            )
        }
    }

    @Test
    fun `LP16 accepts empty and maximum ASCII and rejects non-canonical values`() {
        assertArrayEquals(hex("0000"), RecoveryBinaryPrimitives.encodeLp16Ascii(""))
        assertEquals("", RecoveryBinaryPrimitives.decodeLp16Ascii(hex("0000")))

        val maximum = "a".repeat(RecoveryContract.MAX_LP16_ASCII_BYTES)
        val encodedMaximum = RecoveryBinaryPrimitives.encodeLp16Ascii(maximum)
        assertEquals(RecoveryContract.MAX_LP16_ASCII_BYTES + 2, encodedMaximum.size)
        assertEquals(maximum, RecoveryBinaryPrimitives.decodeLp16Ascii(encodedMaximum))

        assertThrows(RecoveryContractException::class.java) {
            RecoveryBinaryPrimitives.encodeLp16Ascii("é")
        }
        assertThrows(RecoveryContractException::class.java) {
            RecoveryBinaryPrimitives.encodeLp16Ascii("a\u0000b")
        }
        assertThrows(RecoveryContractException::class.java) {
            RecoveryBinaryPrimitives.encodeLp16Ascii("ab", maximumBytes = 1)
        }
        assertThrows(RecoveryContractException::class.java) {
            RecoveryBinaryPrimitives.decodeLp16Ascii(byteArrayOf(0, 2, 'a'.code.toByte()))
        }
        assertThrows(RecoveryContractException::class.java) {
            RecoveryBinaryPrimitives.decodeLp16Ascii(byteArrayOf(0, 1, 0))
        }
        assertThrows(RecoveryContractException::class.java) {
            RecoveryBinaryPrimitives.decodeLp16Ascii(byteArrayOf(0, 1, 0x80.toByte()))
        }
    }

    @Test
    fun `UUID uses RFC4122 network order and stored identities are defensively copied`() {
        val source = hex("00112233445566778899aabbccddeeff")
        val runId = RunId.fromBytes(source)
        source.fill(0)
        assertArrayEquals(hex("00112233445566778899aabbccddeeff"), runId.toByteArray())
        assertEquals("00112233-4455-6677-8899-aabbccddeeff", runId.toCanonicalString())
        assertEquals(runId, RunId.fromCanonicalString("00112233-4455-6677-8899-aabbccddeeff"))

        val exported = runId.toByteArray()
        exported.fill(0)
        assertNotEquals(RunId.fromBytes(exported), runId)
        assertThrows(RecoveryContractException::class.java) { RunId.fromBytes(ByteArray(15)) }
        assertThrows(RecoveryContractException::class.java) {
            RunId.fromCanonicalString("00112233-4455-6677-8899-AABBCCDDEEFF")
        }
    }

    @Test
    fun `SHA256 values enforce exact length content equality and defensive copies`() {
        val source = ByteArray(Sha256Value.SIZE_BYTES) { it.toByte() }
        val first = Sha256Value.fromBytes(source)
        val second = Sha256Value.fromBytes(source.copyOf())
        source.fill(0)
        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
        assertEquals(
            "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f",
            first.toLowercaseHex(),
        )
        val exported = first.toByteArray()
        exported.fill(0)
        assertEquals(second, first)
        assertThrows(RecoveryContractException::class.java) { Sha256Value.fromBytes(ByteArray(31)) }
        assertThrows(RecoveryContractException::class.java) { Sha256Value.fromBytes(ByteArray(33)) }
    }

    private companion object {
        const val SEED = 0xD04A
        const val RANDOM_CASES = 256
    }
}

internal fun hex(value: String): ByteArray {
    require(value.length % 2 == 0)
    return ByteArray(value.length / 2) { index ->
        value.substring(index * 2, index * 2 + 2).toInt(radix = 16).toByte()
    }
}

internal fun ByteArray.withTrailingByte(): ByteArray = this + byteArrayOf(0)
