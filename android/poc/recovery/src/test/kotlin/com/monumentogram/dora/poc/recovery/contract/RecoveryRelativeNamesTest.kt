package com.monumentogram.dora.poc.recovery.contract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RecoveryRelativeNamesTest {
    @Test
    fun `all eight final names and mapped temporary names are exact`() {
        val cases = canonicalCases(generation = 42UL, unitIndex = 7UL)
        assertEquals(
            listOf(
                "stream/stream.ct",
                "key-envelopes/stream.ks",
                "checkpoints/g-00000000000000000042.ct",
                "key-envelopes/checkpoint-g-00000000000000000042.ks",
                "units/u-0000000007.ct",
                "key-envelopes/u-0000000007.ks",
                "manifests/g-00000000000000000042.ct",
                "key-envelopes/manifest-g-00000000000000000042.ks",
            ),
            cases.map { it.finalName },
        )
        assertEquals(cases.map { "${it.finalName}.tmp" }, cases.map { it.temporaryName })

        cases.forEach { case ->
            case.validateFinal(case.finalName)
            case.validateTemporary(case.temporaryName)
            assertThrows(RecoveryContractException::class.java) {
                case.validateFinal(case.temporaryName)
            }
            assertThrows(RecoveryContractException::class.java) {
                case.validateTemporary(case.finalName)
            }
        }
    }

    @Test
    fun `generation and unit index builders cover canonical numeric boundaries`() {
        assertEquals(
            "checkpoints/g-00000000000000000001.ct",
            RecoveryRelativeNames.checkpointCiphertext(1UL),
        )
        assertEquals(
            "manifests/g-18446744073709551615.ct",
            RecoveryRelativeNames.manifestCiphertext(ULong.MAX_VALUE),
        )
        assertEquals(
            "units/u-0000000000.ct",
            RecoveryRelativeNames.microfileCiphertext(0UL),
        )
        assertEquals(
            "key-envelopes/u-4294967295.ks",
            RecoveryRelativeNames.microfileKeyEnvelope(RecoveryContract.U32_MAX),
        )
        assertThrows(RecoveryContractException::class.java) {
            RecoveryRelativeNames.checkpointCiphertext(0UL)
        }
        assertThrows(RecoveryContractException::class.java) {
            RecoveryRelativeNames.microfileCiphertext(RecoveryContract.U32_MAX + 1UL)
        }
    }

    @Test
    fun `validators reject traversal absolute drive backslash segment suffix and noncanonical text`() {
        val invalidCheckpointNames =
            listOf(
                "../checkpoints/g-00000000000000000042.ct",
                "/checkpoints/g-00000000000000000042.ct",
                "C:/checkpoints/g-00000000000000000042.ct",
                "C:\\checkpoints\\g-00000000000000000042.ct",
                "checkpoints\\g-00000000000000000042.ct",
                "manifests/g-00000000000000000042.ct",
                "checkpoints/g-00000000000000000042.ks",
                "checkpoints/g-0000000000000000042.ct",
                "checkpoints/g-000000000000000000042.ct",
                "checkpoints/g--0000000000000000042.ct",
                "checkpoints/g-18446744073709551616.ct",
                "checkpoints/g-00000000000000000041.ct",
                "checkpoints/extra/g-00000000000000000042.ct",
                "checkpoints/g-00000000000000000042.ct/extra",
                "checkpoints/g-00000000000000000042.ct.tmp.tmp",
            )
        invalidCheckpointNames.forEach { value ->
            assertThrows(RecoveryContractException::class.java) {
                RecoveryRelativeNames.validateCheckpointCiphertext(value, generation = 42UL)
            }
        }

        val invalidUnitNames =
            listOf(
                "units/u-000000008.ct",
                "units/u-00000000008.ct",
                "units/u--000000007.ct",
                "units/u-4294967296.ct",
                "units/u-0000000008.ct",
                "wrong/u-0000000007.ct",
                "units/u-0000000007.ks",
                "units/extra/u-0000000007.ct",
            )
        invalidUnitNames.forEach { value ->
            assertThrows(RecoveryContractException::class.java) {
                RecoveryRelativeNames.validateMicrofileCiphertext(value, unitIndex = 7UL)
            }
        }
    }

    @Test
    fun `record values bind exact final names to their unit identity`() {
        assertThrows(RecoveryContractException::class.java) {
            RecoveryManifestEntry(
                unitIndex = 7UL,
                plaintextStartInclusive = 0UL,
                plaintextEndExclusive = 1UL,
                cadenceSeconds = 5UL,
                ciphertextBytes = 1UL,
                ciphertextSha256 = DIGEST,
                keyEnvelopeBytes = 1UL,
                keyEnvelopeSha256 = DIGEST,
                ciphertextRelativeName = RecoveryRelativeNames.microfileCiphertext(8UL),
                keyEnvelopeRelativeName = RecoveryRelativeNames.microfileKeyEnvelope(7UL),
            )
        }
        assertThrows(RecoveryContractException::class.java) {
            RecoveryCheckpoint(
                candidate = RecoveryCandidate.STREAM,
                runId = RUN_ID,
                generation = 1UL,
                previousCheckpointCiphertextSha256 = Sha256Value.ZERO,
                durableNonFinalSegmentCount = 0UL,
                ciphertextPrefixBytes = 0UL,
                committedEndExclusive = 0UL,
                streamKeyEnvelopeBytes = 1UL,
                streamKeyEnvelopeSha256 = DIGEST,
                streamCiphertextRelativeName =
                    RecoveryRelativeNames.streamCiphertext(RecoveryRelativeNameState.TEMPORARY),
                streamKeyEnvelopeRelativeName = RecoveryRelativeNames.streamKeyEnvelope(),
            )
        }
    }

    @Test
    fun `record decoders reject noncanonical relative identities`() {
        val checkpoint = hex(RecoveryRecordsTest.CHECKPOINT_GOLDEN_HEX)
        checkpoint[findAscii(checkpoint, "stream/stream.ct")] = 'x'.code.toByte()
        assertThrows(RecoveryContractException::class.java) {
            RecoveryCheckpointCodec.decode(checkpoint)
        }

        val manifest = hex(RecoveryRecordsTest.MANIFEST_GOLDEN_HEX)
        manifest[findAscii(manifest, "units/u-0000000000.ct")] = 'x'.code.toByte()
        assertThrows(RecoveryContractException::class.java) {
            RecoveryManifestCodec.decode(manifest)
        }
    }

    private fun canonicalCases(
        generation: ULong,
        unitIndex: ULong,
    ): List<NameCase> =
        listOf(
            NameCase(
                RecoveryRelativeNames.streamCiphertext(),
                RecoveryRelativeNames.streamCiphertext(RecoveryRelativeNameState.TEMPORARY),
                { value -> RecoveryRelativeNames.validateStreamCiphertext(value) },
                { value ->
                    RecoveryRelativeNames.validateStreamCiphertext(
                        value,
                        RecoveryRelativeNameState.TEMPORARY,
                    )
                },
            ),
            NameCase(
                RecoveryRelativeNames.streamKeyEnvelope(),
                RecoveryRelativeNames.streamKeyEnvelope(RecoveryRelativeNameState.TEMPORARY),
                { value -> RecoveryRelativeNames.validateStreamKeyEnvelope(value) },
                { value ->
                    RecoveryRelativeNames.validateStreamKeyEnvelope(
                        value,
                        RecoveryRelativeNameState.TEMPORARY,
                    )
                },
            ),
            generationCase(
                RecoveryRelativeNames::checkpointCiphertext,
                RecoveryRelativeNames::validateCheckpointCiphertext,
                generation,
            ),
            generationCase(
                RecoveryRelativeNames::checkpointKeyEnvelope,
                RecoveryRelativeNames::validateCheckpointKeyEnvelope,
                generation,
            ),
            unitCase(
                RecoveryRelativeNames::microfileCiphertext,
                RecoveryRelativeNames::validateMicrofileCiphertext,
                unitIndex,
            ),
            unitCase(
                RecoveryRelativeNames::microfileKeyEnvelope,
                RecoveryRelativeNames::validateMicrofileKeyEnvelope,
                unitIndex,
            ),
            generationCase(
                RecoveryRelativeNames::manifestCiphertext,
                RecoveryRelativeNames::validateManifestCiphertext,
                generation,
            ),
            generationCase(
                RecoveryRelativeNames::manifestKeyEnvelope,
                RecoveryRelativeNames::validateManifestKeyEnvelope,
                generation,
            ),
        )

    private fun generationCase(
        builder: (ULong, RecoveryRelativeNameState) -> String,
        validator: (String, ULong, RecoveryRelativeNameState) -> Unit,
        generation: ULong,
    ): NameCase =
        NameCase(
            finalName = builder(generation, RecoveryRelativeNameState.FINAL),
            temporaryName = builder(generation, RecoveryRelativeNameState.TEMPORARY),
            validateFinal = { value ->
                validator(value, generation, RecoveryRelativeNameState.FINAL)
            },
            validateTemporary = { value ->
                validator(value, generation, RecoveryRelativeNameState.TEMPORARY)
            },
        )

    private fun unitCase(
        builder: (ULong, RecoveryRelativeNameState) -> String,
        validator: (String, ULong, RecoveryRelativeNameState) -> Unit,
        unitIndex: ULong,
    ): NameCase =
        NameCase(
            finalName = builder(unitIndex, RecoveryRelativeNameState.FINAL),
            temporaryName = builder(unitIndex, RecoveryRelativeNameState.TEMPORARY),
            validateFinal = { value ->
                validator(value, unitIndex, RecoveryRelativeNameState.FINAL)
            },
            validateTemporary = { value ->
                validator(value, unitIndex, RecoveryRelativeNameState.TEMPORARY)
            },
        )

    private data class NameCase(
        val finalName: String,
        val temporaryName: String,
        val validateFinal: (String) -> Unit,
        val validateTemporary: (String) -> Unit,
    )

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

    private companion object {
        val RUN_ID = RunId.fromCanonicalString("00112233-4455-6677-8899-aabbccddeeff")
        val DIGEST = Sha256Value.calculate("digest".encodeToByteArray())
    }
}
