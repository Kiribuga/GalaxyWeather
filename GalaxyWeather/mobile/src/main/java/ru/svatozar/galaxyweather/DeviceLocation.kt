package ru.svatozar.galaxyweather

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

object DeviceLocation {
    fun permitted(context: Context) = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    fun enabled(context: Context) = LocationManagerCompat.isLocationEnabled(context.getSystemService(LocationManager::class.java))

    suspend fun city(context: Context): City? {
        if (!permitted(context) || !enabled(context)) return null
        val manager = context.getSystemService(LocationManager::class.java)
        val precise = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val providers = (listOf("fused", LocationManager.NETWORK_PROVIDER) + if (precise) listOf(LocationManager.GPS_PROVIDER) else emptyList())
            .filter { it in manager.allProviders && manager.isProviderEnabled(it) }
        if (providers.isEmpty()) return null
        val location = withTimeoutOrNull(25_000) {
            suspendCancellableCoroutine<Location?> { continuation ->
                val cancellations = providers.map { android.os.CancellationSignal() }
                continuation.invokeOnCancellation { cancellations.forEach { it.cancel() } }
                var remaining = providers.size
                fun complete(location: Location?) {
                    remaining--
                    if (continuation.isActive && (location != null || remaining == 0)) {
                        continuation.resume(location)
                        cancellations.forEach { it.cancel() }
                    }
                }
                providers.forEachIndexed { index, provider ->
                    try {
                        LocationManagerCompat.getCurrentLocation(manager, provider, cancellations[index], ContextCompat.getMainExecutor(context), ::complete)
                    } catch (_: SecurityException) { complete(null) }
                }
            }
        } ?: return null
        val name = try { withTimeoutOrNull(5_000) {
            if (!Geocoder.isPresent()) return@withTimeoutOrNull null
            val geocoder = Geocoder(context, Locale("ru"))
            if (Build.VERSION.SDK_INT >= 33) {
                suspendCancellableCoroutine<String?> { continuation ->
                    geocoder.getFromLocation(location.latitude, location.longitude, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<android.location.Address>) {
                            val address = addresses.firstOrNull()
                            if (continuation.isActive) continuation.resume(address?.locality ?: address?.subAdminArea)
                        }
                        override fun onError(errorMessage: String?) { if (continuation.isActive) continuation.resume(null) }
                    })
                }
            } else withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION") // Required only on Android 8–12; asynchronous API starts at 33.
                val address = runCatching { geocoder.getFromLocation(location.latitude, location.longitude, 1)?.firstOrNull() }.getOrNull()
                address?.locality ?: address?.subAdminArea
            }
        }
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { null }
        return City(name ?: "Текущее место", "По местоположению", kotlin.math.round(location.latitude * 10000) / 10000, kotlin.math.round(location.longitude * 10000) / 10000)
    }
}
