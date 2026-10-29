@file:Suppress("MagicNumber", "LongMethod")

package com.monumentogram.dora.transcript

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.monumentogram.dora.MainActivity
import com.monumentogram.dora.R
import java.io.File
import java.util.UUID
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TextExportLifecycleTest {
    @get:Rule val ui = createEmptyComposeRule()

    @Test
    fun exactSnapshotAndResultFeedbackSurviveActivityRecreation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val text = "Сохранённая правка\n\nSelected English version 😀\n"
        val output = File(context.cacheDir, "synthetic-export-${UUID.randomUUID()}.txt")
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var controller: TextExportController
                scenario.onActivity {
                    controller = ViewModelProvider(it)[TextExportController::class.java]
                    assertTrue(controller.beginSelection(text))
                    val intent = LocalTextDocument().createIntent(it, "DORA.txt")
                    assertEquals(Intent.ACTION_CREATE_DOCUMENT, intent.action)
                    assertEquals("text/plain", intent.type)
                    assertTrue(intent.getBooleanExtra(Intent.EXTRA_LOCAL_ONLY, false))
                }
                scenario.recreate()
                scenario.onActivity {
                    assertSame(controller, ViewModelProvider(it)[TextExportController::class.java])
                    controller.completeSelection(Uri.fromFile(output))
                }
                ui.waitUntil(30000) {
                    ui.onAllNodesWithText(context.getString(R.string.stage11_export_saved))
                        .fetchSemanticsNodes()
                        .isNotEmpty()
                }
                assertArrayEquals(text.toByteArray(Charsets.UTF_8), output.readBytes())
            }
        } finally {
            output.delete()
        }
    }
}
