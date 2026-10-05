package com.stillcast.app.domain

import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Finds every eligible full session within the next 24 elapsed hours, earliest first.
 *
 * Open-Meteo's gust and rain probability at T refer to the preceding hour. For [T, T+1],
 * use those readings at T+1. Wind is instantaneous, so use the greater endpoint value;
 * this remains an endpoint estimate, not a claim about an unobserved within-hour maximum.
 */
object WindowPlanner {
    fun analyze(
        forecast: Forecast,
        settings: RecordingSettings,
        now: Instant,
    ): WindowAnalysis {
        val localHour = now.atZone(forecast.zone).truncatedTo(ChronoUnit.HOURS).toInstant()
        val firstStart = if (localHour < now) localHour.plusSeconds(HOUR_SECONDS) else localHour
        val horizon = now.plus(Duration.ofHours(24))
        // Ambiguous duplicate records are treated as unavailable instead of silently choosing one.
        val pointsByTime = forecast.points.groupBy(ForecastPoint::time)
            .mapValues { (_, readings) -> readings.singleOrNull() }
        val hours = generateSequence(firstStart) { it.plusSeconds(HOUR_SECONDS) }
            .takeWhile { it.plusSeconds(HOUR_SECONDS) <= horizon }
            .map { start ->
                val end = start.plusSeconds(HOUR_SECONDS)
                val left = pointsByTime[start]
                val right = pointsByTime[end]
                val wind = if (left?.windMs?.isValidWind() == true &&
                    right?.windMs?.isValidWind() == true
                ) {
                    maxOf(left.windMs, right.windMs)
                } else {
                    null
                }
                // Nulls are visible gaps in the timeline, never zero-valued substitute readings.
                RecordingHour(start, end, wind, right?.gustMs, right?.precipitationProbability)
            }
            .toList()

        val windows = hours.windowed(settings.sessionHours)
            .filter { session ->
                session.all { matches(it, settings) } &&
                    session.zipWithNext().all { (left, right) -> left.end == right.start }
            }
            .map { session ->
                RecordingWindow(
                    start = session.first().start,
                    end = session.last().end,
                    maxWindMs = session.maxOf { requireNotNull(it.windMs) },
                    maxGustMs = session.maxOf { requireNotNull(it.gustMs) },
                    maxPrecipitationProbability = session.maxOf {
                        requireNotNull(it.precipitationProbability)
                    },
                )
            }
        return WindowAnalysis(hours, windows)
    }

    fun matches(hour: RecordingHour, settings: RecordingSettings): Boolean =
        hour.complete &&
            requireNotNull(hour.windMs) <= settings.maxWindMs &&
            requireNotNull(hour.gustMs) <= settings.maxGustMs &&
            requireNotNull(hour.precipitationProbability) <= settings.maxPrecipitationProbability

    private const val HOUR_SECONDS = 3_600L
}
