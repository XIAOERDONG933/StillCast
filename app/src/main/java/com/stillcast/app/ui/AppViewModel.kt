package com.stillcast.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stillcast.app.data.ForecastDataException
import com.stillcast.app.domain.Forecast
import com.stillcast.app.domain.Place
import com.stillcast.app.domain.RecordingSettings
import com.stillcast.app.domain.WeatherRepository
import com.stillcast.app.domain.WindowAnalysis
import com.stillcast.app.domain.WindowPlanner
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlaceSearch(
    val query: String = "",
    val results: List<Place> = emptyList(),
    val loading: Boolean = false,
    val completed: Boolean = false,
    val error: String? = null,
)

data class StillCastUiState(
    val settings: RecordingSettings = RecordingSettings(),
    val forecast: Forecast? = null,
    val analysis: WindowAnalysis? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val search: PlaceSearch = PlaceSearch(),
    val now: Instant = Instant.now(),
) {
    val stale: Boolean get() = forecast?.let {
        Duration.between(it.fetchedAt, now) >= Duration.ofHours(1)
    } ?: false

    // Recommendations expire even if a disconnected device still has old forecast values.
    val expired: Boolean get() = forecast?.let {
        Duration.between(it.fetchedAt, now) >= Duration.ofHours(3)
    } ?: false
}

class AppViewModel(
    private val repository: WeatherRepository,
    private val clock: Clock = Clock.systemUTC(),
) : ViewModel() {
    private val mutableState = MutableStateFlow(StillCastUiState(now = clock.instant()))
    val state = mutableState.asStateFlow()
    private var forecastJob: Job? = null
    private var searchJob: Job? = null
    private var minuteJob: Job? = null
    private var forecastGeneration = 0
    private var searchGeneration = 0

    init { refresh() }

    fun refresh() {
        forecastJob?.cancel()
        val generation = ++forecastGeneration
        val place = mutableState.value.settings.place
        mutableState.update { it.copy(loading = true, error = null, now = clock.instant()) }
        forecastJob = viewModelScope.launch {
            try {
                val forecast = repository.forecast(place)
                if (generation == forecastGeneration && mutableState.value.settings.place == place) {
                    mutableState.update {
                        it.copy(forecast = forecast, loading = false, error = null, now = clock.instant())
                            .recalculate()
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (generation == forecastGeneration) {
                    mutableState.update {
                        it.copy(loading = false, error = requestMessage(error), now = clock.instant())
                            .recalculate()
                    }
                }
            }
        }
    }

    fun search(query: String) {
        searchJob?.cancel()
        val generation = ++searchGeneration
        val trimmed = query.trim()
        mutableState.update { it.copy(search = PlaceSearch(query = query, loading = trimmed.length >= 3)) }
        if (trimmed.length < 3) return
        searchJob = viewModelScope.launch {
            delay(350)
            try {
                val results = repository.searchPlaces(trimmed)
                if (generation == searchGeneration) {
                    mutableState.update { it.copy(search = PlaceSearch(query, results, completed = true)) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (generation == searchGeneration) {
                    mutableState.update {
                        it.copy(search = PlaceSearch(query, completed = true, error = requestMessage(error)))
                    }
                }
            }
        }
    }

    fun selectPlace(place: Place) {
        searchJob?.cancel()
        searchGeneration++
        val changed = place != mutableState.value.settings.place
        mutableState.update {
            it.copy(
                settings = it.settings.copy(place = place),
                search = PlaceSearch(),
                forecast = if (changed) null else it.forecast,
                analysis = if (changed) null else it.analysis,
                error = null,
            )
        }
        if (changed) refresh()
    }

    fun setSessionHours(hours: Int) = changeSettings { copy(sessionHours = hours.coerceIn(1, 3)) }
    fun setWindLimit(value: Double) = changeSettings { copy(maxWindMs = value.coerceIn(1.0, 8.0)) }
    fun setGustLimit(value: Double) = changeSettings { copy(maxGustMs = value.coerceIn(2.0, 16.0)) }
    fun setRainLimit(value: Int) = changeSettings {
        copy(maxPrecipitationProbability = value.coerceIn(0, 80))
    }

    fun resetLimits() = changeSettings {
        RecordingSettings(place = place)
    }

    /** A single timer lives only while the app is foregrounded; it never fetches every minute. */
    fun onResume() {
        tick()
        val forecast = mutableState.value.forecast
        if (!mutableState.value.loading && forecast != null &&
            Duration.between(forecast.fetchedAt, clock.instant()) >= Duration.ofMinutes(15)
        ) refresh()
        minuteJob?.cancel()
        minuteJob = viewModelScope.launch {
            while (isActive) {
                delay(60_000)
                tick()
            }
        }
    }

    fun onBackground() {
        minuteJob?.cancel()
        minuteJob = null
    }

    private fun tick() {
        mutableState.update { it.copy(now = clock.instant()).recalculate() }
    }

    private fun changeSettings(change: RecordingSettings.() -> RecordingSettings) {
        mutableState.update {
            it.copy(settings = it.settings.change(), now = clock.instant()).recalculate()
        }
    }

    private fun StillCastUiState.recalculate(): StillCastUiState = copy(
        analysis = forecast?.takeIf { it.place == settings.place }
            ?.let { WindowPlanner.analyze(it, settings, now) },
    )

    private fun requestMessage(error: Exception): String = when (error) {
        is ForecastDataException -> "The service returned incomplete or unexpected data. Please try again."
        is IOException -> "Couldn’t load weather data. Check your connection or try again shortly."
        else -> "The service returned incomplete or unexpected data. Please try again."
    }
}
