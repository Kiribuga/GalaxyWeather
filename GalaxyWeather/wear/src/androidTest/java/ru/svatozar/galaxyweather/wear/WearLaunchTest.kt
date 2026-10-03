package ru.svatozar.galaxyweather.wear

import android.view.WindowManager
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class WearLaunchTest {
    @get:Rule val rule = createAndroidComposeRule<WearMainActivity>()

    @Test fun reopeningAndRecreatingStartsAtTheTop() {
        fun keepVisible() {
            val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
            val power = instrumentation.targetContext.getSystemService(android.os.PowerManager::class.java)
            if (!power.isInteractive) {
                android.os.ParcelFileDescriptor.AutoCloseInputStream(
                    instrumentation.uiAutomation.executeShellCommand("input keyevent 26")
                ).use { it.readBytes() }
            }
            rule.activityRule.scenario.onActivity {
                it.setTurnScreenOn(true)
                it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
        keepVisible()
        rule.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        // Wear can resume before the first Compose hierarchy is attached.
        rule.waitUntil(30_000) {
            runCatching { rule.onAllNodesWithTag("weather_list").fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
        }
        fun top() = rule.onNodeWithTag("weather_list").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        assertEquals(0f, top(), 0f)
        rule.onNodeWithTag("weather_list").performScrollToIndex(4)
        rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        rule.waitUntil { runCatching { top() == 0f }.getOrDefault(false) }
        rule.onNodeWithTag("weather_list").performScrollToIndex(4)
        rule.activityRule.scenario.recreate()
        keepVisible()
        rule.waitUntil { runCatching { top() == 0f }.getOrDefault(false) }
        assertEquals(0f, top(), 0f)
    }
}
