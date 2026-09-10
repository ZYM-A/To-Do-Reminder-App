$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location -LiteralPath $projectRoot
try {
    $env:GRADLE_USER_HOME = Join-Path $projectRoot '.tools/gradle-home'
    $env:ANDROID_USER_HOME = Join-Path $projectRoot '.tools/android-user'
    $bundledSdk = Join-Path $projectRoot '.tools/android-sdk'
    if (!(Test-Path -LiteralPath 'local.properties') -and (Test-Path -LiteralPath $bundledSdk)) {
        $sdkValue = $bundledSdk.Replace('\', '/')
        Set-Content -LiteralPath 'local.properties' -Value "sdk.dir=$sdkValue" -Encoding ascii
    }
    $localGradle = Join-Path $projectRoot '.tools/gradle-8.11.1/bin/gradle.bat'
    $gradleCommand = if (Test-Path -LiteralPath $localGradle) { $localGradle } else { Join-Path $projectRoot 'gradlew.bat' }
    & $gradleCommand :app:assembleDebug :app:testDebugUnitTest :app:lintDebug --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Build failed with exit code $LASTEXITCODE" }
    Write-Output (Join-Path $projectRoot 'app/build/outputs/apk/debug/app-debug.apk')
} finally {
    Pop-Location
}
