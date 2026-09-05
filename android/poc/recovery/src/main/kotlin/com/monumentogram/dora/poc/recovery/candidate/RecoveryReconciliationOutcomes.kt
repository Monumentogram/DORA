package com.monumentogram.dora.poc.recovery.candidate

import com.monumentogram.dora.poc.recovery.contract.RecoveryManifest

internal enum class RecoveryFailureCategory {
    UNSAFE_PARENT,
    CORRUPT_LEAF,
    MISSING_ARTIFACT,
    STRUCTURAL,
    AUTHENTICATION_REJECTED,
    OPERATIONAL,
    UNKNOWN_OUTCOME,
}

internal data class RecoveryFailureDiagnostic(
    val category: RecoveryFailureCategory,
    val type: String,
    val message: String,
) {
    companion object {
        fun capture(category: RecoveryFailureCategory, error: Throwable) =
            RecoveryFailureDiagnostic(
                category,
                error::class.java.name.take(MAX_TEXT),
                (error.message ?: "").take(MAX_TEXT),
            )

        private const val MAX_TEXT = 256
    }
}

internal sealed interface ManifestAuthenticationOutcome {
    data class Authenticated(val manifest: RecoveryManifest) : ManifestAuthenticationOutcome

    data class Rejected(val diagnostic: RecoveryFailureDiagnostic) : ManifestAuthenticationOutcome
}

internal sealed interface UnitAuthenticationOutcome {
    class Authenticated(bytes: ByteArray) : UnitAuthenticationOutcome {
        private val value = bytes.copyOf()

        fun snapshot(): ByteArray = value.copyOf()
    }

    data class Rejected(val diagnostic: RecoveryFailureDiagnostic) : UnitAuthenticationOutcome
}
