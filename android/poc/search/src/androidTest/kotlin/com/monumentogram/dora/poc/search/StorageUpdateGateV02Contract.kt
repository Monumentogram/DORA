package com.monumentogram.dora.poc.search

enum class GateV02DatabaseKind {
    CONTROL,
    INDEXED,
}

enum class GateV02OperationClass(
    val id: String,
    val group: String,
    val cardinality: Int,
) {
    ADD_CONVERSATION_100("ADD_CONVERSATION_100", "bulk-100", 100),
    UPDATE_SEGMENT_TEXT_1("UPDATE_SEGMENT_TEXT_1", "single-row", 1),
    UPDATE_CONVERSATION_FILTER_1("UPDATE_CONVERSATION_FILTER_1", "single-row", 1),
    DELETE_SEGMENT_1("DELETE_SEGMENT_1", "single-row", 1),
    DELETE_CONVERSATION_100("DELETE_CONVERSATION_100", "bulk-100", 100),
}

data class GateV02RunConfig(
    val profileId: String,
    val freshBuildOrdinal: Int,
    val conversationCount: Int,
    val transcriptSegmentCount: Int,
    val segmentsPerConversation: Int,
    val warmupsPerClass: Int,
    val measuredOperationsPerClass: Int,
    val smokeOnly: Boolean,
    val compilationMode: String,
    val cooldownMinutesBeforeFreshBuild: Int,
) {
    val pairOrder: List<GateV02DatabaseKind> =
        if (freshBuildOrdinal == 2) {
            listOf(GateV02DatabaseKind.INDEXED, GateV02DatabaseKind.CONTROL)
        } else {
            listOf(GateV02DatabaseKind.CONTROL, GateV02DatabaseKind.INDEXED)
        }

    fun validate() {
        require(profileId in StorageUpdateGateV02Contract.REQUIRED_PROFILES)
        require(freshBuildOrdinal in 1..StorageUpdateGateV02Contract.FRESH_BUILDS_PER_PROFILE)
        require(conversationCount > 0)
        require(transcriptSegmentCount == conversationCount * segmentsPerConversation)
        require(segmentsPerConversation == StorageUpdateGateV02Contract.SEGMENTS_PER_CONVERSATION)
        require(warmupsPerClass >= 0)
        require(measuredOperationsPerClass > 0)
        require(cooldownMinutesBeforeFreshBuild >= 0)
        val operations = warmupsPerClass + measuredOperationsPerClass
        require(conversationCount >= operations * 4 + 1)
    }
}

data class GateV02NormalizedStorage(
    val mainDatabaseBytes: Long,
    val pageCount: Long,
    val pageSizeBytes: Long,
    val walBytesAfterClose: Long,
    val shmBytesAfterClose: Long,
    val integrityCheck: String,
    val conversationCount: Long,
    val transcriptSegmentCount: Long,
    val ftsRowCount: Long?,
    val canonicalLogicalSha256: String,
    val missingCanonicalMappings: Long?,
    val missingIndexRows: Long?,
    val firstCheckpointBusy: Int,
    val secondCheckpointBusy: Int,
    val mainFileMatchesPageGeometry: Boolean,
    val transientFilesCleared: Boolean,
    val freeStorageBytesAfterNormalization: Long,
)

data class GateV02OperationSample(
    val commitNanos: Long,
    val visibilityNanos: Long?,
    val correctnessPassed: Boolean,
    val staleSuccessfulResponse: Boolean,
    val crashed: Boolean,
)

data class GateV02OperationSamples(
    val operationClass: GateV02OperationClass,
    val warmupSamples: List<GateV02OperationSample>,
    val measuredSamples: List<GateV02OperationSample>,
)

data class GateV02DatabaseObservation(
    val kind: GateV02DatabaseKind,
    val storage: GateV02NormalizedStorage,
    val operations: List<GateV02OperationSamples>,
    val sqliteVersion: String,
    val compileOptions: List<String>,
    val finalIntegrityCheck: String,
    val finalConversationCount: Long,
    val finalTranscriptSegmentCount: Long,
    val finalFtsRowCount: Long?,
    val finalMissingCanonicalMappings: Long?,
    val finalMissingIndexRows: Long?,
    val deletedAfterRun: Boolean,
)

data class GateV02BuildCheckpoint(
    val config: GateV02RunConfig,
    val control: GateV02DatabaseObservation,
    val indexed: GateV02DatabaseObservation,
) {
    val incrementalBytes: Long =
        indexed.storage.mainDatabaseBytes - control.storage.mainDatabaseBytes
    val overheadRatio: Double = incrementalBytes.toDouble() / control.storage.mainDatabaseBytes
    val overheadBytesPerSegment: Double =
        incrementalBytes.toDouble() / config.transcriptSegmentCount

    val storageAndPairingCorrect: Boolean =
        control.storage.canonicalLogicalSha256 == indexed.storage.canonicalLogicalSha256 &&
            listOf(control.storage, indexed.storage).all {
                it.integrityCheck == "ok" &&
                    it.conversationCount == config.conversationCount.toLong() &&
                    it.transcriptSegmentCount == config.transcriptSegmentCount.toLong() &&
                    it.firstCheckpointBusy == 0 &&
                    it.secondCheckpointBusy == 0 &&
                    it.mainFileMatchesPageGeometry &&
                    it.transientFilesCleared
            } &&
            indexed.storage.ftsRowCount == config.transcriptSegmentCount.toLong() &&
            indexed.storage.missingCanonicalMappings == 0L &&
            indexed.storage.missingIndexRows == 0L &&
            (config.smokeOnly ||
                minOf(
                    control.storage.freeStorageBytesAfterNormalization,
                    indexed.storage.freeStorageBytesAfterNormalization,
                ) >=
                    4L *
                        (control.storage.mainDatabaseBytes +
                            StorageUpdateGateV02Contract.MAX_INDEX_INCREMENTAL_BYTES)) &&
            listOf(control, indexed).all {
                it.finalIntegrityCheck == "ok" &&
                    it.finalConversationCount == config.conversationCount.toLong() &&
                    it.finalTranscriptSegmentCount ==
                        config.transcriptSegmentCount.toLong() -
                            (config.warmupsPerClass + config.measuredOperationsPerClass) &&
                    it.deletedAfterRun
            } &&
            indexed.finalFtsRowCount == indexed.finalTranscriptSegmentCount &&
            indexed.finalMissingCanonicalMappings == 0L &&
            indexed.finalMissingIndexRows == 0L

    val allCorrect: Boolean =
        storageAndPairingCorrect &&
            (control.operations + indexed.operations)
                .flatMap { it.warmupSamples + it.measuredSamples }
                .all { it.correctnessPassed && !it.staleSuccessfulResponse && !it.crashed }
}

object StorageUpdateGateV02Contract {
    const val GATE_SET_VERSION: String = "stage0-v0.2"
    const val SELECTED_OPTION: String = "B"
    const val REFERENCE_CONVERSATIONS: Int = 10_000
    const val REFERENCE_SEGMENTS: Int = 1_000_000
    const val SEGMENTS_PER_CONVERSATION: Int = 100
    const val FRESH_BUILDS_PER_PROFILE: Int = 3
    const val WARMUPS_PER_CLASS: Int = 10
    const val MEASURED_OPERATIONS_PER_CLASS: Int = 100
    const val MEASURED_SAMPLES_PER_CLASS_PROFILE: Int = 300
    const val VISIBILITY_POLL_INTERVAL_MS: Long = 10
    const val VISIBILITY_DEADLINE_MS: Long = 1_000

    const val MAX_INDEX_INCREMENTAL_BYTES: Long = 536_870_912
    const val MAX_INDEX_OVERHEAD_RATIO: Double = 1.0
    const val MAX_INDEX_OVERHEAD_BYTES_PER_SEGMENT: Double = 512.0
    const val MAX_SINGLE_ROW_MAINTENANCE_DELTA_P95_MS: Double = 50.0
    const val MAX_BULK_100_MAINTENANCE_DELTA_P95_MS: Double = 250.0
    const val MAX_INDEXED_COMMIT_P99_MS: Double = 500.0
    const val MAX_VISIBILITY_P95_MS: Double = 250.0
    const val MAX_VISIBILITY_P99_MS: Double = 1_000.0

    val REQUIRED_PROFILES: Set<String> = setOf("D1", "D2", "D3")

    fun formalConfig(profileId: String, freshBuildOrdinal: Int): GateV02RunConfig =
        GateV02RunConfig(
                profileId = profileId,
                freshBuildOrdinal = freshBuildOrdinal,
                conversationCount = REFERENCE_CONVERSATIONS,
                transcriptSegmentCount = REFERENCE_SEGMENTS,
                segmentsPerConversation = SEGMENTS_PER_CONVERSATION,
                warmupsPerClass = WARMUPS_PER_CLASS,
                measuredOperationsPerClass = MEASURED_OPERATIONS_PER_CLASS,
                smokeOnly = false,
                compilationMode = "full_aot_recorded",
                cooldownMinutesBeforeFreshBuild = 10,
            )
            .also(GateV02RunConfig::validate)

    fun smokeConfig(): GateV02RunConfig =
        GateV02RunConfig(
                profileId = "D2",
                freshBuildOrdinal = 1,
                conversationCount = 20,
                transcriptSegmentCount = 2_000,
                segmentsPerConversation = SEGMENTS_PER_CONVERSATION,
                warmupsPerClass = 1,
                measuredOperationsPerClass = 3,
                smokeOnly = true,
                compilationMode = "not_applicable_smoke",
                cooldownMinutesBeforeFreshBuild = 0,
            )
            .also(GateV02RunConfig::validate)
}
