package com.monumentogram.dora.poc.recovery.contract

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyConfirmationContractTest {
    @Test
    fun `canonical alias and digest match independent fixed values`() {
        val value = KeyConfirmationValue(RecoveryCandidate.STREAM, RUN_ID)
        assertEquals(
            "android-keystore://dora.poc.recovery.v1.00112233-4455-6677-8899-aabbccddeeff",
            value.canonicalAlias,
        )
        assertEquals(CanonicalRecoveryAlias.EXACT_ASCII_BYTES, value.canonicalAlias.length)
        assertEquals(ALIAS_SHA256_HEX, value.canonicalAliasSha256.toLowercaseHex())
    }

    @Test
    fun `key confirmation plaintext and AAD match independent fixed golden vectors`() {
        val value = KeyConfirmationValue(RecoveryCandidate.STREAM, RUN_ID)
        assertArrayEquals(hex(PLAINTEXT_GOLDEN_HEX), KeyConfirmationPlaintextCodec.encode(value))
        assertArrayEquals(hex(AAD_GOLDEN_HEX), KeyConfirmationAadCodec.encode(value))
        assertEquals(value, KeyConfirmationPlaintextCodec.decode(hex(PLAINTEXT_GOLDEN_HEX)))
        assertEquals(value, KeyConfirmationAadCodec.decode(hex(AAD_GOLDEN_HEX)))
    }

    @Test
    fun `key confirmation codecs enforce the 222-byte structural maximum`() {
        val maximumBody =
            lp16("p".repeat(RecoveryContract.MAX_PROTOCOL_ID_BYTES)) +
                lp16("c".repeat(RecoveryContract.MAX_CANDIDATE_ID_BYTES)) +
                RUN_ID.toByteArray() +
                ByteArray(Sha256Value.SIZE_BYTES)
        val maximumPlaintext =
            ascii(KeyConfirmationPlaintextCodec.MAGIC) + hex("0001") + maximumBody
        val maximumAad = ascii(KeyConfirmationAadCodec.MAGIC) + hex("0001") + maximumBody
        assertEquals(KeyConfirmationBinaryContract.MAXIMUM_ENCODED_BYTES, maximumPlaintext.size)
        assertEquals(KeyConfirmationBinaryContract.MAXIMUM_ENCODED_BYTES, maximumAad.size)
        assertThrows(RecoveryContractException::class.java) {
            KeyConfirmationPlaintextCodec.decode(maximumPlaintext)
        }
        assertThrows(RecoveryContractException::class.java) {
            KeyConfirmationAadCodec.decode(maximumAad)
        }
        assertThrows(RecoveryContractException::class.java) {
            KeyConfirmationPlaintextCodec.decode(maximumPlaintext.withTrailingByte())
        }
        assertThrows(RecoveryContractException::class.java) {
            KeyConfirmationAadCodec.decode(maximumAad.withTrailingByte())
        }
    }

    @Test
    fun `key confirmation rejects wrong magic schema identity alias digest and trailing bytes`() {
        val valid = hex(PLAINTEXT_GOLDEN_HEX)
        listOf(
                MAGIC_OFFSET,
                SCHEMA_LOW_BYTE_OFFSET,
                PROTOCOL_FIRST_BYTE_OFFSET,
                CANDIDATE_FIRST_BYTE_OFFSET,
                RUN_ID_FIRST_BYTE_OFFSET,
                ALIAS_DIGEST_FIRST_BYTE_OFFSET,
            )
            .forEach { offset ->
                val mutated = valid.copyOf()
                mutated[offset] = (mutated[offset].toInt() xor 1).toByte()
                assertThrows(RecoveryContractException::class.java) {
                    KeyConfirmationPlaintextCodec.decode(mutated)
                }
            }
        assertThrows(RecoveryContractException::class.java) {
            KeyConfirmationPlaintextCodec.decode(valid.withTrailingByte())
        }
    }

    @Test
    fun `canonical taxonomy has the exact precedence order`() {
        assertEquals(
            listOf(
                "KEY_REF_COLLISION",
                "INCOMPLETE_KEY_BOOTSTRAP",
                "KEY_CONFIRMATION_MISSING",
                "CORRUPT_KEY_CONFIRMATION",
                "KEY_UNAVAILABLE",
                "KEY_UNAVAILABLE_KEY_MISMATCH",
                "CORRUPT_KEY_ENVELOPE",
                "KEY_ENVELOPE_AUTH_FAILURE",
            ),
            KeyRecoveryClassification.entries.map { it.name },
        )
    }

    @Test
    fun `fault catalog has exactly 46 ordered unique IDs and one KEY-04`() {
        val expected =
            listOf(
                "COR-01",
                "COR-02",
                "COR-03",
                "COR-04",
                "COR-05",
                "COR-06",
                "TRU-01",
                "TRU-02",
                "TRU-03",
                "KEY-01",
                "KEY-02",
                "KEY-03",
                "KEY-04",
                "KEY-05",
                "KEY-06",
                "KEY-07",
                "SPL-01",
                "SPL-02",
                "SPL-03",
                "SPL-04",
                "SPL-05",
                "RBK-01",
                "RBK-02",
                "PAR-01",
                "QUA-01",
                "QUA-02",
                "QUA-03",
                "IDE-01",
                "IDE-02",
                "EVT-01",
                "CLN-01",
                "CLN-02",
                "CLN-03",
                "KCB-01",
                "KCB-02",
                "KCB-03",
                "KCB-04",
                "KCB-05",
                "KCB-06",
                "KCF-01",
                "KCF-02",
                "KCF-03",
                "KCF-04",
                "KCF-05",
                "KCF-06",
                "KCF-07",
            )
        assertEquals(expected, RecoveryFaultCatalog.orderedIds)
        assertEquals(RecoveryFaultCatalog.EXPECTED_ROW_COUNT, expected.toSet().size)
        assertEquals(1, expected.count { it == "KEY-04" })
        assertEquals(184, RecoveryFaultCatalog.PHASE_A_INJECTIONS)
        assertEquals(138, RecoveryFaultCatalog.FULL_PHYSICAL_INJECTIONS)
        assertEquals(120, RecoveryFaultCatalog.BASE_HARD_KILLS_PER_CANDIDATE)
    }

    @Test
    @Suppress("UNCHECKED_CAST", "PLATFORM_CLASS_MAPPED_TO_KOTLIN")
    fun `fault catalog rejects Kotlin and Java mutation attempts without changing invariants`() {
        val before = RecoveryFaultCatalog.orderedIds
        val kotlinMutable = RecoveryFaultCatalog.orderedIds as MutableList<String>
        assertThrows(UnsupportedOperationException::class.java) {
            kotlinMutable.add("MUT-01")
        }
        assertThrows(UnsupportedOperationException::class.java) {
            kotlinMutable[0] = "MUT-02"
        }

        val javaMutable = RecoveryFaultCatalog.orderedIds as java.util.List<String>
        assertThrows(UnsupportedOperationException::class.java) {
            javaMutable.remove("KEY-04")
        }
        assertThrows(UnsupportedOperationException::class.java) {
            javaMutable.clear()
        }

        val after = RecoveryFaultCatalog.orderedIds
        assertEquals(before, after)
        assertEquals(RecoveryFaultCatalog.EXPECTED_ROW_COUNT, after.size)
        assertEquals(RecoveryFaultCatalog.EXPECTED_ROW_COUNT, after.toSet().size)
        assertEquals(1, after.count { it == "KEY-04" })
    }

    @Test
    fun `KEY-04 routes only the exact eight-precondition authentication failure`() {
        val valid = validKey04()
        assertEquals(
            KeyRecoveryClassification.KEY_UNAVAILABLE_KEY_MISMATCH,
            KeyConfirmationRouting.classifyKey04(valid),
        )

        val failedBooleanPreconditions =
            listOf(
                valid.copy(durableRunRowExists = false),
                valid.copy(confirmationFinalExists = false),
                valid.copy(pathTypeLengthAndSha256Match = false),
                valid.copy(approvedAliasExistsAndIsAccessible = false),
                valid.copy(exactActiveProtocolAadComputed = false),
                valid.copy(underlyingAliasKeyReplacedWithCiphertextIdentityPreserved = false),
                valid.copy(recoveryCreatedOrReplacedKey = true),
            )
        failedBooleanPreconditions.forEach { observation ->
            assertThrows(RecoveryContractException::class.java) {
                KeyConfirmationRouting.classifyKey04(observation)
            }
        }
        listOf(
                KeyConfirmationDecryptOutcome.SUCCESS,
                KeyConfirmationDecryptOutcome.POST_DECRYPT_PLAINTEXT_MISMATCH,
                KeyConfirmationDecryptOutcome.OTHER_FAILURE,
            )
            .forEach { outcome ->
                assertThrows(RecoveryContractException::class.java) {
                    KeyConfirmationRouting.classifyKey04(valid.copy(decryptOutcome = outcome))
                }
            }
    }

    @Test
    fun `KCF-07 routes every post-decrypt malformed identity only as corrupt confirmation`() {
        Kcf07PlaintextFailure.entries.forEach { failure ->
            assertEquals(
                KeyRecoveryClassification.CORRUPT_KEY_CONFIRMATION,
                KeyConfirmationRouting.classifyKcf07(
                    Kcf07Observation(
                        storedCiphertextIdentityPasses = true,
                        decryptSucceeds = true,
                        plaintextFailure = failure,
                    )
                ),
            )
        }
        assertThrows(RecoveryContractException::class.java) {
            KeyConfirmationRouting.classifyKcf07(
                Kcf07Observation(
                    storedCiphertextIdentityPasses = false,
                    decryptSucceeds = true,
                    plaintextFailure = Kcf07PlaintextFailure.WRONG_MAGIC,
                )
            )
        }
        assertThrows(RecoveryContractException::class.java) {
            KeyConfirmationRouting.classifyKcf07(
                Kcf07Observation(
                    storedCiphertextIdentityPasses = true,
                    decryptSucceeds = false,
                    plaintextFailure = Kcf07PlaintextFailure.WRONG_MAGIC,
                )
            )
        }
    }

    @Test
    fun `contract carries no execution or candidate-selection verdict`() {
        assertFalse(RecoveryStreamingMath.SACRIFICIAL_SEGMENT_IS_COMMITTED)
        assertTrue(RecoveryStreamingMath.DURABLE_LAST_NON_FINAL_SEGMENT_IS_SACRIFICIAL)
    }

    private fun validKey04(): Key04Observation =
        Key04Observation(
            durableRunRowExists = true,
            confirmationFinalExists = true,
            pathTypeLengthAndSha256Match = true,
            approvedAliasExistsAndIsAccessible = true,
            exactActiveProtocolAadComputed = true,
            underlyingAliasKeyReplacedWithCiphertextIdentityPreserved = true,
            recoveryCreatedOrReplacedKey = false,
            decryptOutcome = KeyConfirmationDecryptOutcome.AUTHENTICATION_OR_AAD_FAILURE,
        )

    private fun lp16(value: String): ByteArray = RecoveryBinaryPrimitives.encodeLp16Ascii(value)

    private fun ascii(value: String): ByteArray = value.map { it.code.toByte() }.toByteArray()

    companion object {
        val RUN_ID = RunId.fromCanonicalString("00112233-4455-6677-8899-aabbccddeeff")

        const val MAGIC_OFFSET = 0
        const val SCHEMA_LOW_BYTE_OFFSET = 9
        const val PROTOCOL_FIRST_BYTE_OFFSET = 12
        const val CANDIDATE_FIRST_BYTE_OFFSET = 47
        const val RUN_ID_FIRST_BYTE_OFFSET = 62
        const val ALIAS_DIGEST_FIRST_BYTE_OFFSET = 78

        const val ALIAS_SHA256_HEX =
            "b98b49ece839e88bcf01ca4a93ef86658f51304c52a485c682cea2ef9cc957e6"

        const val PLAINTEXT_GOLDEN_HEX =
            "444f52414b43303100010021706f632d7265636f766572792d70726f746f636f6c2d7374616765302d76302e36" +
                "000f5245432d53545245414d2d54494e4b00112233445566778899aabbccddeeffb98b49ece839e88bcf01ca4a" +
                "93ef86658f51304c52a485c682cea2ef9cc957e6"

        const val AAD_GOLDEN_HEX =
            "444f52414b41303100010021706f632d7265636f766572792d70726f746f636f6c2d7374616765302d76302e36" +
                "000f5245432d53545245414d2d54494e4b00112233445566778899aabbccddeeffb98b49ece839e88bcf01ca4a" +
                "93ef86658f51304c52a485c682cea2ef9cc957e6"
    }
}
