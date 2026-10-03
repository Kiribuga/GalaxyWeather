package ru.svatozar.galaxyweather.wear

import android.content.ComponentName
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.wear.compose.material.*
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.google.android.gms.wearable.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.svatozar.galaxyweather.shared.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val DayTop = Color(0xFF64A9DE)
private val DayBottom = Color(0xFF397BC3)
private val NightTop = Color(0xFF172B55)
private val NightBottom = Color(0xFF071426)
private val Glass = Color(0x33203A5D)
private val SoftWhite = Color(0xFFEAF4FF)

class WearMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ComplicationDataSourceUpdateRequester.create(
            this,
            ComponentName(this, WeatherComplicationService::class.java),
        ).requestUpdateAll()
        setContent { WearWeather(this) }
    }
}

@Composable private fun WearWeather(activity: WearMainActivity) {
    var data by remember { mutableStateOf(PhoneWeatherSync.cached(activity)) }
    var error by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var revision by remember { mutableIntStateOf(0) }
    var launchRevision by remember { mutableIntStateOf(0) }
    val listState = remember { LazyListState() }
    val focusRequester = remember { FocusRequester() }
    DisposableEffect(activity) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) { revision++; launchRevision++ } }
        val client = Wearable.getDataClient(activity)
        val listener = DataClient.OnDataChangedListener { events ->
            if (events.any { it.type == DataEvent.TYPE_CHANGED && it.dataItem.uri.path == "/weather/location" }) revision++
        }
        activity.lifecycle.addObserver(observer)
        client.addListener(listener)
        onDispose { activity.lifecycle.removeObserver(observer); client.removeListener(listener) }
    }
    LaunchedEffect(launchRevision) { listState.scrollToItem(0) }
    LaunchedEffect(launchRevision, data != null) { if (data != null) focusRequester.requestFocus() }
    LaunchedEffect(revision) {
        loading = true; error = false
        try {
            val result = withContext(Dispatchers.IO) {
                PhoneWeatherSync.load(activity)
            }
            data = result.getOrElse { PhoneWeatherSync.cached(activity) }
            error = result.isFailure
        } finally { loading = false }
    }
    val isDay = data?.current?.isDay != false
    MaterialTheme(colors = MaterialTheme.colors.copy(primary = Color.White, onSurface = Color.White)) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(if (isDay) listOf(DayTop, DayBottom) else listOf(NightTop, NightBottom)))) {
            val snapshot = data
            if (snapshot == null) EmptyWeather(loading, error) { revision++ }
            else LazyColumn(
                modifier = Modifier.fillMaxSize().testTag("weather_list").rotaryScrollable(
                    behavior = RotaryScrollableDefaults.behavior(scrollableState = listState),
                    focusRequester = focusRequester,
                ),
                state = listState,
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = PaddingValues(horizontal = 22.dp, vertical = 32.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item(key = "hero") { Hero(snapshot, loading) { revision++ } }
                item(key = "hourly") { HourlyCard(snapshot) }
                item(key = "metrics") { MetricsCard(snapshot) }
                item(key = "days-title") { Text("7 дней", fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)) }
                snapshot.daily.forEachIndexed { index, day -> item(key = "day-${day.date}") { DayRow(day, index == 0) } }
                if (error) item(key = "error") { Text("Показаны сохранённые данные", color = Color(0xFFFFD3CD), fontSize = 11.sp, textAlign = TextAlign.Center) }
                item(key = "source") {
                    val time = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(snapshot.fetchedAtMillis))
                    Text("$time · ${snapshot.source}\nCC BY 4.0", color = SoftWhite.copy(alpha = .72f), fontSize = 9.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 8.dp))
                }
            }
            if (data != null) PositionIndicator(lazyListState = listState)
        }
    }
}

@Composable private fun Hero(data: WeatherSnapshot, loading: Boolean, refresh: () -> Unit) {
    val today = data.daily.firstOrNull()
    val alpha = animateFloatAsState(if (loading) .55f else 1f, tween(250), label = "hero-alpha")
    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Chip(
            onClick = refresh,
            enabled = !loading,
            label = { Text(data.locationName, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
            colors = ChipDefaults.chipColors(backgroundColor = Color.White.copy(alpha = .14f)),
            modifier = Modifier.widthIn(max = 270.dp).height(42.dp)
        )
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text(weatherEmoji(data.current.weatherCode, data.current.isDay), fontSize = 50.sp)
            Spacer(Modifier.width(4.dp))
            Text(
                "${data.current.temperature.roundToInt()}°",
                fontSize = 60.sp,
                lineHeight = 62.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                modifier = Modifier.graphicsLayer { this.alpha = alpha.value },
                maxLines = 1,
                softWrap = false,
            )
        }
        Text(weatherDescription(data.current.weatherCode), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        today?.let { Text("↑${it.maxTemperature.roundToInt()}°   ↓${it.minTemperature.roundToInt()}°", fontSize = 17.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 4.dp)) }
    }
}

@Composable private fun HourlyCard(data: WeatherSnapshot) {
    val hours = remember(data.hourly, data.fetchedAtMillis) {
        val now = Instant.now()
        val formatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
        data.hourly.filter { runCatching { Instant.parse(it.time).isAfter(now) }.getOrDefault(false) }.take(6)
            .map { it to runCatching { formatter.format(Instant.parse(it.time)) }.getOrDefault("—") }
    }
    if (hours.isEmpty()) return
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Glass).padding(vertical = 14.dp)) {
        Text("Ближайшие часы", fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            hours.forEach { (hour, time) ->
                Column(Modifier.width(62.dp).clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = .09f)).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(time, color = SoftWhite.copy(alpha = .82f), fontSize = 10.sp)
                    Text(weatherEmoji(hour.weatherCode, hour.isDay), fontSize = 22.sp)
                    Text("${hour.temperature.roundToInt()}°", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable private fun MetricsCard(data: WeatherSnapshot) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Metric("💧", "${data.current.humidity}%", "влажность", Modifier.weight(1f))
        Metric("💨", "${data.current.windSpeed.roundToInt()}", "км/ч", Modifier.weight(1f))
    }
}

@Composable private fun Metric(icon: String, value: String, label: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(24.dp)).background(Glass).padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(icon, fontSize = 20.sp)
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(label, color = SoftWhite.copy(alpha = .76f), fontSize = 10.sp)
    }
}

@Composable private fun DayRow(day: DailyWeather, today: Boolean) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.White.copy(alpha = if (today) .15f else .07f)).padding(horizontal = 15.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(if (today) "Сегодня" else day.date.takeLast(5), modifier = Modifier.weight(1f), fontSize = 13.sp, fontWeight = if (today) FontWeight.Bold else FontWeight.Normal)
        Text(weatherEmoji(day.weatherCode), fontSize = 20.sp)
        Spacer(Modifier.width(10.dp))
        Text("${day.minTemperature.roundToInt()}°", color = SoftWhite.copy(alpha = .7f), fontSize = 14.sp)
        Text("  ${day.maxTemperature.roundToInt()}°", fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable private fun EmptyWeather(loading: Boolean, error: Boolean, refresh: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(34.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(if (loading) "☁️" else "⌁", fontSize = 54.sp)
        Text(if (loading) "Загружаем погоду" else "Прогноз недоступен", fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        if (error) Text("Проверьте подключение часов", color = SoftWhite.copy(alpha = .76f), fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
        if (!loading) CompactChip(onClick = refresh, label = { Text("Повторить") }, modifier = Modifier.padding(top = 10.dp))
    }
}
