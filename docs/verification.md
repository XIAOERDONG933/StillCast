# Verification — 15 October 2026

## Environment and commands

macOS, JDK 21.0.11, Gradle 8.13, Android SDK 36, and an Android 15 / API 35 ARM64 emulator (`StillCast_Test`). The normal display was 1080 × 2400 px at 420 dpi. Existing SDK installations and dependency caches were reused.

```sh
sh ./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
sh ./gradlew connectedDebugAndroidTest
```

The final run passed 67 JVM tests, Android Lint (no errors; 13 target/API and dependency-version update warnings), both debug APK builds, and 17 device tests. CI runs the JVM tests, Lint and APK builds on Ubuntu and Windows; it does not run device tests.

## Regression and lifecycle checks

| Check | Method and observed result |
|---|---|
| Clock rollback | Fake wall clock and elapsed time in `AppViewModelTest`: an expired response stays expired after a rollback; a successful new response resets its age. |
| Partial coverage with no match | Fixed forecast in `UtilityAppTest`: the result names the available-hour count and offers refresh, rather than implying a full-day assessment. |
| Activity recreation | `MainActivityTest` launches the real activity, changes the duration and recreates it. The selected tab, duration and forecast survive without another request. |
| Background return | The real activity is stopped; test time advances four hours and the repository fails. On resume, expired recommendations and hourly data stay hidden. A later successful refresh restores them. |
| Change city during refresh | A controlled pending repository request overlaps city selection and activity recreation. The selected city and its result remain current when the request completes. |

The activity tests replace the repository and clocks, while using the production activity, ViewModel and planner. They do not simulate process death or a four-hour real-time wait. Settings intentionally reset after process death.

## Large text and narrow layouts

`LargeTextTest` exercises 320 × 640 dp and 640 × 320 dp at 200% font scale, plus 411 × 891 dp at normal scale. These are Compose test configuration overrides, with system insets removed for the forced viewport. The tests reach refresh, a repeated-hour daylight-saving time range, the metrics, hourly heading, city input, duration choices and all three sliders. Visible text is checked for actual line clipping and ellipsis; screenshots are captured for review.

A separate manual run used the Android system font setting at 200% and an 840 × 1680 px display override (320 × 640 dp at 420 dpi). The location, refresh action and recording time remained readable and scrollable. At large sizes, the Change button moves below the location, metrics stack vertically, and hourly rows use labelled lines. The display and font settings were restored afterwards.

## TalkBack walkthrough

The AOSP emulator had no bundled screen reader. Google's [TalkBack source](https://github.com/google/talkback/tree/229212fdf5842191d0a93fc95d9ca1423b346866) (`TfPu_release_16_2`) was built and installed, with eSpeak NG 1.52.0 as the speech engine. Local build adjustments addressed JDK/NDK compatibility; TalkBack behaviour was not modified. Touch exploration was enabled. Speech text was checked in TalkBack's debug log and on-screen speech overlay, alongside the visible focus rectangle and resulting app state.

| Task | Observed result |
|---|---|
| Read the recording window | Session length, time range, metrics and hourly-probability explanation were reachable. Each metric now reads its label, value and unit together. |
| Switch screens | Field and Settings were identified as tabs and activated by double tap. |
| Search and choose a city | Entering `os` on the screen keyboard returned results. TalkBack read the city and region; selecting Oss updated the location. |
| Change duration | The three-hour option announced its selection and changed state. |
| Adjust limits | Swipe-up adjustment changed wind from 3.0 to 3.5 m/s, gust from 6.0 to 6.5 m/s and precipitation from 20% to 30%. The updated values were announced, rather than the slider position percentage. |
| Reset and refresh | Reset restored 3.0 m/s, 6.0 m/s and 20%; refresh initiated a request and updated the retrieval time. |
| Read hourly data | Each row read date, time, match status and all three values as one node. A right swipe moved directly to the next hour without repeating the individual cells. |

This is an emulator walkthrough, not a physical-device or blind-user usability study. Audio quality, outdoor use, every Android version and every screen-reader setting have not been evaluated.

## Import check

An earlier submission archive was independently opened in Android Studio Quail 4 (2026.1.4), with JDK 21 selected in place of its default JDK 25. Sync, Run, live weather, two-character city search and all three offline previews worked. The updated source was then built and exercised with the checks above. A newly extracted final archive is also built before delivery; this reuses the same SDK and dependency cache, rather than representing a clean operating-system installation.
