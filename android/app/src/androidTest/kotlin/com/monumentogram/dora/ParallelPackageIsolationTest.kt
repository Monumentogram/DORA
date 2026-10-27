package com.monumentogram.dora

import android.os.Bundle
import android.os.Process
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Synthetic emulator evidence only. These probes are absent from the product APK. */
@Suppress("MagicNumber")
class ParallelPackageIsolationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val alias = "dora-parallel-isolation-synthetic"
    private val marker = "synthetic-isolation-only".toByteArray()

    private fun key(): SecretKey =
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.getKey(alias, null)
            as SecretKey

    @Test
    fun seed() {
        assertTrue(
            context.packageName.endsWith(".pilot") || context.packageName.endsWith(".preview")
        )
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        assertFalse(store.containsAlias(alias))
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .apply {
                init(
                    KeyGenParameterSpec.Builder(
                            alias,
                            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                        )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .build()
                )
            }
            .generateKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val sealed = cipher.iv + cipher.doFinal(marker)
        File(context.noBackupFilesDir, "parallel-synthetic-marker").writeBytes(marker)
        File(context.noBackupFilesDir, "parallel-synthetic-sealed").writeBytes(sealed)
        instrumentation.sendStatus(
            0,
            Bundle().apply {
                putString("parallel_uid", Process.myUid().toString())
                putString("parallel_dir", context.noBackupFilesDir.absolutePath)
                putString("parallel_sealed", Base64.encodeToString(sealed, Base64.NO_WRAP))
            },
        )
    }

    @Test
    fun cannotReadPeerFileOrDecryptPeerKey() {
        val args = InstrumentationRegistry.getArguments()
        assertNotEquals(Process.myUid(), checkNotNull(args.getString("peerUid")).toInt())
        val peerDir = checkNotNull(args.getString("peerDir"))
        assertNotEquals(context.noBackupFilesDir.absolutePath, peerDir)
        var denied = false
        try {
            FileInputStream(File(peerDir, "parallel-synthetic-marker")).use { it.read() }
        } catch (_: IOException) {
            denied = true
        }
        assertTrue("Peer private file must be inaccessible", denied)
        val sealed = Base64.decode(checkNotNull(args.getString("peerSealed")), Base64.NO_WRAP)
        var rejected = false
        try {
            decrypt(sealed)
        } catch (_: javax.crypto.AEADBadTagException) {
            rejected = true
        }
        assertTrue("Same alias in another UID must not decrypt peer ciphertext", rejected)
        verifyOwn()
    }

    @Test
    fun verifyOwn() {
        assertArrayEquals(
            marker,
            File(context.noBackupFilesDir, "parallel-synthetic-marker").readBytes(),
        )
        assertArrayEquals(
            marker,
            decrypt(File(context.noBackupFilesDir, "parallel-synthetic-sealed").readBytes()),
        )
        assertEquals(null, key().encoded)
    }

    private fun decrypt(sealed: ByteArray): ByteArray =
        Cipher.getInstance("AES/GCM/NoPadding")
            .apply {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, sealed.copyOfRange(0, 12)))
            }
            .doFinal(sealed.copyOfRange(12, sealed.size))
}
