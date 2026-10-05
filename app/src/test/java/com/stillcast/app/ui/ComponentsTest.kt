package com.stillcast.app.ui

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class ComponentsTest {
    @Test
    fun `repeated New York hour identifies both daylight and standard time`() {
        val zone = ZoneId.of("America/New_York")

        assertEquals("01:00 EDT", clockTime(Instant.parse("2026-11-01T05:00:00Z"), zone))
        assertEquals("01:00 EST", clockTime(Instant.parse("2026-11-01T06:00:00Z"), zone))
    }

    @Test
    fun `ordinary local time keeps the short clock format`() {
        val zone = ZoneId.of("America/New_York")

        assertEquals("01:00", clockTime(Instant.parse("2026-10-31T05:00:00Z"), zone))
        assertEquals("02:00", clockTime(Instant.parse("2026-11-01T07:00:00Z"), zone))
    }
}
