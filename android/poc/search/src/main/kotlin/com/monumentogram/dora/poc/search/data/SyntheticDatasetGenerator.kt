@file:Suppress("CyclomaticComplexMethod", "LongMethod", "MagicNumber")

package com.monumentogram.dora.poc.search.data

import com.monumentogram.dora.poc.search.db.ConversationEntity
import com.monumentogram.dora.poc.search.db.TranscriptSegmentEntity
import java.security.MessageDigest

object SyntheticDatasetGenerator {
    const val DATASET_VERSION: String = "poc-search-synthetic-v1"
    const val GENERATOR_VERSION: String = "search-generator-1.0.0"
    const val SEED: Long = 2_026_081_001L
    const val REFERENCE_CONVERSATIONS: Int = 10_000
    const val SEGMENTS_PER_CONVERSATION: Int = 100
    const val REFERENCE_TRANSCRIPT_ROWS: Int = 1_000_000
    const val BASE_STARTED_AT_MS: Long = 1_735_689_600_000L
    const val CONVERSATION_INTERVAL_MS: Long = 3_600_000L
    const val CONVERSATION_BATCH_SIZE: Int = 1_000
    const val TRANSCRIPT_BATCH_SIZE: Int = 5_000

    val sourceTypes: List<String> = listOf("IN_PERSON", "VIDEO", "VOICE_NOTE", "WORKSHOP")
    val languages: List<String> = listOf("ru", "en", "mixed-ru-en")
    private val technicalTerms = listOf("sqlite", "room", "fts4", "kotlin", "unicode", "индекс")
    private val fillers = listOf("контекст", "detail", "проверка", "review", "данные", "data")

    fun conversation(conversationId: Long): ConversationEntity {
        require(conversationId in 1..REFERENCE_CONVERSATIONS.toLong())
        val company = (conversationId * 13 + SEED) % 41
        val ruParticipant = (conversationId * 31 + SEED) % 97
        val enParticipant = (conversationId * 17 + SEED) % 89
        return ConversationEntity(
            conversationId = conversationId,
            title =
                "Синтетический разговор ${conversationId.toString().padStart(5, '0')} " +
                    "Synthetic workspace ${company.toString().padStart(2, '0')}",
            startedAtMs = conversationStartedAtMs(conversationId),
            sourceType = conversationSource(conversationId),
            participantLabel =
                "СинтУчастник${ruParticipant.toString().padStart(2, '0')}|" +
                    "SynthParticipant${enParticipant.toString().padStart(2, '0')}",
        )
    }

    fun segment(rowId: Long): TranscriptSegmentEntity {
        require(rowId in 1..REFERENCE_TRANSCRIPT_ROWS.toLong())
        val conversationId = (rowId - 1) / SEGMENTS_PER_CONVERSATION + 1
        val sequence = ((rowId - 1) % SEGMENTS_PER_CONVERSATION).toInt()
        val language = languages[((rowId + SEED) % languages.size).toInt()]
        val ruParticipant = (conversationId * 31 + SEED) % 97
        val enParticipant = (conversationId * 17 + SEED) % 89
        val company = (conversationId * 13 + SEED) % 41
        val task = (sequence * 7L + conversationId + SEED) % 23
        val number = (rowId * 37 + SEED) % 1000
        val day = (conversationId + SEED) % 28 + 1
        val technicalTerm = technicalTerms[((rowId + SEED) % technicalTerms.size).toInt()]
        val parts = ArrayList<String>(32)

        when (language) {
            "ru" ->
                parts.addAll(
                    listOf(
                        "команда",
                        "обсуждает",
                        "проект",
                        "задача",
                        "срок",
                        "отчёт",
                        "синтетический",
                        "синтимя${ruParticipant.toString().padStart(2, '0')}",
                        "компаниясигма${company.toString().padStart(2, '0')}",
                        "задачаметка${task.toString().padStart(2, '0')}",
                        technicalTerm,
                        "номер${number.toString().padStart(3, '0')}",
                        "дата2026-08-${day.toString().padStart(2, '0')}",
                    )
                )
            "en" ->
                parts.addAll(
                    listOf(
                        "team",
                        "discusses",
                        "project",
                        "task",
                        "deadline",
                        "report",
                        "synthetic",
                        "synthname${enParticipant.toString().padStart(2, '0')}",
                        "orionlabs${company.toString().padStart(2, '0')}",
                        "tasklabel${task.toString().padStart(2, '0')}",
                        technicalTerm,
                        "number${number.toString().padStart(3, '0')}",
                        "date2026-08-${day.toString().padStart(2, '0')}",
                    )
                )
            else ->
                parts.addAll(
                    listOf(
                        "команда",
                        "project",
                        "обсуждает",
                        "task",
                        "срок",
                        "deadline",
                        "synthetic",
                        "синтимя${ruParticipant.toString().padStart(2, '0')}",
                        "synthname${enParticipant.toString().padStart(2, '0')}",
                        "компаниясигма${company.toString().padStart(2, '0')}",
                        "orionlabs${company.toString().padStart(2, '0')}",
                        "задачаметка${task.toString().padStart(2, '0')}",
                        "tasklabel${task.toString().padStart(2, '0')}",
                        technicalTerm,
                        "номер${number.toString().padStart(3, '0')}",
                        "number${number.toString().padStart(3, '0')}",
                    )
                )
        }

        val fillerCount = (((rowId + SEED) / 7) % 6).toInt()
        parts.addAll(fillers.take(fillerCount))
        appendMarkers(rowId, parts)

        val startMs = sequence * 45_000L
        val durationMs = 4_000L + ((rowId + SEED) % 12) * 750L
        return TranscriptSegmentEntity(
            segmentId = rowId,
            conversationId = conversationId,
            sequence = sequence,
            startMs = startMs,
            endMs = startMs + durationMs,
            language = language,
            text = parts.joinToString(" "),
        )
    }

    fun conversationStartedAtMs(conversationId: Long): Long =
        BASE_STARTED_AT_MS + (conversationId - 1) * CONVERSATION_INTERVAL_MS

    fun conversationSource(conversationId: Long): String =
        sourceTypes[((conversationId + SEED) % sourceTypes.size).toInt()]

    fun segmentId(conversationId: Long, sequence: Int): Long =
        (conversationId - 1) * SEGMENTS_PER_CONVERSATION + sequence + 1

    fun updateLogicalDigest(digest: MessageDigest, conversation: ConversationEntity) {
        digest.update(
            ("C|${conversation.conversationId}|${conversation.title}|${conversation.startedAtMs}|" +
                    "${conversation.sourceType}|${conversation.participantLabel}\n")
                .toByteArray(Charsets.UTF_8)
        )
    }

    fun updateLogicalDigest(digest: MessageDigest, segment: TranscriptSegmentEntity) {
        digest.update(
            ("S|${segment.segmentId}|${segment.conversationId}|${segment.sequence}|" +
                    "${segment.startMs}|${segment.endMs}|${segment.language}|${segment.text}\n")
                .toByteArray(Charsets.UTF_8)
        )
    }

    private fun appendMarkers(rowId: Long, parts: MutableList<String>) {
        if (rowId % 10 == 0L) parts.addAll(listOf("проект", "проект", "project", "project"))
        if (rowId % 65_537 == 0L) parts += "hypergraphdelta"
        if (rowId % 99_991 == 0L) parts += "редкословоаврора"
        if (rowId == 424_242L) parts += "uniquemarkerquasar"
        if (rowId % 10_007 == 0L) parts.addAll(listOf("красный", "спутник"))
        if (rowId % 10_009 == 0L) parts.addAll(listOf("silent", "harbor"))
        if (rowId % 10_037 == 0L) parts.addAll(listOf("проект", "nebula"))
        if (rowId % 997 == 0L) parts += "квантовыйконтур"
        if (rowId % 991 == 0L) parts += "hyperprotocol"
        if (rowId % 5_003 == 0L) {
            parts.addAll(listOf("alpha-beta", "colon:value", "quote\"token", "apostrophe'token"))
        }
        if (rowId % 12_347 == 0L) parts.addAll(listOf("ёжик", "café", "東京", "rocketemoji🚀"))
        if (rowId == 123_456L) parts += "mutationbeforemarker"
        if (rowId == 234_567L) parts += "segmentdeletemarker"
        if (rowId == segmentId(7_777, 0)) parts += "conversationdeletemarker"
    }
}
