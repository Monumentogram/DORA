package com.monumentogram.dora.recording

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.monumentogram.dora.DoraApplication
import com.monumentogram.dora.MainActivity
import com.monumentogram.dora.R
import com.monumentogram.dora.audio.recording.RecordingPhase
import java.io.FileDescriptor
import java.io.PrintWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ProductRecordingService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val controller
        get() = (application as DoraApplication).recording

    private var foreground = false
    private var lastPhase: RecordingPhase? = null
    private var lastPausePending = false
    private var renewedAt = 0L
    private lateinit var wakeLock: PowerManager.WakeLock

    override fun onCreate() {
        super.onCreate()
        controller.serviceCreated()
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(
                NotificationChannel(CHANNEL, "Запись DORA", NotificationManager.IMPORTANCE_LOW)
                    .apply {
                        setShowBadge(false)
                        description = "Состояние микрофона и управление записью"
                    }
            )
        wakeLock =
            getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DORA:recording")
                .apply { setReferenceCounted(false) }
        controller.onTerminal = {
            main.post {
                stopForeground(STOP_FOREGROUND_REMOVE)
                foreground = false
                stopSelf()
            }
        }
        scope.launch {
            controller.state.collect { snapshot ->
                val phase = snapshot.recording.phase
                if (
                    foreground && (phase != lastPhase || snapshot.pausePending != lastPausePending)
                ) {
                    getSystemService(NotificationManager::class.java)
                        .notify(NOTIFICATION_ID, notification(phase, snapshot.pausePending))
                    lastPhase = phase
                    lastPausePending = snapshot.pausePending
                }
                if (foreground && phase == RecordingPhase.RECORDING) {
                    val now = SystemClock.elapsedRealtime()
                    if (!wakeLock.isHeld || now - renewedAt >= WAKE_RENEW_MILLIS) {
                        wakeLock.acquire(WAKE_TIMEOUT_MILLIS)
                        renewedAt = now
                    }
                } else if (wakeLock.isHeld) wakeLock.release()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val token = intent?.getStringExtra(TOKEN)
        if (token == null || token != controller.actionToken) {
            if (!foreground) stopSelf(startId)
            return START_NOT_STICKY
        }
        when (intent?.action) {
            START ->
                if (!foreground) {
                    try {
                        ServiceCompat.startForeground(
                            this,
                            NOTIFICATION_ID,
                            notification(RecordingPhase.PREPARING),
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                            else 0,
                        )
                        foreground = true
                        controller.serviceStart(token)
                    } catch (_: Exception) {
                        controller.serviceRejected()
                        stopSelf()
                    }
                }
            PAUSE -> if (foreground) controller.pause(token) else stopSelf(startId)
            else -> if (!foreground) stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun dump(fd: FileDescriptor, writer: PrintWriter, args: Array<out String>?) {
        writer.write(controller.diagnosticSummary())
    }

    override fun onDestroy() {
        controller.onTerminal = null
        controller.serviceDestroyed()
        if (wakeLock.isHeld) wakeLock.release()
        scope.cancel()
        super.onDestroy()
    }

    private fun notification(phase: RecordingPhase, pausePending: Boolean = false): Notification {
        val paused = phase == RecordingPhase.PAUSED
        val title =
            when (phase) {
                RecordingPhase.PAUSED -> "Запись приостановлена"
                RecordingPhase.RECORDING -> "Запись продолжается"
                RecordingPhase.FINALIZING -> "Завершаем запись"
                RecordingPhase.INTERRUPTED -> "Запись прервана · Останавливаем микрофон"
                else -> "Подключаем микрофон…"
            }
        val builder =
            NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_bootstrap_mic)
                .setContentTitle(if (pausePending) "Приостанавливаем запись…" else title)
                .setContentText("DORA · Только на устройстве")
                .setContentIntent(activityIntent(OPEN, 0))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        if (phase == RecordingPhase.RECORDING || paused) {
            val action =
                if (paused) activityIntent(RESUME, 1)
                else
                    PendingIntent.getService(
                        this,
                        1,
                        bind(Intent(this, ProductRecordingService::class.java).setAction(PAUSE)),
                        FLAGS,
                    )
            builder.addAction(
                R.drawable.ic_bootstrap_mic,
                if (paused) "Продолжить" else "Пауза",
                action,
            )
            builder.addAction(R.drawable.ic_bootstrap_mic, "Стоп", activityIntent(STOP, 2))
        }
        return builder.build()
    }

    private fun activityIntent(action: String, code: Int) =
        PendingIntent.getActivity(
            this,
            code,
            bind(
                Intent(this, MainActivity::class.java)
                    .setAction(action)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            ),
            FLAGS,
        )

    private fun bind(intent: Intent): Intent =
        intent
            .putExtra(TOKEN, controller.actionToken)
            .setData(
                Uri.parse("dora-recording://action/${controller.actionToken}/${intent.action}")
            )

    companion object {
        const val OPEN = "com.monumentogram.dora.recording.OPEN"
        const val STOP = "com.monumentogram.dora.recording.STOP_CONFIRM"
        const val RESUME = "com.monumentogram.dora.recording.RESUME"
        const val TOKEN = "recording_action_token"
        private const val START = "com.monumentogram.dora.recording.START"
        private const val PAUSE = "com.monumentogram.dora.recording.PAUSE"
        private const val CHANNEL = "product_recording"
        private const val NOTIFICATION_ID = 8301
        private const val FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        private const val WAKE_TIMEOUT_MILLIS = 60_000L
        private const val WAKE_RENEW_MILLIS = 30_000L

        fun startIntent(context: Context, token: String) =
            Intent(context, ProductRecordingService::class.java)
                .setAction(START)
                .putExtra(TOKEN, token)
    }
}
