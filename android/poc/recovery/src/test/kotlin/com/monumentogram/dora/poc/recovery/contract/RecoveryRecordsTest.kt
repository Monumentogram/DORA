package com.monumentogram.dora.poc.recovery.contract

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RecoveryRecordsTest {
    @Test
    fun `checkpoint matches an independently fixed golden vector`() {
        val checkpoint =
            RecoveryCheckpoint(
                candidate = RecoveryCandidate.STREAM,
                runId = RUN_ID,
                generation = 1UL,
                previousCheckpointCiphertextSha256 = DIGEST_00_1F,
                durableNonFinalSegmentCount = 3UL,
                ciphertextPrefixBytes = 12_288UL,
                committedEndExclusive = 8_136UL,
                streamKeyEnvelopeBytes = 1_234UL,
                streamKeyEnvelopeSha256 = DIGEST_20_3F,
                streamCiphertextRelativeName = "stream/stream.ct",
                streamKeyEnvelopeRelativeName = "key-envelopes/stream.ks",
            )

        val encoded = RecoveryCheckpointCodec.encode(checkpoint)
        assertArrayEquals(hex(CHECKPOINT_GOLDEN_HEX), encoded)
        assertEquals(checkpoint, RecoveryCheckpointCodec.decode(encoded))
    }

    @Test
    fun `checkpoint rejects generation and one-segment-lookahead mismatches`() {
        assertThrows(RecoveryContractException::class.java) {
            checkpoint(generation = 0UL)
        }
        assertThrows(RecoveryContractException::class.java) {
            checkpoint(ciphertextPrefixBytes = 12_287UL)
        }
        assertThrows(RecoveryContractException::class.java) {
            checkpoint(committedEndExclusive = 8_135UL)
        }
        assertThrows(RecoveryContractException::class.java) {
            checkpoint(streamCiphertextRelativeName = "")
        }
    }

    @Test
    fun `manifest matches an independently fixed golden vector`() {
        val manifest =
            RecoveryManifest.create(
                candidate = RecoveryCandidate.MICROFILE,
                runId = RUN_ID,
                generation = 2UL,
                previousManifestCiphertextSha256 = DIGEST_00_1F,
                committedEndExclusive = 160_000UL,
                entries =
                    listOf(
                        manifestEntry(
                            end = 160_000UL,
                            ciphertextBytes = 160_033UL,
                            keyEnvelopeBytes = 99UL,
                            ciphertextRelativeName = "units/u-0000000000.ct",
                            keyEnvelopeRelativeName = "key-envelopes/u-0000000000.ks",
                        )
                    ),
            )

        val encoded = RecoveryManifestCodec.encode(manifest)
        assertArrayEquals(hex(MANIFEST_GOLDEN_HEX), encoded)
        assertEquals(manifest, RecoveryManifestCodec.decode(encoded))
    }

    @Test
    fun `manifest accepts zero one and 721 entries and defensively copies the list`() {
        val empty = manifest(entries = emptyList(), committedEndExclusive = 0UL)
        assertEquals(
            0,
            RecoveryManifestCodec.decode(RecoveryManifestCodec.encode(empty)).entries.size,
        )

        val mutableEntries = mutableListOf(manifestEntry(end = 1UL))
        val one = manifest(entries = mutableEntries, committedEndExclusive = 1UL)
        mutableEntries.clear()
        assertEquals(1, one.entries.size)

        val maximumEntries =
            List(RecoveryContract.MAX_MANIFEST_ENTRIES) { index ->
                manifestEntry(
                    unitIndex = index.toULong(),
                    start = index.toULong(),
                    end = index.toULong() + 1UL,
                )
            }
        val maximum =
            manifest(
                entries = maximumEntries,
                committedEndExclusive = RecoveryContract.MAX_MANIFEST_ENTRIES.toULong(),
            )
        assertEquals(
            RecoveryContract.MAX_MANIFEST_ENTRIES,
            RecoveryManifestCodec.decode(RecoveryManifestCodec.encode(maximum)).entries.size,
        )

        val tooMany = maximumEntries + manifestEntry(unitIndex = 721UL, start = 721UL, end = 722UL)
        assertThrows(RecoveryContractException::class.java) {
            manifest(entries = tooMany, committedEndExclusive = 722UL)
        }
    }

    @Test
    fun `manifest rejects gaps duplicates reorder removal and committed end mismatch`() {
        assertThrows(RecoveryContractException::class.java) {
            manifest(
                entries = listOf(manifestEntry(unitIndex = 1UL, start = 0UL, end = 1UL)),
                committedEndExclusive = 1UL,
            )
        }
        assertThrows(RecoveryContractException::class.java) {
            manifest(
                entries =
                    listOf(
                        manifestEntry(unitIndex = 0UL, start = 0UL, end = 1UL),
                        manifestEntry(unitIndex = 0UL, start = 1UL, end = 2UL),
                    ),
                committedEndExclusive = 2UL,
            )
        }
        assertThrows(RecoveryContractException::class.java) {
            manifest(
                entries =
                    listOf(
                        manifestEntry(unitIndex = 0UL, start = 0UL, end = 1UL),
                        manifestEntry(unitIndex = 1UL, start = 2UL, end = 3UL),
                    ),
                committedEndExclusive = 3UL,
            )
        }
        assertThrows(RecoveryContractException::class.java) {
            manifest(
                entries = listOf(manifestEntry(unitIndex = 0UL, start = 0UL, end = 1UL)),
                committedEndExclusive = 2UL,
            )
        }
        assertThrows(RecoveryContractException::class.java) {
            manifestEntry(unitIndex = 0UL, start = 1UL, end = 1UL)
        }
    }

    @Test
    fun `manifest enforces the exact encoded plaintext boundary`() {
        val baseEntries = sizedManifestEntries(List(NAME_FIELD_COUNT) { 1 })
        val base = manifest(entries = baseEntries, committedEndExclusive = ENTRY_COUNT.toULong())
        val remaining =
            RecoveryContract.MAX_MANIFEST_PLAINTEXT_BYTES - RecoveryManifestCodec.encode(base).size
        val lengths = MutableList(NAME_FIELD_COUNT) { 1 }
        var bytesToAllocate = remaining
        lengths.indices.forEach { index ->
            val addition = minOf(bytesToAllocate, RecoveryContract.MAX_LP16_ASCII_BYTES - 1)
            lengths[index] += addition
            bytesToAllocate -= addition
        }
        assertEquals(0, bytesToAllocate)

        val exact =
            manifest(
                entries = sizedManifestEntries(lengths),
                committedEndExclusive = ENTRY_COUNT.toULong(),
            )
        assertEquals(
            RecoveryContract.MAX_MANIFEST_PLAINTEXT_BYTES,
            RecoveryManifestCodec.encode(exact).size,
        )

        val expandableIndex = lengths.indexOfLast { it < RecoveryContract.MAX_LP16_ASCII_BYTES }
        val oversizedLengths = lengths.toMutableList()
        oversizedLengths[expandableIndex] += 1
        val oversized =
            manifest(
                entries = sizedManifestEntries(oversizedLengths),
                committedEndExclusive = ENTRY_COUNT.toULong(),
            )
        assertThrows(RecoveryContractException::class.java) {
            RecoveryManifestCodec.encode(oversized)
        }
    }

    private fun checkpoint(
        generation: ULong = 1UL,
        ciphertextPrefixBytes: ULong = 12_288UL,
        committedEndExclusive: ULong = 8_136UL,
        streamCiphertextRelativeName: String = "stream/stream.ct",
    ): RecoveryCheckpoint =
        RecoveryCheckpoint(
            candidate = RecoveryCandidate.STREAM,
            runId = RUN_ID,
            generation = generation,
            previousCheckpointCiphertextSha256 = DIGEST_00_1F,
            durableNonFinalSegmentCount = 3UL,
            ciphertextPrefixBytes = ciphertextPrefixBytes,
            committedEndExclusive = committedEndExclusive,
            streamKeyEnvelopeBytes = 1UL,
            streamKeyEnvelopeSha256 = DIGEST_20_3F,
            streamCiphertextRelativeName = streamCiphertextRelativeName,
            streamKeyEnvelopeRelativeName = "key-envelopes/stream.ks",
        )

    private fun manifest(
        entries: List<RecoveryManifestEntry>,
        committedEndExclusive: ULong,
    ): RecoveryManifest =
        RecoveryManifest.create(
            candidate = RecoveryCandidate.MICROFILE,
            runId = RUN_ID,
            generation = 1UL,
            previousManifestCiphertextSha256 = DIGEST_00_1F,
            committedEndExclusive = committedEndExclusive,
            entries = entries,
        )

    @Suppress("LongParameterList")
    private fun manifestEntry(
        unitIndex: ULong = 0UL,
        start: ULong = 0UL,
        end: ULong,
        ciphertextBytes: ULong = 1UL,
        keyEnvelopeBytes: ULong = 1UL,
        ciphertextRelativeName: String = "c",
        keyEnvelopeRelativeName: String = "k",
    ): RecoveryManifestEntry =
        RecoveryManifestEntry(
            unitIndex = unitIndex,
            plaintextStartInclusive = start,
            plaintextEndExclusive = end,
            cadenceSeconds = 5UL,
            ciphertextBytes = ciphertextBytes,
            ciphertextSha256 = DIGEST_20_3F,
            keyEnvelopeBytes = keyEnvelopeBytes,
            keyEnvelopeSha256 = DIGEST_00_1F,
            ciphertextRelativeName = ciphertextRelativeName,
            keyEnvelopeRelativeName = keyEnvelopeRelativeName,
        )

    private fun sizedManifestEntries(nameLengths: List<Int>): List<RecoveryManifestEntry> {
        require(nameLengths.size == NAME_FIELD_COUNT)
        return List(ENTRY_COUNT) { index ->
            manifestEntry(
                unitIndex = index.toULong(),
                start = index.toULong(),
                end = index.toULong() + 1UL,
                ciphertextRelativeName = "c".repeat(nameLengths[index * 2]),
                keyEnvelopeRelativeName = "k".repeat(nameLengths[index * 2 + 1]),
            )
        }
    }

    companion object {
        val RUN_ID = RunId.fromCanonicalString("00112233-4455-6677-8899-aabbccddeeff")
        val DIGEST_00_1F = Sha256Value.fromBytes(ByteArray(32) { it.toByte() })
        val DIGEST_20_3F = Sha256Value.fromBytes(ByteArray(32) { (it + 32).toByte() })

        const val ENTRY_COUNT = 4
        const val NAME_FIELD_COUNT = ENTRY_COUNT * 2

        const val CHECKPOINT_GOLDEN_HEX =
            "444f52415243303100010021706f632d7265636f766572792d70726f746f636f6c2d7374616765302d76302e36" +
                "000f5245432d53545245414d2d54494e4b00112233445566778899aabbccddeeff0000000000000001" +
                "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f000000030000000000003000" +
                "0000000000001fc800000000000004d2202122232425262728292a2b2c2d2e2f303132333435363738393a3b" +
                "3c3d3e3f001073747265616d2f73747265616d2e637400176b65792d656e76656c6f7065732f73747265616d" +
                "2e6b73"

        const val MANIFEST_GOLDEN_HEX =
            "444f5241524d303100010021706f632d7265636f766572792d70726f746f636f6c2d7374616765302d76302e36" +
                "00125245432d4d4943524f46494c452d54494e4b00112233445566778899aabbccddeeff0000000000000002" +
                "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f000000000002710000000001" +
                "0000000000000000000000000000000000027100000000050000000000027121202122232425262728292a2b" +
                "2c2d2e2f303132333435363738393a3b3c3d3e3f0000000000000063000102030405060708090a0b0c0d0e0f" +
                "101112131415161718191a1b1c1d1e1f0015756e6974732f752d303030303030303030302e6374001d6b6579" +
                "2d656e76656c6f7065732f752d303030303030303030302e6b73"
    }
}
