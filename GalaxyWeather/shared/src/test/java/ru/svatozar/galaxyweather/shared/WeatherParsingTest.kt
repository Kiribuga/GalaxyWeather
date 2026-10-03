package ru.svatozar.galaxyweather.shared

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class WeatherParsingTest {
    private fun response() = JSONObject("""{
        "current":{"temperature_2m":-2.6,"apparent_temperature":-5.1,"weather_code":73,"wind_speed_10m":12.4,"relative_humidity_2m":86,"time":"2026-09-28T13:00"},
        "daily":{"time":["2026-09-28","2026-09-29"],"weather_code":[73,0],"temperature_2m_min":[-4.5,-3.2],"temperature_2m_max":[1.1,2.4],"precipitation_probability_max":[80,0]}
    }""")

    @Test fun preservesUnitsSignsAndDailyAlignment() {
        val result = WeatherRepository.parse("Тест", response())
        assertEquals("Тест", result.locationName)
        assertEquals(-2.6, result.current.temperature, 0.001)
        assertEquals(12.4, result.current.windSpeed, 0.001)
        assertEquals(86, result.current.humidity)
        assertEquals(2, result.daily.size)
        assertEquals(80, result.daily[0].precipitationProbability)
        assertEquals("2026-09-29", result.daily[1].date)
        assertEquals("Снег", weatherDescription(result.current.weatherCode))
    }

    @Test fun rejectsMismatchedDailyArrays() {
        val root = response()
        root.getJSONObject("daily").getJSONArray("weather_code").remove(1)
        assertTrue(runCatching { WeatherRepository.parse("Тест", root) }.isFailure)
    }

    @Test fun rejectsMissingCurrentData() {
        val root = response().apply { remove("current") }
        assertTrue(runCatching { WeatherRepository.parse("Тест", root) }.isFailure)
    }
}
