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
        // Use the forecast's hourly grid: a half-hour DST change can put samples at :30
        // local time. Rounding the wall clock would then invent unavailable :00 samples.
        val anchor = forecast.points.minOfOrNull(ForecastPoint::time)
            ?: now.atZone(forecast.zone).truncatedTo(ChronoUnit.HOURS).toInstant()
        val candidate = anchor.plusSeconds(Duration.between(anchor, now).toHours() * HOUR_SECONDS)
        val firstStart = if (candidate < now) candidate.plusSeconds(HOUR_SECONDS) else candidate
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

        val sessions = hours.windowed(settings.sessionHours)
        val windows = sessions.filter { session -> session.all { matches(it, settings) } }
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
        return WindowAnalysis(
            hours = hours,
            windows = windows,
            hasCompleteSession = sessions.any { session -> session.all(RecordingHour::complete) },
        )
    }

    fun matches(hour: RecordingHour, settings: RecordingSettings): Boolean =
        hour.complete &&
            requireNotNull(hour.windMs) <= settings.maxWindMs &&
            requireNotNull(hour.gustMs) <= settings.maxGustMs &&
            requireNotNull(hour.precipitationProbability) <= settings.maxPrecipitationProbability

    private const val HOUR_SECONDS = 3_600L
}
