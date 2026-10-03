# Совместимость Galaxy Weather 0.1.3

Минимумы взяты из Gradle и проверены в APK: телефон minSdk 26 (Android 8.0), часы minSdk 30 (Android 11, Wear OS 3+). Target SDK телефона — 36, часов — 35. Требования производителя часов к телефону для сопряжения могут быть выше.

| Категория | Модели | Проверка |
| --- | --- | --- |
| Телефон | Samsung Galaxy S22 Ultra SM-S908E, Android 16/API 36 | Установка релиза, запуск, прогноз и передача на часы; ранее debug-проверки городов, геолокации, виджета и lifecycle |
| Часы | Samsung Galaxy Watch4 Classic LTE 46 мм SM-R895F, Android 16/API 36 | Установка релиза, запуск, получение прогноза телефона; ранее прокрутка, усложнение и открытие по касанию |
| Другие Android-телефоны | Samsung Galaxy S/A/Note/Z, Google Pixel, Xiaomi, OnePlus и другие с Android 8+ | Ожидаемая совместимость по API; отдельные модели не тестировались |
| Другие Wear OS-часы | Galaxy Watch4 и более новые Samsung с Wear OS; Google Pixel Watch и другие с Wear OS 3+/API 30+ | Ожидаемая совместимость по API; отдельные модели не тестировались |

Нужны штатное сопряжение, Google Play services, одинаковые applicationId и сертификат подписи. APK этого релиза имеют applicationId ru.svatozar.galaxyweather и один постоянный сертификат. Телефон может работать без часов. Часам требуется телефон для первичного выбора места.

Wear OS 2/API ниже 30, Tizen, HarmonyOS без Wear OS, Apple Watch/iOS не поддерживаются. Samsung перешёл на Wear OS с Galaxy Watch4; более ранние Galaxy Watch, Active/Active2 и Watch3 не подходят для этого APK.

Доступность MET Norway и Nominatim зависит от сети. Ответы проверялись на конкретном подключении телефона; доступность из всех российских сетей не гарантируется. VPN не является требованием приложения.

Официальные источники:

- [Wear OS Data Layer](https://developer.android.com/training/wearables/data/overview).
- [Wear OS 3 и 4: версии Android](https://developer.android.com/training/wearables/versions/4/changes).
- [Переход Samsung на Wear OS с Galaxy Watch4](https://developer.samsung.com/galaxy-watch-tizen/notice.html).
- [Сопряжение Galaxy Watch](https://www.samsung.com/us/support/answer/ANS10003455/).
