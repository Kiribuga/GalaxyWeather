package ru.svatozar.galaxyweather

import android.content.Context
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import ru.svatozar.galaxyweather.shared.WeatherRepository

object LocationSync {
    fun send(context: Context, location: Triple<String, Double, Double>) {
        val repository = WeatherRepository(context.applicationContext)
        if (repository.configuredLocation() != location) return
        val payload = repository.cachedPayload()
        val request = PutDataMapRequest.create("/weather/location").apply {
            dataMap.putString("name", location.first)
            dataMap.putDouble("latitude", location.second)
            dataMap.putDouble("longitude", location.third)
            payload?.let { dataMap.putString("snapshot", it) }
            dataMap.putLong("updated", System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()
        Wearable.getDataClient(context.applicationContext).putDataItem(request)
            .addOnFailureListener { android.util.Log.w("GalaxyWeather", "Не удалось передать местоположение часам", it) }
    }
}

