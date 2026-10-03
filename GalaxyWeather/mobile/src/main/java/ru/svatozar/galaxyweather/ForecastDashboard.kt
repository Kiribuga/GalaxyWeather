package ru.svatozar.galaxyweather

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.svatozar.galaxyweather.shared.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.*

private val Frost = Color(0xFFF0F6FF)
private val Mist = Color(0xFFADBED9)
private val Panel = Color(0xCC152D4C)
private val Blue = Color(0xFF88C9FF)
private val Gold = Color(0xFFFFD18C)

@Composable internal fun ForecastDashboard(data: WeatherSnapshot) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().animateContentSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.fillMaxWidth().height(300.dp)) {
            Landscape(data.current, Modifier.fillMaxSize())
            Column(Modifier.padding(top = 12.dp, start = 4.dp)) {
                Text("СЕЙЧАС", color = Mist, fontSize = 10.sp, letterSpacing = 2.sp)
                Text("${data.current.temperature.roundToInt()}°", fontSize = 82.sp, fontWeight = FontWeight.Light, letterSpacing = (-4).sp, color = Frost)
                Text(weatherDescription(data.current.weatherCode), color = Frost, fontSize = 20.sp)
                data.daily.firstOrNull()?.let {
                    Text("↑ ${it.maxTemperature.roundToInt()}°   ↓ ${it.minTemperature.roundToInt()}°", color = Mist, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
                }
            }
            Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
                val tomorrow = data.daily.firstOrNull { it.date == LocalDate.now().plusDays(1).toString() }
                Text(if (tomorrow == null) "Ваш прогноз на ближайшие дни" else "Завтра · ${weatherDescription(tomorrow.weatherCode).lowercase()}", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Frost)
                tomorrow?.let { Text("От ${it.minTemperature.roundToInt()}° до ${it.maxTemperature.roundToInt()}°", color = Mist, fontSize = 12.sp) }
            }
        }
        if (data.hourly.isNotEmpty()) HourlyPanel(data.hourly)
        DailyPanel(data.daily)
        Text("Подробности", fontSize = 21.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GaugeCard("Влажность", "${data.current.humidity}%", "Относительная", data.current.humidity / 100f, Modifier.weight(1f))
            WindCard(data.current, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GaugeCard("Давление", data.current.pressure?.roundToInt()?.toString() ?: "—", "гПа · уровень моря", ((data.current.pressure ?: 960.0) - 960).toFloat() / 100f, Modifier.weight(1f))
            GaugeCard("Облачность", data.current.cloudCover?.let { "${it.roundToInt()}%" } ?: "—", "Покрытие неба", (data.current.cloudCover ?: 0.0).toFloat() / 100f, Modifier.weight(1f))
        }
        Surface(shape = RoundedCornerShape(24.dp), color = Panel) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("${data.source} · обновлено " + java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(data.fetchedAtMillis)), color = Frost, fontSize = 12.sp)
                Text("Диапазоны рассчитаны по точкам прогноза; сегодня — оставшаяся часть дня. Время в часовом поясе устройства. Осадки указаны в миллиметрах за час, а не в процентах. Сохранённый прогноз доступен без сети.", color = Mist, fontSize = 11.sp, lineHeight = 17.sp)
                TextButton(onClick = { context.openWebsite("https://api.met.no/doc/License") }, contentPadding = PaddingValues(0.dp)) { Text("Данные MET Norway · CC BY 4.0", fontSize = 11.sp) }
            }
        }
    }
}

@Composable private fun Landscape(weather: CurrentWeather, modifier: Modifier) {
    val transition = rememberInfiniteTransition(label = "landscape")
    val phase by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(18000, easing = LinearEasing)), label = "sky")
    val glow by transition.animateFloat(.35f, .9f, infiniteRepeatable(tween(3800), RepeatMode.Reverse), label = "stars")
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val horizon = h * .70f
        val day = weather.isDay
        if (!day) repeat(28) { index ->
            val x = w * ((index * 37 % 97) / 97f)
            val y = h * ((index * 23 % 61) / 110f)
            // Keep the text side quiet and leave the illustration on the right.
            if (x > w * .45f || y > h * .49f) drawCircle(Frost.copy(alpha = if (index % 3 == 0) glow else .25f), if (index % 4 == 0) 1.7f else 1f, Offset(x, y))
        }
        val center = Offset(w * .80f, h * .22f)
        drawCircle(Brush.radialGradient(listOf((if (day) Gold else Blue).copy(alpha = .12f), Color.Transparent), center, w * .28f), w * .28f, center)
        if (day) drawCircle(Brush.radialGradient(listOf(Color(0xFFFFE7AB), Gold), center, w * .09f), w * .09f, center)
        else {
            val full = Path().apply { addOval(androidx.compose.ui.geometry.Rect(center, w * .085f)) }
            val cut = Path().apply { addOval(androidx.compose.ui.geometry.Rect(center + Offset(w * .04f, -w * .035f), w * .08f)) }
            drawPath(Path.combine(PathOperation.Difference, full, cut), Color(0xFFD7E9FF))
        }
        if (weather.weatherCode != 0) repeat(3) { i ->
            val x = w * (.53f + i * .12f + .025f * sin(phase * 2 * PI).toFloat())
            drawOval(Color(0xFF9BB3D4).copy(alpha = .16f + i * .07f), Offset(x, h * (.29f + i * .05f)), Size(w * .35f, h * .12f))
        }
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(0x6649ACD3), Color.Transparent), horizon - h * .13f, horizon + h * .12f), Offset(0f, horizon - h * .13f), Size(w, h * .30f))
        fun ridge(color: Color, base: Float, lift: Float, shift: Float) {
            val path = Path().apply {
                moveTo(0f, h)
                lineTo(0f, base)
                cubicTo(w * .15f, base - lift, w * .20f, base + lift * .2f, w * .37f, base - lift * .6f)
                cubicTo(w * .53f, base - lift * 1.6f + shift, w * .61f, base + lift * .2f, w * .76f, base - lift * .8f)
                cubicTo(w * .89f, base - lift * .4f, w * .95f, base - lift, w, base - lift * .5f)
                lineTo(w, h); close()
            }
            drawPath(path, color)
        }
        ridge(if (day) Color(0xFF356581) else Color(0xFF263D72), horizon, h * .14f, 0f)
        ridge(if (day) Color(0xFF20495E) else Color(0xFF152C4A), horizon + h * .10f, h * .09f, 6f)
        ridge(Color(0xFF0A2034), horizon + h * .22f, h * .10f, 0f)
        if (weather.weatherCode >= 51) repeat(22) { i ->
            val x = w * ((i * 41 % 101) / 101f)
            val y = ((phase * 7f + i * .13f) % 1f) * h
            if (weather.weatherCode in 71..77 || weather.weatherCode in 85..86) drawCircle(Frost.copy(alpha = .5f), 2f, Offset(x, y))
            else drawLine(Blue.copy(alpha = .28f), Offset(x, y), Offset(x - 4f, y + 15f), 1.2f)
        }
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(0xDD0A2034)), h * .78f, h))
    }
}

@Composable private fun HourlyPanel(hours: List<HourlyWeather>) {
    var expanded by remember { mutableStateOf(false) }
    val shown = if (expanded) hours else hours.take(24)
    val minimum = shown.minOf { it.temperature }.toFloat()
    val spread = max(2f, shown.maxOf { it.temperature }.toFloat() - minimum)
    Surface(shape = RoundedCornerShape(24.dp), color = Panel) {
        Column(Modifier.padding(vertical = 16.dp).animateContentSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("По часам", Modifier.weight(1f), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(0.dp)) { Text(if (expanded) "24 часа" else "48 часов", fontSize = 12.sp) }
            }
            Column(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp)) {
                Row {
                    shown.forEach { hour ->
                        val local = Instant.parse(hour.time).atZone(ZoneId.systemDefault())
                        Column(Modifier.width(62.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(local.format(DateTimeFormatter.ofPattern("HH:mm")), color = Mist, fontSize = 11.sp)
                            WeatherArt(hour.weatherCode, Modifier.size(43.dp), animated = false, isDay = hour.isDay)
                            Text("${hour.temperature.roundToInt()}°", fontSize = 17.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
                Canvas(Modifier.width(62.dp * shown.size).height(42.dp)) {
                    val slot = size.width / shown.size
                    val positions = shown.mapIndexed { index, hour -> Offset(slot * (index + .5f), size.height * (.82f - .64f * (hour.temperature.toFloat() - minimum) / spread)) }
                    val path = Path().apply { positions.forEachIndexed { index, p -> if (index == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) } }
                    drawPath(path, Brush.horizontalGradient(listOf(Blue, Gold)), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    positions.forEach { drawCircle(Frost, 2.5.dp.toPx(), it) }
                }
                Row {
                    shown.forEach { hour ->
                        Text(hour.rainMillimeters?.let { String.format(Locale.forLanguageTag("ru"), "%.1f мм", it) } ?: "—", color = Blue, fontSize = 10.sp, modifier = Modifier.width(62.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable private fun DailyPanel(days: List<DailyWeather>) {
    val low = days.minOfOrNull { it.minTemperature } ?: 0.0
    val range = max(1.0, (days.maxOfOrNull { it.maxTemperature } ?: 0.0) - low)
    Surface(shape = RoundedCornerShape(24.dp), color = Panel) {
        Column(Modifier.padding(18.dp)) {
            Text("7 дней", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 12.dp))
            days.forEachIndexed { index, day ->
                Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                    val date = LocalDate.parse(day.date)
                    Text(if (date == LocalDate.now()) "Сегодня" else date.format(DateTimeFormatter.ofPattern("EE", Locale.forLanguageTag("ru"))), Modifier.width(68.dp), fontSize = 12.sp)
                    WeatherArt(day.weatherCode, Modifier.size(38.dp), animated = false)
                    Text("${day.minTemperature.roundToInt()}°", color = Mist, fontSize = 13.sp, modifier = Modifier.width(34.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Right)
                    Canvas(Modifier.weight(1f).height(8.dp).padding(horizontal = 10.dp)) {
                        drawLine(Color(0xFF263F5D), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), size.height, StrokeCap.Round)
                        val left = size.width * ((day.minTemperature - low) / range).toFloat()
                        val right = size.width * ((day.maxTemperature - low) / range).toFloat()
                        drawLine(Brush.horizontalGradient(listOf(Blue, Gold)), Offset(left, size.height / 2), Offset(max(left + 2, right), size.height / 2), size.height, StrokeCap.Round)
                    }
                    Text("${day.maxTemperature.roundToInt()}°", fontSize = 14.sp, modifier = Modifier.width(30.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Right)
                }
                if (index < days.lastIndex) HorizontalDivider(color = Color(0x142F567B))
            }
        }
    }
}

@Composable private fun GaugeCard(title: String, value: String, subtitle: String, fraction: Float, modifier: Modifier) {
    val progress by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(900), label = "gauge")
    Surface(modifier, shape = RoundedCornerShape(24.dp), color = Panel) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, Modifier.fillMaxWidth(), color = Mist, fontSize = 12.sp)
            Box(Modifier.height(110.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(96.dp)) {
                    val inset = 7.dp.toPx(); val diameter = size.width - inset * 2
                    drawArc(Color(0xFF2C425D), 140f, 260f, false, Offset(inset, inset), Size(diameter, diameter), style = Stroke(5.dp.toPx(), cap = StrokeCap.Round))
                    drawArc(Brush.sweepGradient(listOf(Blue, Gold, Blue)), 140f, 260f * progress, false, Offset(inset, inset), Size(diameter, diameter), style = Stroke(5.dp.toPx(), cap = StrokeCap.Round))
                }
                Text(value, fontSize = 22.sp, fontWeight = FontWeight.Medium)
            }
            Text(subtitle, color = Mist, fontSize = 10.sp, maxLines = 1)
        }
    }
}

@Composable private fun WindCard(weather: CurrentWeather, modifier: Modifier) {
    val direction = weather.windDirection ?: 0.0
    val compass = listOf("С", "СВ", "В", "ЮВ", "Ю", "ЮЗ", "З", "СЗ")[((direction / 45).roundToInt() % 8 + 8) % 8]
    Surface(modifier, shape = RoundedCornerShape(24.dp), color = Panel) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Ветер", Modifier.fillMaxWidth(), color = Mist, fontSize = 12.sp)
            Box(Modifier.height(110.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(96.dp)) {
                    val r = size.width / 2 - 6.dp.toPx()
                    drawCircle(Color(0xFF2C425D), r, style = Stroke(2.dp.toPx()))
                    repeat(16) { i -> rotate(i * 22.5f) { drawLine(Mist.copy(alpha = .4f), Offset(center.x, center.y - r), Offset(center.x, center.y - r + 4.dp.toPx()), 1.dp.toPx()) } }
                    if (weather.windDirection != null) rotate(direction.toFloat()) {
                        val arrow = Path().apply { moveTo(center.x, center.y - r - 2.dp.toPx()); lineTo(center.x - 4.dp.toPx(), center.y - r + 8.dp.toPx()); lineTo(center.x + 4.dp.toPx(), center.y - r + 8.dp.toPx()); close() }
                        drawPath(arrow, Gold)
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("${weather.windSpeed.roundToInt()}", fontSize = 24.sp); Text("км/ч", fontSize = 10.sp, color = Mist) }
            }
            Text(if (weather.windDirection == null) "Направление неизвестно" else "С ${compass} · ${direction.roundToInt()}°", color = Mist, fontSize = 10.sp)
        }
    }
}
