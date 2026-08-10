@file:Suppress("MagicNumber") // Android constants and Stage 0 D-profile thresholds are explicit.

package com.monumentogram.dora.poc.capture.device

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.os.BatteryManager
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import android.os.SystemClock
import android.os.storage.StorageManager
import android.system.Os
import android.system.OsConstants
import com.monumentogram.dora.poc.capture.model.DeviceProfile
import com.monumentogram.dora.poc.capture.model.SystemSnapshot
import java.util.Locale
import kotlin.math.roundToLong

class DeviceInspector(private val context: Context) {
    fun inspect(): DeviceProfile {
        val memoryInfo = ActivityManager.MemoryInfo()
        context.getSystemService(ActivityManager::class.java).getMemoryInfo(memoryInfo)
        val battery = batteryReading()
        val supportedRates = SAMPLE_RATES.filter { rate ->
            optionalMetric {
                    AudioRecord.getMinBufferSize(
                        rate,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                    )
                }
                ?.let { it > 0 } == true
        }
        val audioManager = context.getSystemService(AudioManager::class.java)
        val inputTypes =
            optionalMetric {
                    audioManager
                        .getDevices(AudioManager.GET_DEVICES_INPUTS)
                        .map { audioRouteLabel(it.type) }
                        .distinct()
                        .sorted()
                }
                .orEmpty()
        val pageSize = pageSizeBytes()
        val ramMb = memoryInfo.totalMem / BYTES_PER_MIB
        val api = Build.VERSION.SDK_INT
        return DeviceProfile(
            manufacturer = sanitize(Build.MANUFACTURER, "unknown-manufacturer", 100),
            model = sanitize(Build.MODEL, "unknown-model", 120),
            androidVersion = sanitize(Build.VERSION.RELEASE, "unknown", 40),
            androidApi = api,
            buildId = sanitize(Build.ID, "unknown-build", 200),
            securityPatch =
                Build.VERSION.SECURITY_PATCH.takeIf { it.matches(SECURITY_PATCH_PATTERN) },
            primaryAbi = normalizedAbi(Build.SUPPORTED_ABIS.firstOrNull()),
            supportedAbis = Build.SUPPORTED_ABIS.map(::normalizedAbi).distinct(),
            totalRamMb = ramMb,
            freeStorageMb = freeStorageMb(),
            batteryPercent = battery.percent,
            chargingState = battery.chargingState,
            thermalStatus = thermalStatus(),
            audioInputTypes = inputTypes.ifEmpty { listOf("Входы не обнаружены") },
            supportedSampleRates = supportedRates,
            pageSizeBytes = pageSize,
            candidateProfileId = candidateProfile(api, ramMb, pageSize),
        )
    }

    fun snapshot(): SystemSnapshot {
        val battery = optionalMetric(::batteryReading) ?: BatteryReading(null, "unknown")
        val processPssMb = optionalMetric {
            val memoryInfo = Debug.MemoryInfo()
            Debug.getMemoryInfo(memoryInfo)
            memoryInfo.totalPss.toDouble() / KIB_PER_MIB
        }
        return SystemSnapshot(
            elapsedRealtimeMs = SystemClock.elapsedRealtime(),
            batteryPercent = battery.percent,
            chargingState = battery.chargingState,
            chargeCounterMicroAh = batteryProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER),
            currentNowMicroA = batteryProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW),
            energyCounterNanoWh =
                batteryLongProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER),
            thermalStatus = thermalStatus(),
            processPssMb = processPssMb,
            processRssMb = null,
            nativeHeapMb =
                optionalMetric {
                    Debug.getNativeHeapAllocatedSize().toDouble() / BYTES_PER_MIB
                },
            freeStorageMb = freeStorageMb(),
            screenInteractive =
                optionalMetric {
                    context.getSystemService(PowerManager::class.java).isInteractive
                } ?: true,
        )
    }

    private fun batteryReading(): BatteryReading {
        val statusIntent = optionalMetric {
            context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }
        if (statusIntent == null) return BatteryReading(null, "unknown")
        val level = statusIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = statusIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val percent =
            if (level >= 0 && scale > 0) {
                (level.toDouble() * 100.0 / scale * 10.0).roundToLong() / 10.0
            } else {
                null
            }
        val chargingState =
            when (statusIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
                BatteryManager.BATTERY_STATUS_CHARGING -> "charging"
                BatteryManager.BATTERY_STATUS_FULL -> "full"
                BatteryManager.BATTERY_STATUS_DISCHARGING,
                BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "unplugged"
                else -> "unknown"
            }
        return BatteryReading(percent, chargingState)
    }

    private fun batteryProperty(id: Int): Long? {
        val value =
            optionalMetric {
                context.getSystemService(BatteryManager::class.java).getIntProperty(id)
            } ?: return null
        return value.takeUnless { it == Int.MIN_VALUE }?.toLong()
    }

    private fun batteryLongProperty(id: Int): Long? {
        val value =
            optionalMetric {
                context.getSystemService(BatteryManager::class.java).getLongProperty(id)
            } ?: return null
        return value.takeUnless { it == Long.MIN_VALUE }
    }

    private fun thermalStatus(): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            optionalMetric {
                    context.getSystemService(PowerManager::class.java).currentThermalStatus
                }
                ?.let(::thermalStatusLabel)
        } else {
            null
        }

    private fun freeStorageMb(): Long {
        val storageManager = context.getSystemService(StorageManager::class.java)
        val bytes =
            runCatching { storageManager.getAllocatableBytes(StorageManager.UUID_DEFAULT) }
                .getOrElse { context.filesDir.usableSpace }
        return bytes / BYTES_PER_MIB
    }

    private data class BatteryReading(val percent: Double?, val chargingState: String)

    companion object {
        val SAMPLE_RATES = listOf(16_000, 48_000, 44_100, 32_000, 22_050, 8_000)
        private val SECURITY_PATCH_PATTERN = Regex("^[0-9]{4}-[0-9]{2}-[0-9]{2}$")
        private const val BYTES_PER_MIB = 1_048_576L
        private const val KIB_PER_MIB = 1024.0

        fun audioRouteLabel(type: Int): String =
            when (type) {
                AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Встроенный микрофон"
                AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Проводная гарнитура"
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth SCO"
                AudioDeviceInfo.TYPE_USB_DEVICE -> "USB-аудиоустройство"
                AudioDeviceInfo.TYPE_USB_HEADSET -> "USB-гарнитура"
                AudioDeviceInfo.TYPE_BLE_HEADSET -> "Bluetooth LE-гарнитура"
                AudioDeviceInfo.TYPE_TELEPHONY -> "Телефонный аудиомаршрут"
                else -> "Аудиовход типа $type"
            }

        fun thermalStatusLabel(status: Int): String =
            when (status) {
                PowerManager.THERMAL_STATUS_NONE -> "NONE"
                PowerManager.THERMAL_STATUS_LIGHT -> "LIGHT"
                PowerManager.THERMAL_STATUS_MODERATE -> "MODERATE"
                PowerManager.THERMAL_STATUS_SEVERE -> "SEVERE"
                PowerManager.THERMAL_STATUS_CRITICAL -> "CRITICAL"
                PowerManager.THERMAL_STATUS_EMERGENCY -> "EMERGENCY"
                PowerManager.THERMAL_STATUS_SHUTDOWN -> "SHUTDOWN"
                else -> "UNKNOWN"
            }

        fun maxThermalStatus(first: String?, second: String?): String? {
            val ranking =
                listOf("NONE", "LIGHT", "MODERATE", "SEVERE", "CRITICAL", "EMERGENCY", "SHUTDOWN")
            return when {
                first == null -> second
                second == null -> first
                else -> {
                    val firstRank = ranking.indexOf(first).takeIf { it >= 0 } ?: -1
                    val secondRank = ranking.indexOf(second).takeIf { it >= 0 } ?: -1
                    if (secondRank > firstRank) second else first
                }
            }
        }

        private fun pageSizeBytes(): Long =
            runCatching { Os.sysconf(OsConstants._SC_PAGESIZE) }.getOrDefault(4096L)

        private fun candidateProfile(api: Int, ramMb: Long, pageSize: Long): String =
            when {
                api >= 35 && pageSize == 16_384L -> "D7"
                api >= 36 && ramMb >= 12_288 -> "D3"
                api in 28..30 && ramMb >= 4_096 -> "D1"
                api in 33..36 && ramMb >= 6_144 -> "D2"
                else -> "D2"
            }

        private fun normalizedAbi(value: String?): String =
            when (value?.lowercase(Locale.US)) {
                "arm64-v8a" -> "arm64-v8a"
                "armeabi-v7a" -> "armeabi-v7a"
                "x86_64" -> "x86_64"
                "x86" -> "x86"
                else -> "x86"
            }

        private fun sanitize(value: String?, fallback: String, maxLength: Int): String {
            val candidate = value.orEmpty().trim().replace(Regex("[\\p{Cntrl}\\r\\n\\t]+"), " ")
            return candidate.ifBlank { fallback }.take(maxLength)
        }
    }
}

internal fun <T> optionalMetric(block: () -> T): T? = runCatching(block).getOrNull()
