package com.monumentogram.dora.transcript

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.UUID

/** Non-exportable installation key. Public proof is enrolled by the backend operator. */
internal class CloudIdentity(context: Context) {
    private val preferences =
        context.getSharedPreferences("cloud-installation-v1", Context.MODE_PRIVATE)
    val installationId: String =
        preferences.getString("installation", null)
            ?: UUID.randomUUID().toString().also {
                check(preferences.edit().putString("installation", it).commit())
            }
    private val alias = "dora-cloud-$installationId"
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    @Synchronized
    private fun ensureKey() {
        if (!keyStore.containsAlias(alias))
            KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
                .apply {
                    initialize(
                        KeyGenParameterSpec.Builder(
                                alias,
                                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
                            )
                            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                            .setDigests(KeyProperties.DIGEST_SHA256)
                            .setUserAuthenticationRequired(false)
                            .build()
                    )
                }
                .generateKeyPair()
    }

    fun publicInvitation(): String {
        ensureKey()
        val publicKey =
            Base64.encodeToString(keyStore.getCertificate(alias).publicKey.encoded, Base64.NO_WRAP)
        return org.json
            .JSONObject()
            .put("installation_id", installationId)
            .put("public_key_spki_base64", publicKey)
            .toString()
    }

    fun sign(bytes: ByteArray): String {
        ensureKey()
        return Signature.getInstance("SHA256withECDSA").run {
            initSign(keyStore.getKey(alias, null) as PrivateKey)
            update(bytes)
            Base64.encodeToString(sign(), Base64.NO_WRAP)
        }
    }

    fun endpoint(): String? = preferences.getString("endpoint", null)

    fun setEndpoint(value: String) {
        check(preferences.edit().putString("endpoint", CloudEndpoint.validate(value)).commit())
    }
}
