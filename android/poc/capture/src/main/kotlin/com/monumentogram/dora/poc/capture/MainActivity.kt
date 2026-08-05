package com.monumentogram.dora.poc.capture

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat
import com.monumentogram.dora.poc.capture.ui.CaptureApp
import com.monumentogram.dora.poc.capture.ui.CaptureTheme

class MainActivity : ComponentActivity() {
    private val controller
        get() = (application as CaptureApplication).controller

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            controller.startAfterPermissionResult(
                result.values.isNotEmpty() && result.values.all { it }
            )
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CaptureTheme {
                LaunchedEffect(controller) {
                    controller.shareRequests.collect { intent ->
                        startActivity(Intent.createChooser(intent, "Передать безопасный отчёт"))
                    }
                }
                CaptureApp(controller = controller, onExplicitStart = ::requestCapturePermissions)
            }
        }
    }

    private fun requestCapturePermissions() {
        if (!controller.requestStart()) return
        val permissions = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        val alreadyGranted = permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        if (alreadyGranted) controller.startAfterPermissionResult(true)
        else permissionLauncher.launch(permissions.toTypedArray())
    }
}
