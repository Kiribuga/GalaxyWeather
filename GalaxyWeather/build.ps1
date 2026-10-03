param(
    [string[]]$Tasks = @(':mobile:assembleDebug', ':wear:assemblePreview', ':shared:testDebugUnitTest', ':mobile:lintDebug', ':wear:lintRelease'),
    [string]$JdkPath = 'C:\Program Files\Android\Android Studio\jbr',
    [string]$SdkPath = "$env:LOCALAPPDATA\Android\Sdk"
)
$ErrorActionPreference = 'Stop'
if (!(Test-Path "$JdkPath\bin\java.exe")) { throw "JDK не найден: $JdkPath. Укажите -JdkPath с путём к JDK Android Studio." }
if (!(Test-Path $SdkPath)) { throw "Android SDK не найден: $SdkPath" }
if (!(Test-Path "$PSScriptRoot\gradlew.bat")) { throw 'В проекте отсутствует Gradle Wrapper.' }
$env:JAVA_HOME = $JdkPath
$env:ANDROID_HOME = $SdkPath
Push-Location $PSScriptRoot
try {
    & .\gradlew.bat @Tasks --no-daemon --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Gradle завершился с кодом $LASTEXITCODE" }
} finally { Pop-Location }
