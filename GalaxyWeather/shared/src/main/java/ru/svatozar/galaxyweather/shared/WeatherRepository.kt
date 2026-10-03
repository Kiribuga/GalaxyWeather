package ru.svatozar.galaxyweather.shared

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class WeatherRepository(context: Context) {
    private val context = context.applicationContext
    private val prefs = context.getSharedPreferences("weather", Context.MODE_PRIVATE)

    fun configuredLocation(): Triple<String, Double, Double> = Triple(
        prefs.getString("location_name", "Москва") ?: "Москва",
        java.lang.Double.longBitsToDouble(prefs.getLong("latitude", 55.7558.toBits())),
        java.lang.Double.longBitsToDouble(prefs.getLong("longitude", 37.6173.toBits()))
    )

    fun saveLocation(name: String, latitude: Double, longitude: Double) {
        require(name.trim().length in 1..200) { "Некорректное название места" }
        require(latitude.isFinite() && latitude in -90.0..90.0 && longitude.isFinite() && longitude in -180.0..180.0) { "Некорректные координаты" }
        prefs.edit()
            .putString("location_name", name.trim().ifBlank { "Выбранное место" })
            .putLong("latitude", latitude.toBits())
            .putLong("longitude", longitude.toBits())
            .apply()
    }

    private fun forecastPrefs(location: Triple<String, Double, Double>) = context.getSharedPreferences(
        "forecast_${location.second.toBits()}_${location.third.toBits()}", Context.MODE_PRIVATE)

    fun load(force: Boolean = false): Result<WeatherSnapshot> = runCatching {
        val location = configuredLocation()
        val prefs = forecastPrefs(location)
        cached(location)?.takeIf { it.source == "MET Norway" && it.isFresh() && (System.currentTimeMillis() < prefs.getLong("expires", 0) || !force) }?.let { return@runCatching it }
        val (name, lat, lon) = location
        val endpoint = String.format(
            Locale.US,
            "https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=%.4f&lon=%.4f",
            lat, lon
        )
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "GalaxyWeather/0.1.3 personal Android application")
        if (cached(location)?.source == "MET Norway") prefs.getString("modified", null)?.let { connection.setRequestProperty("If-Modified-Since", it) }
        try {
            if (connection.responseCode == HttpURLConnection.HTTP_NOT_MODIFIED) {
                val data = checkNotNull(cached(location)).revalidated()
                cache(data, lat, lon)
                prefs.edit().putLong("expires", connection.expiration).apply()
                return@runCatching data
            }
            check(connection.responseCode == HttpURLConnection.HTTP_OK) { "Сервис погоды: HTTP ${connection.responseCode}" }
            connection.inputStream.use { parseMetForecast(name, JSONObject(it.readWeatherResponse())).validate() }
                .also {
                    cache(it, lat, lon)
                    prefs.edit().putLong("expires", connection.expiration).putString("modified", connection.getHeaderField("Last-Modified")).apply()
                }
        } finally { connection.disconnect() }
    }

    fun cached(): WeatherSnapshot? = cached(configuredLocation())

    fun cachedPayload(): String? {
        val location = configuredLocation()
        return if (cached(location) != null) forecastPrefs(location).getString("snapshot", null) ?: prefs.getString("snapshot", null) else null
    }

    fun acceptPhonePayload(payload: String, name: String, latitude: Double, longitude: Double): WeatherSnapshot? = runCatching {
        require(payload.length <= 1_048_576 && name.trim().length in 1..200)
        require(latitude.isFinite() && latitude in -90.0..90.0 && longitude.isFinite() && longitude in -180.0..180.0)
        val root = JSONObject(payload)
        check(root.optInt("schema") == 2)
        check(kotlin.math.abs(root.getDouble("latitude") - latitude) < 0.000001)
        check(kotlin.math.abs(root.getDouble("longitude") - longitude) < 0.000001)
        val location = Triple(name.trim().ifBlank { "Выбранное место" }, latitude, longitude)
        root.put("name", location.first)
        val snapshot = parseCache(root).copy(locationName = location.first).validate()
        require(snapshot.isFresh()) { "Прогноз телефона устарел" }
        root.put("latitude", latitude).put("longitude", longitude)
        saveLocation(location.first, latitude, longitude)
        forecastPrefs(location).edit().putString("snapshot", root.toString()).apply()
        snapshot
    }.getOrNull()

    private fun cached(location: Triple<String, Double, Double>): WeatherSnapshot? = (forecastPrefs(location).getString("snapshot", null) ?: prefs.getString("snapshot", null))?.let {
        runCatching {
            val root = JSONObject(it)
            check(root.optInt("schema") == 2)
            check(root.getDouble("latitude") == location.second && root.getDouble("longitude") == location.third)
            parseCache(root).copy(locationName = location.first).validate()
        }.getOrNull()
    }

    companion object {
    internal fun parse(name: String, root: JSONObject): WeatherSnapshot {
        val c = root.getJSONObject("current")
        val d = root.getJSONObject("daily")
        val dates = d.getJSONArray("time")
        val codes = d.getJSONArray("weather_code")
        val mins = d.getJSONArray("temperature_2m_min")
        val maxs = d.getJSONArray("temperature_2m_max")
        val rain = d.getJSONArray("precipitation_probability_max")
        require(dates.length() > 0 && listOf(codes, mins, maxs, rain).all { it.length() == dates.length() }) { "Неполный прогноз" }
        return WeatherSnapshot(
            name,
            CurrentWeather(c.getDouble("temperature_2m"), c.getDouble("apparent_temperature"), c.getInt("weather_code"), c.getDouble("wind_speed_10m"), c.getInt("relative_humidity_2m"), c.getString("time")),
            (0 until dates.length()).map { i -> DailyWeather(dates.getString(i), mins.getDouble(i), maxs.getDouble(i), rain.optInt(i), codes.getInt(i)) },
            System.currentTimeMillis()
        )
    }
    }

    private fun cache(value: WeatherSnapshot, latitude: Double, longitude: Double) {
        val root = JSONObject().put("name", value.locationName).put("fetched", value.fetchedAtMillis)
            .put("schema", 2)
            .put("source", value.source)
            .put("latitude", latitude).put("longitude", longitude)
        root.put("current", JSONObject().apply {
            put("t", value.current.temperature); put("a", value.current.apparentTemperature); put("c", value.current.weatherCode)
            put("w", value.current.windSpeed); put("h", value.current.humidity); put("at", value.current.observedAt)
            put("day", value.current.isDay)
            put("pressure", value.current.pressure); put("direction", value.current.windDirection); put("clouds", value.current.cloudCover)
        })
        root.put("daily", org.json.JSONArray().apply { value.daily.forEach { x -> put(JSONObject().apply {
            put("d", x.date); put("min", x.minTemperature); put("max", x.maxTemperature); put("p", x.precipitationProbability); put("c", x.weatherCode)
        }) } })
        root.put("hourly", org.json.JSONArray().apply { value.hourly.forEach { x -> put(JSONObject().apply {
            put("time", x.time); put("t", x.temperature); put("c", x.weatherCode); put("day", x.isDay); put("rain", x.rainMillimeters)
        }) } })
        forecastPrefs(Triple(value.locationName, latitude, longitude)).edit().putString("snapshot", root.toString()).apply()
    }

    private fun parseCache(root: JSONObject): WeatherSnapshot {
        val c = root.getJSONObject("current")
        val a = root.getJSONArray("daily")
        val hours = root.optJSONArray("hourly") ?: org.json.JSONArray()
        return WeatherSnapshot(root.getString("name"), CurrentWeather(c.getDouble("t"), if (c.isNull("a")) null else c.getDouble("a"), c.getInt("c"), c.getDouble("w"), c.getInt("h"), c.getString("at"), c.optBoolean("day", false), c.optionalDouble("pressure"), c.optionalDouble("direction"), c.optionalDouble("clouds")),
            (0 until a.length()).map { i -> a.getJSONObject(i).let { DailyWeather(it.getString("d"), it.getDouble("min"), it.getDouble("max"), if (it.isNull("p")) null else it.getInt("p"), it.getInt("c")) } }, root.getLong("fetched"), root.optString("source", "Open-Meteo"),
            (0 until hours.length()).map { i -> hours.getJSONObject(i).let { HourlyWeather(it.getString("time"), it.getDouble("t"), it.getInt("c"), it.getBoolean("day"), it.optionalDouble("rain")) } })
    }
}
