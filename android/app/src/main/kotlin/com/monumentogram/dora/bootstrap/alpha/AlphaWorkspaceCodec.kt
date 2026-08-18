package com.monumentogram.dora.bootstrap.alpha

internal object AlphaWorkspaceCodec {
    const val MAX_FILE_BYTES = 1_048_576
    const val MAX_CONVERSATIONS = 200
    const val MAX_TASKS_PER_CONVERSATION = 100
    const val MAX_ID_BYTES = 64
    const val MAX_TITLE_BYTES = 512
    const val MAX_NOTES_BYTES = 65_536
    const val MAX_SUMMARY_BYTES = 32_768
    const val MAX_TASK_TEXT_BYTES = 4_096

    private val idPattern = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")

    fun encode(snapshot: AlphaWorkspaceSnapshot): ByteArray {
        validateSnapshot(snapshot)
        return AlphaWorkspaceBinaryWriter(snapshot).encode()
    }

    fun utf8Size(value: String): Int = AlphaUtf8Codec.encode(value).size

    fun decode(bytes: ByteArray): AlphaWorkspaceSnapshot {
        if (bytes.size > MAX_FILE_BYTES) {
            throw AlphaWorkspaceFormatException("Alpha snapshot exceeds the file limit")
        }
        return AlphaWorkspaceBinaryReader(bytes).decode().also(::validateSnapshot)
    }

    private fun validateSnapshot(snapshot: AlphaWorkspaceSnapshot) {
        requireAlphaFormat(
            snapshot.conversations.size <= MAX_CONVERSATIONS,
            "Too many Alpha conversations",
        )
        val conversationIds = mutableSetOf<String>()
        val taskIds = mutableSetOf<String>()
        snapshot.conversations.forEach { conversation ->
            requireAlphaFormat(idPattern.matches(conversation.id), "Invalid Alpha conversation ID")
            requireAlphaFormat(
                conversationIds.add(conversation.id),
                "Duplicate Alpha conversation ID",
            )
            requireAlphaFormat(conversation.title.isNotBlank(), "Alpha conversation title is blank")
            requireUtf8Limit(conversation.title, MAX_TITLE_BYTES)
            requireUtf8Limit(conversation.notes, MAX_NOTES_BYTES)
            requireUtf8Limit(conversation.summary, MAX_SUMMARY_BYTES)
            requireAlphaFormat(
                conversation.createdAtEpochMillis >= 0,
                "Invalid Alpha creation time",
            )
            requireAlphaFormat(
                conversation.updatedAtEpochMillis >= conversation.createdAtEpochMillis,
                "Invalid Alpha update time",
            )
            requireAlphaFormat(
                conversation.tasks.size <= MAX_TASKS_PER_CONVERSATION,
                "Too many Alpha tasks",
            )
            conversation.tasks.forEach { task ->
                requireAlphaFormat(idPattern.matches(task.id), "Invalid Alpha task ID")
                requireAlphaFormat(taskIds.add(task.id), "Duplicate Alpha task ID")
                requireAlphaFormat(task.text.isNotBlank(), "Alpha task text is blank")
                requireUtf8Limit(task.text, MAX_TASK_TEXT_BYTES)
                requireAlphaFormat(
                    task.origin == AlphaTaskOrigin.USER,
                    "Unsupported Alpha task origin",
                )
            }
        }
    }

    private fun requireUtf8Limit(value: String, maximumBytes: Int) {
        requireAlphaFormat(
            utf8Size(value) <= maximumBytes,
            "Alpha field exceeds its byte limit",
        )
    }
}

internal class AlphaWorkspaceFormatException(message: String, cause: Throwable? = null) :
    Exception(message, cause)
