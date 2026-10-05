package com.stillcast.app.domain

import java.time.Instant
import java.time.ZoneId

/** A named search result, not the weather model's nearby grid-cell location. */
data class Place(
    val id: Int,
    val name: String,
    val region: String = "",
    val country: String = "",
    val latitude: Double,
    val longitude: Double,
) {
    init {
        require(name.isNotBlank()) { "A place needs a name." }
        require(latitude.isFinite() && latitude in -90.0..90.0) { "Invalid latitude." }
        require(longitude.isFinite() && longitude in -180.0..180.0) { "Invalid longitude." }
    }

    val label: String
        get() = listOf(name, region, country).filter(String::isNotBlank).distinct().joinToString(", ")

    companion object {
        val TOWNSVILLE = Place(
            id = 2146142,
            name = "Townsville",
            region = "Queensland",
            country = "Australia",
            latitude = -19.26639,
            longitude = 146.80569,
        )
    }
}

/** Preferences are deliberately in memory; all thresholds include their boundary value. */
data class RecordingSettings(
    val place: Place = Place.TOWNSVILLE,
    val sessionHours: Int = 2,
    val maxWindMs: Double = 3.0,
    val maxGustMs: Double = 6.0,
    val maxPrecipitationProbability: Int = 20,
) {
    init {
        require(sessionHours in 1..24) { "A session must last between 1 and 24 hours." }
        require(maxWindMs.isValidWind()) { "Invalid wind limit." }
        require(maxGustMs.isValidWind()) { "Invalid gust limit." }
        require(maxPrecipitationProbability in 0..100) { "Invalid precipitation limit." }
    }
}

/** Gust and precipitation describe the hour ENDING at [time]; wind is instantaneous. */
data class ForecastPoint(
    val time: Instant,
    val windMs: Double?,
    val gustMs: Double?,
    val precipitationProbability: Int?,
)

data class Forecast(
    val place: Place,
    val zone: ZoneId,
    val fetchedAt: Instant,
    val points: List<ForecastPoint>,
)

/** A forward interval. Wind is the greater of the two available endpoint readings. */
data class RecordingHour(
    val start: Instant,
    val end: Instant,
    val windMs: Double?,
    val gustMs: Double?,
    val precipitationProbability: Int?,
) {
    val complete: Boolean
        get() = end == start.plusSeconds(3_600) &&
            windMs?.isValidWind() == true &&
            gustMs?.isValidWind() == true &&
            precipitationProbability != null && precipitationProbability in 0..100
}

/** Max precipitation is the highest individual hourly probability, not a session probability. */
data class RecordingWindow(
    val start: Instant,
    val end: Instant,
    val maxWindMs: Double,
    val maxGustMs: Double,
    val maxPrecipitationProbability: Int,
)

data class WindowAnalysis(
    val hours: List<RecordingHour>,
    val windows: List<RecordingWindow>,
    /** Whether any contiguous session has enough data, regardless of the selected limits. */
    val hasCompleteSession: Boolean,
) {
    val next: RecordingWindow? get() = windows.firstOrNull()
    val completeHourCount: Int get() = hours.count(RecordingHour::complete)
}

/** A generous physical sanity bound, not a recording recommendation. */
internal fun Double.isValidWind(): Boolean = isFinite() && this in 0.0..150.0
