package ru.svatozar.galaxyweather.shared

import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId

/** Daily ranges use forecast samples, grouped in the phone/watch time zone. */
internal fun parseMetForecast(name: String, root: JSONObject, zone: ZoneId = ZoneId.systemDefault()): WeatherSnapshot {
    val series = root.getJSONObject("properties").getJSONArray("timeseries")
    require(series.length() > 0) { "Пустой прогноз" }
    val points = (0 until series.length()).map { series.getJSONObject(it) }
    fun details(point: JSONObject) = point.getJSONObject("data").getJSONObject("instant").getJSONObject("details")
    fun isDay(point: JSONObject): Boolean = !sequenceOf("next_1_hours", "next_6_hours", "next_12_hours")
        .mapNotNull { point.getJSONObject("data").optJSONObject(it)?.optJSONObject("summary")?.optString("symbol_code") }
        .firstOrNull().orEmpty().endsWith("_night")
    fun code(point: JSONObject): Int {
        val data = point.getJSONObject("data")
        val symbol = sequenceOf("next_1_hours", "next_6_hours", "next_12_hours")
            .mapNotNull { data.optJSONObject(it)?.optJSONObject("summary")?.optString("symbol_code") }
            .firstOrNull().orEmpty()
        return when {
            "thunder" in symbol -> 95
            "snow" in symbol || "sleet" in symbol -> 73
            "rain" in symbol -> 63
            "fog" in symbol -> 45
            symbol.startsWith("clearsky") -> 0
            symbol.startsWith("fair") -> 1
            symbol.startsWith("partlycloudy") -> 2
            symbol.startsWith("cloudy") -> 3
            else -> -1
        }
    }
    val current = details(points.first())
    val start = Instant.parse(points.first().getString("time"))
    val hourly = points.takeWhile { Instant.parse(it.getString("time")).isBefore(start.plusSeconds(48 * 3600)) }.map { point ->
        val rain = point.getJSONObject("data").optJSONObject("next_1_hours")?.optJSONObject("details")
        HourlyWeather(point.getString("time"), details(point).getDouble("air_temperature"), code(point), isDay(point),
            if (rain == null || rain.isNull("precipitation_amount")) null else rain.getDouble("precipitation_amount"))
    }
    val daily = points.groupBy { Instant.parse(it.getString("time")).atZone(zone).toLocalDate().toString() }
        .entries.take(7).map { (date, samples) ->
            val temperatures = samples.map { details(it).getDouble("air_temperature") }
            val midday = samples.minBy { kotlin.math.abs(Instant.parse(it.getString("time")).atZone(zone).hour - 12) }
            DailyWeather(date, temperatures.min(), temperatures.max(), null, code(midday))
        }
    return WeatherSnapshot(name,
        CurrentWeather(current.getDouble("air_temperature"), null, code(points.first()),
            current.getDouble("wind_speed") * 3.6, current.getDouble("relative_humidity").toInt(), points.first().getString("time"),
            isDay(points.first()), current.optionalDouble("air_pressure_at_sea_level"), current.optionalDouble("wind_from_direction"), current.optionalDouble("cloud_area_fraction")),
        daily, System.currentTimeMillis(), "MET Norway", hourly)
}

internal fun JSONObject.optionalDouble(key: String): Double? = if (isNull(key)) null else getDouble(key)
