package com.monumentogram.dora.audio.persistence

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructStat
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProtectedReadOnlyAcquisitionTest {
    private val fixture = EncryptedAudioVaultFaultFixture()
    private val root =
        File(fixture.context.noBackupFilesDir, "dora-vault-v1").apply { check(mkdir()) }
    private val ciphertext =
        File(root, "ciphertext").apply { writeBytes(ByteArray(100) { it.toByte() }) }
    private val destination = File(fixture.context.noBackupFilesDir, "copy")

    @Test
    fun repeatedInventoryAndReadsKeepDescriptorCountBounded() {
        val expected = ProtectedReadOnlyAcquisition.inventory(root)
        ProtectedReadOnlyAcquisition.read(ciphertext.canonicalFile, 100)
        DiagnosticPolicyLoader.readPrivateFile(ciphertext)
        val before = descriptorCount()
        repeat(128) {
            assertEquals(expected, ProtectedReadOnlyAcquisition.inventory(root))
            assertEquals(100, ProtectedReadOnlyAcquisition.read(ciphertext.canonicalFile, 100).size)
            assertEquals(100, checkNotNull(DiagnosticPolicyLoader.readPrivateFile(ciphertext)).size)
        }
        repeat(32) { index ->
            val copy =
                ProtectedReadOnlyAcquisition.acquire(
                    fixture.context,
                    File(fixture.context.noBackupFilesDir, "fd-copy-$index"),
                    expected,
                ) {}
            copy.verifyCopy()
            copy.verifySource()
        }
        val after = descriptorCount()
        assertTrue("Descriptor count before=$before after=$after", after <= before + 8)
    }

    @Test
    fun repeatedFailedAcquisitionAndPolicyReadsKeepDescriptorCountBounded() {
        val expected = ProtectedReadOnlyAcquisition.inventory(root)
        check(destination.mkdir())
        val empty =
            File(fixture.context.noBackupFilesDir, "empty-policy").apply {
                writeBytes(byteArrayOf())
            }
        val before = descriptorCount()
        repeat(128) {
            assertThrows(Exception::class.java) {
                ProtectedReadOnlyAcquisition.acquire(fixture.context, destination, expected) {}
            }
            assertThrows(Exception::class.java) { DiagnosticPolicyLoader.readPrivateFile(empty) }
            assertThrows(Exception::class.java) {
                ProtectedReadOnlyAcquisition.read(ciphertext.canonicalFile, 99)
            }
        }
        repeat(32) { index ->
            val failed = File(fixture.context.noBackupFilesDir, "fd-rejected-$index")
            val sentinel = File(failed, "dora-vault-v1/ciphertext")
            var authorizations = 0
            assertThrows(Exception::class.java) {
                ProtectedReadOnlyAcquisition.acquire(fixture.context, failed, expected) {
                    authorizations++
                    if (authorizations == 2) sentinel.writeBytes(byteArrayOf(9))
                }
            }
            assertEquals(2, authorizations)
            org.junit.Assert.assertArrayEquals(byteArrayOf(9), sentinel.readBytes())
        }
        val after = descriptorCount()
        assertTrue("Descriptor count before=$before after=$after", after <= before + 8)
        assertEquals(expected, ProtectedReadOnlyAcquisition.inventory(root))
        assertEquals(0, checkNotNull(destination.list()).size)
    }

    private fun descriptorCount() = checkNotNull(File("/proc/self/fd").list()).size

    @Test
    fun exactManifestAcquiresAndRejectsExistingDestination() {
        val before = ProtectedReadOnlyAcquisition.inventory(root)
        val copy = ProtectedReadOnlyAcquisition.acquire(fixture.context, destination, before) {}
        assertEquals(
            before,
            ProtectedReadOnlyAcquisition.inventory(
                File(copy.context.noBackupFilesDir, "dora-vault-v1")
            ),
        )
        assertEquals(before, ProtectedReadOnlyAcquisition.inventory(root))
        assertThrows(Exception::class.java) {
            ProtectedReadOnlyAcquisition.acquire(fixture.context, destination, before) {}
        }
    }

    @Test
    fun sourceDriftAndUnlistedFileAreRejectedBeforeSuccessfulAcquisition() {
        val expected = ProtectedReadOnlyAcquisition.inventory(root)
        ciphertext.appendBytes(byteArrayOf(1))
        assertThrows(Exception::class.java) {
            ProtectedReadOnlyAcquisition.acquire(fixture.context, destination, expected) {}
        }
        val current = ProtectedReadOnlyAcquisition.inventory(root)
        File(root, "extra").writeBytes(byteArrayOf(2))
        assertThrows(Exception::class.java) {
            ProtectedReadOnlyAcquisition.acquire(fixture.context, destination, current) {}
        }
    }

    @Test
    fun symbolicLinksAreRejected() {
        val link = File(root, "symlink")
        Os.symlink(ciphertext.path, link.path)
        assertThrows(IllegalStateException::class.java) {
            ProtectedReadOnlyAcquisition.requireRegularSingleLink(Os.lstat(link.path))
        }
        assertThrows(Exception::class.java) { ProtectedReadOnlyAcquisition.inventory(root) }
    }

    @Test
    fun multiplyLinkedRegularFileMetadataIsRejected() {
        val stat = Os.lstat(ciphertext.path)
        ProtectedReadOnlyAcquisition.requireRegularSingleLink(stat)
        val linked =
            StructStat(
                stat.st_dev,
                stat.st_ino,
                stat.st_mode,
                2L,
                stat.st_uid,
                stat.st_gid,
                stat.st_rdev,
                stat.st_size,
                stat.st_atime,
                stat.st_mtime,
                stat.st_ctime,
                stat.st_blksize,
                stat.st_blocks,
            )
        assertThrows(IllegalStateException::class.java) {
            ProtectedReadOnlyAcquisition.requireRegularSingleLink(linked)
        }
    }

    @Test
    fun hardLinksAreRejectedOrDeniedByPlatform() {
        val expected = ProtectedReadOnlyAcquisition.inventory(root)
        val link = File(root, "hardlink")
        try {
            Os.link(ciphertext.path, link.path)
        } catch (failure: ErrnoException) {
            // Some Android SELinux policies prohibit app hardlink creation itself.
            // This branch proves that platform denial left the source/copy untouched;
            // the separate metadata test still exercises the production nlink guard.
            assertTrue(failure.errno == OsConstants.EACCES || failure.errno == OsConstants.EPERM)
            assertFalse(link.exists())
            assertFalse(destination.exists())
            assertEquals(expected, ProtectedReadOnlyAcquisition.inventory(root))
            return
        }
        assertThrows(Exception::class.java) {
            ProtectedReadOnlyAcquisition.acquire(fixture.context, destination, expected) {}
        }
        assertFalse(destination.exists())
    }
}
