@file:Suppress("TooGenericExceptionCaught") // Service boundary must always release the microphone.

package com.monumentogram.dora.poc.capture.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.monumentogram.dora.poc.capture.CaptureApplication
import com.monumentogram.dora.poc.capture.MainActivity
import com.monumentogram.dora.poc.capture.R
import com.monumentogram.dora.poc.capture.model.RunKind
import com.monumentogram.dora.poc.capture.runtime.IdempotentStopGate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class CaptureService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val stopGate = IdempotentStopGate()
    private var startJob: Job? = null
    private var captureStarted = false

    private val controller
        get() = (application as CaptureApplication).controller

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        when (intent.action) {
            ACTION_START -> handleStart(intent)
            ACTION_STOP -> handleStop(abort = false)
            ACTION_ABORT -> handleStop(abort = true)
            else -> stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        if (captureStarted && !stopGate.isRequested()) controller.unexpectedServiceDestroyed()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun handleStart(intent: Intent) {
        if (captureStarted || startJob?.isActive == true || stopGate.isRequested()) return
        val run = RunKind.fromId(intent.getStringExtra(EXTRA_RUN))
        val runId = intent.getStringExtra(EXTRA_RUN_ID)
        val fixtureEnabled = intent.getBooleanExtra(EXTRA_FIXTURE, false)
        if (run == null || runId == null || !SAFE_RUN_ID.matches(runId)) {
            controller.serviceFailure("Foreground service получил недопустимые параметры теста.")
            stopSelf()
            return
        }
        startMicrophoneForeground(buildNotification(run))
        startJob = serviceScope.launch {
            runCatching { controller.beginServiceCapture(run, runId, fixtureEnabled) }
                .onSuccess { captureStarted = true }
                .onFailure {
                    controller.serviceFailure(
                        "Microphone foreground service не начал запись: ${it.javaClass.simpleName}"
                    )
                    ServiceCompat.stopForeground(
                        this@CaptureService,
                        ServiceCompat.STOP_FOREGROUND_REMOVE,
                    )
                    stopSelf()
                }
        }
    }

    private fun handleStop(abort: Boolean) {
        if (!stopGate.request()) return
        serviceScope.launch {
            startJob?.join()
            try {
                if (captureStarted) controller.finishServiceCapture(abort)
            } catch (error: Throwable) {
                controller.serviceFailure(
                    "Безопасная остановка завершилась ошибкой: ${error.javaClass.simpleName}"
                )
            } finally {
                captureStarted = false
                ServiceCompat.stopForeground(
                    this@CaptureService,
                    ServiceCompat.STOP_FOREGROUND_REMOVE,
                )
                stopSelf()
            }
        }
    }

    private fun startMicrophoneForeground(notification: Notification) {
        val microphoneType =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            } else {
                0
            }
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            microphoneType,
        )
    }

    private fun buildNotification(run: RunKind): Notification {
        val contentIntent =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val stopIntent =
            PendingIntent.getService(
                this,
                1,
                stopIntent(this, abort = false),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_poc_mic)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText("${run.title} · ${getString(R.string.notification_text)}")
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(
                R.drawable.ic_poc_mic,
                getString(R.string.notification_stop),
                stopIntent,
            )
            .build()
    }

    private fun createNotificationChannel() {
        val channel =
            NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                )
                .apply {
                    description = getString(R.string.notification_channel_description)
                    setShowBadge(false)
                }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val ACTION_START = "com.monumentogram.dora.poc.capture.action.START"
        private const val ACTION_STOP = "com.monumentogram.dora.poc.capture.action.STOP"
        private const val ACTION_ABORT = "com.monumentogram.dora.poc.capture.action.ABORT"
        private const val EXTRA_RUN = "run"
        private const val EXTRA_RUN_ID = "run_id"
        private const val EXTRA_FIXTURE = "fixture"
        private const val CHANNEL_ID = "capture_poc_recording"
        private const val NOTIFICATION_ID = 1_170_001
        private val SAFE_RUN_ID = Regex("^run-[abc]-[a-zA-Z0-9-]{10,80}$")

        fun startIntent(
            context: Context,
            run: RunKind,
            runId: String,
            fixtureEnabled: Boolean,
        ): Intent =
            Intent(context, CaptureService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_RUN, run.id)
                .putExtra(EXTRA_RUN_ID, runId)
                .putExtra(EXTRA_FIXTURE, fixtureEnabled)

        fun stopIntent(context: Context, abort: Boolean): Intent =
            Intent(context, CaptureService::class.java)
                .setAction(if (abort) ACTION_ABORT else ACTION_STOP)
    }
}
