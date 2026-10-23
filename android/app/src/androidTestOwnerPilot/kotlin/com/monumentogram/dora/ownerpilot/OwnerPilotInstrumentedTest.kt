@file:Suppress("DEPRECATION")

package com.monumentogram.dora.ownerpilot

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.app.ActivityManager
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.os.Process
import android.os.UserManager
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.view.Choreographer
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.monumentogram.dora.DoraApplication
import com.monumentogram.dora.MainActivity
import com.monumentogram.dora.audio.AudioAvailability
import com.monumentogram.dora.audio.AudioFailure
import com.monumentogram.dora.audio.AudioFormat
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.AudioOpenMode
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.AudioSourceState
import com.monumentogram.dora.audio.AudioStorageUnitIdentity
import com.monumentogram.dora.audio.ProductAudioSession
import com.monumentogram.dora.audio.recording.RecordingAccess
import com.monumentogram.dora.audio.recording.RecordingPhase
import com.monumentogram.dora.audio.recording.RecordingResumeGrant
import com.monumentogram.dora.model.alpha.AudioAssetId
import com.monumentogram.dora.model.alpha.RecordingId
import com.monumentogram.dora.poc.recovery.contract.RecoveryRelativeNames
import com.monumentogram.dora.recording.ProductRecordingService
import com.monumentogram.dora.recording.RecordingRecoveryCard
import com.monumentogram.dora.recording.SavedRecordingVerification
import com.monumentogram.dora.ui.theme.DoraBootstrapTheme
import java.io.File
import java.io.RandomAccessFile
import java.security.KeyStore
import java.util.Collections
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.crypto.KeyGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@Suppress(
    "MagicNumber",
    "LongMethod",
    "TooManyFunctions",
    "LargeClass",
) // PIN_FREE_LOCAL_ACCESS fixture and bounded end-to-end lifecycle.
class OwnerPilotInstrumentedTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val arguments = InstrumentationRegistry.getArguments()
    private val runtime
        get() = (context.applicationContext as DoraApplication).audioRuntime

    @Test
    fun noSystemCredentialCanOpenEncryptedVault() {
        assertEquals(0, processUser())
        assertFalse(context.getSystemService(KeyguardManager::class.java).isDeviceSecure)
        val vault = File(context.noBackupFilesDir, "dora-vault-v1")
        assertFalse(vault.exists())
        val host = launch()
        try {
            awaitCallerResumed(host)
            val done = CountDownLatch(1)
            var result: AudioAvailability? = null
            instrumentation.runOnMainSync {
                runtime.requestOpen(host, AudioOpenMode.CREATE_NEW) {
                    result = it
                    done.countDown()
                }
            }
            assertTrue(done.await(15, TimeUnit.SECONDS))
            assertTrue(result is AudioAvailability.Available)
            assertTrue(vault.exists())
        } finally {
            instrumentation.runOnMainSync {
                runtime.lock()
                host.finish()
            }
        }
    }

    @Test
    fun seedRestartEncryptedRecording() {
        val host = launch()
        try {
            val session = openLocal(host)
            val audio = restartIdentity()
            success(session.writer.createLogicalRecording(audio))
            success(
                session.writer.append(
                    AudioStorageUnitIdentity(audio, id(), 0, 0),
                    AudioFormat.PCM,
                    ByteArray(640) { 11 },
                )
            )
            success(session.writer.finalize(audio))
            verified(host, session, audio, 320, 1)
        } finally {
            instrumentation.runOnMainSync {
                runtime.lock()
                host.finish()
            }
        }
    }

    @Test
    fun restartReadbackWithoutAutomaticCapture() {
        val app = context.applicationContext as DoraApplication
        assertFalse(app.recording.state.value.captureThreadHealthy)
        assertEquals(null, app.recording.actionToken)
        assertTrue(
            context
                .getSystemService(ActivityManager::class.java)
                .getRunningServices(Int.MAX_VALUE)
                .none { it.service.className == ProductRecordingService::class.java.name }
        )
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            try {
                val host = settledForegroundHost(scenario)
                verified(host, openLocal(host), restartIdentity(), 320, 1)
                assertNoCredentialPrompt()
            } finally {
                instrumentation.runOnMainSync { runtime.lock() }
            }
        }
    }

    /** Observe the current foreground caller before one action; never retry revoked access. */
    private fun settledForegroundHost(scenario: ActivityScenario<MainActivity>): MainActivity {
        assertNoCredentialPrompt()
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
        var previous: MainActivity? = null
        var previousConfiguration: Configuration? = null
        while (System.nanoTime() < deadline) {
            ui.waitForIdle()
            val frame = CountDownLatch(1)
            instrumentation.runOnMainSync {
                Choreographer.getInstance().postFrameCallback { frame.countDown() }
            }
            assertTrue("Restart fixture frame timed out", frame.await(5, TimeUnit.SECONDS))
            instrumentation.waitForIdleSync()
            var current: MainActivity? = null
            var configuration: Configuration? = null
            scenario.onActivity { host ->
                if (localCallerState(host) == "1110000") {
                    current = host
                    configuration = Configuration(host.resources.configuration)
                }
            }
            if (current != null && current === previous && configuration == previousConfiguration) {
                return checkNotNull(current)
            }
            previous = current
            previousConfiguration = configuration
        }
        throw AssertionError("Restart fixture configuration did not settle")
    }

    private fun restartIdentity() =
        AudioIdentity(
            RecordingId("c2383106-6d99-4460-a3fb-1b73ce452201"),
            AudioAssetId("c2383106-6d99-4460-a3fb-1b73ce452202"),
            "c2383106-6d99-4460-a3fb-1b73ce452203",
        )

    @Test
    fun packageManifestAndReleaseOverridesAreClosed() {
        assertEquals("com.monumentogram.dora.alpha.pilot", context.packageName)
        val info =
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_ACTIVITIES or
                    PackageManager.GET_SERVICES or
                    PackageManager.GET_RECEIVERS or
                    PackageManager.GET_PROVIDERS or
                    PackageManager.GET_PERMISSIONS or
                    PackageManager.GET_INSTRUMENTATION,
            )
        assertEquals(3L, info.longVersionCode)
        assertEquals("0.1.0-owner-alpha.3", info.versionName)
        assertEquals(null, info.sharedUserId)
        assertEquals(Process.myUid(), context.applicationInfo.uid)
        assertEquals(0, context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE)
        assertEquals(0, context.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
        assertTrue(info.instrumentation.isNullOrEmpty())
        assertEquals(
            listOf(MainActivity::class.java.name),
            info.activities.orEmpty().filter { it.exported }.map { it.name },
        )
        assertTrue(info.services.orEmpty().none { it.exported })
        assertTrue(
            info.receivers.orEmpty().all {
                !it.exported ||
                    (it.name == "androidx.profileinstaller.ProfileInstallReceiver" &&
                        it.permission == "android.permission.DUMP")
            }
        )
        assertTrue(info.providers.orEmpty().none { it.exported })
        assertTrue(
            info.requestedPermissions.orEmpty().none {
                it in
                    setOf(
                        "android.permission.INTERACT_ACROSS_USERS",
                        "android.permission.INTERACT_ACROSS_USERS_FULL",
                        "android.permission.MANAGE_USERS",
                        "android.permission.INTERNET",
                    )
            }
        )
        for (forbidden in
            listOf(
                "com.monumentogram.dora.operator.IsolatedAlphaOperatorActivity",
                "com.monumentogram.dora.operator.IsolatedAlphaOperator",
            )) {
            var absent = false
            try {
                Class.forName(forbidden, false, context.classLoader)
            } catch (_: ClassNotFoundException) {
                absent = true
            }
            assertTrue("Diagnostic entrypoint must be absent from actual pilot DEX", absent)
        }
        assertReleaseOverridesDenyAllInputs(context)
    }

    @Test
    fun realSecondaryRuntimeAuthenticatedReadbackDeletionAndReopen() {
        val expectedUser = checkNotNull(arguments.getString("pilotUserId")).toInt()
        val expectedSerial = checkNotNull(arguments.getString("pilotUserSerial")).toLong()
        assertTrue(expectedUser > 0)
        val configuration = context.resources.configuration
        val configurationEvidence =
            "PILOT_UI_CONFIGURATION width_dp=${configuration.screenWidthDp} " +
                "font_scale_permille=${(configuration.fontScale * 1000).toInt()}"
        assertEquals(configurationEvidence, 360, configuration.screenWidthDp)
        assertTrue(configurationEvidence, configuration.fontScale >= 2.0f)
        assertEquals(expectedUser, processUser())
        assertEquals(
            expectedSerial,
            context
                .getSystemService(UserManager::class.java)
                .getSerialNumberForUser(Process.myUserHandle()),
        )
        val automation = instrumentation.uiAutomation
        val previous = automation.serviceInfo
        var scenario: ActivityScenario<MainActivity>? = null
        try {
            automation.serviceInfo =
                automation.serviceInfo.apply {
                    flags =
                        flags or
                            AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                            AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                }
            SyntheticSystemKeyguard(
                    instrumentation,
                    { checkNotNull(arguments.getString("syntheticAuthPin")) },
                    ::node,
                )
                .unlockIfNeeded()
            val ownedScenario = ActivityScenario.launch(MainActivity::class.java)
            scenario = ownedScenario
            var activity = settledForegroundHost(ownedScenario)
            firstRunRequiresAcknowledgementAndMicrophonePermission()
            val first = openLocal(activity)
            val selected = identity()
            val unit = AudioStorageUnitIdentity(selected, id(), 0, 0)
            success(first.writer.createLogicalRecording(selected))
            success(
                first.writer.append(unit, AudioFormat.PCM, ByteArray(320) { (it * 17).toByte() })
            )
            // A durable partial source cannot be reported as a verified finalized original.
            assertTrue(SavedRecordingVerification.verify(first, selected) is AudioResult.Failed)
            assertTrue(
                first.writer.append(
                    unit.copy(
                        unitId = id(),
                        ordinal = 1,
                        firstFrame = 161,
                        sourceFrameOffset = 161,
                    ),
                    AudioFormat.PCM,
                    ByteArray(320),
                ) is AudioResult.Failed
            )
            success(
                first.writer.append(
                    unit.copy(
                        unitId = id(),
                        ordinal = 1,
                        firstFrame = 160,
                        sourceFrameOffset = 160,
                    ),
                    AudioFormat.PCM,
                    ByteArray(320) { 3 },
                )
            )
            success(first.writer.finalize(selected))
            verified(activity, first, selected, 320, 2)
            assertTrue(
                SavedRecordingVerification.verify(first, selected.copy(sessionId = id()))
                    is AudioResult.Failed
            )
            var borrowed: ByteArray? = null
            success(first.reader.extract(selected) { _, bytes -> borrowed = bytes })
            assertTrue(checkNotNull(borrowed).all { it == 0.toByte() })
            instrumentation.runOnMainSync { runtime.lock() }
            assertNoPlaintext(first, selected)
            assertNoPlaintext(first, selected)
            val second = openLocal(activity)
            assertNoPlaintext(first, selected)
            verified(activity, second, selected, 320, 2)
            // Actual OS background revocation, followed by a fresh foreground action.
            shell("input keyevent ${KeyEvent.KEYCODE_HOME}")
            awaitLocked()
            assertNoPlaintext(second, selected)
            instrumentation.runOnMainSync {
                activity.startActivity(
                    Intent(activity.intent).setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                )
            }
            activity = settledForegroundHost(ownedScenario)
            val third = openLocal(activity)
            verified(activity, third, selected, 320, 2)
            screenOff()
            awaitLocked()
            assertNoPlaintext(third, selected)
            awaitSystemKeyguardLocked()
            shell("input keyevent ${KeyEvent.KEYCODE_WAKEUP}")
            SyntheticSystemKeyguard(
                    instrumentation,
                    { checkNotNull(arguments.getString("syntheticAuthPin")) },
                    ::node,
                )
                .unlockIfNeeded()
            activity = settledForegroundHost(ownedScenario)
            assertNoPlaintext(third, selected)
            // Exercise replacement deterministically even when this OS did not recreate on wake.
            val oldActivity = activity
            ownedScenario.recreate()
            activity = settledForegroundHost(ownedScenario)
            assertTrue(oldActivity.isDestroyed)
            assertTrue(oldActivity !== activity)
            val rejected = CountDownLatch(1)
            var oldCallerResult: AudioAvailability? = null
            instrumentation.runOnMainSync {
                runtime.requestOpen(oldActivity, AudioOpenMode.OPEN_EXISTING) {
                    oldCallerResult = it
                    rejected.countDown()
                }
            }
            assertTrue(rejected.await(60, TimeUnit.SECONDS))
            assertEquals(AudioAvailability.Locked, oldCallerResult)
            assertNoPlaintext(third, selected)
            val afterWake = openLocal(activity)
            assertNoPlaintext(third, selected)
            verified(activity, afterWake, selected, 320, 2)
            exerciseProductUi(selected)
            val fourth = openLocal(activity)
            assertEquals(
                AudioResult.Value(AudioSourceState.UserDeleted),
                fourth.sourceState(selected),
            )
            val inspection = fourth.inspectAudioDeletion(selected)
            assertTrue(inspection is AudioResult.Value && inspection.value.complete)
            assertNoPlaintext(fourth, selected)
            val afterPresentation = exerciseStaleRecoveryPresentation(activity, fourth)
            val corrupted = identity()
            val damaged = AudioStorageUnitIdentity(corrupted, id(), 0, 0)
            success(afterPresentation.writer.createLogicalRecording(corrupted))
            success(
                afterPresentation.writer.append(damaged, AudioFormat.PCM, ByteArray(320) { 11 })
            )
            success(afterPresentation.writer.finalize(corrupted))
            val encrypted =
                File(
                    context.noBackupFilesDir,
                    "dora-vault-v1/poc-recovery/v1/runs/${damaged.unitId}/" +
                        RecoveryRelativeNames.microfileCiphertext(0UL),
                )
            RandomAccessFile(encrypted, "rw").use { file ->
                file.seek(16)
                val byte = file.readByte()
                file.seek(16)
                file.writeByte(byte.toInt() xor 1)
            }
            assertTrue(
                SavedRecordingVerification.verify(afterPresentation, corrupted)
                    is AudioResult.Failed
            )
            exerciseRecordingAuthority(activity, ownedScenario)
        } finally {
            automation.serviceInfo = previous
            instrumentation.runOnMainSync {
                runtime.lock()
            }
            scenario?.close()
        }
    }

    private fun firstRunRequiresAcknowledgementAndMicrophonePermission() {
        val app = context.applicationContext as DoraApplication
        val vault = File(context.noBackupFilesDir, "dora-vault-v1")
        val keys = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val initialAliases = Collections.list(keys.aliases()).toSet()
        assertEquals(
            PackageManager.PERMISSION_DENIED,
            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO),
        )
        assertFalse(vault.exists())
        assertTrue(runtime.isRecordingUiAuthorized())
        ui.onNodeWithContentDescription("Открыть экран записи").performClick()
        ui.onNodeWithText("Начать запись").assertIsDisplayed().assertIsNotEnabled()
        ui.onNodeWithContentDescription("Я предупредил(а) участников о записи")
            .performScrollTo()
            .assertIsOff()
            .performClick()
        ui.onNodeWithText("Начать запись").assertIsDisplayed().assertIsEnabled().performClick()
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
        val denialButtons =
            mapOf(
                "com.android.packageinstaller" to
                    "com.android.packageinstaller:id/permission_deny_button",
                "com.google.android.packageinstaller" to
                    "com.android.packageinstaller:id/permission_deny_button",
                "com.android.permissioncontroller" to
                    "com.android.permissioncontroller:id/permission_deny_button",
                "com.google.android.permissioncontroller" to
                    "com.android.permissioncontroller:id/permission_deny_button",
            )
        var deny: AccessibilityNodeInfo? = null
        while (deny == null && System.nanoTime() < deadline) {
            deny = node {
                val resource = denialButtons[it.packageName?.toString()]
                resource != null &&
                    it.viewIdResourceName == resource &&
                    it.isVisibleToUser &&
                    it.isEnabled &&
                    it.isClickable
            }
            if (deny == null) Thread.sleep(100)
        }
        assertTrue(
            "Microphone denial must use the real Android permission dialog",
            checkNotNull(deny).performAction(AccessibilityNodeInfo.ACTION_CLICK),
        )
        waitText("Микрофон не разрешён. Запись не началась.")
        ui.onNodeWithText("Микрофон не разрешён. Запись не началась.")
            .performScrollTo()
            .assertIsDisplayed()
        assertEquals(
            PackageManager.PERMISSION_DENIED,
            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO),
        )
        val state = app.recording.state.value
        assertEquals(RecordingPhase.PREFLIGHT, state.recording.phase)
        assertEquals(0L, state.recording.frames)
        assertEquals(0L, state.recording.durableFrames)
        assertFalse(state.captureThreadHealthy)
        assertEquals(null, app.recording.actionToken)
        assertTrue(
            "Permission denial must never start the microphone service",
            context
                .getSystemService(ActivityManager::class.java)
                .getRunningServices(Int.MAX_VALUE)
                .none { it.service.className == ProductRecordingService::class.java.name },
        )
        assertTrue(runtime.isRecordingUiAuthorized())
        assertTrue(runtime.availability !is AudioAvailability.Available)
        assertFalse(vault.exists())
        assertTrue(
            "Permission denial must not create vault keys",
            initialAliases == Collections.list(keys.aliases()).toSet(),
        )
        ui.onNodeWithText("Отмена").assertIsDisplayed().performClick()
        ui.onNodeWithContentDescription("Открыть экран записи").assertIsDisplayed()
    }

    private fun verified(
        host: Activity,
        session: ProductAudioSession,
        identity: AudioIdentity,
        frames: Long,
        units: Int,
    ) {
        var before = ""
        instrumentation.runOnMainSync { before = localCallerState(host) }
        val result = SavedRecordingVerification.verify(session, identity)
        val code = (result as? AudioResult.Failed)?.reason?.name ?: "NOT_FAILED"
        assertTrue(
            "Authenticated readback failed: $code caller=" +
                callerDiagnostics(host, before[0] == '1', before[1] == '1'),
            result is AudioResult.Value,
        )
        val value = (result as AudioResult.Value).value
        assertEquals(identity, value.identity)
        assertEquals(frames, value.frames)
        assertEquals(units, value.units)
    }

    private fun assertNoPlaintext(session: ProductAudioSession, identity: AudioIdentity) {
        var callbacks = 0
        assertTrue(session.reader.extract(identity) { _, _ -> callbacks++ } is AudioResult.Failed)
        assertEquals(0, callbacks)
        assertTrue(SavedRecordingVerification.verify(session, identity) is AudioResult.Failed)
    }

    private fun exerciseProductUi(identity: AudioIdentity) {
        ui.onNodeWithContentDescription("Открыть экран записи").performClick()
        loadSaved(identity)
        ui.onNodeWithText("Сохранено кадров: 320").performScrollTo().assertIsDisplayed()
        actWithoutPrompt { ui.onNodeWithText("Проверить аудио").performScrollTo().performClick() }
        waitText("Аудио проверено: 320 кадров · 2 блоков")
        loadSaved(identity)
        actWithoutPrompt {
            ui.onNodeWithText("Удалить аудио…").performScrollTo().assertIsEnabled().performClick()
        }
        clickAudioDeletionDialog("Cancel")
        waitText("Удаление не подтверждено: CANCELLED")
        loadSaved(identity)
        actWithoutPrompt { ui.onNodeWithText("Проверить аудио").performScrollTo().performClick() }
        waitText("Аудио проверено: 320 кадров · 2 блоков")
        loadSaved(identity)
        actWithoutPrompt {
            ui.onNodeWithText("Удалить аудио…").performScrollTo().assertIsEnabled().performClick()
        }
        clickAudioDeletionDialog("Delete audio")
        waitText("Аудио удалено. Удаление проверено.")
    }

    private fun loadSaved(identity: AudioIdentity) {
        waitText("Проверить сохранённые записи")
        actWithoutPrompt {
            ui.onNodeWithText("Проверить сохранённые записи")
                .performScrollTo()
                .assertIsEnabled()
                .performClick()
        }
        // An existing row remains visible while its asynchronous reload runs.
        // Wait for completion, not the stale row, before clicking a disabled action.
        ui.waitUntil(60_000) {
            ui.onAllNodes(hasText("Проверить сохранённые записи") and isEnabled())
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        waitText("Идентификатор записи: ${identity.recordingId.value}")
    }

    private fun exerciseStaleRecoveryPresentation(
        host: Activity,
        initial: ProductAudioSession,
    ): ProductAudioSession {
        val selected = identity()
        success(initial.writer.createLogicalRecording(selected))
        success(
            initial.writer.append(
                AudioStorageUnitIdentity(selected, id(), 0, 0),
                AudioFormat.PCM,
                ByteArray(640) { 9 },
            )
        )
        success(initial.writer.finalize(selected))
        val presentedAuthorization = mutableStateOf(true)
        val activity = host as MainActivity
        instrumentation.runOnMainSync {
            activity.setContent {
                DoraBootstrapTheme {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        RecordingRecoveryCard(activity, presentedAuthorization.value)
                    }
                }
            }
        }
        loadSaved(selected)
        actWithoutPrompt {
            ui.onNode(hasText("Проверить аудио") and isEnabled()).performScrollTo().performClick()
        }
        val verification = "Аудио проверено: 320 кадров · 1 блоков"
        waitText(verification)
        val current = checkNotNull(runtime.availability as? AudioAvailability.Available).session
        awaitCallerResumed(activity)
        assertTrue(runtime.isRecordingUiAuthorized())
        // Deliver only a stale UI snapshot; real foreground authority stays unchanged.
        ui.runOnIdle { presentedAuthorization.value = false }
        ui.waitForIdle()
        assertTrue(runtime.isRecordingUiAuthorized())
        assertTrue((runtime.availability as? AudioAvailability.Available)?.session === current)
        ui.runOnIdle { presentedAuthorization.value = true }
        ui.waitForIdle()
        assertTrue(runtime.isRecordingUiAuthorized())
        assertTrue((runtime.availability as? AudioAvailability.Available)?.session === current)
        ui.onNodeWithText(verification).assertExists()
        ui.runOnIdle {
            runtime.lock()
            presentedAuthorization.value = false
        }
        ui.waitForIdle()
        assertFalse(runtime.isRecordingUiAuthorized())
        assertNoPlaintext(current, selected)
        ui.onNodeWithText(verification).assertDoesNotExist()
        ui.runOnIdle { presentedAuthorization.value = true }
        ui.waitForIdle()
        assertFalse(runtime.isRecordingUiAuthorized())
        ui.onNodeWithText(verification).assertDoesNotExist()
        ui.runOnIdle { presentedAuthorization.value = false }
        ui.waitForIdle()
        val reopened = openLocal(activity)
        ui.runOnIdle { presentedAuthorization.value = true }
        ui.waitForIdle()
        ui.onNodeWithText(verification).assertDoesNotExist()
        return reopened
    }

    /**
     * Real recording authority with synthetic PCM; no claim of microphone or private VAD evidence.
     */
    private fun exerciseRecordingAuthority(
        host: Activity,
        scenario: ActivityScenario<MainActivity>,
    ) {
        val selected = identity()
        val done = CountDownLatch(1)
        var opened: AudioResult<RecordingAccess>? = null
        actWithoutPrompt {
            instrumentation.runOnMainSync {
                runtime.requestRecording(host, selected) {
                    opened = it
                    done.countDown()
                }
            }
        }
        assertTrue(done.await(60, TimeUnit.SECONDS))
        success(checkNotNull(opened))
        val access = (opened as AudioResult.Value).value
        var starts = 0
        try {
            instrumentation.runOnMainSync { access.activate { starts++ } }
            success(access.writer.createLogicalRecording(selected))
            success(
                access.writer.append(
                    AudioStorageUnitIdentity(selected, id(), 0, 0),
                    AudioFormat.PCM,
                    ByteArray(320) { 5 },
                )
            )
            assertEquals(
                AudioResult.Failed(AudioFailure.INVALID_INPUT),
                access.writer.append(
                    AudioStorageUnitIdentity(selected, id(), 1, 4_800_000),
                    AudioFormat.PCM,
                    ByteArray(2),
                ),
            )
            var stale: AudioResult<RecordingResumeGrant>? = null
            var unusedUntilWake: AudioResult<RecordingResumeGrant>? = null
            instrumentation.runOnMainSync {
                runtime.requestRecordingResume(host, access) { stale = it }
                runtime.requestRecordingResume(host, access) { unusedUntilWake = it }
            }
            success(checkNotNull(stale))
            success(checkNotNull(unusedUntilWake))
            screenOff()
            awaitLocked()
            var denied = false
            try {
                (stale as AudioResult.Value).value.consume { starts++ }
            } catch (_: IllegalStateException) {
                denied = true
            }
            assertTrue("A pre-lock resume grant must never start capture", denied)
            assertEquals(1, starts)
            // Screen-off retains the admitted writer but gives no new microphone-start authority.
            success(
                access.writer.append(
                    AudioStorageUnitIdentity(
                        selected,
                        id(),
                        1,
                        160,
                    ),
                    AudioFormat.PCM,
                    ByteArray(320) { 6 },
                )
            )
            awaitSystemKeyguardLocked()
            shell("input keyevent ${KeyEvent.KEYCODE_WAKEUP}")
            SyntheticSystemKeyguard(
                    instrumentation,
                    { checkNotNull(arguments.getString("syntheticAuthPin")) },
                    ::node,
                )
                .unlockIfNeeded()
            val resumedHost = settledForegroundHost(scenario)
            var staleAfterWakeDenied = false
            instrumentation.runOnMainSync {
                try {
                    (unusedUntilWake as AudioResult.Value).value.consume { starts++ }
                } catch (_: IllegalStateException) {
                    staleAfterWakeDenied = true
                }
            }
            assertTrue(
                "An unused pre-lock grant must stay revoked after wake",
                staleAfterWakeDenied,
            )
            assertEquals(1, starts)
            val resumed = CountDownLatch(1)
            var grant: AudioResult<RecordingResumeGrant>? = null
            var prompted = false
            actWithoutPrompt {
                instrumentation.runOnMainSync {
                    runtime.requestRecordingResume(resumedHost, access, { prompted = it }) {
                        grant = it
                        resumed.countDown()
                    }
                }
            }
            assertFalse(prompted)
            assertEquals(1, starts)
            assertTrue(resumed.await(60, TimeUnit.SECONDS))
            success(checkNotNull(grant))
            instrumentation.runOnMainSync {
                (grant as AudioResult.Value).value.consume { starts++ }
            }
            assertEquals(2, starts)
            success(access.writer.finalize(selected))
        } finally {
            access.close()
        }
    }

    private fun waitText(text: String) {
        ui.waitUntil(60_000) { ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun actWithoutPrompt(action: () -> Unit) {
        action()
        instrumentation.waitForIdleSync()
        assertNoCredentialPrompt()
    }

    private fun assertNoCredentialPrompt() {
        assertEquals(null, node { it.isPassword && it.isEditable && it.isVisibleToUser })
        ui.onNodeWithText("Разблокировать").assertDoesNotExist()
        ui.onNodeWithText("Разблокировать сохранённые записи").assertDoesNotExist()
    }

    @Suppress(
        "CyclomaticComplexMethod"
    ) // Match the exact app dialog, never a system credential button.
    private fun clickAudioDeletionDialog(text: String) {
        val resource =
            when (text) {
                "Cancel" -> "android:id/button2"
                "Delete audio" -> "android:id/button1"
                else -> error("Unsupported audio deletion action")
            }
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
        var button: AccessibilityNodeInfo? = null
        while (button == null && System.nanoTime() < deadline) {
            val title = node {
                it.packageName?.toString() == context.packageName &&
                    it.viewIdResourceName == "android:id/alertTitle" &&
                    it.text?.toString() == "Delete audio" &&
                    it.isVisibleToUser
            }
            if (title != null) {
                button = node {
                    it.packageName?.toString() == context.packageName &&
                        it.windowId == title.windowId &&
                        it.viewIdResourceName == resource &&
                        it.text?.toString()?.equals(text, ignoreCase = true) == true &&
                        it.isVisibleToUser &&
                        it.isEnabled &&
                        it.isClickable
                }
            }
            if (button == null) Thread.sleep(100)
        }
        assertTrue(checkNotNull(button).performAction(AccessibilityNodeInfo.ACTION_CLICK))
    }

    private fun awaitLocked() {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
        while (runtime.isRecordingUiAuthorized() && System.nanoTime() < deadline) Thread.sleep(100)
        assertFalse(runtime.isRecordingUiAuthorized())
    }

    private fun screenOff() {
        val delivered = CountDownLatch(1)
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action == Intent.ACTION_SCREEN_OFF) delivered.countDown()
                }
            }
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else context.registerReceiver(receiver, filter)
        try {
            shell("input keyevent ${KeyEvent.KEYCODE_SLEEP}")
            assertTrue("SCREEN_OFF delivery timed out", delivered.await(15, TimeUnit.SECONDS))
            instrumentation.waitForIdleSync()
            assertFalse(context.getSystemService(PowerManager::class.java).isInteractive)
        } finally {
            context.unregisterReceiver(receiver)
        }
    }

    private fun awaitSystemKeyguardLocked() {
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        val secure = keyguard.isDeviceSecure
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
        // The no-PIN swipe keyguard also appears asynchronously. Do not wake while
        // its earlier screen-off transition is still pending.
        while (System.nanoTime() < deadline) {
            if (keyguard.isKeyguardLocked && (!secure || keyguard.isDeviceLocked)) break
            Thread.sleep(100)
        }
        assertTrue("Screen-off keyguard must finish showing before wake", keyguard.isKeyguardLocked)
        assertEquals(secure, keyguard.isDeviceLocked)
        assertFalse(context.getSystemService(PowerManager::class.java).isInteractive)
    }

    private fun shell(command: String) {
        ParcelFileDescriptor.AutoCloseInputStream(
                instrumentation.uiAutomation.executeShellCommand(command)
            )
            .use { it.readBytes() }
    }

    private fun launch(): Activity =
        instrumentation.startActivitySync(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )

    private fun processUser() = Process.myUid() / 100_000

    @Test
    fun seedOwnerIsolationSentinel() {
        assertEquals(0, processUser())
        File(context.noBackupFilesDir, SENTINEL).writeText(SENTINEL_VALUE)
        val keys = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!keys.containsAlias(SENTINEL_ALIAS)) {
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
                init(
                    KeyGenParameterSpec.Builder(
                            SENTINEL_ALIAS,
                            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                        )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .build()
                )
                generateKey()
            }
        }
        assertOwnerSentinel()
    }

    @Test
    fun secondaryCannotReadOwnerSentinel() {
        assertTrue(processUser() > 0)
        assertEquals("com.monumentogram.dora.alpha.pilot", context.packageName)
        val owner = File("/data/user/0/${context.packageName}/no_backup/$SENTINEL")
        var denied = false
        try {
            owner.inputStream().use { it.read() }
        } catch (_: java.io.IOException) {
            denied = true
        } catch (_: SecurityException) {
            denied = true
        }
        assertTrue("Secondary UID cannot read the seeded owner file", denied)
        assertFalse(
            KeyStore.getInstance("AndroidKeyStore")
                .apply { load(null) }
                .containsAlias(SENTINEL_ALIAS)
        )
    }

    @Test
    fun verifyOwnerIsolationSentinelUnchanged() {
        assertOwnerSentinel()
        assertTrue(File(context.noBackupFilesDir, SENTINEL).delete())
        val keys = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        keys.deleteEntry(SENTINEL_ALIAS)
        assertFalse(keys.containsAlias(SENTINEL_ALIAS))
        assertFalse(File(context.noBackupFilesDir, SENTINEL).exists())
    }

    private fun assertOwnerSentinel() {
        assertEquals(0, processUser())
        assertEquals(SENTINEL_VALUE, File(context.noBackupFilesDir, SENTINEL).readText())
        assertTrue(
            KeyStore.getInstance("AndroidKeyStore")
                .apply { load(null) }
                .containsAlias(SENTINEL_ALIAS)
        )
    }

    private fun openLocal(host: Activity): ProductAudioSession {
        awaitCallerResumed(host)
        val done = CountDownLatch(1)
        var result: AudioAvailability? = null
        var before = ""
        var after = ""
        lateinit var authorityBefore: LocalAuthoritySnapshot
        lateinit var authorityAfter: LocalAuthoritySnapshot
        instrumentation.runOnMainSync {
            before = localCallerState(activity = host)
            authorityBefore = localAuthoritySnapshot(host)
            runtime.requestOpen(host, runtime.recordingOpenMode()) {
                result = it
                after = localCallerState(activity = host)
                authorityAfter = localAuthoritySnapshot(host)
                done.countDown()
            }
        }
        assertTrue("Local open timed out", done.await(60, TimeUnit.SECONDS))
        assertNoCredentialPrompt()
        assertTrue(
            "Local open failed: ${availabilityCode(result)} before=$before after=$after " +
                "ownerBefore=${authorityBefore.bits} ownerAfter=${authorityAfter.bits} " +
                "generationChanged=${authorityBefore.generation != authorityAfter.generation} " +
                "extraCoordinatorChange=${authorityAfter.epoch - authorityBefore.epoch != 1L} " +
                "caller=" +
                callerDiagnostics(host, before[0] == '1', before[1] == '1'),
            result is AudioAvailability.Available,
        )
        return (result as AudioAvailability.Available).session
    }

    private fun callerDiagnostics(host: Activity, resumed: Boolean, focus: Boolean): String {
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        var changing = false
        var changes = 0
        var otherMainResumed = false
        instrumentation.runOnMainSync {
            changing = host.isChangingConfigurations
            changes = host.changingConfigurations
            otherMainResumed =
                ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED)
                    .any { it is MainActivity && it !== host }
        }
        val known =
            listOf(
                "DENSITY" to ActivityInfo.CONFIG_DENSITY,
                "FONT_SCALE" to ActivityInfo.CONFIG_FONT_SCALE,
                "ORIENTATION" to ActivityInfo.CONFIG_ORIENTATION,
                "SCREEN_SIZE" to
                    (ActivityInfo.CONFIG_SCREEN_SIZE or
                        ActivityInfo.CONFIG_SMALLEST_SCREEN_SIZE or
                        ActivityInfo.CONFIG_SCREEN_LAYOUT),
                "UI_MODE" to ActivityInfo.CONFIG_UI_MODE,
            )
        val flags = known.filter { changes and it.second != 0 }.map { it.first }.toMutableList()
        val knownMask = known.fold(0) { mask, entry -> mask or entry.second }
        if (changes and knownMask.inv() != 0) flags.add("OTHER")
        val categories = if (flags.isEmpty()) "NONE" else flags.joinToString(",")
        return "callerResumedAtRequest=$resumed focusAtRequest=$focus " +
            "secure=${keyguard.isDeviceSecure} locked=${keyguard.isDeviceLocked} " +
            "callerFinishing=${host.isFinishing} callerDestroyed=${host.isDestroyed} " +
            "callerChangingConfigurations=$changing configurationChanges=$categories " +
            "configurationMask=${changes.toUInt().toString(16).padStart(8, '0')} " +
            "otherMainResumed=$otherMainResumed"
    }

    private fun awaitCallerResumed(host: Activity) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
        var resumed = false
        while (!resumed && System.nanoTime() < deadline) {
            instrumentation.runOnMainSync {
                resumed = localCallerState(host) == "1110000"
            }
            if (!resumed) Thread.sleep(100)
        }
        assertTrue(
            "Fixture caller never resumed: " +
                callerDiagnostics(host, resumed, host.hasWindowFocus()),
            resumed,
        )
        assertFalse(
            "Foreground actions require an unlocked Android session",
            context.getSystemService(KeyguardManager::class.java).isDeviceLocked,
        )
    }

    /** Fixed boolean order only; never includes identifiers, content or provider messages. */
    private fun localCallerState(activity: Activity): String {
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        return listOf(
                ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED)
                    .any { it === activity },
                activity.hasWindowFocus(),
                context.getSystemService(PowerManager::class.java).isInteractive,
                keyguard.isDeviceLocked,
                keyguard.isKeyguardLocked,
                activity.isFinishing,
                activity.isDestroyed,
            )
            .joinToString("") { if (it) "1" else "0" }
    }

    private data class LocalAuthoritySnapshot(
        val bits: String,
        val generation: Long,
        val epoch: Long,
    )

    /** Read-only fixture diagnostics: never writes a field or calls a readiness-mutating method. */
    private fun localAuthoritySnapshot(activity: Activity): LocalAuthoritySnapshot {
        fun field(owner: Any, name: String): Any? =
            owner.javaClass.getDeclaredField(name).let {
                it.isAccessible = true
                it.get(owner)
            }
        val local = checkNotNull(field(runtime, "localAccess"))
        val session = checkNotNull(field(local, "session"))
        val (foreground, generation) =
            synchronized(checkNotNull(field(session, "monitor"))) {
                (field(session, "foreground") as Boolean) to (field(session, "generation") as Long)
            }
        val coordinator = checkNotNull(field(runtime, "coordinator"))
        val epoch =
            synchronized(checkNotNull(field(coordinator, "monitor"))) {
                field(coordinator, "epoch") as Long
            }
        val bits =
            listOf(
                    field(local, "resumed") === activity,
                    foreground,
                    (field(local, "power") as PowerManager).isInteractive,
                    (field(local, "keyguard") as KeyguardManager).isDeviceLocked,
                )
                .joinToString("") { if (it) "1" else "0" }
        return LocalAuthoritySnapshot(bits, generation, epoch)
    }

    private fun availabilityCode(result: AudioAvailability?): String =
        when (result) {
            null -> "NO_RESULT"
            AudioAvailability.Locked -> "LOCKED"
            AudioAvailability.Opening -> "OPENING"
            is AudioAvailability.Available -> "AVAILABLE"
            is AudioAvailability.Failed -> "FAILED_${result.reason.name}"
        }

    private fun node(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        fun find(current: AccessibilityNodeInfo?): AccessibilityNodeInfo? =
            when {
                current == null -> null
                predicate(current) -> current
                else ->
                    (0 until current.childCount).firstNotNullOfOrNull { find(current.getChild(it)) }
            }
        val automation = instrumentation.uiAutomation
        return automation.windows.firstNotNullOfOrNull { find(it.root) }
            ?: find(automation.rootInActiveWindow)
    }

    private fun success(result: AudioResult<*>) =
        assertTrue("Authenticated operation failed", result is AudioResult.Value)

    private fun id() = UUID.randomUUID().toString()

    private fun identity() = AudioIdentity(RecordingId(id()), AudioAssetId(id()), id())

    private companion object {
        const val SENTINEL = "owner-pilot-synthetic-owner-sentinel.txt"
        const val SENTINEL_VALUE = "SYNTHETIC_ISOLATION_SENTINEL_ONLY"
        const val SENTINEL_ALIAS = "dora.owner-pilot.synthetic-owner-sentinel"
    }
}
