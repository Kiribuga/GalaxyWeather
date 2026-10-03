package ru.svatozar.galaxyweather.shared

import java.io.InputStream
import java.time.Instant

fun InputStream.readWeatherResponse(maxBytes: Int = 1_048_576): String {
    val bytes = readNBytesCompat(maxBytes + 1)
    require(bytes.size <= maxBytes) { "Ответ сервера слишком большой" }
    return bytes.toString(Charsets.UTF_8)
}

private fun InputStream.readNBytesCompat(limit: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (output.size() < limit) {
        val count = read(buffer, 0, minOf(buffer.size, limit - output.size()))
        if (count == -1) break
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}

internal fun WeatherSnapshot.validate(): WeatherSnapshot = apply {
    require(locationName.isNotBlank() && locationName.length <= 200)
    require(fetchedAtMillis > 0 && fetchedAtMillis <= System.currentTimeMillis() + 300_000)
    require(current.temperature.isFinite() && current.windSpeed.isFinite() && current.windSpeed >= 0)
    require(current.humidity in 0..100)
    require(source.length in 1..80)
    require(current.apparentTemperature == null || current.apparentTemperature.isFinite())
    require(current.pressure == null || (current.pressure.isFinite() && current.pressure > 0))
    require(current.windDirection == null || (current.windDirection.isFinite() && current.windDirection in 0.0..360.0))
    require(current.cloudCover == null || (current.cloudCover.isFinite() && current.cloudCover in 0.0..100.0))
    Instant.parse(current.observedAt)
    require(daily.size in 1..14 && hourly.size <= 200)
    daily.forEach {
        java.time.LocalDate.parse(it.date)
        require(it.minTemperature.isFinite() && it.maxTemperature.isFinite() && it.minTemperature <= it.maxTemperature)
        require(it.precipitationProbability == null || it.precipitationProbability in 0..100)
    }
    hourly.forEach {
        Instant.parse(it.time)
        require(it.temperature.isFinite() && (it.rainMillimeters == null || (it.rainMillimeters.isFinite() && it.rainMillimeters >= 0)))
    }
}

fun WeatherSnapshot.isFresh(now: Long = System.currentTimeMillis()): Boolean =
    now - fetchedAtMillis in 0..30 * 60_000L &&
        runCatching { now - Instant.parse(current.observedAt).toEpochMilli() in -3_600_000L..7_200_000L }.getOrDefault(false)

internal fun WeatherSnapshot.revalidated(now: Long = System.currentTimeMillis()): WeatherSnapshot =
    copy(fetchedAtMillis = now).validate().also { require(it.isFresh(now)) { "Сервис подтвердил устаревший прогноз" } }
