package com.monumentogram.dora.poc.search.query

import com.monumentogram.dora.poc.search.db.SearchHit

enum class SearchMode {
    EXACT,
    PHRASE,
    PREFIX,
}

data class SearchFilters(
    val conversationId: Long? = null,
    val sourceType: String? = null,
    val startedAtFromMs: Long? = null,
    val startedAtToMs: Long? = null,
) {
    fun isEmpty(): Boolean =
        conversationId == null &&
            sourceType == null &&
            startedAtFromMs == null &&
            startedAtToMs == null
}

data class SearchRequest(
    val rawQuery: String,
    val mode: SearchMode,
    val filters: SearchFilters = SearchFilters(),
    val limit: Int = 25,
    val offset: Int = 0,
)

enum class QueryStatus {
    READY,
    FILTER_ONLY,
    EMPTY,
    REJECTED,
}

data class CompiledUserQuery(
    val status: QueryStatus,
    val tokens: List<String>,
    val matchExpression: String?,
    val rejectionCode: String?,
)

sealed interface SearchExecution {
    data class Success(val hits: List<SearchHit>, val compiled: CompiledUserQuery) : SearchExecution

    data class EmptyInput(val compiled: CompiledUserQuery) : SearchExecution

    data class Rejected(val compiled: CompiledUserQuery) : SearchExecution

    data class Failure(val code: String, val compiled: CompiledUserQuery) : SearchExecution
}

sealed interface CountExecution {
    data class Success(val count: Long, val compiled: CompiledUserQuery) : CountExecution

    data class EmptyInput(val compiled: CompiledUserQuery) : CountExecution

    data class Rejected(val compiled: CompiledUserQuery) : CountExecution

    data class Failure(val code: String, val compiled: CompiledUserQuery) : CountExecution
}
