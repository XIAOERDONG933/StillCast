# StillCast

StillCast is a small Android app for planning outdoor sound recordings, such as birdsong or water sounds. It finds the earliest complete 1–3 hour session in the next 24 hours that meets the user's wind, gust and precipitation limits.

## Screenshots

| Field — next recording window | Settings — session length and weather limits |
|---|---|
| <img src="docs/screenshots/field.png" alt="Field screen showing the next recording window" width="280"> | <img src="docs/screenshots/settings.png" alt="Settings screen showing session length and weather limits" width="280"> |

## Run in Android Studio

1. Open the `StillCast` folder containing `settings.gradle.kts`.
2. Select **JDK 21** under Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK.
3. Install **Android SDK Platform 36** if prompted and let Gradle Sync finish.
4. Choose the `app` configuration and an **Android 8.0 / API 26** or newer emulator or device, then click **Run**.

If Studio reports that Gradle 8.13 is incompatible with its default JDK 25, choose **Use JVM 21**. If JDK 21 is not listed, use **Download JDK** in the Gradle JDK selector, then sync again.

The forecast and city search need an internet connection. No account, API key or runtime permission is needed. The Gradle wrapper is included. A Google-hosted Maven Central mirror is configured because the standard endpoint was unavailable on the development network.

In Android Studio's Preview pane, open `UtilityAppPreview` or `SettingsScreenPreview` to inspect the screens offline using fixed example data.

## Using the app

- **Field** shows the next matching session, local start/end times, wind estimate, peak gust and highest hourly precipitation chance. Expand the hourly list to see the readings, or refresh to request new data.
- **Settings** lets you search for a city, choose a duration and change the three weather limits. Enter at least two characters; two-character searches match exact place names. Duration and limit changes use the loaded forecast immediately. Selecting another city downloads its forecast.
- Separate messages explain loading, no matching session, incomplete data and request failures. A failed refresh keeps the previous forecast and its retrieval time. Data older than one hour is marked stale; recommendations and the hourly list are hidden after three hours until refreshed. Moving the device clock backwards cannot make an expired response fresh again.
- Settings survive screen changes, rotation and returning from the background. They reset when the app process restarts.

Townsville is the starting location. Defaults are a 2-hour session, wind ≤ 3 m/s, gust ≤ 6 m/s and hourly precipitation chance ≤ 20%. These are adjustable preferences, not equipment ratings.

## Choosing a session

`WindowPlanner` checks complete, consecutive hourly intervals within the next 24 hours. Every interval must meet all three limits; a value equal to a limit is allowed. The earliest matching session is shown. Missing readings never count as zero.

Open-Meteo's gust and precipitation readings describe the hour ending at their timestamp, so the calculation uses them for that interval. Wind uses the larger reading at the two endpoints. This is an estimate, not the maximum wind measured throughout the hour. Times use the selected location's time zone, including daylight-saving changes.

The precipitation value is the highest individual hourly probability, not the probability for the whole session. Local shelter, traffic and microphone protection are outside the forecast, so the app cannot predict recording quality.

## Code structure

StillCast extends the supplied Utility App starter, keeping its single activity and two-screen bottom navigation.

| Component | What it does |
|---|---|
| `UtilityApp`, `UtilityScreen`, `SettingsScreen` | Two Material 3 screens with a bottom bar in one activity |
| `AppViewModel` | Holds screen state, handles settings changes and manages requests |
| `WeatherRepository` / `OpenMeteoRepository` | Fetches data with Retrofit, validates responses and creates application models |
| `WindowPlanner` | Calculates suitable sessions using plain Kotlin |
| `Components` | Shares controls and display formatting between screens |

`StillCastApplication` creates the repository, which is passed to the ViewModel through its constructor. The screens display state and send user actions through callbacks. This separates layout, network calls and calculations. Tests can pass in a repository with fixed responses. The project uses one module and simple two-screen switching.

## Tests

JVM tests are in `app/src/test`; Compose device tests are in `app/src/androidTest`. In Android Studio, right-click a test class and choose **Run**. Alternatively, with JDK 21 selected:

```sh
sh ./gradlew testDebugUnitTest lintDebug assembleDebug
sh ./gradlew connectedDebugAndroidTest
```

The second command needs a running emulator or device. On Windows, use `gradlew.bat` instead of `sh ./gradlew`. The `sh` form works when ZIP extraction does not preserve the wrapper's executable bit.

Tests cover time boundaries, limits, missing data, response validation, overlapping requests, settings and screen interactions.

[Android checks](https://github.com/XIAOERDONG933/StillCast/actions/workflows/android.yml) runs the JVM tests, Android Lint and debug builds on Ubuntu and Windows for each push to `main` and each pull request. Each run keeps its test reports and debug APKs for 14 days. The UI test APK is compiled in CI; running its tests still needs an emulator or device.

The 15 October 2026 validation passed 67 JVM tests, Android Lint, both debug APK builds and 17 device tests on an API 35 emulator. It includes real activity recreation/background-return tests, narrow and landscape layouts at 200% text size, and a separate TalkBack walkthrough. See [verification notes](docs/verification.md) for the methods, results and limits of these checks.

## Data sources and privacy

- Weather: [Open-Meteo](https://open-meteo.com/). Places: [GeoNames](https://www.geonames.org/), via Open-Meteo. Data: [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/). StillCast calculates the recording windows.
- API references: [forecast](https://open-meteo.com/en/docs) and [city search](https://open-meteo.com/en/docs/geocoding-api).
- City searches and coordinates are sent to Open-Meteo. No GPS, microphone or analytics are used; settings and forecasts stay in memory. See [service terms and privacy](https://open-meteo.com/en/terms).
