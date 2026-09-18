package com.monumentogram.dora.poc.recovery.candidate

@Suppress("LongParameterList") // The observation is a fixed, factual host evidence record.
internal data class RecoveryCampaignStreamPersistenceState(
    val sealedValidOutcomes: Int,
    val activeRanges: Int,
    val activeRangeStart: Long?,
    val activeRangeEnd: Long?,
    val activeRangeCertainty: String?,
    val persistedStateDigest: String,
    val runRows: Int,
    val checkpointRows: Int,
    val totalOutcomeRows: Int,
    val rangeRows: Int,
)

internal object RecoveryCampaignStreamPersistenceSummary {
    fun observe(
        runs: List<RecoveryPersistedRow>,
        checkpoints: List<RecoveryPersistedRow>,
        outcomes: List<RecoveryPersistedRow>,
        ranges: List<RecoveryPersistedRow>,
    ): RecoveryCampaignStreamPersistenceState {
        val active = ranges.filter { it.text("state") == "ACTIVE" }
        val onlyActive = active.singleOrNull()
        return RecoveryCampaignStreamPersistenceState(
            sealedValidOutcomes =
                outcomes.count { it.text("decision") == "VALID" && it.text("state") == "SEALED" },
            activeRanges = active.size,
            activeRangeStart = onlyActive?.integer("range_start"),
            activeRangeEnd = onlyActive?.integer("range_end"),
            activeRangeCertainty = onlyActive?.text("boundary_certainty"),
            persistedStateDigest =
                RecoveryCampaignStreamPersistenceDigest.sha256(
                    runs + checkpoints + outcomes + ranges
                ),
            runRows = runs.size,
            checkpointRows = checkpoints.size,
            totalOutcomeRows = outcomes.size,
            rangeRows = ranges.size,
        )
    }
}
