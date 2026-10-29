@file:Suppress(
    "NestedBlockDepth",
    "LongMethod",
) // Bounded provider scenarios and main-thread observations.

package com.monumentogram.dora.transcript

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.DocumentsContract
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.monumentogram.dora.MainActivity
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextExportProviderTest {
    @Test
    fun providerFailureAndFullStorageNeverReportSavedAndAllowRetry() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val granted = java.util.concurrent.CountDownLatch(1)
        instrumentation.targetContext.sendOrderedBroadcast(
            Intent()
                .setClassName(
                    instrumentation.context.packageName,
                    GrantExportReceiver::class.java.name,
                )
                .putExtra("target", instrumentation.targetContext.packageName),
            null,
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    granted.countDown()
                }
            },
            null,
            0,
            null,
            null,
        )
        assertTrue(granted.await(30, java.util.concurrent.TimeUnit.SECONDS))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var controller: TextExportController
            scenario.onActivity {
                controller = ViewModelProvider(it)[TextExportController::class.java]
            }
            for (id in listOf("refuse", "full", "success")) {
                scenario.onActivity {
                    assertTrue(controller.beginSelection("Русский / English 😀\n"))
                    controller.completeSelection(
                        DocumentsContract.buildDocumentUri(
                            instrumentation.context.packageName + ".stage12.documents",
                            id,
                        )
                    )
                }
                val end = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30)
                var status = TextExportStatus.WRITING
                while (status.busy && System.nanoTime() < end) {
                    scenario.onActivity { status = controller.status }
                    if (status.busy) Thread.sleep(20)
                }
                assertEquals(
                    if (id == "success") TextExportStatus.SAVED else TextExportStatus.FAILED,
                    status,
                )
            }
            fun exported() =
                checkNotNull(
                        instrumentation.targetContext.contentResolver.openInputStream(
                            DocumentsContract.buildDocumentUri(
                                instrumentation.context.packageName + ".stage12.documents",
                                "success",
                            )
                        )
                    )
                    .use { it.readBytes() }
            checkNotNull(
                    instrumentation.targetContext.contentResolver.query(
                        DocumentsContract.buildDocumentUri(
                            instrumentation.context.packageName + ".stage12.documents",
                            "full",
                        ),
                        arrayOf("enospc_writes"),
                        null,
                        null,
                        null,
                    )
                )
                .use {
                    assertTrue(it.moveToFirst())
                    assertTrue(
                        "The provider must actually inject ENOSPC during write",
                        it.getInt(0) > 0,
                    )
                }
            assertArrayEquals(
                "Русский / English 😀\n".toByteArray(Charsets.UTF_8),
                exported(),
            )
            scenario.onActivity {
                assertTrue(controller.beginSelection("Must not overwrite"))
                controller.completeSelection(null)
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity { assertEquals(TextExportStatus.CANCELLED, controller.status) }
            assertArrayEquals(
                "Русский / English 😀\n".toByteArray(Charsets.UTF_8),
                exported(),
            )
        }
    }
}
