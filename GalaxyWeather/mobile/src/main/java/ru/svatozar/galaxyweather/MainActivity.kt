package ru.svatozar.galaxyweather

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.svatozar.galaxyweather.shared.*
import ru.svatozar.galaxyweather.widget.WeatherWidget
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val Ink = Color(0xFFF1F5FF)
private val Muted = Color(0xFFAAB8D6)
private val Accent = Color(0xFFAFCAFF)
private val Glass = Color(0xAA182842)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val repository = remember { WeatherRepository(applicationContext) }
            MaterialTheme(colorScheme = darkColorScheme(primary = Accent, onPrimary = Color(0xFF102B54), surface = Color(0xFF14243C), onSurface = Ink, background = Color(0xFF071321))) {
                CompositionLocalProvider(LocalContentColor provides Ink) { WeatherApp(repository) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun WeatherApp(repository: WeatherRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val saved = remember { SavedCities(context) }
    var cities by remember { mutableStateOf(saved.read()) }
    var automatic by remember { mutableStateOf(saved.automatic) }
    var askLocation by remember { mutableStateOf(false) }
    var waitingForSettings by remember { mutableStateOf(false) }
    var snapshot by remember { mutableStateOf(repository.cached()) }
    var city by remember { mutableStateOf(repository.configuredLocation().first) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showSearch by remember { mutableStateOf(false) }
    var refreshRevision by remember { mutableIntStateOf(0) }
    var refreshJob by remember { mutableStateOf<Job?>(null) }
    val sky by animateColorAsState(if (snapshot?.current?.isDay == true) Color(0xFF366478) else Color(0xFF132B50), tween(900), label = "sky-color")
    fun refresh(locate: Boolean = automatic) {
        val revision = ++refreshRevision
        refreshJob?.cancel()
        refreshJob = scope.launch {
            loading = true; error = null
            try {
                if (locate) {
                    val place = try { DeviceLocation.city(context) }
                    catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                    catch (_: Exception) { null }
                    if (place != null) {
                        repository.saveLocation(place.name, place.latitude, place.longitude)
                        city = place.name
                        snapshot = repository.cached()
                        LocationSync.send(context, repository.configuredLocation())
                    } else error = "Местоположение недоступно. Обновляем текущий город."
                }
                val result = withContext(Dispatchers.IO) { repository.load(force = true) }
                if (revision != refreshRevision) return@launch
                result.onSuccess {
                    snapshot = it
                    LocationSync.send(context, repository.configuredLocation())
                }.onFailure {
                    android.util.Log.w("GalaxyWeather", "Weather request failed", it)
                    error = when (it) {
                        is java.net.SocketTimeoutException -> "Сервис не ответил вовремя. Попробуйте ещё раз."
                        is java.net.UnknownHostException -> "Не удаётся найти сервер. Проверьте подключение."
                        else -> "Не удалось обновить прогноз. Попробуйте ещё раз."
                    }
                }
                WeatherWidget().updateAll(context)
            } finally { if (revision == refreshRevision) loading = false }
        }
    }
    fun useAutomatic() {
        automatic = true; saved.automatic = true
        refresh(true)
    }
    fun fallback() {
        askLocation = false
        refresh(false)
    }
    val settingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        waitingForSettings = false
        if (DeviceLocation.enabled(context) && DeviceLocation.permitted(context)) useAutomatic() else fallback()
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        val granted = permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted && DeviceLocation.enabled(context)) useAutomatic()
        else if (granted) askLocation = true
        else fallback()
    }
    fun requestLocation() {
        saved.prompted = true
        if (!DeviceLocation.permitted(context)) permissionLauncher.launch(arrayOf(android.Manifest.permission.ACCESS_COARSE_LOCATION, android.Manifest.permission.ACCESS_FINE_LOCATION))
        else if (!DeviceLocation.enabled(context)) askLocation = true
        else useAutomatic()
    }
    val resumeRefresh by rememberUpdatedState(newValue = {
        if (!waitingForSettings && !askLocation && saved.prompted) refresh()
    })
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeRefresh()
            if (event == Lifecycle.Event.ON_STOP) { refreshJob?.cancel(); loading = false }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(automatic, lifecycle) {
        if (automatic) lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                kotlinx.coroutines.delay(5 * 60_000L)
                refresh(true)
            }
        }
    }
    LaunchedEffect(Unit) {
        LocationSync.send(context, repository.configuredLocation())
        if (!saved.prompted) { saved.prompted = true; askLocation = true; refresh(false) }
        else refresh()
    }
    if (askLocation) AlertDialog(
        onDismissRequest = { fallback() },
        title = { Text("Погода рядом с вами") },
        text = { Text("Разрешите определение города и включите местоположение. Если отказаться, продолжим показывать погоду текущего города.") },
        confirmButton = { TextButton(onClick = {
            askLocation = false
            if (!DeviceLocation.permitted(context)) requestLocation()
            else if (!DeviceLocation.enabled(context)) {
                waitingForSettings = true
                settingsLauncher.launch(Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            } else useAutomatic()
        }) { Text("Включить") } },
        dismissButton = { TextButton(onClick = { fallback() }) { Text("Не сейчас") } }
    )
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(sky, Color(0xFF0B192D), Color(0xFF070E1A))))) {
        PullToRefreshBox(isRefreshing = loading, onRefresh = { refresh() }, modifier = Modifier.fillMaxSize().safeDrawingPadding().testTag("pull_refresh").semantics { stateDescription = if (loading) "Обновление погоды" else "Обновление завершено" }) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp)) {
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("GALAXY WEATHER", color = Muted, fontSize = 10.sp, letterSpacing = 2.sp)
                    TextButton(onClick = { showSearch = true }, modifier = Modifier.testTag("city_search"), contentPadding = PaddingValues(0.dp)) {
                        Text(city, fontSize = 25.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("  ⌄", fontSize = 23.sp)
                    }
                }
                TextButton(onClick = { showSearch = true }) { Text("+ Город") }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                item {
                    FilterChip(selected = automatic, onClick = { requestLocation() }, label = { Text("Моё место") }, modifier = Modifier.testTag("location_tab"))
                }
                items(cities) { place ->
                    val current = repository.configuredLocation()
                    FilterChip(selected = !automatic && current.second == place.latitude && current.third == place.longitude,
                        onClick = {
                            refreshJob?.cancel()
                            automatic = false; saved.automatic = false
                            repository.saveLocation(place.name, place.latitude, place.longitude)
                            city = place.name; snapshot = repository.cached()
                            LocationSync.send(context, repository.configuredLocation())
                            refresh(false)
                        }, label = { Text(place.name) }, modifier = Modifier.testTag("saved_city_${place.latitude}_${place.longitude}"))
                }
            }
            AnimatedVisibility(visible = error != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                Surface(color = Color(0xFF442F40), shape = RoundedCornerShape(18.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                    Text(error.orEmpty(), Modifier.padding(16.dp), color = Color(0xFFFFCED2), fontSize = 13.sp)
                }
            }
            Crossfade(targetState = snapshot != null, animationSpec = tween(500), label = "forecast-reveal") { hasWeather ->
                if (hasWeather) snapshot?.let { ForecastDashboard(it) }
                else Column(Modifier.fillMaxWidth().padding(vertical = 50.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    WeatherArt(2, Modifier.size(160.dp))
                    Text(if (loading) "Загружаем погоду" else "Прогноз пока недоступен", fontSize = 22.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(12.dp))
                    Text("Ваш город, погода и планы на неделю", color = Muted, fontSize = 13.sp)
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = { showSearch = true }) { Text("Выбрать город") }
                    if (!loading) TextButton(onClick = { refresh() }) { Text("Повторить") }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
        }
    }
    if (showSearch) CitySheet(onDismiss = { showSearch = false }, onSelected = { selected ->
        refreshJob?.cancel()
        cities = saved.add(selected)
        automatic = false; saved.automatic = false
        repository.saveLocation(selected.name, selected.latitude, selected.longitude)
        city = selected.name
        snapshot = repository.cached()
        showSearch = false
        LocationSync.send(context, repository.configuredLocation())
        refresh(false)
    })
}

@Composable internal fun WeatherArt(code: Int, modifier: Modifier, animated: Boolean = true, isDay: Boolean = true) {
    val drift = if (animated) {
        val transition = rememberInfiniteTransition(label = "weather-motion")
        val value by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(4500, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "cloud-drift")
        value
    } else .5f
    Canvas(modifier) {
        val u = size.minDimension
        val dy = if (animated) (drift - .5f) * u * .035f else 0f
        val sunny = code in 0..2
        if (sunny) {
            val center = Offset(u * .56f, u * .39f + dy)
            drawCircle(Brush.radialGradient(listOf(Color(0x447BB5FF), Color.Transparent), center, u * .45f), u * .45f, center)
            if (isDay) {
                drawCircle(Brush.radialGradient(listOf(Color(0xFFFFE5A0), Color(0xFFFFB34A)), center, u * .22f), u * .22f, center)
                drawCircle(Color(0x44FFE1A0), u * .27f, center, style = Stroke(u * .01f))
            } else {
                val disc = androidx.compose.ui.graphics.Path().apply { addOval(androidx.compose.ui.geometry.Rect(center, u * .22f)) }
                val cut = androidx.compose.ui.graphics.Path().apply { addOval(androidx.compose.ui.geometry.Rect(center + Offset(u * .12f, -u * .09f), u * .21f)) }
                drawPath(androidx.compose.ui.graphics.Path.combine(androidx.compose.ui.graphics.PathOperation.Difference, disc, cut), Color(0xFFD5E5FF))
            }
        }
        if (code != 0) {
            val cloud = Brush.verticalGradient(listOf(Color(0xFFE0EBFF), Color(0xFF7895C2)), u * .35f, u * .73f)
            drawOval(cloud, Offset(u * .18f, u * .5f + dy), Size(u * .63f, u * .23f))
            drawCircle(cloud, u * .17f, Offset(u * .43f, u * .49f + dy))
            drawCircle(cloud, u * .13f, Offset(u * .62f, u * .52f + dy))
            if (code >= 51) repeat(3) { index ->
                val x = u * (.32f + index * .16f)
                val y = u * .8f + dy
                if (code in 71..77 || code in 85..86) drawCircle(Color(0xFFD5EEFF), u * .023f, Offset(x, y))
                else drawLine(Color(0xFF81BFFF), Offset(x, y), Offset(x - u * .028f, y + u * .06f), u * .023f, StrokeCap.Round)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun CitySheet(onDismiss: () -> Unit, onSelected: (City) -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<City>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Color(0xFF14243C)) {
        Column(Modifier.fillMaxWidth().imePadding().padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Text("Ваш город", fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
            Text("Найдите город и выберите его из списка", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp, bottom = 20.dp))
            OutlinedTextField(value = query, onValueChange = { query = it.take(100); results = emptyList(); message = null }, enabled = !loading, label = { Text("Название города") }, singleLine = true, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth())
            Button(onClick = {
                scope.launch {
                    loading = true; message = null; results = emptyList()
                    try {
                        results = withContext(Dispatchers.IO) { CitySearch.find(query) }
                        if (results.isEmpty()) message = "Ничего не найдено. Уточните название города."
                    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                    catch (error: Exception) { message = "Не удалось выполнить поиск. Проверьте интернет и повторите." }
                    finally { loading = false }
                }
            }, enabled = query.trim().length >= 2 && !loading, modifier = Modifier.fillMaxWidth().padding(top = 12.dp), contentPadding = PaddingValues(16.dp)) {
                if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Найти город")
            }
            message?.let { Text(it, color = Muted, modifier = Modifier.padding(vertical = 14.dp)) }
            LazyColumn(Modifier.heightIn(max = 300.dp).animateContentSize(), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
                items(results) { result ->
                    Surface(onClick = { onSelected(result) }, color = Color(0xFF213753), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().testTag("city_result")) {
                        Column(Modifier.padding(16.dp)) {
                            Text(result.name, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                            Text(result.description, color = Muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onDismiss) { Text("Отмена") }
                TextButton(onClick = { context.openWebsite("https://www.openstreetmap.org/copyright") }) { Text("© OpenStreetMap", fontSize = 11.sp) }
            }
        }
    }
}

