package com.monumentogram.dora.operator

import com.monumentogram.dora.audio.AudioFailure
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.OriginalAudioReference
import com.monumentogram.dora.audio.OriginalAudioStatus
import com.monumentogram.dora.audio.ProductAudioSession
import com.monumentogram.dora.model.alpha.RecordingId

@Suppress("LongParameterList") // Independent process and UserManager denial evidence.
internal data class OperatorEnvironment(
    val packageName: String,
    val processUserId: Int,
    val serial: Long,
    val system: Boolean = false,
    val managed: Boolean = false,
    val profile: Boolean = false,
    val demo: Boolean = false,
    val unlocked: Boolean = true,
)

@Suppress(
    "ReturnCount",
    "ComplexCondition",
    "CyclomaticComplexMethod",
) // Explicit fail-closed gates.
internal class IsolatedAlphaOperator(
    private val environment: () -> OperatorEnvironment,
    private val currentSession: () -> ProductAudioSession?,
) {
    private var admitted: OperatorEnvironment? = null
    private val monitor = Any()
    private var epoch = 0L
    private var proof: Proof? = null

    fun admit(userId: String, hostTopology: String): Boolean {
        cancel()
        admitted = null
        val state = environment()
        val valid =
            state.packageName == "com.monumentogram.dora.alpha.test" &&
                userId == state.processUserId.toString() &&
                state.processUserId > 0 &&
                state.serial >= 0 &&
                !state.system &&
                !state.managed &&
                !state.profile &&
                !state.demo &&
                state.unlocked &&
                hostTopology == "FULL_SECONDARY_USER:${state.processUserId}:${state.serial}"
        if (valid) admitted = state
        return valid
    }

    fun readback(recordingId: String): AudioResult<Readback> {
        cancel()
        val request = synchronized(monitor) { epoch }
        if (!admissionCurrent()) return AudioResult.Failed(AudioFailure.INVALID_INPUT)
        val selected = parse(recordingId) ?: return AudioResult.Failed(AudioFailure.INVALID_INPUT)
        val session = currentSession() ?: return AudioResult.Failed(AudioFailure.LOCKED)
        val found = session.findRecording(selected)
        if (found is AudioResult.Failed) return found
        val identity = (found as AudioResult.Value).value
        if (identity.recordingId != selected) return AudioResult.Failed(AudioFailure.INVALID_INPUT)
        val acquired = session.originals.acquire(identity)
        if (acquired is AudioResult.Failed) return acquired
        val reference =
            ((acquired as AudioResult.Value).value as? OriginalAudioStatus.Available)?.reference
                ?: return AudioResult.Failed(AudioFailure.INCOMPLETE)
        if (reference.identity != identity || reference.frames !in 1..MAX_FRAMES)
            return AudioResult.Failed(AudioFailure.INCOMPLETE)
        var frames = 0L
        var units = 0
        var valid = true
        val read =
            session.originals.extract(reference) { first, pcm ->
                try {
                    if (
                        first != frames ||
                            pcm.isEmpty() ||
                            pcm.size % 2 != 0 ||
                            !requestCurrent(request, session)
                    )
                        valid = false
                    frames = Math.addExact(frames, pcm.size.toLong() / 2)
                    units++
                    if (frames > MAX_FRAMES) valid = false
                } finally {
                    pcm.fill(0)
                }
            }
        if (read is AudioResult.Failed) return read
        val status = (read as AudioResult.Value).value
        if (
            !valid ||
                frames != reference.frames ||
                units == 0 ||
                status != OriginalAudioStatus.Available(reference)
        )
            return AudioResult.Failed(AudioFailure.CORRUPT)
        return synchronized(monitor) {
            if (!requestCurrent(request, session)) AudioResult.Failed(AudioFailure.LOCKED)
            else {
                proof = Proof(session, reference)
                AudioResult.Value(Readback(frames, units))
            }
        }
    }

    fun consumeDeletion(recordingId: String): AudioResult<AudioIdentity> {
        val request = synchronized(monitor) { epoch }
        val selected =
            synchronized(monitor) { proof.also { proof = null } }
                ?: return AudioResult.Failed(AudioFailure.INVALID_INPUT)
        if (
            !admissionCurrent() ||
                currentSession() !== selected.session ||
                parse(recordingId) != selected.reference.identity.recordingId
        )
            return AudioResult.Failed(AudioFailure.INVALID_INPUT)
        val checked = selected.session.originals.inspect(selected.reference)
        if (checked is AudioResult.Failed) return checked
        if (
            (checked as AudioResult.Value).value !=
                OriginalAudioStatus.Available(selected.reference) ||
                !requestCurrent(request, selected.session)
        )
            return AudioResult.Failed(AudioFailure.LOCKED)
        return AudioResult.Value(selected.reference.identity)
    }

    fun cancel() =
        synchronized(monitor) {
            epoch++
            proof = null
        }

    private fun admissionCurrent() = admitted?.let { it == environment() } == true

    private fun requestCurrent(request: Long, session: ProductAudioSession) =
        synchronized(monitor) { epoch == request } &&
            admissionCurrent() &&
            currentSession() === session

    private fun parse(value: String): RecordingId? =
        try {
            RecordingId(value)
        } catch (_: IllegalArgumentException) {
            null
        }

    private class Proof(val session: ProductAudioSession, val reference: OriginalAudioReference)

    data class Readback(val frames: Long, val units: Int)

    private companion object {
        const val MAX_FRAMES = 4_800_000L
    }
}
