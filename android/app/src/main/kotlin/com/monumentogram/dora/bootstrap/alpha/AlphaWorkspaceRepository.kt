package com.monumentogram.dora.bootstrap.alpha

import android.util.AtomicFile
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException

internal sealed interface AlphaLoadResult {
    data class Ready(val snapshot: AlphaWorkspaceSnapshot) : AlphaLoadResult

    data class Unavailable(val userMessage: String) : AlphaLoadResult
}

internal sealed interface AlphaSaveResult {
    data object Saved : AlphaSaveResult

    data class Failed(val userMessage: String) : AlphaSaveResult
}

internal interface AlphaWorkspaceRepository {
    fun load(): AlphaLoadResult

    fun save(snapshot: AlphaWorkspaceSnapshot): AlphaSaveResult =
        AlphaSaveResult.Failed("Сохранение не поддерживается.")
}

internal class AtomicFileAlphaWorkspaceRepository(directory: File) : AlphaWorkspaceRepository {
    private val file = AtomicFile(File(directory, FILE_NAME))

    override fun load(): AlphaLoadResult =
        try {
            val bytes = file.openRead().use(::readBounded)
            AlphaLoadResult.Ready(AlphaWorkspaceCodec.decode(bytes))
        } catch (_: FileNotFoundException) {
            AlphaLoadResult.Ready(AlphaWorkspaceSnapshot())
        } catch (_: AlphaWorkspaceFormatException) {
            AlphaLoadResult.Unavailable(CORRUPT_MESSAGE)
        } catch (_: IOException) {
            AlphaLoadResult.Unavailable(READ_MESSAGE)
        } catch (_: SecurityException) {
            AlphaLoadResult.Unavailable(READ_MESSAGE)
        }

    override fun save(snapshot: AlphaWorkspaceSnapshot): AlphaSaveResult {
        val bytes =
            try {
                AlphaWorkspaceCodec.encode(snapshot)
            } catch (_: AlphaWorkspaceFormatException) {
                return AlphaSaveResult.Failed(LIMIT_MESSAGE)
            }
        var output: FileOutputStream? = null
        return try {
            output = file.startWrite()
            output.write(bytes)
            file.finishWrite(output)
            AlphaSaveResult.Saved
        } catch (_: IOException) {
            output?.let(file::failWrite)
            AlphaSaveResult.Failed(WRITE_MESSAGE)
        } catch (_: SecurityException) {
            output?.let(file::failWrite)
            AlphaSaveResult.Failed(WRITE_MESSAGE)
        }
    }

    private fun readBounded(input: FileInputStream): ByteArray {
        val declaredSize = input.channel.size()
        if (declaredSize > AlphaWorkspaceCodec.MAX_FILE_BYTES) {
            throw AlphaWorkspaceFormatException("Alpha snapshot exceeds the file limit")
        }
        val initialCapacity = declaredSize.coerceIn(0, DEFAULT_BUFFER_SIZE.toLong()).toInt()
        val output = ByteArrayOutputStream(initialCapacity)
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (output.size() + count > AlphaWorkspaceCodec.MAX_FILE_BYTES) {
                throw AlphaWorkspaceFormatException("Alpha snapshot exceeds the file limit")
            }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    internal companion object {
        const val FILE_NAME = "dora-alpha-workspace-v1.bin"
        private const val CORRUPT_MESSAGE =
            "Локальные данные Alpha повреждены или имеют неподдерживаемую версию. " +
                "Редактирование заблокировано, исходный файл не перезаписан."
        private const val READ_MESSAGE =
            "Не удалось безопасно прочитать локальные данные Alpha. Редактирование заблокировано."
        private const val WRITE_MESSAGE =
            "Не удалось сохранить изменение. Предыдущая версия локальных данных сохранена."
        private const val LIMIT_MESSAGE =
            "Изменение превышает безопасные ограничения Alpha и не было сохранено."
    }
}
