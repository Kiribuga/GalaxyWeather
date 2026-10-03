package ru.svatozar.galaxyweather.wear

import com.google.android.gms.wearable.*
import androidx.wear.tiles.TileService
import android.content.ComponentName
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester

class LocationDataListenerService : WearableListenerService() {
    override fun onDataChanged(buffer: DataEventBuffer) {
        buffer.filter { it.type == DataEvent.TYPE_CHANGED && it.dataItem.uri.path == "/weather/location" }.forEach { event ->
            PhoneWeatherSync.accept(this, DataMapItem.fromDataItem(event.dataItem).dataMap)
            TileService.getUpdater(this).requestUpdate(WeatherTileService::class.java)
            ComplicationDataSourceUpdateRequester.create(this, ComponentName(this, WeatherComplicationService::class.java)).requestUpdateAll()
        }
    }
}

