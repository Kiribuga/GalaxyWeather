package ru.svatozar.galaxyweather.shared

data class CurrentWeather(
    val temperature: Double,
    val apparentTemperature: Double?,
    val weatherCode: Int,
    val windSpeed: Double,
    val humidity: Int,
    val observedAt: String,
    val isDay: Boolean = true,
    val pressure: Double? = null,
    val windDirection: Double? = null,
    val cloudCover: Double? = null
)

data class DailyWeather(
    val date: String,
    val minTemperature: Double,
    val maxTemperature: Double,
    val precipitationProbability: Int?,
    val weatherCode: Int
)

data class WeatherSnapshot(
    val locationName: String,
    val current: CurrentWeather,
    val daily: List<DailyWeather>,
    val fetchedAtMillis: Long,
    val source: String = "Open-Meteo",
    val hourly: List<HourlyWeather> = emptyList()
)

data class HourlyWeather(val time: String, val temperature: Double, val weatherCode: Int, val isDay: Boolean, val rainMillimeters: Double?)

fun probabilityText(value: Int?): String = value?.let { "$it%" } ?: "—"

fun weatherDescription(code: Int): String = when (code) {
    0 -> "Ясно"
    1, 2 -> "Переменная облачность"
    3 -> "Пасмурно"
    45, 48 -> "Туман"
    51, 53, 55, 56, 57 -> "Морось"
    61, 63, 65, 66, 67, 80, 81, 82 -> "Дождь"
    71, 73, 75, 77, 85, 86 -> "Снег"
    95, 96, 99 -> "Гроза"
    else -> "Нет данных"
}

fun weatherEmoji(code: Int, isDay: Boolean = true): String = when (code) {
    0 -> if (isDay) "☀️" else "🌙"
    1, 2 -> if (isDay) "🌤️" else "☁️"
    3 -> "☁️"
    45, 48 -> "🌫️"
    51, 53, 55, 56, 57 -> "🌦️"
    61, 63, 65, 66, 67, 80, 81, 82 -> "🌧️"
    71, 73, 75, 77, 85, 86 -> "🌨️"
    95, 96, 99 -> "⛈️"
    else -> "—"
}
