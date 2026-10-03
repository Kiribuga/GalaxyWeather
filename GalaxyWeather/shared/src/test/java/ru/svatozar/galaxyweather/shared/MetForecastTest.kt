package ru.svatozar.galaxyweather.shared

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId

class MetForecastTest {
    @org.junit.Test fun nightIconsDoNotShowTheSun() {
        org.junit.Assert.assertEquals("🌙", weatherEmoji(0, false))
        org.junit.Assert.assertEquals("☁️", weatherEmoji(2, false))
        org.junit.Assert.assertEquals("🌤️", weatherEmoji(2, true))
    }

    @Test fun groupsLocalDaysConvertsWindAndKeepsUnavailableFieldsMissing() {
        val point = """{"time":"2026-09-28T23:00:00Z","data":{"instant":{"details":{"air_temperature":-2.5,"wind_speed":5.0,"relative_humidity":80.0}},"next_1_hours":{"summary":{"symbol_code":"snow_night"}}}}"""
        val root = JSONObject("""{"properties":{"timeseries":[$point]}}""")
        val data = parseMetForecast("Тест", root, ZoneId.of("Europe/Moscow"))
        assertEquals("2026-09-29", data.daily.single().date)
        assertEquals(18.0, data.current.windSpeed, 0.001)
        assertEquals(-2.5, data.daily.single().minTemperature, 0.001)
        assertEquals(73, data.current.weatherCode)
        assertFalse(data.current.isDay)
        assertNull(data.current.apparentTemperature)
        assertNull(data.daily.single().precipitationProbability)
        assertEquals("MET Norway", data.source)
        assertEquals(1, data.hourly.size)
        assertEquals(-2.5, data.hourly.single().temperature, 0.001)
        assertNull(data.hourly.single().rainMillimeters)
        assertNull(data.current.pressure)
    }
}
