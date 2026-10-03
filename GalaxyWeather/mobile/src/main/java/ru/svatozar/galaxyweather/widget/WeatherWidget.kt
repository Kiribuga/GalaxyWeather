package ru.svatozar.galaxyweather.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.glance.*
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.unit.ColorProvider
import androidx.compose.ui.graphics.Color
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import ru.svatozar.galaxyweather.MainActivity
import ru.svatozar.galaxyweather.LocationSync
import ru.svatozar.galaxyweather.R
import androidx.glance.layout.*
import androidx.glance.text.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.svatozar.galaxyweather.shared.*
import java.util.concurrent.TimeUnit

class WeatherWidget : GlanceAppWidget() {
    override val sizeMode = androidx.glance.appwidget.SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = withContext(Dispatchers.IO) { WeatherRepository(context).load().getOrElse { WeatherRepository(context).cached() } }
        provideContent { WidgetContent(data, context) }
    }
}

internal fun widgetViews(context: Context, data: WeatherSnapshot?, expanded: Boolean, large: Boolean): android.widget.RemoteViews {
    return android.widget.RemoteViews(context.packageName, R.layout.widget_clock).apply {
        val opacity = context.getSharedPreferences("widget_settings", Context.MODE_PRIVATE).getInt("opacity", 80).coerceIn(0, 100)
        setInt(R.id.widget_background, "setImageAlpha", opacity * 255 / 100)
        val icon = data?.let { weatherEmoji(it.current.weatherCode, it.current.isDay) }.orEmpty()
        setTextViewText(R.id.widget_temperature, data?.let { "${kotlin.math.round(it.current.temperature).toInt()}° $icon" } ?: "—")
        setTextViewText(R.id.widget_city, data?.locationName ?: "Galaxy Weather")
        setTextViewText(R.id.widget_condition, data?.let { weatherDescription(it.current.weatherCode) } ?: "Откройте приложение")
        val updated = data?.let { "Данные " + java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(it.fetchedAtMillis)) }.orEmpty()
        val range = data?.daily?.firstOrNull()?.let { "↓ ${kotlin.math.round(it.minTemperature).toInt()}°   ↑ ${kotlin.math.round(it.maxTemperature).toInt()}°" }
        setTextViewText(R.id.widget_updated, if (expanded) range ?: updated else updated)
        setTextViewTextSize(R.id.widget_time, android.util.TypedValue.COMPLEX_UNIT_SP, if (large) 48f else 32f)
        setTextViewTextSize(R.id.widget_temperature, android.util.TypedValue.COMPLEX_UNIT_SP, if (large) 36f else 30f)
        setViewVisibility(R.id.widget_details, if (expanded) android.view.View.VISIBLE else android.view.View.GONE)
        setTextViewText(R.id.widget_metrics, data?.let { "Влажность ${it.current.humidity}%   ·   Ветер ${kotlin.math.round(it.current.windSpeed).toInt()} км/ч" } ?: "Погода пока недоступна")
        setTextViewText(R.id.widget_source, "$updated · ${data?.source.orEmpty()}   ⚙")
        setOnClickPendingIntent(R.id.widget_source, android.app.PendingIntent.getActivity(context, 1,
            android.content.Intent(context, WidgetSettingsActivity::class.java), android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE))
        removeAllViews(R.id.widget_hours)
        data?.hourly?.filter { runCatching { java.time.Instant.parse(it.time).isAfter(java.time.Instant.now()) }.getOrDefault(false) }?.take(4)?.forEach { hour ->
            val cell = android.widget.RemoteViews(context.packageName, R.layout.widget_hour).apply {
                val time = java.time.Instant.parse(hour.time).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
                setTextViewText(R.id.hour_time, time)
                val symbol = weatherEmoji(hour.weatherCode, hour.isDay)
                setTextViewText(R.id.hour_weather, "$symbol ${kotlin.math.round(hour.temperature).toInt()}°")
            }
            addView(R.id.widget_hours, cell)
        }
    }
}

@Composable private fun WidgetContent(data: WeatherSnapshot?, context: Context) {
    val size = LocalSize.current
    val expanded = size.height >= 185.dp
    val large = size.width >= 320.dp && expanded
    Box(GlanceModifier.fillMaxSize().clickable(actionStartActivity<MainActivity>())) {
        AndroidRemoteViews(widgetViews(context, data, expanded, large), GlanceModifier.fillMaxSize())
    }
}

class WeatherWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeatherWidget()
    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        WorkManager.getInstance(context).cancelUniqueWork("weather_widget")
    }
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("weather_widget", ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<WeatherWorker>(30, TimeUnit.MINUTES).setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
    }
}

class WeatherWorker(private val context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val repository = WeatherRepository(context)
        repository.load(force = true).fold({
            LocationSync.send(context, repository.configuredLocation())
            WeatherWidget().updateAll(context)
            Result.success()
        }, { Result.retry() })
    }
}

