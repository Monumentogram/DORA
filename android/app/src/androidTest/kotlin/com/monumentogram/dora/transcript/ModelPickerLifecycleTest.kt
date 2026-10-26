package com.monumentogram.dora.transcript

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayInputStream
import java.io.File
import java.util.ArrayDeque
import java.util.concurrent.Executor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelPickerLifecycleTest {
    @Suppress("LongMethod") // One ordered lifecycle transition/result-delivery regression.
    @Test
    fun resultWhileStoppedIsDeliveredToStableOwnerOnRestartAndImportStaysOnWorker() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val application = instrumentation.targetContext.applicationContext
        val worker = ArrayDeque<Runnable>()
        val states = mutableListOf<ModelImportStatus>()
        val queue =
            ModelImportQueue(Executor { worker.add(it) }, Executor { it.run() }, states::add)
        var opened = false
        var request = 0
        instrumentation.runOnMainSync {
            val owner =
                object : LifecycleOwner {
                    override val lifecycle = LifecycleRegistry(this)
                }
            val registry =
                object : ActivityResultRegistry() {
                    override fun <I, O> onLaunch(
                        requestCode: Int,
                        contract: ActivityResultContract<I, O>,
                        input: I,
                        options: ActivityOptionsCompat?,
                    ) {
                        request = requestCode
                    }
                }
            val launcher =
                registerModelPicker(registry, owner) { uri ->
                    assertEquals("content://synthetic-model/invalid", uri.toString())
                    queue.completeSelection {
                        opened = true
                        LocalAsrModel(application)
                            .install(ByteArrayInputStream(byteArrayOf(1, 2, 3)))
                    }
                }
            owner.lifecycle.currentState = Lifecycle.State.STARTED
            assertTrue(queue.beginSelection())
            launcher.launch(arrayOf("application/octet-stream"))
            owner.lifecycle.currentState = Lifecycle.State.CREATED
            assertTrue(
                registry.dispatchResult(
                    request,
                    Activity.RESULT_OK,
                    Intent().setData(Uri.parse("content://synthetic-model/invalid")),
                )
            )
            assertTrue(worker.isEmpty())
            assertFalse(opened)
            owner.lifecycle.currentState = Lifecycle.State.STARTED
            assertEquals(ModelImportStatus.IMPORTING, states.last())
            assertEquals(1, worker.size)
            assertFalse(opened)
        }
        worker.removeFirst().run()
        assertTrue(opened)
        assertEquals(ModelImportStatus.REJECTED, states.last())
        assertFalse(
            File(application.noBackupFilesDir, "local-asr").listFiles().orEmpty().any {
                it.name.endsWith(".pending")
            }
        )
        queue.close()
    }
}
