package com.monumentogram.dora.poc.recovery.storage

import com.monumentogram.dora.poc.recovery.contract.RunId
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RecoveryCandidatePathPolicyTest {
    private val run = RunId.fromCanonicalString("00112233-4455-6677-8899-aabbccddeeff")

    @Test
    fun `exact canonical final and temporary names remain under run root`() {
        listOf(
                "units/u-0000000000.ct",
                "units/u-0000000000.ct.tmp",
                "key-envelopes/u-0000000000.ks",
                "key-envelopes/manifest-g-00000000000000000001.ks.tmp",
                "manifests/g-00000000000000000001.ct",
            )
            .forEach { name ->
                val paths = RecoveryCandidatePathPolicy.paths(File("root"), run, name)
                assertEquals(
                    File(paths.runRoot, name).absoluteFile.normalize(),
                    paths.artifact.absoluteFile.normalize(),
                )
            }
    }

    @Test
    fun `traversal absolute malformed and unknown names fail closed`() {
        listOf(
                "../units/u-0000000000.ct",
                "/units/u-0000000000.ct",
                "units/u-0.ct",
                "units/u-0000000000.ct.tmp.tmp",
                "key-confirmation/run.kc",
                "units/u-0000000000.ks",
            )
            .forEach { name ->
                assertThrows(IllegalArgumentException::class.java) {
                    RecoveryCandidatePathPolicy.paths(File("root"), run, name)
                }
            }
    }
}
