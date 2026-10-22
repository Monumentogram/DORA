package com.monumentogram.dora

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Rule
import org.junit.Test

class VersionLabelInstrumentedTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()

    @Test
    fun installedVersionIsVisibleBeforeAndAfterRecreation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        val prefix =
            if (context.packageName == "com.monumentogram.dora.alpha.pilot") "DORA Owner Alpha"
            else "DORA Alpha"
        val expected = "$prefix ${info.versionName} (${info.longVersionCode})"
        assertEquals(expected, context.getString(R.string.alpha_version_label))
        ui.onNodeWithText(expected).assertIsDisplayed()
        val previous = ui.activity
        ui.activityRule.scenario.recreate()
        ui.waitForIdle()
        assertNotSame(previous, ui.activity)
        ui.onNodeWithText(expected).assertIsDisplayed()
    }
}
