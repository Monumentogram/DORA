@file:Suppress("MagicNumber", "ReturnCount")

package com.monumentogram.dora.poc.search.query

import android.database.sqlite.SQLiteException
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.monumentogram.dora.poc.search.db.SearchPocDao

class SearchRepository(private val dao: SearchPocDao) {
    fun search(request: SearchRequest): SearchExecution {
        val compiled = compile(request)
        when (compiled.status) {
            QueryStatus.EMPTY -> return SearchExecution.EmptyInput(compiled)
            QueryStatus.REJECTED -> return SearchExecution.Rejected(compiled)
            QueryStatus.READY,
            QueryStatus.FILTER_ONLY -> Unit
        }
        if (request.limit !in 1..100 || request.offset < 0) {
            return SearchExecution.Failure("INVALID_PAGE", compiled)
        }

        return try {
            val statement = buildStatement(request, compiled, countOnly = false)
            SearchExecution.Success(dao.rawSearch(statement.asQuery()), compiled)
        } catch (_: SQLiteException) {
            SearchExecution.Failure("SQLITE_QUERY_FAILED", compiled)
        } catch (_: IllegalStateException) {
            SearchExecution.Failure("INDEX_UNAVAILABLE", compiled)
        }
    }

    fun count(request: SearchRequest): CountExecution {
        val compiled = compile(request)
        when (compiled.status) {
            QueryStatus.EMPTY -> return CountExecution.EmptyInput(compiled)
            QueryStatus.REJECTED -> return CountExecution.Rejected(compiled)
            QueryStatus.READY,
            QueryStatus.FILTER_ONLY -> Unit
        }

        return try {
            val statement = buildCountStatement(request, compiled)
            CountExecution.Success(dao.rawCount(statement.asQuery()), compiled)
        } catch (_: SQLiteException) {
            CountExecution.Failure("SQLITE_QUERY_FAILED", compiled)
        } catch (_: IllegalStateException) {
            CountExecution.Failure("INDEX_UNAVAILABLE", compiled)
        }
    }

    fun compile(request: SearchRequest): CompiledUserQuery =
        SafeFtsQueryCompiler.compile(request.rawQuery, request.mode, !request.filters.isEmpty())

    fun explainCountQuery(request: SearchRequest): SupportSQLiteQuery {
        val compiled = compile(request)
        require(compiled.status == QueryStatus.READY || compiled.status == QueryStatus.FILTER_ONLY)
        return buildCountStatement(request, compiled).asExplainQuery()
    }

    fun explainSearchQuery(request: SearchRequest): SupportSQLiteQuery {
        val compiled = compile(request)
        require(compiled.status == QueryStatus.READY || compiled.status == QueryStatus.FILTER_ONLY)
        return buildStatement(request, compiled, countOnly = false).asExplainQuery()
    }

    private fun buildCountStatement(
        request: SearchRequest,
        compiled: CompiledUserQuery,
    ): BoundQuery {
        // A count-only MATCH without metadata filters is fully answered by the FTS doclist.
        // Joining every matching row back through the canonical tables is logically redundant
        // and turns common-term oracle checks into multi-hour scans at the 1M-row reference scale.
        if (compiled.status == QueryStatus.READY && request.filters.isEmpty()) {
            return BoundQuery(
                "SELECT COUNT(*) FROM transcript_segments_fts " +
                    "WHERE transcript_segments_fts MATCH ?",
                arrayOf(Fts4MatchExpressionRenderer.render(compiled, request.mode)),
            )
        }
        return buildStatement(request, compiled, countOnly = true)
    }

    private fun buildStatement(
        request: SearchRequest,
        compiled: CompiledUserQuery,
        countOnly: Boolean,
    ): BoundQuery {
        val sql = StringBuilder()
        if (countOnly) {
            sql.append("SELECT COUNT(*) ")
        } else {
            sql.append(
                "SELECT s.segment_id AS segmentId, s.conversation_id AS conversationId, " +
                    "s.sequence AS sequence, s.start_ms AS startMs, s.end_ms AS endMs, " +
                    "s.text AS text, c.title AS conversationTitle, c.source_type AS sourceType, " +
                    "c.started_at_ms AS conversationStartedAtMs "
            )
        }
        if (compiled.status == QueryStatus.READY) {
            sql.append(
                "FROM transcript_segments_fts " +
                    "CROSS JOIN transcript_segments AS s " +
                    "ON s.segment_id = transcript_segments_fts.rowid " +
                    "CROSS JOIN conversations AS c " +
                    "ON c.conversation_id = s.conversation_id "
            )
        } else {
            sql.append(
                "FROM conversations AS c " +
                    "CROSS JOIN transcript_segments AS s " +
                    "ON s.conversation_id = c.conversation_id "
            )
        }

        val predicates = mutableListOf<String>()
        val arguments = mutableListOf<Any>()
        Fts4MatchExpressionRenderer.render(compiled, request.mode)?.let {
            predicates += "transcript_segments_fts MATCH ?"
            arguments += it
        }
        request.filters.conversationId?.let {
            predicates += "c.conversation_id = ?"
            arguments += it
        }
        request.filters.sourceType?.let {
            predicates += "c.source_type = ?"
            arguments += it
        }
        request.filters.startedAtFromMs?.let {
            predicates += "c.started_at_ms >= ?"
            arguments += it
        }
        request.filters.startedAtToMs?.let {
            predicates += "c.started_at_ms < ?"
            arguments += it
        }
        if (predicates.isNotEmpty()) {
            sql.append("WHERE ").append(predicates.joinToString(" AND ")).append(' ')
        }
        if (!countOnly) {
            sql.append("ORDER BY ${orderByColumn(compiled)} ASC LIMIT ? OFFSET ?")
            arguments += request.limit
            arguments += request.offset
        }
        return BoundQuery(sql.toString(), arguments.toTypedArray())
    }

    private fun orderByColumn(compiled: CompiledUserQuery): String =
        if (compiled.status == QueryStatus.READY) "transcript_segments_fts.rowid"
        else "s.segment_id"

    private data class BoundQuery(val sql: String, val arguments: Array<out Any?>) {
        fun asQuery(): SupportSQLiteQuery = SimpleSQLiteQuery(sql, arguments)

        fun asExplainQuery(): SupportSQLiteQuery =
            SimpleSQLiteQuery("EXPLAIN QUERY PLAN $sql", arguments)
    }
}
