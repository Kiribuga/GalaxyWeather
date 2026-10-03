package ru.svatozar.galaxyweather.shared

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.time.Instant

class WeatherValidationTest {
    @Test fun rejectsOversizedResponsesAndStaleOrMalformedForecasts() {
        assertEquals("{}", ByteArrayInputStream("{}".toByteArray()).use { it.readWeatherResponse(2) })
        assertTrue(runCatching { ByteArrayInputStream(ByteArray(3)).use { it.readWeatherResponse(2) } }.isFailure)
        val now = System.currentTimeMillis()
        val data = WeatherSnapshot("Москва", CurrentWeather(11.0, null, 3, 5.0, 70, Instant.ofEpochMilli(now).toString()),
            listOf(DailyWeather("2026-10-03", 5.0, 12.0, null, 3)), now)
        assertTrue(data.validate().isFresh(now))
        assertFalse(data.copy(fetchedAtMillis = now - 1_800_001).isFresh(now))
        assertTrue(data.copy(fetchedAtMillis = now - 1_800_001).revalidated(now).isFresh(now))
        assertTrue(runCatching { data.copy(current = data.current.copy(observedAt = Instant.ofEpochMilli(now - 7_200_001).toString())).revalidated(now) }.isFailure)
        assertFalse(data.copy(current = data.current.copy(observedAt = Instant.ofEpochMilli(now - 7_200_001).toString())).isFresh(now))
        assertTrue(runCatching { data.copy(current = data.current.copy(humidity = 101)).validate() }.isFailure)
        assertTrue(runCatching { data.copy(current = data.current.copy(pressure = Double.NaN)).validate() }.isFailure)
        assertTrue(runCatching { data.copy(current = data.current.copy(cloudCover = 101.0)).validate() }.isFailure)
        assertTrue(runCatching { data.copy(hourly = listOf(HourlyWeather("invalid", 1.0, 3, true, null))).validate() }.isFailure)
    }
}
