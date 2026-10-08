package com.monumentogram.dora.audio.persistence

import com.monumentogram.dora.audio.AudioIdentity

/** Immutable diagnostic restriction. It never supplies authorization or declares durability. */
internal class DiagnosticSourcePolicy
private constructor(
    val active: Boolean,
    sources: Set<AudioIdentity>,
    identifiers: Set<String>,
    private val binding: Pair<String, String>? = null,
) {
    private val historical = sources.toSet()
    private val forbidden = (identifiers + historical.flatMap(::components)).toSet()

    fun isProtected(identity: AudioIdentity): Boolean = active && identity in historical

    fun protectedSources(): Set<AudioIdentity> = historical.toSet()

    fun requireAsset(asset: String) = requireComponent(asset)

    fun boundTo(owner: String, vault: String) =
        DiagnosticSourcePolicy(active, historical, forbidden, owner to vault)

    fun requireBinding(owner: String, vault: String) {
        if (active && binding != null) check(binding == owner to vault) { REJECTED }
    }

    fun requireNewSource(identity: AudioIdentity) {
        if (!active) return
        val values = components(identity)
        check(values.distinct().size == values.size && values.none { it in forbidden }) { REJECTED }
    }

    fun requireComponent(value: String) {
        if (active) check(value !in forbidden) { REJECTED }
    }

    fun requireRun(run: String, owner: AudioIdentity?) {
        if (!active) return
        check(owner != null) { REJECTED }
        requireNewSource(owner)
        requireComponent(run)
        check(run !in components(owner)) { REJECTED }
    }

    companion object {
        const val PROTECTED_SOURCE_COUNT = 47
        private const val REJECTED = "Protected diagnostic operation rejected"

        fun ordinary() = DiagnosticSourcePolicy(false, emptySet(), emptySet())

        fun protected(
            sources: Set<AudioIdentity>,
            identifiers: Set<String>,
        ): DiagnosticSourcePolicy {
            check(sources.size == PROTECTED_SOURCE_COUNT) { REJECTED }
            return DiagnosticSourcePolicy(true, sources, identifiers)
        }

        private fun components(identity: AudioIdentity) =
            listOf(identity.recordingId.value, identity.assetId.value, identity.sessionId)
    }
}
