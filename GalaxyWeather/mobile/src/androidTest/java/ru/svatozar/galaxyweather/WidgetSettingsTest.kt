package ru.svatozar.galaxyweather

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.svatozar.galaxyweather.widget.WidgetSettingsActivity

@RunWith(AndroidJUnit4::class)
class WidgetSettingsTest {
    @get:Rule val compose = createAndroidComposeRule<WidgetSettingsActivity>()

    @Test fun transparencyIsSavedOnlyAfterSave() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("widget_settings", Context.MODE_PRIVATE)
        val before = prefs.getInt("opacity", 80)
        try {
            compose.onNodeWithTag("widget_transparency").performSemanticsAction(SemanticsActions.SetProgress) { it(75f) }
            compose.onNodeWithText("Прозрачность фона: 75%").assertExists()
            assertEquals(before, prefs.getInt("opacity", 80))
            compose.activityRule.scenario.recreate()
            compose.onNodeWithText("Прозрачность фона: 75%").assertExists()
            compose.onNodeWithText("Сохранить").performClick()
            compose.waitUntil(10_000) { prefs.getInt("opacity", 80) == 25 }
        } finally { prefs.edit().putInt("opacity", before).commit() }
    }

    @Test fun cancelDoesNotChangeTransparency() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("widget_settings", Context.MODE_PRIVATE)
        val before = prefs.getInt("opacity", 80)
        compose.onNodeWithTag("widget_transparency").performSemanticsAction(SemanticsActions.SetProgress) { it(100f) }
        compose.onNodeWithText("Отмена").performClick()
        assertEquals(before, prefs.getInt("opacity", 80))
    }
}

