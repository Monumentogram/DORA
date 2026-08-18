package com.monumentogram.dora.bootstrap.alpha

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

internal class AlphaWorkspaceBinaryWriter(private val snapshot: AlphaWorkspaceSnapshot) {
    fun encode(): ByteArray {
        val bytes = ByteArrayOutputStream()
        val output = DataOutputStream(bytes)
        output.writeInt(MAGIC)
        output.writeInt(VERSION)
        output.writeInt(snapshot.conversations.size)
        snapshot.conversations.forEach { conversation ->
            writeString(output, bytes, conversation.id, AlphaWorkspaceCodec.MAX_ID_BYTES)
            output.writeLong(conversation.createdAtEpochMillis)
            output.writeLong(conversation.updatedAtEpochMillis)
            writeString(output, bytes, conversation.title, AlphaWorkspaceCodec.MAX_TITLE_BYTES)
            writeString(output, bytes, conversation.notes, AlphaWorkspaceCodec.MAX_NOTES_BYTES)
            writeString(output, bytes, conversation.summary, AlphaWorkspaceCodec.MAX_SUMMARY_BYTES)
            output.writeInt(conversation.tasks.size)
            conversation.tasks.forEach { task ->
                writeString(output, bytes, task.id, AlphaWorkspaceCodec.MAX_ID_BYTES)
                writeString(output, bytes, task.text, AlphaWorkspaceCodec.MAX_TASK_TEXT_BYTES)
                output.writeBoolean(task.completed)
                output.writeByte(USER_ORIGIN_CODE)
                requireFileCapacity(bytes.size())
            }
            requireFileCapacity(bytes.size())
        }
        output.flush()
        requireFileCapacity(bytes.size())
        return bytes.toByteArray()
    }

    private fun writeString(
        output: DataOutputStream,
        bytes: ByteArrayOutputStream,
        value: String,
        maximumBytes: Int,
    ) {
        val encoded = AlphaUtf8Codec.encode(value)
        requireAlphaFormat(encoded.size <= maximumBytes, "Alpha field exceeds its byte limit")
        requireAlphaFormat(
            bytes.size().toLong() + Int.SIZE_BYTES + encoded.size <=
                AlphaWorkspaceCodec.MAX_FILE_BYTES,
            "Alpha snapshot exceeds the file limit",
        )
        output.writeInt(encoded.size)
        output.write(encoded)
    }

    private fun requireFileCapacity(size: Int) {
        requireAlphaFormat(
            size <= AlphaWorkspaceCodec.MAX_FILE_BYTES,
            "Alpha snapshot exceeds the file limit",
        )
    }

    private companion object {
        const val MAGIC = 0x444F5241
        const val VERSION = 1
        const val USER_ORIGIN_CODE = 1
    }
}

internal class AlphaWorkspaceBinaryReader(private val bytes: ByteArray) {
    fun decode(): AlphaWorkspaceSnapshot =
        try {
            val byteInput = ByteArrayInputStream(bytes)
            val input = DataInputStream(byteInput)
            requireAlphaFormat(input.readInt() == MAGIC, "Unexpected Alpha snapshot magic")
            requireAlphaFormat(input.readInt() == VERSION, "Unsupported Alpha snapshot version")
            val conversationCount = input.readInt()
            requireAlphaCount(
                conversationCount,
                AlphaWorkspaceCodec.MAX_CONVERSATIONS,
                "conversation",
            )
            val conversations = List(conversationCount) { readConversation(input, byteInput) }
            requireAlphaFormat(byteInput.available() == 0, "Trailing Alpha snapshot data")
            AlphaWorkspaceSnapshot(conversations)
        } catch (error: AlphaWorkspaceFormatException) {
            throw error
        } catch (error: EOFException) {
            throw AlphaWorkspaceFormatException("Truncated Alpha snapshot", error)
        } catch (error: IOException) {
            throw AlphaWorkspaceFormatException("Unreadable Alpha snapshot", error)
        }

    private fun readConversation(
        input: DataInputStream,
        byteInput: ByteArrayInputStream,
    ): AlphaConversation {
        val id = readString(input, byteInput, AlphaWorkspaceCodec.MAX_ID_BYTES)
        val createdAt = input.readLong()
        val updatedAt = input.readLong()
        val title = readString(input, byteInput, AlphaWorkspaceCodec.MAX_TITLE_BYTES)
        val notes = readString(input, byteInput, AlphaWorkspaceCodec.MAX_NOTES_BYTES)
        val summary = readString(input, byteInput, AlphaWorkspaceCodec.MAX_SUMMARY_BYTES)
        val taskCount = input.readInt()
        requireAlphaCount(taskCount, AlphaWorkspaceCodec.MAX_TASKS_PER_CONVERSATION, "task")
        val tasks =
            List(taskCount) {
                val taskId = readString(input, byteInput, AlphaWorkspaceCodec.MAX_ID_BYTES)
                val text = readString(input, byteInput, AlphaWorkspaceCodec.MAX_TASK_TEXT_BYTES)
                val completedByte = input.readUnsignedByte()
                requireAlphaFormat(
                    completedByte == 0 || completedByte == 1,
                    "Invalid Alpha task state",
                )
                requireAlphaFormat(
                    input.readUnsignedByte() == USER_ORIGIN_CODE,
                    "Unsupported Alpha task origin",
                )
                AlphaTask(id = taskId, text = text, completed = completedByte == 1)
            }
        return AlphaConversation(
            id = id,
            title = title,
            notes = notes,
            summary = summary,
            tasks = tasks,
            createdAtEpochMillis = createdAt,
            updatedAtEpochMillis = updatedAt,
        )
    }

    private fun readString(
        input: DataInputStream,
        byteInput: ByteArrayInputStream,
        maximumBytes: Int,
    ): String {
        val length = input.readInt()
        requireAlphaFormat(length in 0..maximumBytes, "Invalid Alpha field length")
        requireAlphaFormat(length <= byteInput.available(), "Truncated Alpha field")
        val fieldBytes = ByteArray(length)
        input.readFully(fieldBytes)
        return AlphaUtf8Codec.decode(fieldBytes)
    }

    private companion object {
        const val MAGIC = 0x444F5241
        const val VERSION = 1
        const val USER_ORIGIN_CODE = 1
    }
}

internal object AlphaUtf8Codec {
    fun encode(value: String): ByteArray =
        try {
            val encoded =
                StandardCharsets.UTF_8.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(CharBuffer.wrap(value))
            ByteArray(encoded.remaining()).also { encoded.get(it) }
        } catch (error: java.nio.charset.CharacterCodingException) {
            throw AlphaWorkspaceFormatException("Invalid Unicode in Alpha text", error)
        }

    fun decode(bytes: ByteArray): String =
        try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (error: java.nio.charset.CharacterCodingException) {
            throw AlphaWorkspaceFormatException("Invalid UTF-8 in Alpha snapshot", error)
        }
}

internal fun requireAlphaFormat(condition: Boolean, message: String) {
    if (!condition) throw AlphaWorkspaceFormatException(message)
}

private fun requireAlphaCount(value: Int, maximum: Int, label: String) {
    requireAlphaFormat(value in 0..maximum, "Invalid Alpha $label count")
}
