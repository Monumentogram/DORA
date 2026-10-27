package com.monumentogram.dora.transcript

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONArray
import org.json.JSONObject

/** Monotonic tombstones: cancellation can never turn into consent after a process restart. */
internal interface CloudCancellations {
    fun blocked(id: String, destination: String): Boolean

    fun record(id: String, destination: String)

    fun actions(destination: String): List<String>
}

/** Independent of the foreground vault lease. Contains only opaque action IDs and destinations. */
@Suppress("MagicNumber", "TooGenericExceptionCaught")
internal class EncryptedCloudCancellations(context: Context) : CloudCancellations {
    private val file = AtomicFile(File(context.noBackupFilesDir, "cloud-cancellations-v1"))

    override fun blocked(id: String, destination: String) =
        synchronized(lock) {
            read().any { it.first == id && it.second == destination }
        }

    override fun actions(destination: String) =
        synchronized(lock) {
            read().filter { it.second == destination }.map { it.first }
        }

    override fun record(id: String, destination: String) =
        synchronized(lock) {
            try {
                require(UUID.fromString(id).toString() == id)
                require(CloudEndpoint.validate(destination) == destination)
                val entries = read()
                if (id to destination !in entries) {
                    check(entries.size < 4096)
                    write(entries + (id to destination))
                }
            } catch (_: Exception) {
                // No further Cloud action in this process after an uncertain durable write.
                poisoned = true
                throw CloudFailure("CANCEL_STORAGE")
            }
        }

    private fun key(existing: Boolean): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!store.containsAlias(ALIAS)) {
            check(!existing)
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
                .apply {
                    init(
                        KeyGenParameterSpec.Builder(
                                ALIAS,
                                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                            )
                            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                            .setKeySize(256)
                            .setUserAuthenticationRequired(false)
                            .build()
                    )
                }
                .generateKey()
        }
        return store.getKey(ALIAS, null) as SecretKey
    }

    private fun read(): List<Pair<String, String>> {
        check(!poisoned)
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists())
            return emptyList()
        check(file.baseFile.length() <= MAX_BYTES)
        val encrypted = file.readFully()
        require(encrypted.size in 28..MAX_BYTES)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            key(true),
            GCMParameterSpec(128, encrypted.copyOfRange(0, 12)),
        )
        cipher.updateAAD(ALIAS.toByteArray(Charsets.UTF_8))
        val plain = cipher.doFinal(encrypted, 12, encrypted.size - 12)
        return try {
            val array = JSONArray(String(plain, Charsets.UTF_8))
            require(array.length() <= 4096)
            List(array.length()) { index ->
                val entry = array.getJSONObject(index)
                val id = entry.getString("id")
                val destination = entry.getString("destination")
                require(UUID.fromString(id).toString() == id)
                require(CloudEndpoint.validate(destination) == destination)
                id to destination
            }
        } finally {
            plain.fill(0)
        }
    }

    private fun write(entries: List<Pair<String, String>>) {
        val array = JSONArray()
        entries.forEach { (id, destination) ->
            array.put(JSONObject().put("id", id).put("destination", destination))
        }
        val plain = array.toString().toByteArray(Charsets.UTF_8)
        try {
            require(plain.size <= MAX_BYTES - 28)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key(file.baseFile.exists()))
            cipher.updateAAD(ALIAS.toByteArray(Charsets.UTF_8))
            val encrypted = cipher.iv + cipher.doFinal(plain)
            val output = file.startWrite()
            try {
                output.write(encrypted)
                output.fd.sync()
                file.finishWrite(output)
                // AtomicFile may only log a failed rename; verify the authenticated commit.
                check(read() == entries)
            } catch (error: Exception) {
                file.failWrite(output)
                throw error
            }
        } finally {
            plain.fill(0)
        }
    }

    companion object {
        private const val ALIAS = "dora-cloud-cancellation-v1"
        private const val MAX_BYTES = 1024 * 1024
        private val lock = Any()
        private var poisoned = false
    }
}
