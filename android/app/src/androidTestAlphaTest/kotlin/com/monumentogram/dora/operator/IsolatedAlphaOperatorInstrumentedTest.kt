@file:Suppress("DEPRECATION")

package com.monumentogram.dora.operator

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.app.KeyguardManager
import android.content.Intent
import android.os.ParcelFileDescriptor
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.monumentogram.dora.DoraApplication
import com.monumentogram.dora.MainActivity
import com.monumentogram.dora.audio.AudioAvailability
import com.monumentogram.dora.audio.AudioFailure
import com.monumentogram.dora.audio.AudioFormat
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.AudioSourceState
import com.monumentogram.dora.audio.AudioStorageUnitIdentity
import com.monumentogram.dora.audio.ProductAudioSession
import com.monumentogram.dora.model.alpha.AudioAssetId
import com.monumentogram.dora.model.alpha.RecordingId
import com.monumentogram.dora.poc.recovery.contract.RecoveryRelativeNames
import java.io.File
import java.io.RandomAccessFile
import java.security.KeyStore
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.crypto.KeyGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@Suppress(
    "MagicNumber",
    "LongMethod",
    "TooManyFunctions",
) // One real credential lifecycle and synthetic fixtures.
class IsolatedAlphaOperatorInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val arguments = InstrumentationRegistry.getArguments()
    private val runtime
        get() = (context.applicationContext as DoraApplication).audioRuntime

    @Test
    fun realSecondaryRuntimeAuthenticatedReadbackDeletionAndReopen() {
        val expectedUser = checkNotNull(arguments.getString("operatorUserId"))
        val serial = checkNotNull(arguments.getString("operatorUserSerial"))
        val operator =
            IsolatedAlphaOperator({ androidOperatorEnvironment(context) }) {
                (runtime.availability as? AudioAvailability.Available)?.session
            }
        assertTrue(
            "Requires actual full-secondary emulator and explicit host receipt",
            operator.admit(expectedUser, "FULL_SECONDARY_USER:$expectedUser:$serial"),
        )
        val automation = instrumentation.uiAutomation
        val previous = automation.serviceInfo
        val host =
            instrumentation.startActivitySync(
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        try {
            automation.serviceInfo =
                automation.serviceInfo.apply {
                    flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                }
            val first = unlock(host)
            // Prompt pauses revoke local operator proof; explicit identity admission is repeated.
            assertTrue(operator.admit(expectedUser, "FULL_SECONDARY_USER:$expectedUser:$serial"))
            val selected = identity()
            val preserved = identity()
            val unit = AudioStorageUnitIdentity(selected, id(), 0, 0)
            success(first.writer.createLogicalRecording(selected))
            success(
                first.writer.append(unit, AudioFormat.PCM, ByteArray(320) { (it * 17).toByte() })
            )
            assertTrue(operator.readback(selected.recordingId.value) is AudioResult.Failed)
            assertTrue(operator.consumeDeletion(selected.recordingId.value) is AudioResult.Failed)
            // The real encrypted adapter rejects duplicate and gapped canonical append positions.
            assertTrue(
                first.writer.append(
                    unit.copy(unitId = id(), ordinal = 1, firstFrame = 0),
                    AudioFormat.PCM,
                    ByteArray(320),
                ) is AudioResult.Failed
            )
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
            success(first.writer.createLogicalRecording(preserved))
            success(
                first.writer.append(
                    AudioStorageUnitIdentity(preserved, id(), 0, 0),
                    AudioFormat.PCM,
                    ByteArray(32) { 7 },
                )
            )
            success(first.writer.finalize(preserved))
            assertEquals(
                AudioResult.Value(IsolatedAlphaOperator.Readback(320, 2)),
                operator.readback(selected.recordingId.value),
            )
            assertTrue(operator.consumeDeletion(id()) is AudioResult.Failed)
            assertTrue(operator.consumeDeletion(selected.recordingId.value) is AudioResult.Failed)
            success(operator.readback(selected.recordingId.value))
            instrumentation.runOnMainSync { runtime.lock() }
            assertTrue(operator.consumeDeletion(selected.recordingId.value) is AudioResult.Failed)
            val second = unlock(host)
            assertTrue(operator.admit(expectedUser, "FULL_SECONDARY_USER:$expectedUser:$serial"))
            success(operator.readback(selected.recordingId.value))
            val cancellation =
                (operator.consumeDeletion(selected.recordingId.value) as AudioResult.Value).value
            delete(host, second, cancellation, "Cancel")
            success(operator.readback(selected.recordingId.value))
            val deletion =
                (operator.consumeDeletion(selected.recordingId.value) as AudioResult.Value).value
            delete(host, second, deletion, "Delete audio")
            val checked = second.inspectAudioDeletion(selected)
            assertTrue(checked is AudioResult.Value && checked.value.complete)
            success(operator.readback(preserved.recordingId.value))
            instrumentation.runOnMainSync { runtime.lock() }
            val third = unlock(host)
            assertEquals(
                AudioResult.Value(AudioSourceState.UserDeleted),
                third.sourceState(selected),
            )
            val reopened = third.inspectAudioDeletion(selected)
            assertTrue(reopened is AudioResult.Value && reopened.value.complete)
            assertTrue(
                third.inspectAudioDeletion(selected.copy(sessionId = id())) is AudioResult.Failed
            )
            assertTrue(operator.admit(expectedUser, "FULL_SECONDARY_USER:$expectedUser:$serial"))
            success(operator.readback(preserved.recordingId.value))
            val corrupted = identity()
            val damagedUnit = AudioStorageUnitIdentity(corrupted, id(), 0, 0)
            success(third.writer.create(corrupted))
            success(third.writer.append(damagedUnit, AudioFormat.PCM, ByteArray(320) { 11 }))
            success(third.writer.finalize(corrupted))
            val encrypted =
                File(
                    context.noBackupFilesDir,
                    "dora-vault-v1/poc-recovery/v1/runs/${damagedUnit.unitId}/" +
                        RecoveryRelativeNames.microfileCiphertext(0UL),
                )
            RandomAccessFile(encrypted, "rw").use { file ->
                file.seek(16)
                val byte = file.readByte()
                file.seek(16)
                file.writeByte(byte.toInt() xor 1)
            }
            assertTrue(operator.readback(corrupted.recordingId.value) is AudioResult.Failed)
            assertTrue(operator.consumeDeletion(corrupted.recordingId.value) is AudioResult.Failed)
            exerciseOperatorUi(preserved)
        } finally {
            automation.serviceInfo = previous
            instrumentation.runOnMainSync {
                runtime.lock()
                host.finish()
            }
        }
    }

    @Test
    fun actualPackageAndOwnerIdentityAreNeverImplicitlySelected() {
        val environment = androidOperatorEnvironment(context)
        val operator = IsolatedAlphaOperator({ environment }) { null }
        assertFalse(operator.admit("0", "FULL_SECONDARY_USER:0:0"))
        assertFalse(operator.admit("", ""))
        assertFalse(
            operator.admit((environment.processUserId + 1).toString(), "FULL_SECONDARY_USER:10:42")
        )
        val host =
            instrumentation.startActivitySync(
                Intent(context, IsolatedAlphaOperatorActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        try {
            instrumentation.runOnMainSync {
                field(host, "Explicit secondary userId").setText("0")
                field(host, "FULL_SECONDARY_USER:<userId>:<serial>")
                    .setText("FULL_SECONDARY_USER:0:0")
                button(host, "Authenticate and list test-local RecordingIds").performClick()
                assertTrue(
                    views(host.window.decorView).filterIsInstance<TextView>().any {
                        it.text.toString().startsWith("STOP / ISOLATION_IDENTITY_REJECTED")
                    }
                )
                assertFalse(button(host, "Delete this verified recording…").isEnabled)
            }
        } finally {
            instrumentation.runOnMainSync { host.finish() }
        }
    }

    @Test
    fun seedOwnerIsolationSentinel() {
        assertEquals(0, androidOperatorEnvironment(context).processUserId)
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
        val operator = IsolatedAlphaOperator({ androidOperatorEnvironment(context) }) { null }
        assertFalse(operator.admit("0", "FULL_SECONDARY_USER:0:0"))
    }

    @Test
    fun secondaryCannotReadOwnerSentinel() {
        assertTrue(androidOperatorEnvironment(context).processUserId > 0)
        assertEquals("com.monumentogram.dora.alpha.test", context.packageName)
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
        assertEquals(0, androidOperatorEnvironment(context).processUserId)
        assertEquals(SENTINEL_VALUE, File(context.noBackupFilesDir, SENTINEL).readText())
        assertTrue(
            KeyStore.getInstance("AndroidKeyStore")
                .apply { load(null) }
                .containsAlias(SENTINEL_ALIAS)
        )
    }

    private fun unlock(host: Activity): ProductAudioSession {
        val done = CountDownLatch(1)
        var result: AudioAvailability? = null
        var resumedAtRequest = false
        var focusAtRequest = false
        try {
            instrumentation.uiAutomation
                .executeAndWaitForEvent(
                    {
                        instrumentation.runOnMainSync {
                            resumedAtRequest =
                                ActivityLifecycleMonitorRegistry.getInstance()
                                    .getActivitiesInStage(Stage.RESUMED)
                                    .any { it === host }
                            focusAtRequest = host.hasWindowFocus()
                            runtime.requestOpen(host, runtime.recordingOpenMode()) {
                                result = it
                                done.countDown()
                            }
                        }
                    },
                    { node { it.isPassword && it.isEditable && it.isFocused } != null },
                    15_000,
                )
                .recycle()
        } catch (_: TimeoutException) {
            val keyguard = context.getSystemService(KeyguardManager::class.java)
            throw AssertionError(
                "Credential prompt timeout: ${availabilityCode(result)} " +
                    "callerResumedAtRequest=$resumedAtRequest focusAtRequest=$focusAtRequest " +
                    "secure=${keyguard.isDeviceSecure} locked=${keyguard.isDeviceLocked} " +
                    "callerFinishing=${host.isFinishing} callerDestroyed=${host.isDestroyed}"
            )
        }
        enterCredential()
        val completed = done.await(60, TimeUnit.SECONDS)
        // Never stringify a session, identity, provider exception or storage path in diagnostics.
        val state = availabilityCode(result)
        assertTrue("Real authenticated open timed out: $state", completed)
        assertTrue("Real authenticated open failed: $state", result is AudioAvailability.Available)
        return (result as AudioAvailability.Available).session
    }

    private fun availabilityCode(result: AudioAvailability?): String =
        when (result) {
            null -> "NO_RESULT"
            AudioAvailability.Locked -> "LOCKED"
            AudioAvailability.Opening -> "OPENING"
            is AudioAvailability.Available -> "AVAILABLE"
            is AudioAvailability.Failed -> "FAILED_${result.reason.name}"
        }

    private fun enterCredential() {
        instrumentation.uiAutomation.waitForIdle(500, 10_000)
        val pin = checkNotNull(arguments.getString("syntheticAuthPin"))
        require(pin.matches(Regex("[0-9]{6}")))
        val field = checkNotNull(node { it.isPassword && it.isEditable && it.isFocused })
        assertTrue(
            "Credential input must belong to the actual system prompt",
            field.packageName?.toString() in setOf("com.android.systemui", "com.android.settings"),
        )
        assertTrue(field.isFocused && field.isEnabled && field.isEditable && field.isPassword)
        // API36 SystemUI advertises ACTION_SET_TEXT but rejects it for this secure field.
        // Ordinary focused key events still pass through the real system credential verifier.
        val events = pin.map { KeyEvent.KEYCODE_0 + it.digitToInt() }.joinToString(" ")
        ParcelFileDescriptor.AutoCloseInputStream(
                instrumentation.uiAutomation.executeShellCommand("input keyevent $events")
            )
            .use { it.readBytes() }
        assertTrue("System credential field disappeared before submission", field.refresh())
        assertEquals("System credential input length", 6, field.text?.length)
        ParcelFileDescriptor.AutoCloseInputStream(
                instrumentation.uiAutomation.executeShellCommand(
                    "input keyevent ${KeyEvent.KEYCODE_ENTER}"
                )
            )
            .use { it.readBytes() }
    }

    private fun exerciseOperatorUi(identity: AudioIdentity) {
        val host =
            instrumentation.startActivitySync(
                Intent(context, IsolatedAlphaOperatorActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        try {
            instrumentation.runOnMainSync {
                field(host, "Explicit secondary userId")
                    .setText(arguments.getString("operatorUserId"))
                field(host, "FULL_SECONDARY_USER:<userId>:<serial>")
                    .setText(
                        "FULL_SECONDARY_USER:${arguments.getString("operatorUserId")}:" +
                            arguments.getString("operatorUserSerial")
                    )
                field(host, "Exact new test RecordingId").setText(identity.recordingId.value)
            }
            instrumentation.uiAutomation
                .executeAndWaitForEvent(
                    {
                        instrumentation.runOnMainSync {
                            button(host, "Authenticate and list test-local RecordingIds")
                                .performClick()
                        }
                    },
                    { node { it.isPassword && it.isEditable && it.isFocused } != null },
                    15_000,
                )
                .recycle()
            enterCredential()
            waitUi(host, "TEST_LOCAL_RECORDING_IDS")
            instrumentation.runOnMainSync {
                assertTrue(
                    views(host.window.decorView).filterIsInstance<TextView>().any {
                        it.text.toString().contains(identity.recordingId.value)
                    }
                )
                assertFalse(button(host, "Delete this verified recording…").isEnabled)
            }
            uiReadback(host)
            val monitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)
            try {
                instrumentation.runOnMainSync {
                    assertTrue(button(host, "Delete this verified recording…").isEnabled)
                    host.startActivity(Intent(host, MainActivity::class.java))
                }
                val background = checkNotNull(monitor.waitForActivityWithTimeout(10_000))
                instrumentation.waitForIdleSync()
                instrumentation.runOnMainSync {
                    assertFalse(button(host, "Delete this verified recording…").isEnabled)
                    background.finish()
                }
                instrumentation.waitForIdleSync()
            } finally {
                instrumentation.removeMonitor(monitor)
            }
            instrumentation.runOnMainSync {
                assertFalse(button(host, "Delete this verified recording…").isEnabled)
                field(host, "Exact new test RecordingId").setText(id())
                assertFalse(button(host, "Delete this verified recording…").isEnabled)
                field(host, "Exact new test RecordingId").setText(identity.recordingId.value)
            }
            uiReadback(host)
            instrumentation.uiAutomation
                .executeAndWaitForEvent(
                    {
                        instrumentation.runOnMainSync {
                            button(host, "Delete this verified recording…").performClick()
                        }
                    },
                    {
                        node {
                            it.text?.toString()?.equals("Delete audio", ignoreCase = true) ==
                                true && it.isClickable
                        } != null
                    },
                    10_000,
                )
                .recycle()
            assertTrue(
                checkNotNull(
                        node {
                            it.text?.toString()?.equals("Delete audio", ignoreCase = true) ==
                                true && it.isClickable
                        }
                    )
                    .performAction(AccessibilityNodeInfo.ACTION_CLICK)
            )
            waitUi(host, "DELETION_OK")
            instrumentation.runOnMainSync {
                assertFalse(button(host, "Delete this verified recording…").isEnabled)
                button(host, "Cancel / lock").performClick()
                assertFalse(button(host, "Delete this verified recording…").isEnabled)
            }
        } finally {
            instrumentation.runOnMainSync { host.finish() }
        }
    }

    private fun uiReadback(host: Activity) {
        instrumentation.uiAutomation
            .executeAndWaitForEvent(
                {
                    instrumentation.runOnMainSync {
                        button(host, "Authenticate and read full recording").performClick()
                    }
                },
                { node { it.isPassword && it.isEditable && it.isFocused } != null },
                15_000,
            )
            .recycle()
        enterCredential()
        waitUi(host, "READBACK_OK")
    }

    private fun waitUi(host: Activity, prefix: String) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60)
        while (System.nanoTime() < deadline) {
            var complete = false
            instrumentation.runOnMainSync {
                val text =
                    views(host.window.decorView)
                        .filterIsInstance<TextView>()
                        .map { it.text.toString() }
                        .toList()
                assertFalse("Operator UI rejected operation", text.any { it.startsWith("STOP /") })
                complete = text.any { it.startsWith(prefix) }
            }
            if (complete) return
            Thread.sleep(100)
        }
        throw AssertionError("Operator UI metadata did not reach $prefix")
    }

    private fun button(host: Activity, text: String) =
        views(host.window.decorView).filterIsInstance<Button>().single {
            it.text.toString() == text
        }

    private fun field(host: Activity, hint: String) =
        views(host.window.decorView).filterIsInstance<EditText>().single {
            it.hint.toString() == hint
        }

    private fun views(view: View): List<View> = buildList {
        add(view)
        if (view is ViewGroup)
            for (index in 0 until view.childCount) addAll(views(view.getChildAt(index)))
    }

    private fun delete(
        host: Activity,
        session: ProductAudioSession,
        identity: AudioIdentity,
        button: String,
    ) {
        val done = CountDownLatch(1)
        var result: AudioResult<Unit>? = null
        instrumentation.uiAutomation
            .executeAndWaitForEvent(
                {
                    instrumentation.runOnMainSync {
                        runtime.requestAudioDeletion(host, session, identity) {
                            result = it
                            done.countDown()
                        }
                    }
                },
                {
                    node {
                        it.text?.toString()?.equals(button, ignoreCase = true) == true &&
                            it.isClickable
                    } != null
                },
                10_000,
            )
            .recycle()
        assertTrue(
            checkNotNull(
                    node {
                        it.text?.toString()?.equals(button, ignoreCase = true) == true &&
                            it.isClickable
                    }
                )
                .performAction(AccessibilityNodeInfo.ACTION_CLICK)
        )
        assertTrue(done.await(60, TimeUnit.SECONDS))
        assertEquals(
            if (button == "Cancel") AudioResult.Failed(AudioFailure.CANCELLED)
            else AudioResult.Value(Unit),
            result,
        )
    }

    private fun node(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        fun find(current: AccessibilityNodeInfo?): AccessibilityNodeInfo? =
            when {
                current == null -> null
                predicate(current) -> current
                else ->
                    (0 until current.childCount).firstNotNullOfOrNull { find(current.getChild(it)) }
            }
        return instrumentation.uiAutomation.windows.firstNotNullOfOrNull { find(it.root) }
    }

    private fun success(result: AudioResult<*>) =
        assertTrue("Authenticated operation failed", result is AudioResult.Value)

    private fun id() = UUID.randomUUID().toString()

    private fun identity() = AudioIdentity(RecordingId(id()), AudioAssetId(id()), id())

    private companion object {
        const val SENTINEL = "isolated-alpha-synthetic-owner-sentinel.txt"
        const val SENTINEL_VALUE = "SYNTHETIC_ISOLATION_SENTINEL_ONLY"
        const val SENTINEL_ALIAS = "dora.isolated-alpha.synthetic-owner-sentinel"
    }
}
