package com.monumentogram.dora.poc.recovery.crypto

import java.security.GeneralSecurityException
import java.security.ProviderException
import javax.crypto.AEADBadTagException
import javax.crypto.BadPaddingException

/** Neutral low-level signal from an AEAD decrypt attempt; no REC-I3 outcome is assigned here. */
internal enum class RecoveryDecryptFailureSignal {
    AUTHENTICATION_REJECTED,
    OPERATIONAL,
    UNKNOWN,
}

/** Stable failure boundary for encrypted-keyset parsing below the Recovery controller. */
internal enum class RecoveryEncryptedKeysetParseFailure {
    OUTER_STRUCTURAL_OR_ENCODING_INVALID,
    AUTHENTICATION_REJECTED,
    OPERATIONAL,
    UNKNOWN,
    AUTHENTICATED_INNER_KEYSET_INVALID,
    PARSED_BUT_UNSUPPORTED_PARAMETER_SUITE,
}

internal class RecoveryEncryptedKeysetParseException(
    val failure: RecoveryEncryptedKeysetParseFailure,
    cause: Throwable,
) : GeneralSecurityException("Recovery encrypted-keyset parse failure: ${failure.name}", cause)

internal fun failEncryptedKeysetParse(
    failure: RecoveryEncryptedKeysetParseFailure,
    cause: Throwable,
): Nothing = throw RecoveryEncryptedKeysetParseException(failure, cause)

internal fun Throwable.toRecoveryDecryptFailureSignal(): RecoveryDecryptFailureSignal =
    when {
        causeChainContains { it is AEADBadTagException || it is BadPaddingException } ->
            RecoveryDecryptFailureSignal.AUTHENTICATION_REJECTED
        causeChainContains { it is ProviderException } -> RecoveryDecryptFailureSignal.OPERATIONAL
        else -> RecoveryDecryptFailureSignal.UNKNOWN
    }

private inline fun Throwable.causeChainContains(predicate: (Throwable) -> Boolean): Boolean {
    var current: Throwable? = this
    var remainingDepth = MAX_CAUSE_CHAIN_DEPTH
    var found = false
    while (current != null && remainingDepth > 0 && !found) {
        val candidate = current
        found = predicate(candidate)
        val next = candidate.cause
        current = if (next === candidate) null else next
        remainingDepth -= 1
    }
    return found
}

private const val MAX_CAUSE_CHAIN_DEPTH = 32
