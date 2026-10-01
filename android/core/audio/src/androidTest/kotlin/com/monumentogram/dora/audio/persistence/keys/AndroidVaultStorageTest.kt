package com.monumentogram.dora.audio.persistence.keys

import android.content.Context
import android.content.ContextWrapper
import android.system.Os
import android.system.OsConstants
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileDescriptor
import java.security.KeyStore
import java.util.UUID
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidVaultStorageTest {
    @Test
    fun exactReadbackRecoversCompletedBundleAfterUncertainFsyncWithoutReplacement() =
        isolated { context ->
            val io =
                object : VaultFileIo by AndroidVaultFileIo {
                    override fun fsync(fd: FileDescriptor) {
                        if (
                            AndroidVaultFileIo.fstat(fd).st_size ==
                                VaultEnvelope.BUNDLE_BYTES.toLong()
                        ) {
                            throw android.system.ErrnoException(
                                "synthetic-fsync-canary",
                                OsConstants.EIO,
                            )
                        }
                        AndroidVaultFileIo.fsync(fd)
                    }
                }
            val storage = AndroidVaultBundleStorage(context, io)
            val result = VaultSecretStore(storage, AndroidVaultKeyBackend(context)).createNew()
            assertEquals(KeyFailure.STORAGE_FAILURE, (result as KeyAccess.Unavailable).failure)
            val bundle = File(storage.vaultDirectory, "vault.bundle").readBytes()
            val reopened =
                VaultSecretStore(
                        AndroidVaultBundleStorage(context),
                        AndroidVaultKeyBackend(context),
                    )
                    .openExisting()
            assertTrue(reopened is KeyAccess.Available)
            assertArrayEquals(bundle, File(storage.vaultDirectory, "vault.bundle").readBytes())
        }

    @Test
    fun replacingLeafBetweenLstatAndOpenIsRejected() = isolated { context ->
        val storage = AndroidVaultBundleStorage(context)
        storage.reserve(ByteArray(32) { 3 })
        val marker = File(storage.vaultDirectory, "selector")
        val io =
            object : VaultFileIo by AndroidVaultFileIo {
                override fun open(path: String, flags: Int, mode: Int): FileDescriptor {
                    if (path == marker.path) {
                        val bytes = marker.readBytes()
                        check(marker.renameTo(File(storage.vaultDirectory, "retained-selector")))
                        val fd =
                            AndroidVaultFileIo.open(
                                path,
                                OsConstants.O_WRONLY or OsConstants.O_CREAT or OsConstants.O_EXCL,
                                0x180,
                            )
                        try {
                            Os.write(fd, bytes, 0, bytes.size)
                        } finally {
                            Os.close(fd)
                        }
                    }
                    return AndroidVaultFileIo.open(path, flags, mode)
                }
            }
        val failure =
            assertThrows(KeyBoundaryException::class.java) {
                AndroidVaultBundleStorage(context, io).readSelector()
            }
        assertEquals(KeyFailure.CORRUPT_CIPHERTEXT, failure.failure)
        assertTrue(File(storage.vaultDirectory, "retained-selector").exists())
    }

    @Test
    fun durableBundleReopensExactIdentityAndNoPlaintextIdentifiers() = isolated { context ->
        val storage = AndroidVaultBundleStorage(context)
        val store = VaultSecretStore(storage, AndroidVaultKeyBackend(context))
        val created = (store.createNew() as KeyAccess.Available).value
        val reopened =
            (VaultSecretStore(AndroidVaultBundleStorage(context), AndroidVaultKeyBackend(context))
                    .openExisting() as KeyAccess.Available)
                .value
        assertEquals(created.ownerId, reopened.ownerId)
        assertEquals(created.databaseObjectSelector, reopened.databaseObjectSelector)
        created.borrowDatabaseSecret { secret ->
            reopened.borrowDatabaseSecret { assertArrayEquals(secret, it) }
        }
        storage.vaultDirectory
            .listFiles()!!
            .filter { it.isFile }
            .forEach {
                val text = it.readBytes().toString(Charsets.ISO_8859_1)
                assertFalse(text.contains(created.ownerId))
                assertFalse(text.contains(created.vaultId))
            }
        assertEquals(
            KeyFailure.NAMESPACE_OCCUPIED,
            (store.createNew() as KeyAccess.Unavailable).failure,
        )
    }

    @Test
    fun partialWriteAndFsyncFailureFenceNamespace() {
        for (failSync in listOf(false, true)) isolated { context ->
            val io =
                object : VaultFileIo by AndroidVaultFileIo {
                    var writes = 0

                    override fun write(
                        fd: FileDescriptor,
                        bytes: ByteArray,
                        offset: Int,
                        count: Int,
                    ): Int {
                        writes++
                        if (!failSync && writes == 2)
                            throw android.system.ErrnoException("synthetic", OsConstants.ENOSPC)
                        return AndroidVaultFileIo.write(fd, bytes, offset, minOf(count, 8))
                    }

                    override fun fsync(fd: FileDescriptor) {
                        if (failSync && writes > 0)
                            throw android.system.ErrnoException("synthetic", OsConstants.EIO)
                        AndroidVaultFileIo.fsync(fd)
                    }
                }
            val store =
                VaultSecretStore(
                    AndroidVaultBundleStorage(context, io),
                    AndroidVaultKeyBackend(context),
                )
            assertTrue(store.createNew() is KeyAccess.Unavailable)
            assertEquals(
                KeyFailure.NAMESPACE_OCCUPIED,
                (store.createNew() as KeyAccess.Unavailable).failure,
            )
            assertTrue(File(context.noBackupFilesDir, "dora-vault-v1").exists())
        }
    }

    @Test
    fun symlinkAndOversizedBundleAreRetainedAndRejected() = isolated { context ->
        val storage = AndroidVaultBundleStorage(context)
        storage.reserve(ByteArray(32) { 1 })
        val target =
            File(context.noBackupFilesDir, "synthetic-target").apply { writeBytes(ByteArray(4096)) }
        val bundle = File(storage.vaultDirectory, "vault.bundle")
        Os.symlink(target.path, bundle.path)
        assertThrows(KeyBoundaryException::class.java) { storage.readBundle() }
        assertEquals(4096L, target.length())
        Os.remove(bundle.path)
        bundle.writeBytes(ByteArray(4096))
        assertThrows(KeyBoundaryException::class.java) { storage.readBundle() }
        assertEquals(4096L, bundle.length())
    }

    private fun isolated(block: (Context) -> Unit) {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val root =
            File(base.noBackupFilesDir, "synthetic-keys-${UUID.randomUUID()}").apply { mkdir() }
        val context =
            object : ContextWrapper(base) {
                override fun getNoBackupFilesDir() = root

                override fun getApplicationContext(): Context = this
            }
        try {
            block(context)
        } finally {
            val marker = File(root, "dora-vault-v1/selector")
            if (marker.isFile && marker.length() == 32L) {
                val alias = VaultKeyAlias.forSelector(marker.readBytes())
                KeyStore.getInstance("AndroidKeyStore").apply {
                    load(null)
                    deleteEntry(alias)
                }
            }
            root.deleteRecursively()
        }
    }
}
