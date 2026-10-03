package ru.svatozar.galaxyweather

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import android.widget.Toast

internal fun Context.openWebsite(url: String) {
    try { startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
    catch (_: ActivityNotFoundException) { Toast.makeText(this, "На устройстве нет приложения для открытия ссылки", Toast.LENGTH_SHORT).show() }
}
