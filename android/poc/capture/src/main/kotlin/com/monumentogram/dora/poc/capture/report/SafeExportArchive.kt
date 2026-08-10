@file:Suppress("LongMethod") // The ZIP allowlist topology is reviewable in one place.

package com.monumentogram.dora.poc.capture.report

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.monumentogram.dora.poc.capture.model.CaptureOutcome
import com.monumentogram.dora.poc.capture.model.DeletionReceipt
import com.monumentogram.dora.poc.capture.model.DeviceProfile
import com.monumentogram.dora.poc.capture.model.ManualObservations
import com.monumentogram.dora.poc.capture.model.SanitizedEvent
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class SafeExportArchive(private val context: Context) {
    fun createRunArchive(
        profile: DeviceProfile,
        outcome: CaptureOutcome,
        receipt: DeletionReceipt,
        observations: ManualObservations,
        events: List<SanitizedEvent>,
    ): File {
        val privateCapture = File(File(context.filesDir, "captures"), outcome.privateFileName)
        ExportReadiness.requireSafe(receipt, privateCapture.exists())
        val entries = buildList {
            add(
                textEntry(
                    DEVICE_PROFILE,
                    SanitizedReportBuilder.deviceProfileJson(profile, receipt.verifiedAtUtc),
                )
            )
            add(
                textEntry(
                    RUN_RESULT,
                    SanitizedReportBuilder.runResultJson(
                        profile,
                        outcome,
                        receipt,
                        observations,
                    ),
                )
            )
            add(
                textEntry(
                    DELETION_RECEIPT,
                    SanitizedReportBuilder.deletionReceiptJson(receipt),
                )
            )
            if (outcome.fixtureUsed) {
                add(
                    textEntry(
                        FIXTURE_MANIFEST,
                        SanitizedReportBuilder.fixtureManifestJson(),
                    )
                )
            }
            add(
                textEntry(
                    EVENT_LOG,
                    SanitizedReportBuilder.eventLogJson(outcome.runId, events),
                )
            )
            add(textEntry(README, SanitizedReportBuilder.readme(outcome)))
        }
        SafeExportPolicy.validate(entries)
        val exportDirectory = File(context.cacheDir, "exports")
        check(exportDirectory.mkdirs() || exportDirectory.isDirectory)
        val archive = File(exportDirectory, "dora-capture-poc-export-${outcome.runId}.zip")
        FileOutputStream(archive).use { stream ->
            ZipOutputStream(stream).use { zip ->
                entries.forEach { entry ->
                    zip.putNextEntry(ZipEntry(entry.name).apply { time = 0L })
                    zip.write(entry.bytes)
                    zip.closeEntry()
                }
            }
        }
        SafeExportPolicy.validateArchive(archive)
        return archive
    }

    fun createDeviceProfile(profile: DeviceProfile, generatedAtUtc: String): File {
        val exportDirectory = File(context.cacheDir, "exports")
        check(exportDirectory.mkdirs() || exportDirectory.isDirectory)
        val file = File(exportDirectory, "dora-capture-poc-device-profile.json")
        val entry =
            textEntry(
                DEVICE_PROFILE,
                SanitizedReportBuilder.deviceProfileJson(profile, generatedAtUtc),
            )
        SafeExportPolicy.validate(listOf(entry))
        file.writeBytes(entry.bytes)
        return file
    }

    fun shareIntent(file: File, mimeType: String): Intent {
        val uri =
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.files",
                file,
            )
        return Intent(Intent.ACTION_SEND)
            .setType(mimeType)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    companion object {
        const val DEVICE_PROFILE = "device-profile.json"
        const val RUN_RESULT = "run-result.json"
        const val DELETION_RECEIPT = "deletion-receipt.json"
        const val FIXTURE_MANIFEST = "fixture-manifest.json"
        const val EVENT_LOG = "sanitized-event-log.json"
        const val README = "README.txt"

        private fun textEntry(name: String, value: String) =
            ExportEntry(name, value.toByteArray(Charsets.UTF_8))
    }
}

object ExportReadiness {
    fun requireSafe(receipt: DeletionReceipt, privateCaptureExists: Boolean) {
        require(receipt.deletionSucceeded && receipt.absenceVerified) {
            "Экспорт заблокирован до подтверждённого удаления raw audio"
        }
        require(!privateCaptureExists) { "Raw audio всё ещё существует" }
    }
}

object SafeExportPolicy {
    private val allowedNames =
        setOf(
            SafeExportArchive.DEVICE_PROFILE,
            SafeExportArchive.RUN_RESULT,
            SafeExportArchive.DELETION_RECEIPT,
            SafeExportArchive.FIXTURE_MANIFEST,
            SafeExportArchive.EVENT_LOG,
            SafeExportArchive.README,
        )
    private val forbiddenJsonFields =
        listOf(
            "serialNumber",
            "androidId",
            "imei",
            "macAddress",
            "ipAddress",
            "accountName",
            "phoneNumber",
            "localPath",
            "usbIdentifier",
            "advertisingId",
            "transcript",
            "waveformBytes",
            "rawAudioBytes",
        )
    private val forbiddenTextPatterns =
        listOf(
            Regex("[A-Za-z]:\\\\", RegexOption.IGNORE_CASE),
            Regex("/(?:data|storage|sdcard|home)/", RegexOption.IGNORE_CASE),
            Regex("-----BEGIN [A-Z ]+PRIVATE KEY-----"),
            Regex("(?:ghp_|github_pat_|AIza)[A-Za-z0-9_-]+"),
        )
    private val forbiddenBinaryNames =
        Regex(".*\\.(?:wav|pcm|m4a|aac|mp3|flac|ogg|raw|trace)$", RegexOption.IGNORE_CASE)

    fun validate(entries: List<ExportEntry>) {
        require(entries.isNotEmpty())
        require(entries.map { it.name }.distinct().size == entries.size) { "Повтор имени в ZIP" }
        entries.forEach { entry ->
            require(entry.name in allowedNames) { "Запрещённый файл экспорта: ${entry.name}" }
            require(!forbiddenBinaryNames.matches(entry.name)) { "Audio/trace файл запрещён" }
            require(entry.bytes.size <= MAX_ENTRY_BYTES) { "Файл отчёта неожиданно большой" }
            val text = entry.bytes.toString(Charsets.UTF_8)
            forbiddenJsonFields.forEach { field ->
                require(!text.contains("\"$field\"", ignoreCase = true)) {
                    "Экспорт содержит запрещённое поле"
                }
            }
            forbiddenTextPatterns.forEach { pattern ->
                require(!pattern.containsMatchIn(text)) { "Экспорт содержит запрещённый шаблон" }
            }
        }
    }

    fun validateArchive(file: File) {
        require(file.isFile && file.length() in 1..MAX_ARCHIVE_BYTES)
        ZipFile(file).use { zip ->
            val entries = zip.entries().asSequence().toList()
            require(entries.isNotEmpty())
            require(entries.all { !it.isDirectory && it.name in allowedNames })
            require(entries.none { forbiddenBinaryNames.matches(it.name) })
            require(entries.sumOf { it.size.coerceAtLeast(0L) } <= MAX_ARCHIVE_BYTES)
        }
    }

    private const val MAX_ENTRY_BYTES = 512 * 1024
    private const val MAX_ARCHIVE_BYTES = 2L * 1024L * 1024L
}
