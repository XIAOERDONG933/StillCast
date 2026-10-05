package com.stillcast.app.domain

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowPlannerTest {
    private val base = Instant.parse("2026-09-28T00:00:00Z")
    private val settings = RecordingSettings()

    @Test
    fun `first full matching session wins rather than the calmest later session`() {
        val result = analyze(points(26).mapIndexed { index, point ->
            point.copy(windMs = if (index < 3) 3.0 else 1.0)
        })
        assertEquals(base, result.next?.start)
        assertEquals(base.plusSeconds(7_200), result.next?.end)
        assertEquals(3.0, result.next!!.maxWindMs, 0.0)
    }

    @Test
    fun `all three limits are inclusive`() {
        val result = analyze(points(3, wind = 3.0, gust = 6.0, rain = 20))
        assertNotNull(result.next)
        assertEquals(20, result.next!!.maxPrecipitationProbability)
    }

    @Test
    fun `each metric independently excludes a session above its limit`() {
        listOf(
            points(3, wind = 3.01),
            points(3, gust = 6.01),
            points(3, rain = 21),
        ).forEach { samples -> assertNull(analyze(samples).next) }
    }

    @Test
    fun `one failing hour prevents every overlapping multi hour session`() {
        val samples = points(7).mapIndexed { index, point ->
            point.copy(gustMs = if (index == 2) 10.0 else 2.0)
        }
        val result = analyze(samples)
        assertEquals(base.plusSeconds(7_200), result.next?.start)
        assertTrue(result.windows.none { it.start < base.plusSeconds(7_200) })
    }

    @Test
    fun `gust and precipitation use the hour ending at the right endpoint`() {
        val samples = listOf(
            ForecastPoint(base, 1.0, 100.0, 100),
            ForecastPoint(base.plusSeconds(3_600), 2.0, 4.0, 10),
            ForecastPoint(base.plusSeconds(7_200), 1.0, 5.0, 20),
        )
        val result = analyze(samples)
        assertEquals(4.0, result.hours[0].gustMs!!, 0.0)
        assertEquals(10, result.hours[0].precipitationProbability)
        assertEquals(5.0, result.next!!.maxGustMs, 0.0)
        // This is max(10, 20), not 1 - (1 - .10) * (1 - .20).
        assertEquals(20, result.next!!.maxPrecipitationProbability)
    }

    @Test
    fun `wind uses both endpoints so a rise at session end is not ignored`() {
        val samples = points(3).mapIndexed { index, point ->
            point.copy(windMs = if (index == 2) 8.0 else 1.0)
        }
        val result = analyze(samples)
        assertEquals(8.0, result.hours[1].windMs!!, 0.0)
        assertNull(result.next)
    }

    @Test
    fun `missing observation is unavailable not calm and no session crosses it`() {
        val samples = points(6).mapIndexed { index, point ->
            if (index == 1) point.copy(windMs = null) else point
        }
        val result = analyze(samples)
        assertNull(result.hours[0].windMs)
        assertFalse(result.hours[0].complete)
        assertFalse(result.hours[1].complete)
        assertEquals(base.plusSeconds(7_200), result.next?.start)
        assertEquals(3, result.completeHourCount)
    }

    @Test
    fun `missing gust or probability makes the interval unavailable`() {
        val missingGust = points(3).mapIndexed { index, point ->
            if (index == 1) point.copy(gustMs = null) else point
        }
        val missingRain = points(3).mapIndexed { index, point ->
            if (index == 1) point.copy(precipitationProbability = null) else point
        }
        assertNull(analyze(missingGust).next)
        assertNull(analyze(missingRain).next)
    }

    @Test
    fun `gapped timestamps cannot become a synthetic contiguous session`() {
        val result = analyze(points(6).filterIndexed { index, _ -> index != 1 })
        assertFalse(result.hours[0].complete)
        assertFalse(result.hours[1].complete)
        assertEquals(base.plusSeconds(7_200), result.next?.start)
    }

    @Test
    fun `duplicate timestamps are treated as ambiguous observations`() {
        val samples = points(4)
        val result = analyze(samples + samples[1].copy(windMs = 8.0))
        assertFalse(result.hours[0].complete)
        assertFalse(result.hours[1].complete)
        assertNull(result.next)
    }

    @Test
    fun `unavailable endpoint prevents a session extending beyond the final reading`() {
        val result = analyze(points(2))
        assertEquals(1, result.completeHourCount)
        assertNull(result.next)
    }

    @Test
    fun `exact hour can start now but a partly elapsed hour is skipped`() {
        val samples = points(27)
        assertEquals(base, analyze(samples, now = base).next?.start)
        assertEquals(
            base.plusSeconds(3_600),
            analyze(samples, now = base.plusNanos(1)).next?.start,
        )
    }

    @Test
    fun `entire session must finish within 24 elapsed hours from now`() {
        val now = base.plusSeconds(1_800)
        val result = analyze(points(30), now = now)
        assertEquals(23, result.hours.size)
        assertEquals(base.plusSeconds(24 * 3_600), result.windows.last().end)
        assertTrue(result.windows.all { it.end <= now.plus(Duration.ofHours(24)) })
    }

    @Test
    fun `horizon includes exactly 24 hours when now is an hour boundary`() {
        val result = analyze(points(30))
        assertEquals(24, result.hours.size)
        assertEquals(base.plus(Duration.ofHours(24)), result.windows.last().end)
        assertEquals(23, result.windows.size)
    }

    @Test
    fun `local midnight is crossed without resetting the session`() {
        val start = Instant.parse("2026-09-28T13:00:00Z") // Brisbane 23:00.
        val result = analyze(points(4, start = start), now = start, zone = "Australia/Brisbane")
        assertEquals(start, result.next?.start)
        assertEquals(start.plusSeconds(7_200), result.next?.end)
        assertEquals(1, result.next!!.end.atZone(ZoneId.of("Australia/Brisbane")).hour)
    }

    @Test
    fun `fractional UTC offsets still begin at the next LOCAL hour`() {
        val start = Instant.parse("2026-09-28T04:30:00Z") // Kolkata 10:00.
        val result = analyze(
            points(5, start = start),
            now = start.plusSeconds(600),
            zone = "Asia/Kolkata",
        )
        assertEquals(start.plusSeconds(3_600), result.next?.start)
        assertEquals(0, result.next!!.start.atZone(ZoneId.of("Asia/Kolkata")).minute)
    }

    @Test
    fun `spring DST jump is a continuous real hour`() {
        val start = Instant.parse("2026-03-08T06:00:00Z") // New York 01:00 EST.
        val result = analyze(points(5, start = start), now = start, zone = "America/New_York")
        assertEquals(2L, Duration.between(result.next!!.start, result.next!!.end).toHours())
        assertEquals(4, result.next!!.end.atZone(ZoneId.of("America/New_York")).hour)
    }

    @Test
    fun `autumn repeated local hour remains two distinct instants`() {
        val start = Instant.parse("2026-11-01T05:00:00Z") // First New York 01:00.
        val result = analyze(points(5, start = start), now = start, zone = "America/New_York")
        val zone = ZoneId.of("America/New_York")
        assertEquals(1, result.hours[0].start.atZone(zone).hour)
        assertEquals(1, result.hours[1].start.atZone(zone).hour)
        assertEquals(2L, Duration.between(result.next!!.start, result.next!!.end).toHours())
    }

    @Test
    fun `one hour preference can use an isolated complete interval`() {
        val forecast = forecast(points(2))
        val result = WindowPlanner.analyze(forecast, settings.copy(sessionHours = 1), base)
        assertEquals(1, result.windows.size)
    }

    @Test
    fun `invalid domain readings never qualify even without repository validation`() {
        val invalidHours = listOf(
            RecordingHour(base, base.plusSeconds(3_600), Double.NaN, 1.0, 0),
            RecordingHour(base, base.plusSeconds(3_600), 1.0, -1.0, 0),
            RecordingHour(base, base.plusSeconds(3_600), 1.0, 1.0, -1),
            RecordingHour(base, base.plusSeconds(7_200), 1.0, 1.0, 0),
        )
        invalidHours.forEach { assertFalse(WindowPlanner.matches(it, settings)) }
    }

    @Test
    fun `empty forecast reports zero complete intervals and no window`() {
        val result = analyze(emptyList())
        assertEquals(0, result.completeHourCount)
        assertNull(result.next)
        assertTrue(result.hours.all { !it.complete })
    }

    private fun analyze(
        samples: List<ForecastPoint>,
        now: Instant = base,
        zone: String = "UTC",
    ) = WindowPlanner.analyze(forecast(samples, zone), settings, now)

    private fun forecast(samples: List<ForecastPoint>, zone: String = "UTC") = Forecast(
        Place.TOWNSVILLE, ZoneId.of(zone), base, samples,
    )

    private fun points(
        count: Int,
        start: Instant = base,
        wind: Double = 1.0,
        gust: Double = 2.0,
        rain: Int = 10,
    ) = List(count) { index ->
        ForecastPoint(start.plusSeconds(index * 3_600L), wind, gust, rain)
    }
}
