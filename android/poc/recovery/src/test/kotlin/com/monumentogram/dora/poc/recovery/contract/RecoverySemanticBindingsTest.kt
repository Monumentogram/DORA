package com.monumentogram.dora.poc.recovery.contract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RecoverySemanticBindingsTest {
    @Test
    fun `all exact candidate kind and target bindings construct and decode`() {
        val checkpoint = checkpoint(RecoveryCandidate.STREAM)
        assertEquals(
            checkpoint,
            RecoveryCheckpointCodec.decode(RecoveryCheckpointCodec.encode(checkpoint)),
        )

        val manifest = manifest(RecoveryCandidate.MICROFILE)
        assertEquals(manifest, RecoveryManifestCodec.decode(RecoveryManifestCodec.encode(manifest)))

        val streaming = StreamingAad(RecoveryCandidate.STREAM, RUN_ID)
        assertEquals(streaming, StreamingAadCodec.decode(StreamingAadCodec.encode(streaming)))

        val microfile = microfileAad(RecoveryCandidate.MICROFILE)
        assertEquals(microfile, MicrofileAadCodec.decode(MicrofileAadCodec.encode(microfile)))

        PublicationKind.entries.forEach { kind ->
            val value = publicationAad(kind.expectedCandidate, kind)
            assertEquals(value, PublicationAadCodec.decode(PublicationAadCodec.encode(value)))
        }
        KeyEnvelopeTargetKind.entries.forEach { target ->
            val value = keyEnvelopeAad(target.expectedCandidate, target)
            assertEquals(value, KeyEnvelopeAadCodec.decode(KeyEnvelopeAadCodec.encode(value)))
        }
    }

    @Test
    fun `every wrong candidate kind and target binding is rejected before encoding`() {
        assertThrows(RecoveryContractException::class.java) {
            checkpoint(RecoveryCandidate.MICROFILE)
        }
        assertThrows(RecoveryContractException::class.java) {
            manifest(RecoveryCandidate.STREAM)
        }
        assertThrows(RecoveryContractException::class.java) {
            StreamingAad(RecoveryCandidate.MICROFILE, RUN_ID)
        }
        assertThrows(RecoveryContractException::class.java) {
            microfileAad(RecoveryCandidate.STREAM)
        }
        PublicationKind.entries.forEach { kind ->
            assertThrows(RecoveryContractException::class.java) {
                publicationAad(opposite(kind.expectedCandidate), kind)
            }
        }
        KeyEnvelopeTargetKind.entries.forEach { target ->
            assertThrows(RecoveryContractException::class.java) {
                keyEnvelopeAad(opposite(target.expectedCandidate), target)
            }
        }
    }

    @Test
    fun `every wrong encoded candidate kind and target binding is rejected at decode`() {
        assertThrows(RecoveryContractException::class.java) {
            RecoveryCheckpointCodec.decode(rawCheckpoint(RecoveryCandidate.MICROFILE))
        }
        assertThrows(RecoveryContractException::class.java) {
            RecoveryManifestCodec.decode(rawManifest(RecoveryCandidate.STREAM))
        }
        assertThrows(RecoveryContractException::class.java) {
            StreamingAadCodec.decode(rawStreamingAad(RecoveryCandidate.MICROFILE))
        }
        assertThrows(RecoveryContractException::class.java) {
            MicrofileAadCodec.decode(rawMicrofileAad(RecoveryCandidate.STREAM))
        }
        PublicationKind.entries.forEach { kind ->
            assertThrows(RecoveryContractException::class.java) {
                PublicationAadCodec.decode(
                    rawPublicationAad(opposite(kind.expectedCandidate), kind)
                )
            }
        }
        KeyEnvelopeTargetKind.entries.forEach { target ->
            assertThrows(RecoveryContractException::class.java) {
                KeyEnvelopeAadCodec.decode(
                    rawKeyEnvelopeAad(opposite(target.expectedCandidate), target)
                )
            }
        }
    }

    @Test
    fun `plaintext end accepts exactly 115200000 where the boundary is applicable`() {
        val maximum = RecoveryContract.MAX_PLAINTEXT_BYTES_PER_RUN
        val maximumEntry = manifestEntry(end = maximum, cadenceSeconds = 5UL)
        val maximumManifest =
            RecoveryManifest.create(
                candidate = RecoveryCandidate.MICROFILE,
                runId = RUN_ID,
                generation = 1UL,
                previousManifestCiphertextSha256 = Sha256Value.ZERO,
                committedEndExclusive = maximum,
                entries = listOf(maximumEntry),
            )
        assertEquals(maximum, maximumManifest.committedEndExclusive)
        assertEquals(
            maximum,
            microfileAad(RecoveryCandidate.MICROFILE, end = maximum).plaintextEndExclusive,
        )
        assertEquals(
            maximum,
            publicationAad(
                    RecoveryCandidate.MICROFILE,
                    PublicationKind.MANIFEST,
                    terminalUnitIndex = 0UL,
                    end = maximum,
                )
                .plaintextEndExclusive,
        )
        assertEquals(
            maximum,
            keyEnvelopeAad(
                    RecoveryCandidate.MICROFILE,
                    KeyEnvelopeTargetKind.MICROFILE,
                    end = maximum,
                )
                .plaintextEndExclusive,
        )
    }

    @Test
    fun `plaintext end rejects 115200001 and every greater tested value`() {
        val maximum = RecoveryContract.MAX_PLAINTEXT_BYTES_PER_RUN
        listOf(maximum + 1UL, maximum + 2UL, ULong.MAX_VALUE).forEach { invalidEnd ->
            assertThrows(RecoveryContractException::class.java) {
                manifestEntry(end = invalidEnd, cadenceSeconds = 5UL)
            }
            assertThrows(RecoveryContractException::class.java) {
                microfileAad(RecoveryCandidate.MICROFILE, end = invalidEnd)
            }
            assertThrows(RecoveryContractException::class.java) {
                publicationAad(
                    RecoveryCandidate.MICROFILE,
                    PublicationKind.MANIFEST,
                    terminalUnitIndex = 0UL,
                    end = invalidEnd,
                )
            }
            assertThrows(RecoveryContractException::class.java) {
                keyEnvelopeAad(
                    RecoveryCandidate.MICROFILE,
                    KeyEnvelopeTargetKind.MICROFILE,
                    end = invalidEnd,
                )
            }
        }
        assertThrows(RecoveryContractException::class.java) {
            RecoveryManifest.create(
                candidate = RecoveryCandidate.MICROFILE,
                runId = RUN_ID,
                generation = 1UL,
                previousManifestCiphertextSha256 = Sha256Value.ZERO,
                committedEndExclusive = maximum + 1UL,
                entries = emptyList(),
            )
        }
    }

    @Test
    fun `microfile cadences are exactly 5 15 and 30 while non-applicable targets use zero`() {
        listOf(5UL, 15UL, 30UL).forEach { cadence ->
            manifestEntry(end = 1UL, cadenceSeconds = cadence)
            microfileAad(RecoveryCandidate.MICROFILE, cadenceSeconds = cadence)
            keyEnvelopeAad(
                RecoveryCandidate.MICROFILE,
                KeyEnvelopeTargetKind.MICROFILE,
                cadenceSeconds = cadence,
            )
        }
        listOf(0UL, 1UL, 7UL, 31UL, RecoveryContract.U32_MAX).forEach { cadence ->
            assertThrows(RecoveryContractException::class.java) {
                manifestEntry(end = 1UL, cadenceSeconds = cadence)
            }
            assertThrows(RecoveryContractException::class.java) {
                microfileAad(RecoveryCandidate.MICROFILE, cadenceSeconds = cadence)
            }
            assertThrows(RecoveryContractException::class.java) {
                keyEnvelopeAad(
                    RecoveryCandidate.MICROFILE,
                    KeyEnvelopeTargetKind.MICROFILE,
                    cadenceSeconds = cadence,
                )
            }
        }

        listOf(
                KeyEnvelopeTargetKind.STREAM,
                KeyEnvelopeTargetKind.MANIFEST,
                KeyEnvelopeTargetKind.CHECKPOINT,
            )
            .forEach { target ->
                keyEnvelopeAad(target.expectedCandidate, target, cadenceSeconds = 0UL)
                listOf(5UL, 15UL, 30UL).forEach { cadence ->
                    assertThrows(RecoveryContractException::class.java) {
                        keyEnvelopeAad(
                            target.expectedCandidate,
                            target,
                            cadenceSeconds = cadence,
                        )
                    }
                }
            }
    }

    @Test
    fun `semantic cadence and plaintext bounds are also rejected during decode`() {
        assertThrows(RecoveryContractException::class.java) {
            MicrofileAadCodec.decode(
                rawMicrofileAad(
                    RecoveryCandidate.MICROFILE,
                    cadenceSeconds = 7UL,
                )
            )
        }
        assertThrows(RecoveryContractException::class.java) {
            PublicationAadCodec.decode(
                rawPublicationAad(
                    RecoveryCandidate.MICROFILE,
                    PublicationKind.MANIFEST,
                    terminalUnitIndex = 0UL,
                    end = RecoveryContract.MAX_PLAINTEXT_BYTES_PER_RUN + 1UL,
                )
            )
        }
        assertThrows(RecoveryContractException::class.java) {
            KeyEnvelopeAadCodec.decode(
                rawKeyEnvelopeAad(
                    RecoveryCandidate.STREAM,
                    KeyEnvelopeTargetKind.STREAM,
                    cadenceSeconds = 5UL,
                )
            )
        }
    }

    private fun checkpoint(candidate: RecoveryCandidate): RecoveryCheckpoint =
        RecoveryCheckpoint(
            candidate = candidate,
            runId = RUN_ID,
            generation = 1UL,
            previousCheckpointCiphertextSha256 = Sha256Value.ZERO,
            durableNonFinalSegmentCount = 0UL,
            ciphertextPrefixBytes = 0UL,
            committedEndExclusive = 0UL,
            streamKeyEnvelopeBytes = 1UL,
            streamKeyEnvelopeSha256 = DIGEST,
            streamCiphertextRelativeName = RecoveryRelativeNames.streamCiphertext(),
            streamKeyEnvelopeRelativeName = RecoveryRelativeNames.streamKeyEnvelope(),
        )

    private fun manifest(candidate: RecoveryCandidate): RecoveryManifest =
        RecoveryManifest.create(
            candidate = candidate,
            runId = RUN_ID,
            generation = 1UL,
            previousManifestCiphertextSha256 = Sha256Value.ZERO,
            committedEndExclusive = 0UL,
            entries = emptyList(),
        )

    private fun manifestEntry(
        end: ULong,
        cadenceSeconds: ULong,
    ): RecoveryManifestEntry =
        RecoveryManifestEntry(
            unitIndex = 0UL,
            plaintextStartInclusive = 0UL,
            plaintextEndExclusive = end,
            cadenceSeconds = cadenceSeconds,
            ciphertextBytes = 1UL,
            ciphertextSha256 = DIGEST,
            keyEnvelopeBytes = 1UL,
            keyEnvelopeSha256 = DIGEST,
            ciphertextRelativeName = RecoveryRelativeNames.microfileCiphertext(0UL),
            keyEnvelopeRelativeName = RecoveryRelativeNames.microfileKeyEnvelope(0UL),
        )

    private fun microfileAad(
        candidate: RecoveryCandidate,
        end: ULong = 1UL,
        cadenceSeconds: ULong = 5UL,
    ): MicrofileAad =
        MicrofileAad(
            candidate = candidate,
            runId = RUN_ID,
            manifestGeneration = 1UL,
            unitIndex = 0UL,
            plaintextStartInclusive = 0UL,
            plaintextEndExclusive = end,
            cadenceSeconds = cadenceSeconds,
            previousManifestCiphertextSha256 = Sha256Value.ZERO,
        )

    private fun publicationAad(
        candidate: RecoveryCandidate,
        kind: PublicationKind,
        terminalUnitIndex: ULong = PublicationAad.EMPTY_TERMINAL_UNIT_INDEX,
        end: ULong = 0UL,
    ): PublicationAad =
        PublicationAad(
            candidate = candidate,
            runId = RUN_ID,
            publicationKind = kind,
            generation = 1UL,
            terminalUnitIndex = terminalUnitIndex,
            plaintextEndExclusive = end,
            previousPublicationCiphertextSha256 = Sha256Value.ZERO,
        )

    private fun keyEnvelopeAad(
        candidate: RecoveryCandidate,
        target: KeyEnvelopeTargetKind,
        end: ULong = if (target == KeyEnvelopeTargetKind.MICROFILE) 1UL else 0UL,
        cadenceSeconds: ULong = if (target == KeyEnvelopeTargetKind.MICROFILE) 5UL else 0UL,
    ): KeyEnvelopeAad =
        KeyEnvelopeAad(
            candidate = candidate,
            runId = RUN_ID,
            targetKind = target,
            generation = 1UL,
            unitIndex =
                if (target == KeyEnvelopeTargetKind.MICROFILE) {
                    0UL
                } else {
                    KeyEnvelopeAad.NOT_APPLICABLE_UNIT_INDEX
                },
            plaintextStartInclusive = 0UL,
            plaintextEndExclusive = end,
            cadenceSeconds = cadenceSeconds,
            previousPublicationCiphertextSha256 = Sha256Value.ZERO,
        )

    private fun rawCheckpoint(candidate: RecoveryCandidate): ByteArray =
        BoundedBinaryWriter(RecoveryContract.MAX_GENERAL_RECORD_BYTES)
            .apply {
                writeMagic(RecoveryCheckpointCodec.MAGIC)
                writeU16(RecoveryContract.SCHEMA_VERSION)
                writeContractIdentity(candidate, RUN_ID)
                writeU64(1UL)
                writeSha256(Sha256Value.ZERO)
                writeU32(0UL)
                writeU64(0UL)
                writeU64(0UL)
                writeU64(1UL)
                writeSha256(DIGEST)
                writeLp16Ascii(RecoveryRelativeNames.streamCiphertext())
                writeLp16Ascii(RecoveryRelativeNames.streamKeyEnvelope())
            }
            .toByteArray()

    private fun rawManifest(candidate: RecoveryCandidate): ByteArray =
        BoundedBinaryWriter(RecoveryContract.MAX_MANIFEST_PLAINTEXT_BYTES)
            .apply {
                writeMagic(RecoveryManifestCodec.MAGIC)
                writeU16(RecoveryContract.SCHEMA_VERSION)
                writeContractIdentity(candidate, RUN_ID)
                writeU64(1UL)
                writeSha256(Sha256Value.ZERO)
                writeU64(0UL)
                writeU32(0UL)
            }
            .toByteArray()

    private fun rawStreamingAad(candidate: RecoveryCandidate): ByteArray =
        BoundedBinaryWriter(MAX_AAD_BYTES)
            .apply {
                writeMagic(StreamingAadCodec.MAGIC)
                writeU16(RecoveryContract.SCHEMA_VERSION)
                writeContractIdentity(candidate, RUN_ID)
                writeU64(1UL)
                writeU64(0UL)
                writeU64(RecoveryContract.MAX_PLAINTEXT_BYTES_PER_RUN)
                writeSha256(Sha256Value.ZERO)
            }
            .toByteArray()

    private fun rawMicrofileAad(
        candidate: RecoveryCandidate,
        cadenceSeconds: ULong = 5UL,
    ): ByteArray =
        BoundedBinaryWriter(MAX_AAD_BYTES)
            .apply {
                writeMagic(MicrofileAadCodec.MAGIC)
                writeU16(RecoveryContract.SCHEMA_VERSION)
                writeContractIdentity(candidate, RUN_ID)
                writeU64(1UL)
                writeU32(0UL)
                writeU64(0UL)
                writeU64(1UL)
                writeU32(cadenceSeconds)
                writeSha256(Sha256Value.ZERO)
            }
            .toByteArray()

    private fun rawPublicationAad(
        candidate: RecoveryCandidate,
        kind: PublicationKind,
        terminalUnitIndex: ULong = PublicationAad.EMPTY_TERMINAL_UNIT_INDEX,
        end: ULong = 0UL,
    ): ByteArray =
        BoundedBinaryWriter(MAX_AAD_BYTES)
            .apply {
                writeMagic(PublicationAadCodec.MAGIC)
                writeU16(RecoveryContract.SCHEMA_VERSION)
                writeContractIdentity(candidate, RUN_ID)
                writeLp16Ascii(kind.contractId)
                writeU64(1UL)
                writeU32(terminalUnitIndex)
                writeU64(0UL)
                writeU64(end)
                writeSha256(Sha256Value.ZERO)
            }
            .toByteArray()

    private fun rawKeyEnvelopeAad(
        candidate: RecoveryCandidate,
        target: KeyEnvelopeTargetKind,
        cadenceSeconds: ULong = if (target == KeyEnvelopeTargetKind.MICROFILE) 5UL else 0UL,
    ): ByteArray =
        BoundedBinaryWriter(MAX_AAD_BYTES)
            .apply {
                writeMagic(KeyEnvelopeAadCodec.MAGIC)
                writeU16(RecoveryContract.SCHEMA_VERSION)
                writeContractIdentity(candidate, RUN_ID)
                writeLp16Ascii(target.contractId)
                writeU64(1UL)
                writeU32(
                    if (target == KeyEnvelopeTargetKind.MICROFILE) {
                        0UL
                    } else {
                        KeyEnvelopeAad.NOT_APPLICABLE_UNIT_INDEX
                    }
                )
                writeU64(0UL)
                writeU64(if (target == KeyEnvelopeTargetKind.MICROFILE) 1UL else 0UL)
                writeU32(cadenceSeconds)
                writeSha256(Sha256Value.ZERO)
            }
            .toByteArray()

    private fun opposite(value: RecoveryCandidate): RecoveryCandidate =
        when (value) {
            RecoveryCandidate.STREAM -> RecoveryCandidate.MICROFILE
            RecoveryCandidate.MICROFILE -> RecoveryCandidate.STREAM
        }

    private companion object {
        const val MAX_AAD_BYTES = 512
        val RUN_ID = RunId.fromCanonicalString("00112233-4455-6677-8899-aabbccddeeff")
        val DIGEST = Sha256Value.calculate("digest".encodeToByteArray())
    }
}
