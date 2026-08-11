@file:Suppress("TooManyFunctions")

package com.monumentogram.dora.poc.search.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.sqlite.db.SupportSQLiteQuery

@Dao
interface SearchPocDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertConversations(conversations: List<ConversationEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertSegments(segments: List<TranscriptSegmentEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertFtsRows(rows: List<TranscriptSegmentFts>)

    @RawQuery(
        observedEntities =
            [ConversationEntity::class, TranscriptSegmentEntity::class, TranscriptSegmentFts::class]
    )
    fun rawSearch(query: SupportSQLiteQuery): List<SearchHit>

    @RawQuery(
        observedEntities =
            [ConversationEntity::class, TranscriptSegmentEntity::class, TranscriptSegmentFts::class]
    )
    fun rawCount(query: SupportSQLiteQuery): Long

    @RawQuery fun rawQueryPlan(query: SupportSQLiteQuery): List<QueryPlanRow>

    @Query("SELECT COUNT(*) FROM conversations") fun conversationCount(): Long

    @Query("SELECT COUNT(*) FROM transcript_segments") fun transcriptCount(): Long

    @Query("SELECT COUNT(*) FROM transcript_segments_fts") fun ftsCount(): Long

    @Query("UPDATE transcript_segments SET text = :text WHERE segment_id = :segmentId")
    fun updateSegmentTextCanonical(segmentId: Long, text: String): Int

    @Query("UPDATE transcript_segments_fts SET text = :text WHERE rowid = :segmentId")
    fun updateSegmentTextIndex(segmentId: Long, text: String): Int

    @Query(
        "UPDATE conversations SET source_type = :sourceType WHERE conversation_id = :conversationId"
    )
    fun updateConversationSource(conversationId: Long, sourceType: String): Int

    @Query("DELETE FROM transcript_segments_fts WHERE rowid = :segmentId")
    fun deleteFtsRow(segmentId: Long): Int

    @Query("DELETE FROM transcript_segments WHERE segment_id = :segmentId")
    fun deleteCanonicalSegment(segmentId: Long): Int

    @Query(
        "DELETE FROM transcript_segments_fts WHERE rowid IN " +
            "(SELECT segment_id FROM transcript_segments WHERE conversation_id = :conversationId)"
    )
    fun deleteConversationFtsRows(conversationId: Long): Int

    @Query("DELETE FROM conversations WHERE conversation_id = :conversationId")
    fun deleteConversationCanonical(conversationId: Long): Int

    @Transaction
    fun insertConversationWithSegments(
        conversation: ConversationEntity,
        segments: List<TranscriptSegmentEntity>,
    ) {
        insertConversations(listOf(conversation))
        insertSegments(segments)
        insertFtsRows(segments.map { TranscriptSegmentFts(it.segmentId, it.text) })
    }

    @Transaction
    fun updateSegmentText(segmentId: Long, text: String) {
        check(updateSegmentTextCanonical(segmentId, text) == 1)
        check(updateSegmentTextIndex(segmentId, text) == 1)
    }

    @Transaction
    fun deleteSegment(segmentId: Long) {
        check(deleteFtsRow(segmentId) == 1)
        check(deleteCanonicalSegment(segmentId) == 1)
    }

    @Transaction
    fun deleteConversation(conversationId: Long) {
        check(deleteConversationFtsRows(conversationId) > 0)
        check(deleteConversationCanonical(conversationId) == 1)
    }
}

data class QueryPlanRow(
    val id: Int,
    val parent: Int,
    val notused: Int,
    val detail: String,
)
