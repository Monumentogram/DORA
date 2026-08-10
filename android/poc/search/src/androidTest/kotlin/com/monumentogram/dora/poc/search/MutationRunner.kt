package com.monumentogram.dora.poc.search

import com.monumentogram.dora.poc.search.data.SyntheticDatasetGenerator
import com.monumentogram.dora.poc.search.db.ConversationEntity
import com.monumentogram.dora.poc.search.db.FtsIndexManager
import com.monumentogram.dora.poc.search.db.SearchPocDatabase
import com.monumentogram.dora.poc.search.db.TranscriptSegmentEntity
import com.monumentogram.dora.poc.search.query.CountExecution
import com.monumentogram.dora.poc.search.query.SearchExecution
import com.monumentogram.dora.poc.search.query.SearchFilters
import com.monumentogram.dora.poc.search.query.SearchMode
import com.monumentogram.dora.poc.search.query.SearchRepository
import com.monumentogram.dora.poc.search.query.SearchRequest
import org.json.JSONObject

data class MutationObservation(
    val id: String,
    val type: String,
    val latencyMs: Double,
    val correctnessPassed: Boolean,
    val observations: Map<String, Any>,
)

data class MutationSummary(
    val manifestId: String,
    val operations: List<MutationObservation>,
    val staleResultErrors: Int,
    val mappingErrors: Int,
    val crashes: Int,
    val finalConversationCount: Long,
    val finalTranscriptCount: Long,
    val finalFtsCount: Long,
) {
    val allCorrect: Boolean =
        operations.all(MutationObservation::correctnessPassed) &&
            staleResultErrors == 0 &&
            mappingErrors == 0 &&
            crashes == 0 &&
            finalTranscriptCount == finalFtsCount
}

class MutationRunner(
    private val database: SearchPocDatabase,
    private val repository: SearchRepository,
    private val indexManager: FtsIndexManager,
    private val contract: MutationContract,
) {
    private val dao = database.searchDao()

    fun run(): MutationSummary {
        verifyManifestShape()
        val observations = mutableListOf<MutationObservation>()
        var crashes = 0
        for (index in 0 until contract.operations.length()) {
            val operation = contract.operations.getJSONObject(index)
            try {
                observations += runOperation(index, operation)
            } catch (error: Exception) {
                crashes += 1
                observations +=
                    MutationObservation(
                        id = operation.getString("id"),
                        type = operation.getString("type"),
                        latencyMs = 0.0,
                        correctnessPassed = false,
                        observations = mapOf("errorCode" to error.javaClass.simpleName),
                    )
            }
        }
        val mappingErrors =
            (indexManager.missingCanonicalMappingCount() + indexManager.missingIndexRowCount())
                .toInt()
        val staleResultErrors = observations.count { !it.correctnessPassed }
        return MutationSummary(
            manifestId = contract.manifestId,
            operations = observations,
            staleResultErrors = staleResultErrors,
            mappingErrors = mappingErrors,
            crashes = crashes,
            finalConversationCount = dao.conversationCount(),
            finalTranscriptCount = dao.transcriptCount(),
            finalFtsCount = dao.ftsCount(),
        )
    }

    private fun runOperation(index: Int, operation: JSONObject): MutationObservation =
        when (index) {
            0 -> addConversation(operation)
            1 -> updateSegment(operation)
            2 -> updateMetadata(operation)
            3 -> deleteSegment(operation)
            4 -> deleteConversation(operation)
            else -> error("Unexpected mutation operation index $index")
        }

    private fun addConversation(operation: JSONObject): MutationObservation {
        val conversationId = operation.getLong("conversationId")
        val marker = operation.getString("marker")
        val segmentIds = operation.getJSONArray("segmentIds")
        val conversation =
            ConversationEntity(
                conversationId = conversationId,
                title = "Синтетический новый разговор 10001 Synthetic mutation workspace",
                startedAtMs = SyntheticDatasetGenerator.conversationStartedAtMs(conversationId),
                sourceType = "VIDEO",
                participantLabel = "СинтНовый|SynthNew",
            )
        val segments =
            buildList(segmentIds.length()) {
                for (index in 0 until segmentIds.length()) {
                    val segmentId = segmentIds.getLong(index)
                    add(
                        TranscriptSegmentEntity(
                            segmentId = segmentId,
                            conversationId = conversationId,
                            sequence = index,
                            startMs = index * 45_000L,
                            endMs = index * 45_000L + 6_000L,
                            language = if (index % 2 == 0) "mixed-ru-en" else "ru",
                            text =
                                "синтетическая новая запись synthetic new segment $marker индекс room fts4",
                        )
                    )
                }
            }
        val (_, latencyMs) =
            BenchmarkClock.measure { dao.insertConversationWithSegments(conversation, segments) }
        val expected = operation.getJSONObject("expectedAfter")
        val markerIds = successfulSearchIds(marker)
        val passed =
            dao.conversationCount() == expected.getLong("conversationCount") &&
                dao.transcriptCount() == expected.getLong("transcriptRowCount") &&
                successfulCount(marker) == expected.getLong("markerCount") &&
                markerIds == segments.map(TranscriptSegmentEntity::segmentId) &&
                mappingsHealthy()
        return observation(
            operation,
            latencyMs,
            passed,
            mapOf(
                "conversationCount" to dao.conversationCount(),
                "transcriptCount" to dao.transcriptCount(),
                "markerCount" to successfulCount(marker),
                "mappedSegmentIds" to markerIds.joinToString(","),
            ),
        )
    }

    private fun updateSegment(operation: JSONObject): MutationObservation {
        val segmentId = operation.getLong("segmentId")
        val oldMarker = operation.getString("oldMarker")
        val newMarker = operation.getString("newMarker")
        check(successfulCount(oldMarker) == 1L)
        check(successfulCount(newMarker) == 0L)
        val updatedText =
            SyntheticDatasetGenerator.segment(segmentId).text.replace(oldMarker, newMarker)
        val (_, latencyMs) =
            BenchmarkClock.measure { dao.updateSegmentText(segmentId, updatedText) }
        val expected = operation.getJSONObject("expectedAfter")
        val oldCount = successfulCount(oldMarker)
        val newCount = successfulCount(newMarker)
        val mappedIds = successfulSearchIds(newMarker)
        val passed =
            oldCount == expected.getLong("oldMarkerCount") &&
                newCount == expected.getLong("newMarkerCount") &&
                mappedIds == listOf(segmentId) &&
                mappingsHealthy()
        return observation(
            operation,
            latencyMs,
            passed,
            mapOf(
                "oldMarkerCount" to oldCount,
                "newMarkerCount" to newCount,
                "mappedSegmentIds" to mappedIds.joinToString(","),
            ),
        )
    }

    private fun updateMetadata(operation: JSONObject): MutationObservation {
        val conversationId = operation.getLong("conversationId")
        val oldSource = operation.getString("oldSourceType")
        val newSource = operation.getString("newSourceType")
        check(
            filterOnlyCount(conversationId, oldSource) ==
                SyntheticDatasetGenerator.SEGMENTS_PER_CONVERSATION.toLong()
        )
        check(filterOnlyCount(conversationId, newSource) == 0L)
        val (_, latencyMs) =
            BenchmarkClock.measure {
                check(dao.updateConversationSource(conversationId, newSource) == 1)
            }
        val expected = operation.getJSONObject("expectedAfter")
        val oldRows = filterOnlyCount(conversationId, oldSource)
        val newRows = filterOnlyCount(conversationId, newSource)
        val passed =
            oldRows == expected.getLong("oldSourceRows") &&
                newRows == expected.getLong("newSourceRows") &&
                mappingsHealthy()
        return observation(
            operation,
            latencyMs,
            passed,
            mapOf("oldSourceRows" to oldRows, "newSourceRows" to newRows),
        )
    }

    private fun deleteSegment(operation: JSONObject): MutationObservation {
        val segmentId = operation.getLong("segmentId")
        val marker = operation.getString("marker")
        check(successfulCount(marker) == 1L)
        val (_, latencyMs) = BenchmarkClock.measure { dao.deleteSegment(segmentId) }
        val expected = operation.getJSONObject("expectedAfter")
        val markerCount = successfulCount(marker)
        val passed =
            markerCount == expected.getLong("markerCount") &&
                dao.transcriptCount() == expected.getLong("transcriptRowCount") &&
                segmentId !in successfulSearchIds(marker) &&
                mappingsHealthy()
        return observation(
            operation,
            latencyMs,
            passed,
            mapOf("markerCount" to markerCount, "transcriptCount" to dao.transcriptCount()),
        )
    }

    private fun deleteConversation(operation: JSONObject): MutationObservation {
        val conversationId = operation.getLong("conversationId")
        val marker = operation.getString("marker")
        check(successfulCount(marker) == 1L)
        val (_, latencyMs) = BenchmarkClock.measure { dao.deleteConversation(conversationId) }
        val expected = operation.getJSONObject("expectedAfter")
        val markerCount = successfulCount(marker)
        val passed =
            markerCount == expected.getLong("markerCount") &&
                dao.conversationCount() == expected.getLong("conversationCount") &&
                dao.transcriptCount() == expected.getLong("transcriptRowCount") &&
                filterOnlyCount(conversationId, null) == 0L &&
                mappingsHealthy()
        return observation(
            operation,
            latencyMs,
            passed,
            mapOf(
                "markerCount" to markerCount,
                "conversationCount" to dao.conversationCount(),
                "transcriptCount" to dao.transcriptCount(),
            ),
        )
    }

    private fun successfulCount(rawQuery: String): Long {
        val result = repository.count(SearchRequest(rawQuery, SearchMode.EXACT))
        check(result is CountExecution.Success)
        return result.count
    }

    private fun successfulSearchIds(rawQuery: String): List<Long> {
        val result = repository.search(SearchRequest(rawQuery, SearchMode.EXACT, limit = 100))
        check(result is SearchExecution.Success)
        return result.hits.map { it.segmentId }
    }

    private fun filterOnlyCount(conversationId: Long, sourceType: String?): Long {
        val result =
            repository.count(
                SearchRequest(
                    rawQuery = "",
                    mode = SearchMode.EXACT,
                    filters =
                        SearchFilters(conversationId = conversationId, sourceType = sourceType),
                )
            )
        check(result is CountExecution.Success)
        return result.count
    }

    private fun mappingsHealthy(): Boolean =
        dao.transcriptCount() == dao.ftsCount() &&
            indexManager.missingCanonicalMappingCount() == 0L &&
            indexManager.missingIndexRowCount() == 0L

    private fun observation(
        operation: JSONObject,
        latencyMs: Double,
        passed: Boolean,
        values: Map<String, Any>,
    ): MutationObservation =
        MutationObservation(
            id = operation.getString("id"),
            type = operation.getString("type"),
            latencyMs = latencyMs,
            correctnessPassed = passed,
            observations = values,
        )

    private fun verifyManifestShape() {
        check(contract.manifestId == "poc-search-001-mutations-v1")
        check(contract.operations.length() == EXPECTED_OPERATION_IDS.size)
        EXPECTED_OPERATION_IDS.forEachIndexed { index, id ->
            check(contract.operations.getJSONObject(index).getString("id") == id)
        }
    }

    companion object {
        private val EXPECTED_OPERATION_IDS =
            listOf(
                "MUT-ADD-CONVERSATION",
                "MUT-UPDATE-SEGMENT",
                "MUT-UPDATE-METADATA",
                "MUT-DELETE-SEGMENT",
                "MUT-DELETE-CONVERSATION",
            )
    }
}
