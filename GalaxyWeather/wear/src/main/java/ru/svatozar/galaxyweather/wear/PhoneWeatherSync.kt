package ru.svatozar.galaxyweather.wear

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import ru.svatozar.galaxyweather.shared.WeatherRepository
import ru.svatozar.galaxyweather.shared.WeatherSnapshot
import ru.svatozar.galaxyweather.shared.isFresh
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

internal object PhoneWeatherSync {
    fun cached(context: Context): WeatherSnapshot? = if (context.getSharedPreferences("phone_sync", Context.MODE_PRIVATE)
            .getBoolean("profile_received", false)) WeatherRepository(context).cached() else null

    fun load(context: Context): Result<WeatherSnapshot> {
        read(context)?.let { Log.d("GalaxyWeather", "Forecast source: phone"); return Result.success(it) }
        if (!context.getSharedPreferences("phone_sync", Context.MODE_PRIVATE).getBoolean("profile_received", false)) {
            return Result.failure(IllegalStateException("Откройте приложение на телефоне для выбора профиля"))
        }
        Log.d("GalaxyWeather", "Forecast source: network/cache for synchronized profile")
        return WeatherRepository(context).load(force = true)
    }

    fun read(context: Context): WeatherSnapshot? = runCatching {
        val task = Wearable.getDataClient(context.applicationContext).dataItems
        val abandoned = AtomicBoolean(false)
        val released = AtomicBoolean(false)
        fun release(buffer: com.google.android.gms.wearable.DataItemBuffer) {
            if (released.compareAndSet(false, true)) buffer.release()
        }
        task.addOnSuccessListener(java.util.concurrent.Executor { it.run() }) { if (abandoned.get()) release(it) }
        val buffer = try { Tasks.await(task, 5, TimeUnit.SECONDS) }
        catch (error: Exception) {
            abandoned.set(true)
            if (task.isSuccessful) task.result?.let(::release)
            throw error
        }
        try {
            buffer.filter { it.uri.path == "/weather/location" }
                .maxByOrNull { DataMapItem.fromDataItem(it).dataMap.getLong("updated") }
                ?.let { accept(context, DataMapItem.fromDataItem(it).dataMap) }
        } finally { release(buffer) }
    }.onFailure { Log.w("GalaxyWeather", "Не удалось получить прогноз с телефона", it) }.getOrNull()

    @Synchronized fun accept(context: Context, map: DataMap): WeatherSnapshot? = runCatching {
        acceptChecked(context, map)
    }.getOrNull()

    private fun acceptChecked(context: Context, map: DataMap): WeatherSnapshot? {
        if (!map.containsKey("latitude") || !map.containsKey("longitude")) return null
        val latitude = map.getDouble("latitude")
        val longitude = map.getDouble("longitude")
        if (!latitude.isFinite() || latitude !in -90.0..90.0 || !longitude.isFinite() || longitude !in -180.0..180.0) return null
        val repository = WeatherRepository(context)
        val name = map.getString("name") ?: "Выбранное место"
        if (name.trim().length !in 1..200) return null
        val state = context.getSharedPreferences("phone_sync", Context.MODE_PRIVATE)
        val updated = map.getLong("updated")
        if (updated <= 0 || updated > System.currentTimeMillis() + 300_000 || updated < state.getLong("updated", 0)) return null
        repository.saveLocation(name, latitude, longitude)
        state.edit().putBoolean("profile_received", true).putLong("updated", updated).apply()
        val data = map.getString("snapshot")?.let { repository.acceptPhonePayload(it, name, latitude, longitude) }
        return data?.takeIf { it.isFresh() }
    }
}
