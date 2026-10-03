package ru.svatozar.galaxyweather

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.svatozar.galaxyweather.shared.WeatherRepository

@RunWith(AndroidJUnit4::class)
class CityAndWidgetTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun savedTabsSurviveRestartAndSwipeLoadsSelectedCity() {
        val context = compose.activity
        val repository = WeatherRepository(context)
        val previous = repository.configuredLocation()
        val prefs = context.getSharedPreferences("cities", Context.MODE_PRIVATE)
        val previousList = prefs.getString("list", null)
        val wasAutomatic = prefs.getBoolean("automatic", false)
        val wasPrompted = prefs.getBoolean("prompted", false)
        val saved = SavedCities(context)
        val added = City("Тула", "Россия", 54.1931, 37.6173)
        try {
            saved.automatic = false
            saved.prompted = false
            saved.add(added)
            saved.add(added)
            assertEquals(1, saved.read().count { it == added })
            compose.activityRule.scenario.recreate()
            compose.onNodeWithText("Не сейчас").performClick()
            compose.waitUntil(30_000) { repository.cached() != null }
            compose.onNodeWithText("Обновить").assertDoesNotExist()
            compose.onNodeWithTag("saved_city_54.1931_37.6173").performScrollTo().performClick()
            compose.waitUntil(30_000) { repository.cached()?.locationName == "Тула" }
            assertEquals("Тула", repository.configuredLocation().first)
            compose.activityRule.scenario.recreate()
            compose.onNodeWithTag("saved_city_54.1931_37.6173").assertIsSelected()
            val forecastPrefs = context.getSharedPreferences("forecast_${added.latitude.toBits()}_${added.longitude.toBits()}", Context.MODE_PRIVATE)
            forecastPrefs.edit().remove("snapshot").remove("expires").remove("modified").commit()
            compose.onNodeWithTag("pull_refresh").performTouchInput { swipeDown(startY = height * .15f, endY = height * .85f, durationMillis = 650) }
            compose.waitUntil(30_000) { repository.cached()?.locationName == "Тула" }
            compose.onNodeWithTag("city_search").performClick()
            compose.onNodeWithText("Отмена").performClick()
            assertEquals("Тула", repository.configuredLocation().first)
        } finally {
            prefs.edit().putString("list", previousList).putBoolean("automatic", wasAutomatic).putBoolean("prompted", wasPrompted).commit()
            repository.saveLocation(previous.first, previous.second, previous.third)
        }
    }

    @Test fun widgetClockFitsCompactAndLargeSizes() {
        compose.runOnUiThread {
            val context = compose.activity
            val density = context.resources.displayMetrics.density
            listOf(250 to 110, 362 to 191, 350 to 230).forEach { (width, height) ->
                val parent = FrameLayout(context)
                val data = checkNotNull(WeatherRepository(context).cached())
                val view = ru.svatozar.galaxyweather.widget.widgetViews(context, data, height >= 185, width >= 320 && height >= 185).apply(context, parent)
                parent.addView(view)
                view.findViewById<TextView>(R.id.widget_temperature).text = "−25° ☀"
                view.findViewById<TextView>(R.id.widget_city).text = "Санкт-Петербург"
                view.findViewById<TextView>(R.id.widget_condition).text = "Переменная облачность"
                view.findViewById<TextView>(R.id.widget_updated).text = "Данные 19:28"
                val w = (width * density).toInt(); val h = (height * density).toInt()
                parent.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
                parent.layout(0, 0, w, h)
                context.addContentView(parent, android.view.ViewGroup.LayoutParams(w, h))
                parent.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
                parent.layout(0, 0, w, h)
                val clock = view.findViewById<TextView>(R.id.widget_time)
                assertTrue("Clock must contain live time", clock.text.isNotBlank())
                val bounds = android.graphics.Rect()
                clock.getDrawingRect(bounds)
                parent.offsetDescendantRectToMyCoords(clock, bounds)
                assertTrue("Clock clipped at ${width}x$height: $bounds", bounds.bottom <= h && bounds.top >= 0 && bounds.right <= w)
                assertTrue(clock.height >= clock.layout.height)
                if (height >= 185) {
                    assertEquals(View.VISIBLE, view.findViewById<View>(R.id.widget_details).visibility)
                    assertTrue(view.findViewById<android.widget.LinearLayout>(R.id.widget_hours).childCount > 0)
                    val footer = view.findViewById<View>(R.id.widget_source)
                    footer.getDrawingRect(bounds)
                    parent.offsetDescendantRectToMyCoords(footer, bounds)
                    assertTrue("Forecast footer clipped", bounds.bottom <= h)
                }
                val bitmap = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.rgb(24, 48, 79))
                parent.draw(android.graphics.Canvas(bitmap))
                java.io.File(context.getExternalFilesDir(null), "widget-${width}x$height.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
                (parent.parent as android.view.ViewGroup).removeView(parent)
            }
        }
    }
}
