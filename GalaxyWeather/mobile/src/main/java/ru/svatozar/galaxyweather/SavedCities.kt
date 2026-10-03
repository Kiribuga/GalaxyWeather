package ru.svatozar.galaxyweather

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import ru.svatozar.galaxyweather.shared.WeatherRepository

class SavedCities(context: Context) {
    private val prefs = context.getSharedPreferences("cities", Context.MODE_PRIVATE)
    private val repository = WeatherRepository(context)
    var automatic: Boolean
        get() = prefs.getBoolean("automatic", false)
        set(value) { prefs.edit().putBoolean("automatic", value).apply() }
    var prompted: Boolean
        get() = prefs.getBoolean("prompted", false)
        set(value) { prefs.edit().putBoolean("prompted", value).apply() }

    fun read(): List<City> {
        val stored = runCatching {
            val array = JSONArray(prefs.getString("list", "[]"))
            (0 until array.length()).map { index -> array.getJSONObject(index).let {
                City(it.getString("name"), it.optString("description"), it.getDouble("lat"), it.getDouble("lon"))
            } }
        }.getOrDefault(emptyList())
        if (stored.isNotEmpty()) return stored
        val (name, lat, lon) = repository.configuredLocation()
        return listOf(City(name, name, lat, lon)).also(::write)
    }

    fun add(city: City): List<City> {
        require(city.name.isNotBlank() && city.latitude.isFinite() && city.latitude in -90.0..90.0 && city.longitude.isFinite() && city.longitude in -180.0..180.0)
        return (read().filterNot { it.latitude == city.latitude && it.longitude == city.longitude } + city).also(::write)
    }

    private fun write(cities: List<City>) {
        prefs.edit().putString("list", JSONArray().apply { cities.forEach {
            put(JSONObject().put("name", it.name).put("description", it.description).put("lat", it.latitude).put("lon", it.longitude))
        } }.toString()).apply()
    }
}
