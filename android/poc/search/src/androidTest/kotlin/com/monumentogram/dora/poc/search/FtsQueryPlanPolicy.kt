package com.monumentogram.dora.poc.search

import com.monumentogram.dora.poc.search.db.QueryPlanRow

data class FtsQueryPlanObservation(
    val details: List<String>,
    val ftsLoopIndex: Int,
    val segmentLoopIndex: Int,
    val conversationLoopIndex: Int,
    val ftsIsDrivingTable: Boolean,
    val canonicalLookupsUseRowId: Boolean,
    val sourceIndexIsNotDriving: Boolean,
) {
    val accepted: Boolean = ftsIsDrivingTable && canonicalLookupsUseRowId && sourceIndexIsNotDriving
}

object FtsQueryPlanPolicy {
    fun inspect(rows: List<QueryPlanRow>): FtsQueryPlanObservation {
        val details = rows.map(QueryPlanRow::detail)
        val ftsLoop = details.indexOfFirst { detail ->
            detail.contains("transcript_segments_fts") &&
                detail.contains("VIRTUAL TABLE", ignoreCase = true)
        }
        val segmentLoop = details.indexOfFirst { detail ->
            detail.contains("SEARCH s USING INTEGER PRIMARY KEY", ignoreCase = true)
        }
        val conversationLoop = details.indexOfFirst { detail ->
            detail.contains("SEARCH c USING INTEGER PRIMARY KEY", ignoreCase = true)
        }
        val sourceIndexLoop = details.indexOfFirst { detail ->
            detail.contains("index_conversations_source_type", ignoreCase = true)
        }
        return FtsQueryPlanObservation(
            details = details,
            ftsLoopIndex = ftsLoop,
            segmentLoopIndex = segmentLoop,
            conversationLoopIndex = conversationLoop,
            ftsIsDrivingTable =
                ftsLoop == 0 && segmentLoop > ftsLoop && conversationLoop > segmentLoop,
            canonicalLookupsUseRowId = segmentLoop >= 0 && conversationLoop >= 0,
            sourceIndexIsNotDriving = sourceIndexLoop < 0 || sourceIndexLoop > ftsLoop,
        )
    }
}
