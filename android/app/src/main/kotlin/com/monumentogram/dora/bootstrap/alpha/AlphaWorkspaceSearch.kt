package com.monumentogram.dora.bootstrap.alpha

import java.util.Locale

internal fun AlphaWorkspaceSnapshot.searchConversations(query: String): List<AlphaConversation> {
    val normalized = query.trim().lowercase(Locale.ROOT)
    return conversations.filter { conversation ->
        normalized.isEmpty() || conversation.searchableText().contains(normalized)
    }
}

private fun AlphaConversation.searchableText(): String =
    buildString {
            append(title)
            append('\n')
            append(notes)
            append('\n')
            append(summary)
            tasks.forEach {
                append('\n')
                append(it.text)
            }
        }
        .lowercase(Locale.ROOT)
