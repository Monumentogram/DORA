package com.monumentogram.dora.bootstrap.alpha

import java.nio.ByteBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AlphaWorkspaceCodecTest {
    @Test
    fun roundTripsUnicodeAndUserTaskOrigin() {
        val snapshot = sampleSnapshot()

        val decoded = AlphaWorkspaceCodec.decode(AlphaWorkspaceCodec.encode(snapshot))

        assertEquals(snapshot, decoded)
        assertEquals(AlphaTaskOrigin.USER, decoded.conversations.single().tasks.single().origin)
    }

    @Test
    fun rejectsTruncatedUnsupportedAndTrailingData() {
        val encoded = AlphaWorkspaceCodec.encode(sampleSnapshot())
        val unsupportedVersion = encoded.copyOf().also { ByteBuffer.wrap(it).putInt(4, 2) }
        val invalidTaskState = encoded.copyOf().also { it[it.lastIndex - 1] = 2 }

        assertThrows(AlphaWorkspaceFormatException::class.java) {
            AlphaWorkspaceCodec.decode(encoded.copyOf(encoded.size - 1))
        }
        assertThrows(AlphaWorkspaceFormatException::class.java) {
            AlphaWorkspaceCodec.decode(unsupportedVersion)
        }
        assertThrows(AlphaWorkspaceFormatException::class.java) {
            AlphaWorkspaceCodec.decode(encoded + 0x01)
        }
        assertThrows(AlphaWorkspaceFormatException::class.java) {
            AlphaWorkspaceCodec.decode(invalidTaskState)
        }
    }

    @Test
    fun rejectsDuplicateIdsBlankTitlesAndOversizedFields() {
        val conversation = sampleSnapshot().conversations.single()

        assertThrows(AlphaWorkspaceFormatException::class.java) {
            AlphaWorkspaceCodec.encode(
                AlphaWorkspaceSnapshot(listOf(conversation, conversation.copy(title = "Другая")))
            )
        }
        assertThrows(AlphaWorkspaceFormatException::class.java) {
            AlphaWorkspaceCodec.encode(
                AlphaWorkspaceSnapshot(listOf(conversation.copy(title = "  ")))
            )
        }
        assertThrows(AlphaWorkspaceFormatException::class.java) {
            AlphaWorkspaceCodec.encode(
                AlphaWorkspaceSnapshot(
                    listOf(
                        conversation.copy(
                            notes = "x".repeat(AlphaWorkspaceCodec.MAX_NOTES_BYTES + 1)
                        )
                    )
                )
            )
        }
    }

    @Test
    fun rejectsUnpairedUtf16SurrogateInsteadOfReplacingIt() {
        val conversation = sampleSnapshot().conversations.single()

        assertThrows(AlphaWorkspaceFormatException::class.java) {
            AlphaWorkspaceCodec.encode(
                AlphaWorkspaceSnapshot(listOf(conversation.copy(notes = "invalid-\uD800-text")))
            )
        }
    }

    @Test
    fun rejectsOversizedInputBeforeParsing() {
        val oversized = ByteArray(AlphaWorkspaceCodec.MAX_FILE_BYTES + 1)

        assertThrows(AlphaWorkspaceFormatException::class.java) {
            AlphaWorkspaceCodec.decode(oversized)
        }
    }

    private fun sampleSnapshot(): AlphaWorkspaceSnapshot =
        AlphaWorkspaceSnapshot(
            conversations =
                listOf(
                    AlphaConversation(
                        id = "conversation-1",
                        title = "Обсуждение Alpha",
                        notes = "Проверяем Unicode: привет 👋",
                        summary = "Ручное резюме",
                        tasks =
                            listOf(
                                AlphaTask(
                                    id = "task-1",
                                    text = "Проверить локальное сохранение",
                                    completed = true,
                                )
                            ),
                        createdAtEpochMillis = 1_000,
                        updatedAtEpochMillis = 2_000,
                    )
                )
        )
}
