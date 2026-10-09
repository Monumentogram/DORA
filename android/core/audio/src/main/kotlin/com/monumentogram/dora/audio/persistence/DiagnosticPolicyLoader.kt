package com.monumentogram.dora.audio.persistence

import android.content.Context
import android.os.Build
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.model.alpha.AudioAssetId
import com.monumentogram.dora.model.alpha.RecordingId
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/** Pin is in the signed debug APK; identities remain in owner-private no-backup storage. */
internal object DiagnosticPolicyLoader {
    const val PIN_ASSET = "dora-protected-policy.sha256"
    const val POLICY_FILE = "stage86-protected-policy.json"
    private const val MAX_BYTES = 1024 * 1024
    private const val SHA_HEX_LENGTH = 64
    private const val MAX_PIN_BYTES = SHA_HEX_LENGTH + 2
    private const val MAX_IDENTIFIERS = 100_000

    fun load(context: Context): DiagnosticSourcePolicy {
        val pin =
            if (PIN_ASSET in checkNotNull(context.assets.list(""))) {
                context.assets.open(PIN_ASSET).use { input ->
                    val bytes = input.readBytes()
                    check(bytes.size in SHA_HEX_LENGTH..MAX_PIN_BYTES)
                    bytes.toString(Charsets.US_ASCII).trim()
                }
            } else null
        val bytes = readPrivateFile(File(context.noBackupFilesDir, POLICY_FILE))
        if (!DiagnosticPolicyBinding.verify(DiagnosticBuild.ENABLED, pin, bytes))
            return DiagnosticSourcePolicy.ordinary()
        return decode(context, checkNotNull(bytes))
    }

    internal fun readPrivateFile(file: File): ByteArray? {
        // Android may expose the private root through /data/data -> /data/user/0.
        // Resolve that platform root, but never follow the policy leaf itself.
        val selected = File(checkNotNull(file.parentFile).canonicalFile, file.name)
        val fd =
            try {
                Os.open(
                    selected.path,
                    OsConstants.O_RDONLY or OsConstants.O_NOFOLLOW or OsConstants.O_NONBLOCK,
                    0,
                )
            } catch (error: ErrnoException) {
                if (error.errno != OsConstants.ENOENT) throw error
                return null
            }
        return try {
            val stat = Os.fstat(fd)
            check(OsConstants.S_ISREG(stat.st_mode) && stat.st_nlink == 1L)
            check(selected.canonicalFile == selected.absoluteFile && stat.st_size in 1..MAX_BYTES)
            val result = ByteArray(stat.st_size.toInt())
            var offset = 0
            while (offset < result.size) {
                val count = Os.read(fd, result, offset, result.size - offset)
                check(count > 0)
                offset += count
            }
            check(Os.read(fd, ByteArray(1), 0, 1) == 0)
            result
        } finally {
            Os.close(fd)
        }
    }

    internal fun decode(context: Context, bytes: ByteArray): DiagnosticSourcePolicy {
        val data = JSONObject(bytes.toString(Charsets.UTF_8))
        check(
            data.getString("format") in
                setOf("DORA_PROTECTED_HISTORICAL_V1", "DORA_PROTECTED_SUCCESSOR_V2")
        )
        check(data.getString("snapshotSha256").matches(Regex("[a-f0-9]{64}")))
        check(data.getString("package") == context.packageName)
        check(data.getString("model") == Build.MODEL && data.getInt("api") == Build.VERSION.SDK_INT)
        check(data.getString("firmware") == Build.VERSION.INCREMENTAL)
        if (data.getString("format") == "DORA_PROTECTED_SUCCESSOR_V2")
            return DiagnosticSuccessorPolicy.decode(data)
        val list = data.getJSONArray("protectedSources")
        check(list.length() == DiagnosticSourcePolicy.PROTECTED_SOURCE_COUNT)
        val sources =
            (0 until list.length())
                .map {
                    val row = list.getJSONObject(it)
                    AudioIdentity(
                        RecordingId(row.getString("recordingId")),
                        AudioAssetId(row.getString("assetId")),
                        row.getString("sessionId"),
                    )
                }
                .toSet()
        return DiagnosticSourcePolicy.protected(
                sources,
                strings(data.getJSONArray("historicalIdentifiers")),
            )
            .boundTo(data.getString("ownerId"), data.getString("vaultId"))
    }

    private fun strings(values: JSONArray): Set<String> {
        check(values.length() in 1..MAX_IDENTIFIERS)
        val result = (0 until values.length()).map { values.getString(it) }
        check(
            result.all {
                it.matches(Regex("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}"))
            }
        )
        check(result.size == result.toSet().size)
        return result.toSet()
    }
}
