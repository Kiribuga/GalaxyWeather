package ru.svatozar.galaxyweather

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONArray
import ru.svatozar.galaxyweather.shared.readWeatherResponse

data class City(val name: String, val description: String, val latitude: Double, val longitude: Double)

/** Explicit searches only: Nominatim does not allow client autocomplete. */
object CitySearch {
    private var lastRequest = 0L
    private val cache = object : LinkedHashMap<String, List<City>>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<City>>?): Boolean = size > 20
    }

    @Synchronized fun find(query: String): List<City> {
        val term = query.trim()
        require(term.length in 2..100)
        cache[term]?.let { return it }
        val delay = 1100 - (android.os.SystemClock.elapsedRealtime() - lastRequest)
        if (delay > 0) Thread.sleep(delay)
        lastRequest = android.os.SystemClock.elapsedRealtime()
        val url = "https://nominatim.openstreetmap.org/search?city=" + URLEncoder.encode(term, "UTF-8") + "&format=jsonv2&addressdetails=1&limit=5&accept-language=ru"
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("User-Agent", "GalaxyWeather/0.1.1 personal Android application")
        return try {
            check(connection.responseCode == 200) { "Поиск недоступен: HTTP ${connection.responseCode}" }
            val array = connection.inputStream.use { JSONArray(it.readWeatherResponse(65_536)) }
            require(array.length() <= 5)
            (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                val latitude = item.getDouble("lat")
                val longitude = item.getDouble("lon")
                require(latitude.isFinite() && latitude in -90.0..90.0 && longitude.isFinite() && longitude in -180.0..180.0)
                City(item.optString("name").ifBlank { item.getString("display_name").substringBefore(',') }, item.getString("display_name"), latitude, longitude)
            }.also { cache[term] = it }
        } finally { connection.disconnect() }
    }
}
