package com.stillcast.app.ui

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stillcast.app.domain.Forecast
import com.stillcast.app.domain.ForecastPoint
import com.stillcast.app.domain.Place
import com.stillcast.app.domain.RecordingSettings
import com.stillcast.app.domain.WindowPlanner
import com.stillcast.app.ui.theme.StillCastTheme
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** UI contracts use fixed data; repository and ViewModel behaviour have separate unit tests. */
@RunWith(AndroidJUnit4::class)
class UtilityAppTest {
    @get:Rule
    val compose = createComposeRule()

    private val now = Instant.parse("2026-09-28T00:00:00Z")
    private lateinit var state: MutableState<StillCastUiState>
    private lateinit var backDispatcher: OnBackPressedDispatcher
    private var refreshCount = 0
    private var lastQuery = ""
    private var selectedPlace: Place? = null

    @Test
    fun tabsAndSystemBackReturnToTheFieldScreen() {
        showApp()

        compose.onNodeWithTag("fieldTab").assertIsSelected()
        compose.onNodeWithTag("windowTime").assertTextEquals("10:00 – 12:00")
        compose.onNodeWithTag("settingsTab").performClick().assertIsSelected()
        compose.onNodeWithText("Recording preferences").assertIsDisplayed()

        // Dispatch through the host's real back handler without coupling to MainActivity.
        compose.runOnIdle { backDispatcher.onBackPressed() }
        compose.onNodeWithTag("fieldTab").assertIsSelected()
        compose.onNodeWithTag("windowTime").assertIsDisplayed()

        compose.onNodeWithTag("settingsTab").performClick()
        compose.onNodeWithTag("fieldTab").performClick().assertIsSelected()
    }

    @Test
    fun sessionSelectionImmediatelyChangesTheFieldWindow() {
        showApp()
        compose.onNodeWithTag("settingsTab").performClick()
        compose.onNodeWithTag("settingsList").performScrollToNode(hasTestTag("duration-3"))
        compose.onNodeWithTag("duration-3").performClick().assertIsSelected()

        compose.onNodeWithTag("fieldTab").performClick()
        compose.onNodeWithText("Today · 3-hour session").assertIsDisplayed()
        compose.onNodeWithTag("windowTime").assertTextEquals("10:00 – 13:00")
        compose.runOnIdle { assertEquals(0, refreshCount) }
    }

    @Test
    fun eachSliderExposesItsNewValueAndUpdatesTheFieldLimits() {
        showApp()
        compose.onNodeWithTag("settingsTab").performClick()

        setSlider("windLimit", 4f, "Wind limit, 4.0 m/s")
        setSlider("gustLimit", 8f, "Gust limit, 8.0 m/s")
        setSlider("rainLimit", 30f, "Precipitation chance limit, 30%")

        compose.onNodeWithTag("fieldTab").performClick()
        val windSummary = "Wind ≤ 4.0 m/s  ·  Gust ≤ 8.0 m/s"
        val rainSummary = "Precipitation ≤ 30%  ·  2-hour session"
        compose.onNodeWithTag("fieldList").performScrollToNode(hasText(windSummary))
        compose.onNodeWithText(windSummary).assertIsDisplayed()
        compose.onNodeWithTag("fieldList").performScrollToNode(hasText(rainSummary))
        compose.onNodeWithText(rainSummary).assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, refreshCount) }
    }

    @Test
    fun missingDataAndCompleteButUnsuitableDataHaveDifferentOutcomes() {
        showApp(forecastState(wind = null))
        compose.onNodeWithText("Not enough\nhourly data.").assertIsDisplayed()
        compose.onNodeWithText("Some forecast readings are unavailable. Try refreshing the data.")
            .assertIsDisplayed()
        compose.onNodeWithTag("windowTime").assertDoesNotExist()

        compose.runOnIdle { state.value = forecastState(wind = 7.0) }
        compose.onNodeWithText("No window fits\nyour limits yet.").assertIsDisplayed()
        compose.onNodeWithText("No complete 2-hour session meets every limit in the next 24 hours.")
            .assertIsDisplayed()
        compose.onNodeWithText("Adjust limits").performClick()
        compose.onNodeWithTag("settingsTab").assertIsSelected()
    }

    @Test
    fun loadingThenFailureProvidesAWorkingRetryAction() {
        showApp(StillCastUiState(loading = true, now = now))
        compose.onNodeWithText("Checking the\nfield conditions…").assertIsDisplayed()
        compose.onNodeWithTag("refresh").assertIsNotEnabled()

        val error = "Couldn’t load weather data. Check your connection or try again shortly."
        compose.runOnIdle { state.value = state.value.copy(loading = false, error = error) }
        compose.onNodeWithText(error).assertIsDisplayed()
        compose.onNodeWithText("Try again").performClick()
        compose.runOnIdle { assertEquals(1, refreshCount) }
        compose.onNodeWithTag("windowTime").assertDoesNotExist()
    }

    @Test
    fun citySearchRequiresAnExplicitResultSelection() {
        val kyoto = Place(1857910, "Kyoto", "Kyoto", "Japan", 35.02107, 135.75385)
        showApp()
        compose.onNodeWithTag("settingsTab").performClick()
        compose.onNodeWithTag("citySearch").performTextInput("Kyoto")
        compose.onNodeWithTag("citySearch").performImeAction()

        compose.runOnIdle {
            assertEquals("Kyoto", lastQuery)
            assertNull(selectedPlace)
            state.value = state.value.copy(
                search = PlaceSearch(query = "Kyoto", results = listOf(kyoto), completed = true),
            )
        }
        compose.onNodeWithTag("settingsList").performScrollToNode(hasTestTag("place-${kyoto.id}"))
        compose.onNodeWithTag("place-${kyoto.id}").performClick()

        compose.runOnIdle {
            assertEquals(kyoto, selectedPlace)
            assertEquals(kyoto, state.value.settings.place)
            assertEquals("", state.value.search.query)
        }
        compose.onNodeWithTag("fieldTab").performClick()
        compose.onNodeWithText("Kyoto").assertIsDisplayed()
        compose.onNodeWithText("A live forecast is needed to find your next window.")
            .assertIsDisplayed()
    }

    @Test
    fun datedForecastIsLabelledAndExpiredRecommendationIsHidden() {
        val forecast = forecastState().forecast!!
        showApp(forecastState(forecast = forecast, at = now.plusSeconds(3_600)))
        compose.onNodeWithText("This forecast was retrieved over an hour ago. Refresh before making plans.")
            .assertIsDisplayed()
        compose.onNodeWithTag("windowTime").assertTextEquals("11:00 – 13:00")

        compose.runOnIdle {
            state.value = forecastState(forecast = forecast, at = now.plusSeconds(10_800))
        }
        compose.onNodeWithText("Time for a\nfresh forecast.").assertIsDisplayed()
        compose.onNodeWithTag("windowTime").assertDoesNotExist()
        compose.onNodeWithText("Refresh forecast").performClick()
        compose.runOnIdle { assertEquals(1, refreshCount) }
    }

    @Test
    fun repeatedDaylightSavingHourShowsDifferentTimeZones() {
        val start = Instant.parse("2026-11-01T05:00:00Z")
        val place = Place(5128581, "New York", "New York", "United States", 40.71, -74.01)
        val settings = RecordingSettings(place = place, sessionHours = 1)
        val forecast = Forecast(
            place = place,
            zone = ZoneId.of("America/New_York"),
            fetchedAt = start,
            points = List(26) { index ->
                ForecastPoint(start.plusSeconds(index * 3_600L), 1.0, 2.0, 10)
            },
        )
        showApp(StillCastUiState(
            settings = settings,
            forecast = forecast,
            analysis = WindowPlanner.analyze(forecast, settings, start),
            now = start,
        ))

        compose.onNodeWithTag("windowTime")
            .assertTextEquals("01:00 EDT – 01:00 EST")
            .assertIsDisplayed()
    }

    private fun showApp(initial: StillCastUiState = forecastState()) {
        state = mutableStateOf(initial)
        compose.setContent {
            backDispatcher = checkNotNull(LocalOnBackPressedDispatcherOwner.current)
                .onBackPressedDispatcher
            StillCastTheme(darkTheme = false) {
                UtilityApp(
                    state = state.value,
                    onRefresh = { refreshCount++ },
                    onSearch = { query ->
                        lastQuery = query
                        state.value = state.value.copy(search = PlaceSearch(query = query))
                    },
                    onPlaceSelected = { place ->
                        selectedPlace = place
                        state.value = state.value.copy(
                            settings = state.value.settings.copy(place = place),
                            search = PlaceSearch(),
                            forecast = null,
                            analysis = null,
                        )
                    },
                    onSessionHours = { hours -> updateSettings { copy(sessionHours = hours) } },
                    onWindLimit = { value -> updateSettings { copy(maxWindMs = value) } },
                    onGustLimit = { value -> updateSettings { copy(maxGustMs = value) } },
                    onRainLimit = { value ->
                        updateSettings { copy(maxPrecipitationProbability = value) }
                    },
                    onReset = { updateSettings { RecordingSettings(place = place) } },
                )
            }
        }
    }

    private fun updateSettings(change: RecordingSettings.() -> RecordingSettings) {
        val settings = state.value.settings.change()
        state.value = state.value.copy(
            settings = settings,
            analysis = state.value.forecast?.let { WindowPlanner.analyze(it, settings, state.value.now) },
        )
    }

    private fun setSlider(tag: String, value: Float, description: String) {
        compose.onNodeWithTag("settingsList").performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.SetProgress) { it(value) }
        compose.onNodeWithTag(tag).assertContentDescriptionEquals(description)
    }

    private fun forecastState(
        wind: Double? = 1.0,
        forecast: Forecast = Forecast(
            place = Place.TOWNSVILLE,
            zone = ZoneId.of("Australia/Brisbane"),
            fetchedAt = now,
            points = List(26) { index ->
                ForecastPoint(now.plusSeconds(index * 3_600L), wind, 2.0, 10)
            },
        ),
        at: Instant = now,
    ): StillCastUiState {
        val settings = RecordingSettings()
        return StillCastUiState(
            settings = settings,
            forecast = forecast,
            analysis = WindowPlanner.analyze(forecast, settings, at),
            now = at,
        )
    }
}
