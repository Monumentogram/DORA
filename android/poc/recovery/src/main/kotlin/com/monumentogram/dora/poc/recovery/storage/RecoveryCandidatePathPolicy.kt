package com.monumentogram.dora.poc.recovery.storage

import com.monumentogram.dora.poc.recovery.contract.RunId
import java.io.File

internal data class RecoveryCandidatePaths(val runRoot: File, val artifact: File)

internal object RecoveryCandidatePathPolicy {
    private val allowed =
        Regex(
            "(?:units/u-[0-9]{10}\\.ct|manifests/g-[0-9]{20}\\.ct|" +
                "key-envelopes/(?:u-[0-9]{10}|manifest-g-[0-9]{20})\\.ks)(?:\\.tmp)?"
        )

    fun paths(noBackupRoot: File, runId: RunId, relativeName: String): RecoveryCandidatePaths {
        require(allowed.matches(relativeName)) { "Candidate path is not an exact allowlisted name" }
        val runRoot =
            File(noBackupRoot.absoluteFile, "poc-recovery/v1/runs/${runId.toCanonicalString()}")
        val artifact = File(runRoot, relativeName)
        RecoveryBootstrapPathPolicy.requireContained(runRoot, artifact)
        return RecoveryCandidatePaths(runRoot, artifact)
    }

    fun finalExists(type: BootstrapPathType, leafName: String): Boolean =
        when (type) {
            BootstrapPathType.ABSENT -> false
            BootstrapPathType.REGULAR -> true
            else -> throw UnsafeRecoveryBootstrapPathException("Unsafe candidate final: $leafName")
        }
}
