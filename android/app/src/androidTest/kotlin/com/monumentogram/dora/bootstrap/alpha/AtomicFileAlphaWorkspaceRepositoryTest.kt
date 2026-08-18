package com.monumentogram.dora.bootstrap.alpha

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AtomicFileAlphaWorkspaceRepositoryTest {
    @Test
    fun savesAndLoadsSnapshotAcrossRepositoryInstances() = withTemporaryDirectory { directory ->
        val snapshot = sampleSnapshot()
        val firstRepository = AtomicFileAlphaWorkspaceRepository(directory)

        assertEquals(AlphaSaveResult.Saved, firstRepository.save(snapshot))
        val reloaded = AtomicFileAlphaWorkspaceRepository(directory).load()

        assertEquals(AlphaLoadResult.Ready(snapshot), reloaded)
    }

    @Test
    fun emptySnapshotPersistsWholeConversationDeletion() = withTemporaryDirectory { directory ->
        val firstRepository = AtomicFileAlphaWorkspaceRepository(directory)
        assertEquals(AlphaSaveResult.Saved, firstRepository.save(sampleSnapshot()))

        assertEquals(AlphaSaveResult.Saved, firstRepository.save(AlphaWorkspaceSnapshot()))

        assertEquals(
            AlphaLoadResult.Ready(AlphaWorkspaceSnapshot()),
            AtomicFileAlphaWorkspaceRepository(directory).load(),
        )
    }

    @Test
    fun corruptSnapshotFailsClosedWithoutReplacingSource() = withTemporaryDirectory { directory ->
        val source = byteArrayOf(0x01, 0x02, 0x03)
        val snapshotFile = File(directory, AtomicFileAlphaWorkspaceRepository.FILE_NAME)
        snapshotFile.writeBytes(source)

        val result = AtomicFileAlphaWorkspaceRepository(directory).load()

        assertTrue(result is AlphaLoadResult.Unavailable)
        assertTrue(snapshotFile.readBytes().contentEquals(source))
    }

    private fun withTemporaryDirectory(block: (File) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "alpha-repository-test-${UUID.randomUUID()}")
        check(directory.mkdirs())
        try {
            block(directory)
        } finally {
            check(directory.deleteRecursively())
        }
    }

    private fun sampleSnapshot(): AlphaWorkspaceSnapshot =
        AlphaWorkspaceSnapshot(
            conversations =
                listOf(
                    AlphaConversation(
                        id = "conversation-test",
                        title = "Синтетическая встреча",
                        notes = "Только тестовый текст",
                        summary = "Ручное резюме",
                        tasks = listOf(AlphaTask(id = "task-test", text = "Проверить сохранение")),
                        createdAtEpochMillis = 1_000,
                        updatedAtEpochMillis = 2_000,
                    )
                )
        )
}
