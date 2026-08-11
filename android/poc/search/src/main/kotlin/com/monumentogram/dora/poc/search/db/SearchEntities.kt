package com.monumentogram.dora.poc.search.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "conversations",
    indices = [Index("started_at_ms"), Index("source_type")],
)
data class ConversationEntity(
    @PrimaryKey @ColumnInfo(name = "conversation_id") val conversationId: Long,
    val title: String,
    @ColumnInfo(name = "started_at_ms") val startedAtMs: Long,
    @ColumnInfo(name = "source_type") val sourceType: String,
    @ColumnInfo(name = "participant_label") val participantLabel: String,
)

@Entity(
    tableName = "transcript_segments",
    foreignKeys =
        [
            ForeignKey(
                entity = ConversationEntity::class,
                parentColumns = ["conversation_id"],
                childColumns = ["conversation_id"],
                onDelete = ForeignKey.CASCADE,
            )
        ],
    indices =
        [
            Index("conversation_id"),
            Index(value = ["conversation_id", "sequence"], unique = true),
        ],
)
data class TranscriptSegmentEntity(
    @PrimaryKey @ColumnInfo(name = "segment_id") val segmentId: Long,
    @ColumnInfo(name = "conversation_id") val conversationId: Long,
    val sequence: Int,
    @ColumnInfo(name = "start_ms") val startMs: Long,
    @ColumnInfo(name = "end_ms") val endMs: Long,
    val language: String,
    val text: String,
)

@Entity(tableName = "transcript_segments_fts")
@Fts4(
    tokenizer = FtsOptions.TOKENIZER_UNICODE61,
    tokenizerArgs = ["remove_diacritics=0"],
    order = FtsOptions.Order.ASC,
)
data class TranscriptSegmentFts(
    @PrimaryKey @ColumnInfo(name = "rowid") val rowId: Long,
    val text: String,
)

data class SearchHit(
    val segmentId: Long,
    val conversationId: Long,
    val sequence: Int,
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val conversationTitle: String,
    val sourceType: String,
    val conversationStartedAtMs: Long,
)
