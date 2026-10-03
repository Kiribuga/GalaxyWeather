package ru.svatozar.galaxyweather

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertEquals
import ru.svatozar.galaxyweather.shared.WeatherRepository

@RunWith(AndroidJUnit4::class)
class WeatherSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @org.junit.Before fun dismissFirstLocationPrompt() {
        compose.waitForIdle()
        if (compose.onAllNodesWithText("Не сейчас").fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithText("Не сейчас").performClick()
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        // Wait for the device compositor after the test clock completes the scroll.
        Thread.sleep(400)
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let { bitmap ->
            java.io.File(compose.activity.getExternalFilesDir(null), name).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    @Test fun referenceDashboardShowsRealHourlyDataAndDetails() {
        compose.waitUntil(30_000) { compose.onAllNodesWithText("По часам").fetchSemanticsNodes().isNotEmpty() }
        val data = checkNotNull(WeatherRepository(compose.activity).cached())
        org.junit.Assert.assertTrue(data.hourly.size >= 24)
        org.junit.Assert.assertNotNull(data.current.pressure)
        compose.onNodeWithText("48 часов").performScrollTo().performClick()
        compose.onNodeWithText("24 часа").assertExists()
        compose.onNodeWithText("7 дней").performScrollTo().assertIsDisplayed()
        screenshot("forecast-days.png")
        compose.onNodeWithText("гПа · уровень моря").performScrollTo().assertIsDisplayed()
        screenshot("forecast-details.png")
        compose.onNodeWithText("GALAXY WEATHER").performScrollTo().assertIsDisplayed()
        screenshot("forecast-home.png")
    }

    @Test fun loadsRealForecastAndSearchesCityWithoutSavingOnCancel() {
        compose.waitUntil(30_000) {
            compose.onAllNodesWithText("7 дней").fetchSemanticsNodes().isNotEmpty()
        }
        val repository = WeatherRepository(compose.activity)
        val before = repository.configuredLocation()
        compose.onNodeWithTag("city_search").performClick()
        compose.onNodeWithText("Найти город").assertIsNotEnabled()
        compose.onNodeWithText("Широта").assertDoesNotExist()
        compose.onNodeWithText("Название города").performTextInput("Москва")
        compose.onNodeWithText("Найти город").performClick()
        compose.waitUntil(20_000) { compose.onAllNodesWithTag("city_result").fetchSemanticsNodes().isNotEmpty() }
        screenshot("city-search.png")
        compose.onNodeWithText("Отмена").performClick()
        assertEquals(before, repository.configuredLocation())
        compose.onNodeWithTag("city_search").performClick()
        compose.onNodeWithText("Название города").performTextInput(before.first)
        compose.onNodeWithText("Найти город").performClick()
        compose.waitUntil(20_000) { compose.onAllNodesWithTag("city_result").fetchSemanticsNodes().isNotEmpty() }
        try {
            compose.onAllNodesWithTag("city_result")[0].performClick()
            compose.waitUntil(30_000) { compose.onAllNodesWithText("7 дней").fetchSemanticsNodes().isNotEmpty() }
            org.junit.Assert.assertNotNull(repository.cached())
        } finally {
            if (repository.configuredLocation() != before) repository.saveLocation(before.first, before.second, before.third)
        }
        compose.activityRule.scenario.recreate()
        compose.waitUntil(30_000) {
            compose.onAllNodesWithText("7 дней").fetchSemanticsNodes().isNotEmpty()
        }
    }
}
