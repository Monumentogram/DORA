package com.monumentogram.dora.audio.persistence.keys

import androidx.test.platform.app.InstrumentationRegistry
import com.monumentogram.dora.poc.recovery.contract.CanonicalRecoveryAlias
import com.monumentogram.dora.poc.recovery.contract.RunId
import com.monumentogram.dora.poc.recovery.crypto.RecoveryDecryptFailureSignal
import com.monumentogram.dora.poc.recovery.crypto.toRecoveryDecryptFailureSignal
import java.security.KeyStore
import java.security.ProviderException
import java.security.SecureRandom
import java.util.UUID
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidVaultKeyTest {
    @Test
    fun recoveryProviderFailureRemainsOperationalWithoutProviderDetails() {
        assertRecoveryFailureSignal(
            ProviderException("synthetic-provider-alias-canary"),
            RecoveryDecryptFailureSignal.OPERATIONAL,
        )
    }

    @Test
    fun recoveryProviderWrappedBadTagRemainsAuthenticationRejectedWithoutProviderDetails() {
        assertRecoveryFailureSignal(
            ProviderException(
                "synthetic-provider-alias-canary",
                javax.crypto.AEADBadTagException("synthetic-ciphertext-canary"),
            ),
            RecoveryDecryptFailureSignal.AUTHENTICATION_REJECTED,
        )
    }

    private fun assertRecoveryFailureSignal(
        injected: Exception,
        expected: RecoveryDecryptFailureSignal,
    ) {
        val run = RunId.fromCanonicalString(UUID.randomUUID().toString())
        val operations =
            object : VaultKeystoreIo by AndroidVaultKeystoreIo {
                override fun open(alias: String): javax.crypto.SecretKey = throw injected
            }
        val backend = NoLogRecoveryRunAeadBackend(context, UUID.randomUUID().toString(), operations)
        try {
            backend.generateNew(CanonicalRecoveryAlias.forRun(run))
            val failure =
                assertThrows(Exception::class.java) {
                    backend.getAead(CanonicalRecoveryAlias.forRun(run))
                }
            assertEquals(expected, failure.toRecoveryDecryptFailureSignal())
            assertNull(failure.cause)
            assertTrue(failure.suppressed.isEmpty())
            assertFalse(failure.toString().contains("synthetic-"))
            assertTrue(backend.aliasExists(run))
        } finally {
            backend.removeAlias(run)
        }
    }

    @Test
    fun wrappedPermanentInvalidationAndAuthenticationFailuresRemainDistinct() {
        val failures =
            listOf(
                android.security.keystore.KeyPermanentlyInvalidatedException() to
                    KeyFailure.PERMANENTLY_MISSING_OR_INVALIDATED,
                android.security.keystore.UserNotAuthenticatedException() to
                    KeyFailure.AUTHENTICATION_FAILED,
            )
        for ((injected, expected) in failures) {
            val selector = selector()
            val operations =
                object : VaultKeystoreIo by AndroidVaultKeystoreIo {
                    override fun open(alias: String): javax.crypto.SecretKey =
                        throw java.security.InvalidKeyException(
                            "synthetic-provider-canary",
                            injected,
                        )
                }
            try {
                AndroidVaultKeyBackend(context).createNew(selector)
                val error =
                    assertThrows(KeyBoundaryException::class.java) {
                        AndroidVaultKeyBackend(context, operations).openExisting(selector)
                    }
                assertEquals(expected, error.failure)
                assertNull(error.cause)
                assertTrue(AndroidVaultKeystoreIo.exists(VaultKeyAlias.forSelector(selector)))
            } finally {
                remove(VaultKeyAlias.forSelector(selector))
            }
        }
    }

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun selector() = ByteArray(32).also(SecureRandom()::nextBytes)

    @Test
    fun realKeystoreWrapReopenAndMissingKeyAreFailClosed() {
        val selector = selector()
        val keys = AndroidVaultKeyBackend(context)
        try {
            val key = keys.createNew(selector)
            val secret = selector()
            val bundle =
                VaultEnvelope.seal(
                    selector,
                    UUID.randomUUID().toString(),
                    UUID.randomUUID().toString(),
                    secret,
                    key.aead,
                )
            val reopened = VaultEnvelope.open(selector, bundle, keys.openExisting(selector).aead)
            reopened.borrowDatabaseSecret { assertArrayEquals(secret, it) }
            assertThrows(KeyBoundaryException::class.java) { keys.createNew(selector) }
            remove(VaultKeyAlias.forSelector(selector))
            val failure =
                assertThrows(KeyBoundaryException::class.java) { keys.openExisting(selector) }
            assertEquals(KeyFailure.PERMANENTLY_MISSING_OR_INVALIDATED, failure.failure)
            assertEquals(VaultEnvelope.BUNDLE_BYTES, bundle.size)
        } finally {
            remove(VaultKeyAlias.forSelector(selector))
        }
    }

    @Test
    fun realKeystoreRejectsWrongAadAndTamperedCiphertextWithoutLeakingCause() {
        val selector = selector()
        try {
            val aead = AndroidVaultKeyBackend(context).createNew(selector).aead
            val cipher = aead.encrypt(byteArrayOf(1, 2, 3), byteArrayOf(4))
            val wrong =
                assertThrows(javax.crypto.AEADBadTagException::class.java) {
                    aead.decrypt(cipher, byteArrayOf(5))
                }
            assertNull(wrong.cause)
            cipher[cipher.lastIndex] = (cipher.last().toInt() xor 1).toByte()
            assertThrows(javax.crypto.AEADBadTagException::class.java) {
                aead.decrypt(cipher, byteArrayOf(4))
            }
        } finally {
            remove(VaultKeyAlias.forSelector(selector))
        }
    }

    @Test
    fun recoveryUsesOnlyExactCanonicalAliasAndIndependentKeys() {
        val backend = NoLogRecoveryRunAeadBackend(context, UUID.randomUUID().toString())
        val first = RunId.fromCanonicalString(UUID.randomUUID().toString())
        val second = RunId.fromCanonicalString(UUID.randomUUID().toString())
        try {
            backend.generateNew(CanonicalRecoveryAlias.forRun(first))
            backend.generateNew(CanonicalRecoveryAlias.forRun(second))
            val cipher =
                backend
                    .getAead(CanonicalRecoveryAlias.forRun(first))
                    .encrypt(byteArrayOf(9), byteArrayOf(7))
            assertThrows(javax.crypto.AEADBadTagException::class.java) {
                backend
                    .getAead(CanonicalRecoveryAlias.forRun(second))
                    .decrypt(cipher, byteArrayOf(7))
            }
            assertThrows(KeyBoundaryException::class.java) {
                backend.generateNew(CanonicalRecoveryAlias.forRun(first))
            }
            assertThrows(KeyBoundaryException::class.java) {
                backend.getAead("android-keystore://not-a-run")
            }
            assertTrue(backend.aliasExists(first))
            backend.removeAlias(first)
            assertFalse(backend.aliasExists(first))
        } finally {
            backend.removeAlias(first)
            backend.removeAlias(second)
        }
    }

    @Test
    fun uncertainAliasObservationCannotAuthorizeGenerationAndDiscardsProviderCause() {
        val selector = selector()
        val operations =
            object : VaultKeystoreIo by AndroidVaultKeystoreIo {
                override fun exists(alias: String): Boolean =
                    throw ProviderException("synthetic-alias-path-canary")
            }
        try {
            val error =
                assertThrows(KeyBoundaryException::class.java) {
                    AndroidVaultKeyBackend(context, operations).createNew(selector)
                }
            assertEquals(KeyFailure.TEMPORARILY_UNAVAILABLE, error.failure)
            assertNull(error.cause)
            assertFalse(AndroidVaultKeystoreIo.exists(VaultKeyAlias.forSelector(selector)))
        } finally {
            remove(VaultKeyAlias.forSelector(selector))
        }
    }

    @Test
    fun generatedButOpenFailedRecoveryAliasIsRetainedAndWitnessed() {
        val run = RunId.fromCanonicalString(UUID.randomUUID().toString())
        val operations =
            object : VaultKeystoreIo by AndroidVaultKeystoreIo {
                override fun open(alias: String): javax.crypto.SecretKey =
                    throw ProviderException("synthetic-key-canary")
            }
        val backend = NoLogRecoveryRunAeadBackend(context, UUID.randomUUID().toString(), operations)
        try {
            val result = ProductRecoveryBootstrapCrypto(backend).createNewAlias(run)
            assertTrue(
                result
                    is
                    com.monumentogram.dora.poc.recovery.bootstrap.BootstrapAliasCreation.GeneratedButOpenFailed
            )
            assertTrue(backend.aliasExists(run))
            val cause =
                (result
                        as
                        com.monumentogram.dora.poc.recovery.bootstrap.BootstrapAliasCreation.GeneratedButOpenFailed)
                    .cause
            assertNull(cause.cause)
            assertEquals("TEMPORARILY_UNAVAILABLE", cause.message)
            assertThrows(KeyBoundaryException::class.java) {
                backend.generateNew(CanonicalRecoveryAlias.forRun(run))
            }
        } finally {
            backend.removeAlias(run)
        }
    }

    private fun remove(alias: String) {
        KeyStore.getInstance("AndroidKeyStore").apply {
            load(null)
            deleteEntry(alias)
        }
    }
}
