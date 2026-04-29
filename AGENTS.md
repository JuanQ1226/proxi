# AGENTS.md

## Project Shape
- Single Android app module: `:app`; package/namespace/application ID is `com.jq.proxi`.
- Main entrypoints are `app/src/main/java/com/jq/proxi/MainActivity.kt` and foreground `ProxyService.kt`.
- The app runs local proxies bound to `127.0.0.1`: SOCKS5 on `1080`, HTTP/CONNECT on `8080`; UI tells users to use `adb forward tcp:1080 tcp:1080` and `adb forward tcp:8080 tcp:8080`.

## Build And Verify
- Use the checked-in wrapper: `./gradlew`.
- Build debug APK: `./gradlew :app:assembleDebug`.
- Run local unit tests: `./gradlew :app:testDebugUnitTest`.
- Run Android instrumentation tests, requires a connected device/emulator: `./gradlew :app:connectedDebugAndroidTest`.
- Run Android lint for the debug variant: `./gradlew :app:lintDebug`.

## Toolchain Notes
- Gradle wrapper is `9.4.1`; Android Gradle Plugin is `9.2.0`; Kotlin is `2.2.10`.
- `compileSdk` uses Android SDK `36` with `minorApiLevel = 1`; install that SDK before assuming Gradle failures are code issues.
- Java source/target compatibility is `11`.

## Code Notes
- Despite Compose dependencies/theme files, `MainActivity` currently builds the UI with classic Android `View` widgets, not Compose.
- Proxy connection counters live in `ProxyStats`; both SOCKS5 and HTTP handlers update the same global stats.
- Do not commit or rely on `local.properties`; it is intentionally ignored and should only contain machine-local SDK configuration.
