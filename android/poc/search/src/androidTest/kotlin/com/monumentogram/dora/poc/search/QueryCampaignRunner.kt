@file:Suppress("CyclomaticComplexMethod", "LongMethod", "ReturnCount")

package com.monumentogram.dora.poc.search

import android.os.SystemClock
import com.monumentogram.dora.poc.search.db.SearchHit
import com.monumentogram.dora.poc.search.db.SearchPocDao
import com.monumentogram.dora.poc.search.query.CountExecution
import com.monumentogram.dora.poc.search.query.QueryStatus
import com.monumentogram.dora.poc.search.query.SearchExecution
import com.monumentogram.dora.poc.search.query.SearchRepository
import com.monumentogram.dora.poc.search.query.SearchRequest
import java.security.MessageDigest

data class QueryObservation(
    val id: String,
    val category: String,
    val expectedStatus: String,
    val observedStatus: String,
    val expectedCount: Long,
    val observedCount: Long?,
    val compilerContractMatched: Boolean,
    val countMatched: Boolean,
    val mappingMatched: Boolean,
    val duplicateFree: Boolean,
    val safeExecution: Boolean,
    val observedSegmentIds: List<Long>,
    val errorCode: String?,
)

data class CorrectnessSummary(
    val label: String,
    val expectedCases: Int,
    val matchedCases: Int,
    val compilerErrors: Int,
    val countErrors: Int,
    val mappingErrors: Int,
    val duplicateResultErrors: Int,
    val adversarialFailures: Int,
    val specialCharacterFailures: Int,
    val failureExecutions: Int,
    val crashes: Int,
    val canonicalRowsBefore: Long,
    val canonicalRowsAfter: Long,
    val conversationsBefore: Long,
    val conversationsAfter: Long,
    val queryResultSha256: String,
    val observations: List<QueryObservation>,
) {
    val allMatched: Boolean =
        matchedCases == expectedCases &&
            compilerErrors == 0 &&
            countErrors == 0 &&
            mappingErrors == 0 &&
            duplicateResultErrors == 0 &&
            adversarialFailures == 0 &&
            specialCharacterFailures == 0 &&
            failureExecutions == 0 &&
            crashes == 0 &&
            canonicalRowsBefore == canonicalRowsAfter &&
            conversationsBefore == conversationsAfter
}

data class LatencySummary(
    val warmupOperations: Int,
    val measuredOperations: Int,
    val scheduleSeed: Long,
    val checksum: Long,
    val overall: DistributionStats,
    val byCategory: Map<String, DistributionStats>,
)

class QueryCampaignRunner(
    private val repository: SearchRepository,
    private val dao: SearchPocDao,
    private val campaign: QueryCampaignContract,
) {
    fun runCorrectness(label: String): CorrectnessSummary {
        val conversationsBefore = dao.conversationCount()
        val canonicalRowsBefore = dao.transcriptCount()
        val observations =
            campaign.cases.mapIndexed { index, queryCase ->
                BenchmarkProgress.report(
                    "$label correctness: ${index + 1}/${campaign.cases.size} ${queryCase.id} started"
                )
                evaluateCase(queryCase).also {
                    BenchmarkProgress.report(
                        "$label correctness: ${index + 1}/${campaign.cases.size} " +
                            "${queryCase.id} complete"
                    )
                }
            }
        val conversationsAfter = dao.conversationCount()
        val canonicalRowsAfter = dao.transcriptCount()
        val digest = MessageDigest.getInstance("SHA-256")
        observations.forEach { observation ->
            digest.update(
                ("${observation.id}|${observation.observedStatus}|${observation.observedCount}|" +
                        "${observation.observedSegmentIds.joinToString(",")}|" +
                        "${observation.errorCode ?: ""}\n")
                    .toByteArray(Charsets.UTF_8)
            )
        }

        return CorrectnessSummary(
            label = label,
            expectedCases = observations.size,
            matchedCases = observations.count(::isFullyMatched),
            compilerErrors = observations.count { !it.compilerContractMatched },
            countErrors = observations.count { !it.countMatched },
            mappingErrors = observations.count { !it.mappingMatched },
            duplicateResultErrors = observations.count { !it.duplicateFree },
            adversarialFailures =
                observations.count { it.category == "adversarial" && !it.safeExecution },
            specialCharacterFailures =
                observations.count { it.category == "special-characters" && !it.safeExecution },
            failureExecutions = observations.count { it.observedStatus == "FAILURE" },
            crashes = observations.count { it.observedStatus == "CRASH" },
            canonicalRowsBefore = canonicalRowsBefore,
            canonicalRowsAfter = canonicalRowsAfter,
            conversationsBefore = conversationsBefore,
            conversationsAfter = conversationsAfter,
            queryResultSha256 = BenchmarkDigests.toSha256(digest),
            observations = observations,
        )
    }

    fun runLatency(): LatencySummary {
        val eligible = campaign.cases.filter { it.latencyEligible }
        check(eligible.isNotEmpty())
        var warmupOperations = 0
        var checksum = 0L
        eligible.forEach { queryCase ->
            repeat(campaign.warmupPerQuery) {
                checksum += successfulSearch(queryCase).firstOrNull()?.segmentId ?: 0L
                warmupOperations += 1
            }
        }

        val allDurations = ArrayList<Long>(eligible.size * campaign.repetitionsPerQuery)
        val categoryDurations = linkedMapOf<String, MutableList<Long>>()
        repeat(campaign.repetitionsPerQuery) { repetition ->
            val offset =
                Math.floorMod(
                    (campaign.scheduleSeed + repetition * SCHEDULE_STRIDE).toInt(),
                    eligible.size,
                )
            repeat(eligible.size) { index ->
                val queryCase = eligible[(index + offset) % eligible.size]
                val started = SystemClock.elapsedRealtimeNanos()
                val hits = successfulSearch(queryCase)
                val elapsed = SystemClock.elapsedRealtimeNanos() - started
                checksum += hits.firstOrNull()?.segmentId ?: 0L
                allDurations += elapsed
                categoryDurations.getOrPut(queryCase.category) { mutableListOf() } += elapsed
            }
        }

        return LatencySummary(
            warmupOperations = warmupOperations,
            measuredOperations = allDurations.size,
            scheduleSeed = campaign.scheduleSeed,
            checksum = checksum,
            overall = BenchmarkStatistics.fromNanoseconds(allDurations),
            byCategory =
                categoryDurations.mapValues { BenchmarkStatistics.fromNanoseconds(it.value) },
        )
    }

    private fun evaluateCase(queryCase: QueryCase): QueryObservation {
        val request = request(queryCase)
        return try {
            val compiled = repository.compile(request)
            val compilerMatched =
                compiled.status == queryCase.expectedStatus &&
                    compiled.matchExpression == queryCase.expectedMatch &&
                    compiled.rejectionCode == queryCase.expectedRejectionCode &&
                    compiled.tokens == queryCase.expectedTokens
            val count = repository.count(request)
            val search = repository.search(request)
            val observedCount = (count as? CountExecution.Success)?.count
            val hits = (search as? SearchExecution.Success)?.hits.orEmpty()
            val expectedExecutionMatched =
                when (queryCase.expectedStatus) {
                    QueryStatus.READY,
                    QueryStatus.FILTER_ONLY ->
                        count is CountExecution.Success && search is SearchExecution.Success
                    QueryStatus.EMPTY ->
                        count is CountExecution.EmptyInput && search is SearchExecution.EmptyInput
                    QueryStatus.REJECTED ->
                        count is CountExecution.Rejected && search is SearchExecution.Rejected
                }
            val mappingMatched = mappingsMatch(queryCase.expectedMappings, hits)
            val duplicateFree = hits.map(SearchHit::segmentId).distinct().size == hits.size
            val safetyMatched = runSafetyRepetitions(queryCase, request)
            val errorCode =
                when {
                    count is CountExecution.Failure -> count.code
                    search is SearchExecution.Failure -> search.code
                    !expectedExecutionMatched -> "UNEXPECTED_EXECUTION_STATUS"
                    else -> null
                }
            QueryObservation(
                id = queryCase.id,
                category = queryCase.category,
                expectedStatus = queryCase.expectedStatus.name,
                observedStatus = executionStatus(search),
                expectedCount = queryCase.expectedCount,
                observedCount = observedCount ?: if (queryCase.expectedCount == 0L) 0L else null,
                compilerContractMatched = compilerMatched,
                countMatched =
                    expectedExecutionMatched &&
                        (observedCount == queryCase.expectedCount ||
                            (queryCase.expectedCount == 0L && observedCount == null)),
                mappingMatched = mappingMatched,
                duplicateFree = duplicateFree,
                safeExecution = expectedExecutionMatched && safetyMatched && errorCode == null,
                observedSegmentIds =
                    hits.take(queryCase.expectedMappings.size).map(SearchHit::segmentId),
                errorCode = errorCode,
            )
        } catch (error: Exception) {
            QueryObservation(
                id = queryCase.id,
                category = queryCase.category,
                expectedStatus = queryCase.expectedStatus.name,
                observedStatus = "CRASH",
                expectedCount = queryCase.expectedCount,
                observedCount = null,
                compilerContractMatched = false,
                countMatched = false,
                mappingMatched = false,
                duplicateFree = false,
                safeExecution = false,
                observedSegmentIds = emptyList(),
                errorCode = error.javaClass.simpleName.take(MAX_ERROR_CODE_LENGTH),
            )
        }
    }

    private fun runSafetyRepetitions(queryCase: QueryCase, request: SearchRequest): Boolean {
        if (queryCase.latencyEligible) return true
        repeat(campaign.safetyRepetitions) {
            if (repository.search(request) is SearchExecution.Failure) return false
        }
        return true
    }

    private fun successfulSearch(queryCase: QueryCase): List<SearchHit> {
        return when (val result = repository.search(request(queryCase))) {
            is SearchExecution.Success -> result.hits
            else ->
                error("Latency-eligible query ${queryCase.id} returned ${executionStatus(result)}")
        }
    }

    private fun request(queryCase: QueryCase): SearchRequest =
        SearchRequest(
            rawQuery = queryCase.rawQuery,
            mode = queryCase.mode,
            filters = queryCase.filters,
            limit = campaign.resultLimit,
        )

    private fun mappingsMatch(expected: List<ExpectedMapping>, actual: List<SearchHit>): Boolean {
        if (actual.size < expected.size) return false
        return expected.zip(actual).all { (expectedMapping, hit) ->
            expectedMapping.segmentId == hit.segmentId &&
                expectedMapping.conversationId == hit.conversationId &&
                expectedMapping.startMs == hit.startMs &&
                expectedMapping.endMs == hit.endMs &&
                expectedMapping.textSha256 == BenchmarkDigests.sha256(hit.text)
        }
    }

    private fun executionStatus(execution: SearchExecution): String =
        when (execution) {
            is SearchExecution.Success -> "SUCCESS"
            is SearchExecution.EmptyInput -> "EMPTY"
            is SearchExecution.Rejected -> "REJECTED"
            is SearchExecution.Failure -> "FAILURE"
        }

    private fun isFullyMatched(observation: QueryObservation): Boolean =
        observation.compilerContractMatched &&
            observation.countMatched &&
            observation.mappingMatched &&
            observation.duplicateFree &&
            observation.safeExecution &&
            observation.observedStatus != "CRASH"

    companion object {
        private const val SCHEDULE_STRIDE = 17L
        private const val MAX_ERROR_CODE_LENGTH = 80
    }
}
