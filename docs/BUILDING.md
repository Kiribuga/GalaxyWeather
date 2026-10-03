# Сборка и подпись

Откройте GalaxyWeather/ в Android Studio. Проверенная среда: JBR 25, Gradle 9.6.0, AGP 9.4.1, Compose compiler plugin 2.2.10. Установите SDK 37 (mobile) и 35 (wear/shared). Java source/target — 17. Gradle Wrapper входит в исходники.

```powershell
.\GalaxyWeather\build.ps1
# Либо из GalaxyWeather/:
.\gradlew.bat :mobile:assembleRelease :wear:assembleRelease :shared:testDebugUnitTest :mobile:lintRelease :wear:lintRelease
```

Скрипт принимает -JdkPath и -SdkPath для другой установки среды. Android Studio создаёт local.properties с путём SDK локально; файл исключён из репозитория.

| Вариант | Назначение |
| --- | --- |
| mobile debug | Проверки телефона, отладочная подпись |
| wear debug | Инструментальные проверки часов |
| wear preview | R8 и отладочная подпись для обновления проверочных сборок |
| mobile/wear release | Без debuggable; wear оптимизирован R8 |

## Постоянная подпись

**Build → Generate Signed Bundle / APK → APK**, сначала mobile, затем wear. Используйте одно хранилище, alias и ключ. Обычный assembleRelease без переданной конфигурации подписи создаёт unsigned APK.

Сохраните резервную копию ключа и паролей вне исходников. Не публикуйте .jks, .keystore, keystore.properties или пароли. Новые версии должны использовать прежний ключ. Debug/preview и опубликованный release имеют разные сертификаты.

```powershell
apksigner verify --verbose --print-certs app-release.apk
zipalign -c -P 16 -v 4 app-release.apk
```

Сертификат 0.1.3, SHA-256:

```text
d871adff49f77fdc58f66f12c703a7dd8ce6c299bfd116473f230341095f2eb7
```

[Официальная инструкция подписи](https://developer.android.com/studio/publish/app-signing).
