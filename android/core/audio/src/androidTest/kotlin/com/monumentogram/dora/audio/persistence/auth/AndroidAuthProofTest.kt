package com.monumentogram.dora.audio.persistence.auth

import android.app.KeyguardManager
import android.os.Build
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.security.KeyStore
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidAuthProofTest {
    @Test
    fun recentProofHasConfiguredOneSecondValidityAndIsNonExportable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(context.getSystemService(KeyguardManager::class.java).isDeviceSecure)
        val proof = AndroidAuthProof.create()
        try {
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val key = store.getKey(proof.recentAlias, null) as SecretKey
            assertNull(key.encoded)
            val info =
                SecretKeyFactory.getInstance(key.algorithm, "AndroidKeyStore")
                    .getKeySpec(key, KeyInfo::class.java) as KeyInfo
            assertTrue(info.isUserAuthenticationRequired)
            assertEquals(1, info.userAuthenticationValidityDurationSeconds)
            if (Build.VERSION.SDK_INT >= 30) {
                assertEquals(
                    KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL,
                    info.userAuthenticationType,
                )
            }
        } finally {
            proof.close()
        }
        assertFalse(
            KeyStore.getInstance("AndroidKeyStore")
                .apply { load(null) }
                .containsAlias(proof.recentAlias)
        )
    }

    @Test
    fun recentProofRejectsOperationAfterAuthenticationWindow() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(context.getSystemService(KeyguardManager::class.java).isDeviceSecure)
        val proof = AndroidAuthProof.create()
        try {
            // The preceding test may authenticate. Await an explicit expiry timer, not UI idle.
            val expired = CountDownLatch(1)
            // Keystore timeout enforcement uses platform clocks; allow one second of clock
            // granularity.
            android.os
                .Handler(android.os.Looper.getMainLooper())
                .postDelayed({ expired.countDown() }, 2_100)
            assertTrue(expired.await(5, TimeUnit.SECONDS))
            assertFalse(proof.verifyRecentAuthentication())
        } finally {
            proof.close()
        }
    }

    @Test
    fun authenticatorPolicyUsesCombinedStrongOnlyOnSupportedApis() {
        assertFalse(AppLockAuthenticatorPolicy.combinedPrompt(28))
        assertFalse(AppLockAuthenticatorPolicy.combinedPrompt(29))
        assertTrue(AppLockAuthenticatorPolicy.combinedPrompt(30))
        assertTrue(AppLockAuthenticatorPolicy.combinedPrompt(36))
        assertEquals(32783, AppLockAuthenticatorPolicy.combinedAuthenticators)
    }
}
