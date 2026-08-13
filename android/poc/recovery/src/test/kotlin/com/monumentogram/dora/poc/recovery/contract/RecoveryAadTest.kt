package com.monumentogram.dora.poc.recovery.contract

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RecoveryAadTest {
    @Test
    fun `all AAD encodings match independent fixed golden vectors`() {
        val streaming = StreamingAad(RecoveryCandidate.STREAM, RUN_ID)
        assertArrayEquals(hex(STREAMING_GOLDEN_HEX), StreamingAadCodec.encode(streaming))
        assertEquals(streaming, StreamingAadCodec.decode(hex(STREAMING_GOLDEN_HEX)))

        val microfile =
            MicrofileAad(
                candidate = RecoveryCandidate.MICROFILE,
                runId = RUN_ID,
                manifestGeneration = 2UL,
                unitIndex = 0UL,
                plaintextStartInclusive = 0UL,
                plaintextEndExclusive = 160_000UL,
                cadenceSeconds = 5UL,
                previousManifestCiphertextSha256 = DIGEST_00_1F,
            )
        assertArrayEquals(hex(MICROFILE_GOLDEN_HEX), MicrofileAadCodec.encode(microfile))
        assertEquals(microfile, MicrofileAadCodec.decode(hex(MICROFILE_GOLDEN_HEX)))

        val publication =
            PublicationAad(
                candidate = RecoveryCandidate.MICROFILE,
                runId = RUN_ID,
                publicationKind = PublicationKind.MANIFEST,
                generation = 1UL,
                terminalUnitIndex = PublicationAad.EMPTY_TERMINAL_UNIT_INDEX,
                plaintextEndExclusive = 0UL,
                previousPublicationCiphertextSha256 = Sha256Value.ZERO,
            )
        assertArrayEquals(hex(PUBLICATION_GOLDEN_HEX), PublicationAadCodec.encode(publication))
        assertEquals(publication, PublicationAadCodec.decode(hex(PUBLICATION_GOLDEN_HEX)))

        val keyEnvelope =
            KeyEnvelopeAad(
                candidate = RecoveryCandidate.MICROFILE,
                runId = RUN_ID,
                targetKind = KeyEnvelopeTargetKind.MICROFILE,
                generation = 1UL,
                unitIndex = 0UL,
                plaintextStartInclusive = 0UL,
                plaintextEndExclusive = 160_000UL,
                cadenceSeconds = 5UL,
                previousPublicationCiphertextSha256 = Sha256Value.ZERO,
            )
        assertArrayEquals(hex(KEY_ENVELOPE_GOLDEN_HEX), KeyEnvelopeAadCodec.encode(keyEnvelope))
        assertEquals(keyEnvelope, KeyEnvelopeAadCodec.decode(hex(KEY_ENVELOPE_GOLDEN_HEX)))
    }

    @Test
    fun `AAD value invariants reject wrong generations and ranges`() {
        assertThrows(RecoveryContractException::class.java) {
            MicrofileAad(
                RecoveryCandidate.MICROFILE,
                RUN_ID,
                manifestGeneration = 0UL,
                unitIndex = 0UL,
                plaintextStartInclusive = 0UL,
                plaintextEndExclusive = 1UL,
                cadenceSeconds = 5UL,
                previousManifestCiphertextSha256 = Sha256Value.ZERO,
            )
        }
        assertThrows(RecoveryContractException::class.java) {
            KeyEnvelopeAad(
                RecoveryCandidate.STREAM,
                RUN_ID,
                KeyEnvelopeTargetKind.STREAM,
                generation = 1UL,
                unitIndex = 0UL,
                plaintextStartInclusive = 0UL,
                plaintextEndExclusive = 1UL,
                cadenceSeconds = 0UL,
                previousPublicationCiphertextSha256 = Sha256Value.ZERO,
            )
        }
    }

    @Test
    fun `AAD value invariants reject wrong kinds and sentinel indices`() {
        assertThrows(RecoveryContractException::class.java) {
            PublicationAad(
                RecoveryCandidate.MICROFILE,
                RUN_ID,
                PublicationKind.MANIFEST,
                generation = 1UL,
                terminalUnitIndex = 0UL,
                plaintextEndExclusive = 0UL,
                previousPublicationCiphertextSha256 = Sha256Value.ZERO,
            )
        }
        assertThrows(RecoveryContractException::class.java) {
            PublicationAad(
                RecoveryCandidate.MICROFILE,
                RUN_ID,
                PublicationKind.MANIFEST,
                generation = 1UL,
                terminalUnitIndex = PublicationAad.EMPTY_TERMINAL_UNIT_INDEX,
                plaintextEndExclusive = 1UL,
                previousPublicationCiphertextSha256 = Sha256Value.ZERO,
            )
        }
        assertThrows(RecoveryContractException::class.java) {
            KeyEnvelopeAad(
                RecoveryCandidate.MICROFILE,
                RUN_ID,
                KeyEnvelopeTargetKind.MICROFILE,
                generation = 1UL,
                unitIndex = KeyEnvelopeAad.NOT_APPLICABLE_UNIT_INDEX,
                plaintextStartInclusive = 0UL,
                plaintextEndExclusive = 1UL,
                cadenceSeconds = 5UL,
                previousPublicationCiphertextSha256 = Sha256Value.ZERO,
            )
        }
    }

    @Test
    fun `wrong magic schema identities and fixed AAD fields are rejected`() {
        val valid = StreamingAadCodec.encode(StreamingAad(RecoveryCandidate.STREAM, RUN_ID))
        assertMutationRejected(valid, 0) { StreamingAadCodec.decode(it) }
        assertMutationRejected(valid, SCHEMA_LOW_BYTE_OFFSET) { StreamingAadCodec.decode(it) }
        assertMutationRejected(valid, PROTOCOL_FIRST_BYTE_OFFSET) { StreamingAadCodec.decode(it) }
        assertMutationRejected(valid, STREAM_GENERATION_LAST_BYTE_OFFSET) {
            StreamingAadCodec.decode(it)
        }
        assertMutationRejected(valid, STREAM_END_LAST_BYTE_OFFSET) { StreamingAadCodec.decode(it) }
        assertMutationRejected(valid, STREAM_GENESIS_DIGEST_OFFSET) { StreamingAadCodec.decode(it) }

        val publication = hex(PUBLICATION_GOLDEN_HEX)
        val kindOffset = findAscii(publication, "MANIFEST")
        assertMutationRejected(publication, kindOffset) { PublicationAadCodec.decode(it) }

        val keyEnvelope = hex(KEY_ENVELOPE_GOLDEN_HEX)
        val targetOffset = findAscii(keyEnvelope, "MICROFILE")
        assertMutationRejected(keyEnvelope, targetOffset) { KeyEnvelopeAadCodec.decode(it) }
    }

    private fun assertMutationRejected(
        source: ByteArray,
        offset: Int,
        decoder: (ByteArray) -> Any,
    ) {
        val mutated = source.copyOf()
        mutated[offset] = (mutated[offset].toInt() xor 1).toByte()
        assertThrows(RecoveryContractException::class.java) { decoder(mutated) }
    }

    private fun findAscii(
        source: ByteArray,
        value: String,
    ): Int {
        val needle = value.map { it.code.toByte() }.toByteArray()
        return source.indices.first { start ->
            start + needle.size <= source.size &&
                source.copyOfRange(start, start + needle.size).contentEquals(needle)
        }
    }

    companion object {
        val RUN_ID = RunId.fromCanonicalString("00112233-4455-6677-8899-aabbccddeeff")
        val DIGEST_00_1F = Sha256Value.fromBytes(ByteArray(32) { it.toByte() })

        private const val SCHEMA_LOW_BYTE_OFFSET = 9
        private const val PROTOCOL_FIRST_BYTE_OFFSET = 12
        private const val STREAM_GENERATION_LAST_BYTE_OFFSET = 85
        private const val STREAM_END_LAST_BYTE_OFFSET = 101
        private const val STREAM_GENESIS_DIGEST_OFFSET = 102

        const val STREAMING_GOLDEN_HEX =
            "444f52415341303100010021706f632d7265636f766572792d70726f746f636f6c2d7374616765302d76302e36" +
                "000f5245432d53545245414d2d54494e4b00112233445566778899aabbccddeeff0000000000000001" +
                "00000000000000000000000006ddd00000000000000000000000000000000000000000000000000000000000" +
                "00000000"

        const val MICROFILE_GOLDEN_HEX =
            "444f52414d41303100010021706f632d7265636f766572792d70726f746f636f6c2d7374616765302d76302e36" +
                "00125245432d4d4943524f46494c452d54494e4b00112233445566778899aabbccddeeff0000000000000002" +
                "000000000000000000000000000000000002710000000005000102030405060708090a0b0c0d0e0f10111213" +
                "1415161718191a1b1c1d1e1f"

        const val PUBLICATION_GOLDEN_HEX =
            "444f52414350303100010021706f632d7265636f766572792d70726f746f636f6c2d7374616765302d76302e36001252" +
                "45432d4d4943524f46494c452d54494e4b00112233445566778899aabbccddeeff00084d414e49464553540000000000" +
                "000001ffffffff0000000000000000000000000000000000000000000000000000000000000000000000000000000000" +
                "00000000000000"

        const val KEY_ENVELOPE_GOLDEN_HEX =
            "444f52414b45303100010021706f632d7265636f766572792d70726f746f636f6c2d7374616765302d76302e36001252" +
                "45432d4d4943524f46494c452d54494e4b00112233445566778899aabbccddeeff00094d4943524f46494c4500000000" +
                "000000010000000000000000000000000000000000027100000000050000000000000000000000000000000000000000" +
                "000000000000000000000000"
    }
}
