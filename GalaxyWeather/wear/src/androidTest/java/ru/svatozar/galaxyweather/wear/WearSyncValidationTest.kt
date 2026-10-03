package ru.svatozar.galaxyweather.wear

import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.wearable.DataMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.svatozar.galaxyweather.shared.WeatherRepository

class WearSyncValidationTest {
    @Test fun invalidAndFuturePacketsDoNotChangeSelectedProfile() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = WeatherRepository(context)
        val before = repository.configuredLocation()
        val state = context.getSharedPreferences("phone_sync", android.content.Context.MODE_PRIVATE)
        val updated = state.getLong("updated", 0)
        val map = DataMap().apply {
            putString("name", "Тестовый профиль")
            putDouble("latitude", before.second)
            putDouble("longitude", before.third)
            putLong("updated", Long.MAX_VALUE)
        }
        assertNull(PhoneWeatherSync.accept(context, map))
        assertEquals(before, repository.configuredLocation())
        assertEquals(updated, state.getLong("updated", 0))
        map.putLong("updated", System.currentTimeMillis())
        map.putDouble("latitude", Double.NaN)
        assertNull(PhoneWeatherSync.accept(context, map))
        assertEquals(before, repository.configuredLocation())
    }
}
