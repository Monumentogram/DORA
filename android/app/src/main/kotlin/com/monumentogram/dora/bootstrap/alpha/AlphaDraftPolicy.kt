package com.monumentogram.dora.bootstrap.alpha

internal object AlphaDraftPolicy {
    fun acceptsTitle(value: String): Boolean =
        acceptsText(value, AlphaWorkspaceCodec.MAX_TITLE_BYTES)

    fun acceptsNotes(value: String): Boolean =
        acceptsText(value, AlphaWorkspaceCodec.MAX_NOTES_BYTES)

    fun acceptsSummary(value: String): Boolean =
        acceptsText(value, AlphaWorkspaceCodec.MAX_SUMMARY_BYTES)

    fun acceptsSearchQuery(value: String): Boolean = acceptsText(value, MAX_SEARCH_QUERY_BYTES)

    fun acceptsTaskLines(value: String): Boolean {
        if (!acceptsText(value, MAX_EDITOR_DRAFT_BYTES)) return false
        val taskLines = value.lines().map(String::trim).filter(String::isNotEmpty)
        return taskLines.size <= AlphaWorkspaceCodec.MAX_TASKS_PER_CONVERSATION &&
            taskLines.all { acceptsText(it, AlphaWorkspaceCodec.MAX_TASK_TEXT_BYTES) }
    }

    fun acceptsEditorDraft(
        title: String,
        notes: String,
        summary: String,
        taskLines: String,
    ): Boolean {
        val fieldsAccepted =
            listOf(
                    acceptsTitle(title),
                    acceptsNotes(notes),
                    acceptsSummary(summary),
                    acceptsTaskLines(taskLines),
                )
                .all { it }
        if (!fieldsAccepted) {
            return false
        }
        return try {
            listOf(title, notes, summary, taskLines).sumOf {
                AlphaWorkspaceCodec.utf8Size(it).toLong()
            } <= MAX_EDITOR_DRAFT_BYTES.toLong()
        } catch (_: AlphaWorkspaceFormatException) {
            false
        }
    }

    private fun acceptsText(value: String, maximumBytes: Int): Boolean {
        if (value.length > maximumBytes) return false
        return try {
            AlphaWorkspaceCodec.utf8Size(value) <= maximumBytes
        } catch (_: AlphaWorkspaceFormatException) {
            false
        }
    }

    private const val MAX_SEARCH_QUERY_BYTES = 1_024
    private const val MAX_EDITOR_DRAFT_BYTES = 65_536
}
