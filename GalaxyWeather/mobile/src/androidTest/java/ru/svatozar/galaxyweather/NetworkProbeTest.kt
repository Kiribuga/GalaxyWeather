package ru.svatozar.galaxyweather

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import java.net.HttpURLConnection
import java.net.URL

@RunWith(AndroidJUnit4::class)
class NetworkProbeTest {
    @Test fun probePhoneConnection() {
        for (endpoint in listOf(
            "https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=55.7558&lon=37.6173"
        )) {
            val started = System.currentTimeMillis()
            val connection = URL(endpoint).openConnection() as HttpURLConnection
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.setRequestProperty("User-Agent", "GalaxyWeather/0.1.1 personal Android application")
            try {
                val status = connection.responseCode
                val body = (if (status < 400) connection.inputStream else connection.errorStream)
                    ?.bufferedReader()?.use { it.readText().take(500) }.orEmpty()
                Log.i("WeatherProbe", "${URL(endpoint).host}: HTTP $status, ${System.currentTimeMillis() - started} ms; " +
                    if (endpoint.endsWith("country/") || endpoint.contains("/forecast?")) body else "response received")
            } catch (error: Exception) {
                Log.i("WeatherProbe", "${URL(endpoint).host}: ${error.javaClass.simpleName}, ${System.currentTimeMillis() - started} ms")
            } finally { connection.disconnect() }
        }
    }
}
