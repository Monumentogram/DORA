package com.monumentogram.dora.audio.logical

import com.monumentogram.dora.model.alpha.RecordingId

enum class RecordingConsentMode {
    ALWAYS,
    ASK_EACH_RECORDING,
    MANUAL_ONLY,
}

enum class RecordingConsentState {
    UNDECIDED,
    OFFERED,
    DEFERRED,
    AUTHORIZED,
}

enum class AuthorizationOrigin {
    VALID_ALWAYS_GRANT,
    ACCEPTED_RECORDING_PROMPT,
    EXPLICIT_RECORDING_ACTION,
}

/** Contract evidence only, never a source capability or durable consent record. */
data class RecordingAuthorizationBasis(
    val unit: AuthorizationUnitId,
    val origin: AuthorizationOrigin,
)

data class RecordingPromptOpportunity(
    val state: RecordingAuthorization,
    val prompt: AuthorizationUnitId?,
)

/** Pure transition kernel for future consumers. Chunks are deliberately not transition inputs. */
class RecordingAuthorization
private constructor(
    val recordingId: RecordingId,
    val mode: RecordingConsentMode,
    val status: RecordingConsentState,
    private val origin: AuthorizationOrigin?,
) {
    constructor(
        recordingId: RecordingId,
        mode: RecordingConsentMode,
    ) : this(recordingId, mode, RecordingConsentState.UNDECIDED, null)

    fun automaticOpportunity(): RecordingPromptOpportunity =
        if (
            mode == RecordingConsentMode.ASK_EACH_RECORDING &&
                status == RecordingConsentState.UNDECIDED
        )
            RecordingPromptOpportunity(
                transition(RecordingConsentState.OFFERED),
                AuthorizationUnitId(recordingId),
            )
        else RecordingPromptOpportunity(this, null)

    fun defer(): RecordingAuthorization {
        check(
            mode == RecordingConsentMode.ASK_EACH_RECORDING &&
                status in setOf(RecordingConsentState.OFFERED, RecordingConsentState.DEFERRED)
        )
        return transition(RecordingConsentState.DEFERRED)
    }

    fun acceptPrompt(): RecordingAuthorization {
        check(
            mode == RecordingConsentMode.ASK_EACH_RECORDING &&
                status == RecordingConsentState.OFFERED
        )
        return transition(
            RecordingConsentState.AUTHORIZED,
            AuthorizationOrigin.ACCEPTED_RECORDING_PROMPT,
        )
    }

    /** Caller supplies an already valid recording-level ALWAYS decision, not a new chunk grant. */
    fun authorizeAlways(): RecordingAuthorization {
        check(mode == RecordingConsentMode.ALWAYS)
        return transition(RecordingConsentState.AUTHORIZED, AuthorizationOrigin.VALID_ALWAYS_GRANT)
    }

    fun recognizeExplicitly(): RecordingAuthorization =
        transition(RecordingConsentState.AUTHORIZED, AuthorizationOrigin.EXPLICIT_RECORDING_ACTION)

    fun basis(): RecordingAuthorizationBasis? = origin?.let {
        RecordingAuthorizationBasis(AuthorizationUnitId(recordingId), it)
    }

    fun basisFor(recordingId: RecordingId): RecordingAuthorizationBasis? =
        if (recordingId == this.recordingId) basis() else null

    private fun transition(state: RecordingConsentState, basis: AuthorizationOrigin? = null) =
        RecordingAuthorization(recordingId, mode, state, basis)

    companion object {
        fun batchUnits(units: Collection<AuthorizationUnitId>): Set<AuthorizationUnitId> =
            java.util.Collections.unmodifiableSet(units.toSet())
    }
}
