package ru.svatozar.galaxyweather.wear

import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.*
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.svatozar.galaxyweather.shared.*
import kotlin.math.roundToInt

class WeatherComplicationService : SuspendingComplicationDataSourceService() {
    override fun getPreviewData(type: ComplicationType): ComplicationData? = create(type, PhoneWeatherSync.cached(this))

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val data = withContext(Dispatchers.IO) {
            PhoneWeatherSync.load(this@WeatherComplicationService).getOrElse { PhoneWeatherSync.cached(this@WeatherComplicationService) }
        }
        return create(request.complicationType, data)
    }

    private fun create(type: ComplicationType, data: WeatherSnapshot?): ComplicationData? {
        val short = data?.let { "${it.current.temperature.roundToInt()}°" } ?: "—"
        val description = data?.let { weatherDescription(it.current.weatherCode) } ?: "Нет данных"
        val symbol = data?.let { weatherEmoji(it.current.weatherCode, it.current.isDay) }
        val image = symbol?.let(::weatherSmallImage)
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, WearMainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return when (type) {
            ComplicationType.SHORT_TEXT -> ShortTextComplicationData.Builder(
                PlainComplicationText.Builder(short).build(),
                PlainComplicationText.Builder("Погода: $description").build(),
            ).setTitle(symbol?.let { PlainComplicationText.Builder(it).build() })
                .setSmallImage(image)
                .setTapAction(openApp)
                .build()
            ComplicationType.LONG_TEXT -> LongTextComplicationData.Builder(
                PlainComplicationText.Builder("$short · $description").build(),
                PlainComplicationText.Builder("Погода").build(),
            ).setSmallImage(image).setTapAction(openApp).build()
            else -> null
        }
    }

    private fun weatherSmallImage(symbol: String): SmallImage {
        val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = 72f
        }
        val baseline = 48f - (paint.ascent() + paint.descent()) / 2f
        Canvas(bitmap).drawText(symbol, 48f, baseline, paint)
        return SmallImage.Builder(Icon.createWithBitmap(bitmap), SmallImageType.ICON).build()
    }
}

