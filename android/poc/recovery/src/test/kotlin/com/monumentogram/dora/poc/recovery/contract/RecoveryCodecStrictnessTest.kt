package com.monumentogram.dora.poc.recovery.contract

import org.junit.Assert.assertThrows
import org.junit.Test

class RecoveryCodecStrictnessTest {
    @Test
    fun `every truncation boundary and every trailing extension is rejected`() {
        val cases =
            listOf(
                CodecCase(
                    hex(RecoveryRecordsTest.CHECKPOINT_GOLDEN_HEX),
                    RecoveryCheckpointCodec::decode,
                ),
                CodecCase(
                    hex(RecoveryRecordsTest.MANIFEST_GOLDEN_HEX),
                    RecoveryManifestCodec::decode,
                ),
                CodecCase(hex(RecoveryAadTest.STREAMING_GOLDEN_HEX), StreamingAadCodec::decode),
                CodecCase(hex(RecoveryAadTest.MICROFILE_GOLDEN_HEX), MicrofileAadCodec::decode),
                CodecCase(hex(RecoveryAadTest.PUBLICATION_GOLDEN_HEX), PublicationAadCodec::decode),
                CodecCase(
                    hex(RecoveryAadTest.KEY_ENVELOPE_GOLDEN_HEX),
                    KeyEnvelopeAadCodec::decode,
                ),
                CodecCase(
                    hex(KeyConfirmationContractTest.PLAINTEXT_GOLDEN_HEX),
                    KeyConfirmationPlaintextCodec::decode,
                ),
                CodecCase(
                    hex(KeyConfirmationContractTest.AAD_GOLDEN_HEX),
                    KeyConfirmationAadCodec::decode,
                ),
            )

        cases.forEach { case ->
            for (boundary in 0 until case.bytes.size) {
                assertThrows(RecoveryContractException::class.java) {
                    case.decode(case.bytes.copyOf(boundary))
                }
            }
            assertThrows(RecoveryContractException::class.java) {
                case.decode(case.bytes.withTrailingByte())
            }
        }
    }

    private data class CodecCase(
        val bytes: ByteArray,
        val decode: (ByteArray) -> Any,
    )
}
