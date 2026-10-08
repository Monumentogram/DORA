package com.monumentogram.dora.audio.diagnostic

import android.security.keystore.KeyInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.monumentogram.dora.audio.persistence.EncryptedAudioVaultFaultFixture
import com.monumentogram.dora.audio.persistence.keys.NoLogRecoveryRunAeadBackend
import com.monumentogram.dora.audio.persistence.keys.VaultKeystoreIo
import com.monumentogram.dora.poc.recovery.contract.CanonicalRecoveryAlias
import com.monumentogram.dora.poc.recovery.contract.RunId
import java.util.UUID
import javax.crypto.SecretKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DiagnosticKeyFenceTest {
    private class ForbiddenKeystore : VaultKeystoreIo {
        var calls = 0

        private fun forbidden(): Nothing {
            calls++
            error("Unexpected keystore access")
        }

        override fun exists(alias: String): Boolean = forbidden()

        override fun generate(alias: String, strongBox: Boolean): Unit = forbidden()

        override fun open(alias: String): SecretKey? = forbidden()

        override fun information(key: SecretKey): KeyInfo = forbidden()

        override fun remove(alias: String): Unit = forbidden()
    }

    @Test
    fun protectedOrUnownedRunCannotCreateOrRemoveKey() {
        val operations = ForbiddenKeystore()
        val backend =
            NoLogRecoveryRunAeadBackend(
                EncryptedAudioVaultFaultFixture().context,
                UUID.randomUUID().toString(),
                operations,
            ) {
                error("Run is not admitted")
            }
        val run = RunId.fromCanonicalString(UUID.randomUUID().toString())
        assertThrows(IllegalStateException::class.java) {
            backend.generateNew(CanonicalRecoveryAlias.forRun(run))
        }
        assertThrows(IllegalStateException::class.java) { backend.removeAlias(run) }
        assertEquals(0, operations.calls)
    }
}
