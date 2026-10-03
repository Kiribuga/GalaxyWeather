package ru.svatozar.galaxyweather.wear

import androidx.wear.protolayout.*
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.wrap
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.LayoutElementBuilders.*
import androidx.wear.protolayout.ResourceBuilders.Resources
import androidx.wear.protolayout.TimelineBuilders.Timeline
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders.Tile
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import ru.svatozar.galaxyweather.shared.*
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class WeatherTileService : TileService() {
    private val executor = Executors.newSingleThreadExecutor()
    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<Tile> = Futures.submit(java.util.concurrent.Callable {
        val data = PhoneWeatherSync.load(this).getOrElse { PhoneWeatherSync.cached(this) }
        val layout = Column.Builder().setWidth(expand()).setHeight(wrap()).setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
            .addContent(Text.Builder().setText(data?.locationName ?: "Galaxy Weather").setFontStyle(FontStyle.Builder().setSize(sp(14f)).setColor(argb(0xFF9ECBE8.toInt())).build()).build())
            .addContent(Spacer.Builder().setHeight(dp(8f)).build())
            .addContent(Text.Builder().setText(data?.let { "${weatherEmoji(it.current.weatherCode, it.current.isDay)}  ${it.current.temperature.roundToInt()}°" } ?: "Нет данных").setFontStyle(FontStyle.Builder().setSize(sp(34f)).setColor(argb(0xFFFFFFFF.toInt())).build()).build())
            .addContent(Spacer.Builder().setHeight(dp(8f)).build())
            .addContent(Text.Builder().setText(data?.let { "${weatherDescription(it.current.weatherCode)} · дождь ${probabilityText(it.daily.firstOrNull()?.precipitationProbability)}" } ?: "Откройте приложение").setFontStyle(FontStyle.Builder().setSize(sp(13f)).setColor(argb(0xFFD5E7F3.toInt())).build()).build())
            .build()
        val openApp = ActionBuilders.LaunchAction.Builder().setAndroidActivity(
            ActionBuilders.AndroidActivity.Builder().setPackageName(packageName).setClassName(WearMainActivity::class.java.name).build()).build()
        val root = Box.Builder().setWidth(expand()).setHeight(expand()).setVerticalAlignment(VERTICAL_ALIGN_CENTER)
            .setModifiers(ModifiersBuilders.Modifiers.Builder()
                .setPadding(ModifiersBuilders.Padding.Builder().setAll(dp(20f)).build())
                .setClickable(ModifiersBuilders.Clickable.Builder().setId("open_weather").setOnClick(openApp).build()).build())
            .addContent(layout).build()
        Tile.Builder().setResourcesVersion("1").setFreshnessIntervalMillis(30 * 60_000).setTileTimeline(Timeline.Builder().addTimelineEntry(TimelineBuilders.TimelineEntry.Builder().setLayout(Layout.Builder().setRoot(root).build()).build()).build()).build()
    }, executor)

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<Resources> = Futures.immediateFuture(Resources.Builder().setVersion("1").build())
}

