package com.monumentogram.dora.audio.persistence.keys

import android.security.keystore.KeyPermanentlyInvalidatedException
import androidx.test.platform.app.InstrumentationRegistry
import com.monumentogram.dora.poc.recovery.contract.CanonicalRecoveryAlias
import com.monumentogram.dora.poc.recovery.contract.RunId
import java.security.ProviderException
import java.util.UUID
import javax.crypto.AEADBadTagException
import javax.crypto.SecretKey
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RunKeyBoundaryTest {
    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun id() = UUID.randomUUID().toString()

    private fun run() = RunId.fromCanonicalString(id())

    @Test
    fun independentVaultsShareLogicalRunButNeverPhysicalKeysOrDeletion() {
        val a = NoLogRecoveryRunAeadBackend(context, id())
        val b = NoLogRecoveryRunAeadBackend(context, id())
        val run = run()
        val uri = CanonicalRecoveryAlias.forRun(run)
        try {
            a.generateNew(uri)
            val original = a.getAead(uri).encrypt(byteArrayOf(1), byteArrayOf())
            b.generateNew(uri)
            val retained = b.getAead(uri).encrypt(byteArrayOf(2), byteArrayOf())
            assertThrows(Exception::class.java) { b.getAead(uri).decrypt(original, byteArrayOf()) }
            assertThrows(Exception::class.java) { a.generateNew(uri) }
            a.removeAlias(run)
            assertFalse(a.aliasExists(run))
            assertTrue(b.aliasExists(run))
            a.removeAlias(run)
            assertArrayEquals(byteArrayOf(2), b.getAead(uri).decrypt(retained, byteArrayOf()))
        } finally {
            a.removeAlias(run)
            b.removeAlias(run)
        }
    }

    @Test
    fun invalidNamespaceRejectsBeforeKeystoreAndOldGlobalAliasIsNeverOpened() {
        var calls = 0
        val operations =
            object : VaultKeystoreIo by AndroidVaultKeystoreIo {
                override fun exists(alias: String): Boolean {
                    calls++
                    return AndroidVaultKeystoreIo.exists(alias)
                }
            }
        assertThrows(IllegalArgumentException::class.java) {
            NoLogRecoveryRunAeadBackend(context, "provider-path-canary", operations)
        }
        assertEquals(0, calls)
        val run = run()
        val uri = CanonicalRecoveryAlias.forRun(run)
        val oldAlias = uri.removePrefix("android-keystore://")
        AndroidVaultKeystoreIo.generate(oldAlias, false)
        try {
            val backend = NoLogRecoveryRunAeadBackend(context, id())
            assertFalse(backend.aliasExists(run))
            assertThrows(Exception::class.java) { backend.getAead(uri) }
            backend.removeAlias(run)
            assertTrue(AndroidVaultKeystoreIo.exists(oldAlias))
        } finally {
            AndroidVaultKeystoreIo.remove(oldAlias)
        }
    }

    @Test
    fun originalWrappedFailuresAreObservedBeforeSanitizationAndRetryClears() {
        val run = run()
        val uri = CanonicalRecoveryAlias.forRun(run)
        var injected: Exception? = null
        val operations =
            object : VaultKeystoreIo by AndroidVaultKeystoreIo {
                override fun open(alias: String): SecretKey? {
                    injected?.let { throw it }
                    return AndroidVaultKeystoreIo.open(alias)
                }
            }
        val backend = NoLogRecoveryRunAeadBackend(context, id(), operations)
        try {
            backend.generateNew(uri)
            for ((cause, expected) in
                listOf(
                    ProviderException("provider-canary") to KeyFailure.TEMPORARILY_UNAVAILABLE,
                    ProviderException("provider-canary", KeyPermanentlyInvalidatedException()) to
                        KeyFailure.PERMANENTLY_MISSING_OR_INVALIDATED,
                    ProviderException("provider-canary", AEADBadTagException("cipher-canary")) to
                        KeyFailure.AUTHENTICATION_FAILED,
                    KeyBoundaryException(KeyFailure.CORRUPT_CIPHERTEXT) to
                        KeyFailure.CORRUPT_CIPHERTEXT,
                )) {
                injected = cause
                val attempt =
                    backend.failureScopes.observe(run, RunKeyOperation.RECONCILIATION) {
                        assertThrows(Exception::class.java) { backend.getAead(uri) }
                    }
                assertEquals(expected, attempt.failure)
                assertNull(attempt.value.cause)
                assertFalse(attempt.value.toString().contains("canary"))
                injected = null
                assertNull(
                    backend.failureScopes
                        .observe(run, RunKeyOperation.RECONCILIATION) { backend.getAead(uri) }
                        .failure
                )
            }
        } finally {
            injected = null
            backend.removeAlias(run)
        }
    }

    @Test
    fun capturedAeadReportsAuthenticationAndMalformedOnlyInsideMatchingAttempt() {
        val backend = NoLogRecoveryRunAeadBackend(context, id())
        val run = run()
        val uri = CanonicalRecoveryAlias.forRun(run)
        try {
            backend.generateNew(uri)
            val aead = backend.getAead(uri)
            val ciphertext = aead.encrypt(byteArrayOf(1), byteArrayOf(2))
            val rejected =
                backend.failureScopes.observe(run, RunKeyOperation.RECONCILIATION) {
                    assertThrows(Exception::class.java) { aead.decrypt(ciphertext, byteArrayOf(3)) }
                }
            assertEquals(KeyFailure.AUTHENTICATION_FAILED, rejected.failure)
            val malformed =
                backend.failureScopes.observe(run, RunKeyOperation.RECONCILIATION) {
                    assertThrows(Exception::class.java) {
                        aead.decrypt(byteArrayOf(1), byteArrayOf())
                    }
                }
            assertEquals(KeyFailure.CORRUPT_CIPHERTEXT, malformed.failure)
            val unrelated =
                backend.failureScopes.observe(run(), RunKeyOperation.RECONCILIATION) {
                    assertThrows(Exception::class.java) { aead.decrypt(ciphertext, byteArrayOf(3)) }
                }
            assertNull(unrelated.failure)
            assertNull(
                backend.failureScopes
                    .observe(run, RunKeyOperation.RECONCILIATION) {
                        aead.decrypt(ciphertext, byteArrayOf(2))
                    }
                    .failure
            )
        } finally {
            backend.removeAlias(run)
        }
    }

    @Test
    fun absenceIsPermanentOnlyForExistingReadAndAliasProviderFailureIsTemporary() {
        val run = run()
        var fail = false
        val operations =
            object : VaultKeystoreIo by AndroidVaultKeystoreIo {
                override fun exists(alias: String): Boolean {
                    if (fail) throw ProviderException("alias-canary")
                    return AndroidVaultKeystoreIo.exists(alias)
                }
            }
        val backend = NoLogRecoveryRunAeadBackend(context, id(), operations)
        assertNull(
            backend.failureScopes
                .observe(run, RunKeyOperation.BOOTSTRAP) { backend.aliasExists(run) }
                .failure
        )
        assertEquals(
            KeyFailure.PERMANENTLY_MISSING_OR_INVALIDATED,
            backend.failureScopes
                .observe(run, RunKeyOperation.RECONCILIATION) { backend.aliasExists(run) }
                .failure,
        )
        fail = true
        assertEquals(
            KeyFailure.TEMPORARILY_UNAVAILABLE,
            backend.failureScopes
                .observe(run, RunKeyOperation.RECONCILIATION) {
                    assertThrows(Exception::class.java) { backend.aliasExists(run) }
                }
                .failure,
        )
    }
}
