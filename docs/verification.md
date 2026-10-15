# Test notes — 15 October 2026

## Environment and commands

Tests ran on macOS with JDK 21.0.11, Gradle 8.13 and Android SDK 36. Device tests used the `StillCast_Test` ARM64 emulator running Android 15 / API 35, normally at 1080 × 2400 px and 420 dpi. The SDK and dependencies were already installed.

```sh
sh ./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
sh ./gradlew connectedDebugAndroidTest
```

The run passed 67 JVM tests and 17 device tests. Both debug APKs built successfully. Lint reported no errors and 13 warnings about the target API and newer dependency versions. The same JVM tests, Lint checks and APK builds passed in CI on Ubuntu and Windows. CI does not run device tests.

## Regression and lifecycle checks

| Check | What was tested |
|---|---|
| Clock rollback | `AppViewModelTest` controls both clocks. Moving the clock back leaves an expired forecast expired. Fetching new data resets its age. |
| Incomplete forecast with no match | `UtilityAppTest` supplies a forecast with missing hours. The message gives the available-hour count and offers a refresh. |
| Activity recreation | `MainActivityTest` opens Settings, selects three hours and recreates the activity. The tab, duration and forecast are kept, with no extra request. |
| Return from the background | The test stops the activity, advances the test clocks by four hours and makes the next request fail. On return, the expired recommendation and hourly list stay hidden. A successful refresh brings them back. |
| Change city during refresh | A refresh is left waiting while the test selects Kyoto and recreates the activity. When requests finish, the app still shows Kyoto and its forecast. |

These activity tests use the app's activity, ViewModel and planner, with a test repository and clocks. The four-hour gap is simulated. Process death is not covered; settings reset when the app process restarts.

## Large text and narrow layouts

`LargeTextTest` checks 320 × 640 dp and 640 × 320 dp at 200% text size, plus 411 × 891 dp at normal size. It sets the size and font scale inside Compose and removes system insets for those test layouts.

The test scrolls through both screens and checks that refresh, the recording time, weather readings, hourly heading, city input, duration choices and three sliders can be reached. It includes the repeated hour at the end of daylight saving time (`01:00 EDT – 01:00 EST`). Visible text is checked for clipped lines and ellipsis, and screenshots were saved for visual review.

A separate emulator check used Android's system font setting at 200%, with the display set to 840 × 1680 px (320 × 640 dp at 420 dpi). The location and recording time were readable, and refresh was reachable. At this size, Change sits below the city name, weather readings stack vertically, and each hourly reading has its own labelled line. Scrolling worked, and the display and font settings were restored afterwards.

## TalkBack walkthrough

The emulator did not include TalkBack. It was built from Google's [source](https://github.com/google/talkback/tree/229212fdf5842191d0a93fc95d9ca1423b346866) (`TfPu_release_16_2`) and installed with eSpeak NG 1.52.0. Building it required JDK/NDK compatibility changes, with no changes to TalkBack behaviour.

With touch exploration enabled, the check followed the focus rectangle, the speech text in TalkBack's log and on-screen overlay, and the app's response to each action.

| Task | Result |
|---|---|
| Read the recording window | TalkBack could reach the session length, time range, weather readings and rain-probability explanation. Each weather reading announced its label, value and unit together. |
| Switch screens | Field and Settings were identified as tabs and activated by double tap. |
| Search and choose a city | Typing `os` on the screen keyboard returned results. TalkBack read the city and region. Selecting Oss changed the location. |
| Change duration | Selecting three hours changed the setting and announced it as selected. |
| Adjust limits | Swiping up changed wind from 3.0 to 3.5 m/s, gust from 6.0 to 6.5 m/s and rain chance from 20% to 30%. TalkBack announced the weather values rather than the slider's position percentage. |
| Reset and refresh | Reset restored 3.0 m/s, 6.0 m/s and 20%. Refresh fetched new data and updated the retrieval time. |
| Read hourly data | Each row announced its date, time, match status and three readings in one focus stop. Swiping right moved to the next hour without repeating the cells. |

The check was limited to this emulator and TalkBack setup. It did not assess audio quality or outdoor use, and did not include a physical device or testing with blind users. Other Android versions and screen-reader settings remain untested.

## Import check

The submission ZIP was extracted into a separate folder and built successfully: all 80 Gradle tasks ran, including 67 passing JVM tests. That project also synced and ran in Android Studio Quail 4 (2026.1.4). Choosing **Use JVM 21** resolved the initial Gradle compatibility error with the default JDK 25. The app installed and loaded a live Townsville forecast.

An earlier import check also covered two-character city search and all three offline previews. Both import checks used the existing SDK and dependency cache; a fresh operating-system installation has not been tested.
