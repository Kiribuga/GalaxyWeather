package ru.svatozar.galaxyweather.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.RemoteViews
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import ru.svatozar.galaxyweather.R

class WidgetSettingsActivity : ComponentActivity() {
    private var originalOpacity = 80
    private var saved = false

    private fun preview(opacity: Int) {
        val manager = AppWidgetManager.getInstance(this)
        val ids = manager.getAppWidgetIds(ComponentName(this, WeatherWidgetReceiver::class.java))
        if (ids.isNotEmpty()) manager.partiallyUpdateAppWidget(ids, RemoteViews(packageName, R.layout.widget_clock).apply {
            setInt(R.id.widget_background, "setImageAlpha", opacity.coerceIn(0, 100) * 255 / 100)
        })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.setGravity(Gravity.BOTTOM)
        setFinishOnTouchOutside(true)
        val prefs = getSharedPreferences("widget_settings", MODE_PRIVATE)
        originalOpacity = savedInstanceState?.getInt("originalOpacity") ?: prefs.getInt("opacity", 80)
        saved = savedInstanceState?.getBoolean("saved") ?: false
        setContent {
            var transparency by rememberSaveable { mutableFloatStateOf(100f - originalOpacity) }
            LaunchedEffect(Unit) { preview(100 - transparency.toInt()) }
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFFAFCAFF))) {
                Surface(Modifier.fillMaxWidth().padding(12.dp), shape = RoundedCornerShape(24.dp), color = Color(0xFF1A2639), contentColor = Color(0xFFF1F5FF)) {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                        Text("Прозрачность фона: ${transparency.toInt()}%", style = MaterialTheme.typography.titleMedium)
                        Slider(value = transparency, onValueChange = {
                            transparency = it
                            preview(100 - it.toInt())
                        }, valueRange = 0f..100f, modifier = Modifier.testTag("widget_transparency"))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { finish() }) { Text("Отмена") }
                            TextButton(onClick = {
                                val opacity = 100 - transparency.toInt()
                                prefs.edit().putInt("opacity", opacity).apply()
                                preview(opacity)
                                saved = true
                                val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                                setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
                                finish()
                            }) { Text("Сохранить") }
                        }
                    }
                }
            }
        }
        window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("originalOpacity", originalOpacity)
        outState.putBoolean("saved", saved)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        if (!saved && !isChangingConfigurations) preview(originalOpacity)
        super.onDestroy()
    }
}
