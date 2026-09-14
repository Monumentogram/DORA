package com.monumentogram.dora.poc.recovery.candidate

import com.monumentogram.dora.poc.recovery.contract.RecoveryCandidate
import com.monumentogram.dora.poc.recovery.contract.RecoveryContractException
import com.monumentogram.dora.poc.recovery.contract.RecoveryManifest
import com.monumentogram.dora.poc.recovery.contract.RecoveryManifestCodec
import com.monumentogram.dora.poc.recovery.contract.RecoveryManifestEntry
import com.monumentogram.dora.poc.recovery.contract.RecoveryRelativeNames
import com.monumentogram.dora.poc.recovery.contract.RunId
import com.monumentogram.dora.poc.recovery.contract.Sha256Value
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class RecoveryCampaignPublicationFaultsTest {
    @Test
    fun everyCor05MutationRejectsAnOriginallyValidThreeUnitManifest() {
        val baseline = baseline()
        assertEquals(3, RecoveryManifestCodec.decode(baseline).entries.size)
        for (variant in listOf("DUPLICATE_ENTRY", "GAP_ENTRY", "REORDER_ENTRIES", "REMOVE_ENTRY")) {
            val fault = RecoveryCampaignPublicationFaults.malformedManifest(baseline, variant)
            assertFalse(baseline.contentEquals(fault))
            assertThrows(RecoveryContractException::class.java) {
                RecoveryManifestCodec.decode(fault)
            }
        }
        assertEquals(3, RecoveryManifestCodec.decode(baseline).entries.size)
    }

    @Test
    fun malformedOversizedTrailingAndUnsafePublicationRecipesCannotDecode() {
        val baseline = baseline()
        for (variant in
            listOf("MALFORMED", "OVERSIZED", "TRAILING_BYTE", "UNSAFE_PATH", "TRAVERSAL_PATH")) {
            val fault =
                RecoveryCampaignPublicationFaults.malformedPublication(baseline, variant, false)
            assertThrows(RecoveryContractException::class.java) {
                RecoveryManifestCodec.decode(fault)
            }
        }
    }

    private fun baseline(): ByteArray {
        val entries =
            List(3) { index ->
                RecoveryManifestEntry(
                    index.toULong(),
                    (index * 160000).toULong(),
                    ((index + 1) * 160000).toULong(),
                    5UL,
                    160028UL,
                    Sha256Value.calculate(byteArrayOf(index.toByte())),
                    123UL,
                    Sha256Value.calculate(byteArrayOf((index + 3).toByte())),
                    RecoveryRelativeNames.microfileCiphertext(index.toULong()),
                    RecoveryRelativeNames.microfileKeyEnvelope(index.toULong()),
                )
            }
        return RecoveryManifestCodec.encode(
            RecoveryManifest.create(
                RecoveryCandidate.MICROFILE,
                RunId.fromCanonicalString("00000000-0000-0000-0000-000000000001"),
                3UL,
                Sha256Value.calculate(byteArrayOf(42)),
                480000UL,
                entries,
            )
        )
    }
}
