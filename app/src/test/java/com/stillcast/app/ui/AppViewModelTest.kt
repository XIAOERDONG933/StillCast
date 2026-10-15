package com.stillcast.app.ui

import androidx.lifecycle.ViewModelStore
import com.stillcast.app.domain.Forecast
import com.stillcast.app.domain.ForecastPoint
import com.stillcast.app.domain.Place
import com.stillcast.app.domain.WeatherRepository
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {
    private val base = Instant.parse("2026-09-28T00:00:00Z")
    private val clock = MutableClock(base)
    private var elapsedMillis = 0L
    private val stores = mutableListOf<ViewModelStore>()
    private val secondPlace = Place(42, "Cairns", "Queensland", "Australia", -16.92, 145.77)

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        stores.forEach(ViewModelStore::clear)
        Dispatchers.resetMain()
    }

    @Test
    fun `limits and session length recalculate immediately without another HTTP request`() = runTest {
        val repository = FakeRepository().apply {
            onForecast = { sampleForecast(it, wind = 3.0) }
        }
        val model = createModel(repository)
        runCurrent()
        assertNotNull(model.state.value.analysis?.next)

        model.setWindLimit(2.0)
        assertNull(model.state.value.analysis?.next)
        model.setWindLimit(4.0)
        model.setSessionHours(3)
        val session = model.state.value.analysis!!.next!!
        assertEquals(3L, Duration.between(session.start, session.end).toHours())
        assertEquals(1, repository.forecastRequests.size)
        assertEquals(4.0, model.state.value.settings.maxWindMs, 0.0)
    }

    @Test
    fun `late previous location response never replaces selected location data`() = runTest {
        val pending = mutableMapOf<Place, Continuation<Forecast>>()
        val repository = FakeRepository().apply {
            // Deliberately ignores cancellation: tests the generation guard as well as cancellation.
            onForecast = { place -> suspendCoroutine { pending[place] = it } }
        }
        val model = createModel(repository)
        runCurrent()
        model.selectPlace(secondPlace)
        runCurrent()
        assertNull(model.state.value.forecast)
        assertTrue(model.state.value.loading)

        val currentForecast = sampleForecast(secondPlace)
        pending.getValue(secondPlace).resume(currentForecast)
        runCurrent()
        pending.getValue(Place.TOWNSVILLE).resume(sampleForecast(Place.TOWNSVILLE))
        runCurrent()

        assertEquals(secondPlace, model.state.value.settings.place)
        assertSame(currentForecast, model.state.value.forecast)
        assertFalse(model.state.value.loading)
        assertEquals(listOf(Place.TOWNSVILLE, secondPlace), repository.forecastRequests)
    }

    @Test
    fun `failed refresh retains original dated data while exposing failure and staleness`() = runTest {
        val original = sampleForecast(Place.TOWNSVILLE)
        val repository = FakeRepository().apply { onForecast = { original } }
        val model = createModel(repository)
        runCurrent()

        clock.current = base.plus(Duration.ofMinutes(90))
        repository.onForecast = { throw IOException("Offline") }
        model.refresh()
        runCurrent()

        val state = model.state.value
        assertSame(original, state.forecast)
        assertEquals(base, state.forecast!!.fetchedAt)
        assertNotNull(state.error)
        assertFalse(state.loading)
        assertTrue(state.stale)
        assertFalse(state.expired)
        // Retained readings are re-evaluated for now, not their original download time.
        assertEquals(base.plus(Duration.ofHours(2)), state.analysis!!.next!!.start)
    }

    @Test
    fun `two character place names are searched after the debounce`() = runTest {
        val beijing = Place(1816670, "Beijing", "", "China", 39.91, 116.40)
        val repository = FakeRepository().apply {
            onForecast = { sampleForecast(it) }
            onSearch = { listOf(beijing) }
        }
        val model = createModel(repository)
        runCurrent()

        model.search(" 北京 ")
        assertTrue(model.state.value.search.loading)
        advanceTimeBy(349)
        runCurrent()
        assertTrue(repository.searchRequests.isEmpty())

        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf("北京"), repository.searchRequests)
        assertEquals(listOf(beijing), model.state.value.search.results)
        assertTrue(model.state.value.search.completed)
        assertFalse(model.state.value.search.loading)
    }

    @Test
    fun `shortening a pending search to one character cancels the request`() = runTest {
        val repository = FakeRepository().apply { onForecast = { sampleForecast(it) } }
        val model = createModel(repository)
        runCurrent()

        model.search("北京")
        advanceTimeBy(200)
        model.search("北")
        advanceTimeBy(350)
        runCurrent()

        assertTrue(repository.searchRequests.isEmpty())
        assertEquals("北", model.state.value.search.query)
        assertFalse(model.state.value.search.loading)
        assertFalse(model.state.value.search.completed)
        assertTrue(model.state.value.search.results.isEmpty())
    }

    @Test
    fun `search debounces typing and ignores a cancelled requests late result`() = runTest {
        val pending = mutableMapOf<String, Continuation<List<Place>>>()
        val repository = FakeRepository().apply {
            onForecast = { sampleForecast(it) }
            onSearch = { query -> suspendCoroutine { pending[query] = it } }
        }
        val model = createModel(repository)
        runCurrent()

        model.search("Tow")
        runCurrent()
        advanceTimeBy(200)
        model.search("Townsville")
        runCurrent()
        advanceTimeBy(350)
        runCurrent()
        assertEquals(listOf("Townsville"), repository.searchRequests)

        model.search("Cairns")
        runCurrent()
        advanceTimeBy(350)
        runCurrent()
        pending.getValue("Cairns").resume(listOf(secondPlace))
        runCurrent()
        pending.getValue("Townsville").resume(listOf(Place.TOWNSVILLE))
        runCurrent()

        assertEquals("Cairns", model.state.value.search.query)
        assertEquals(listOf(secondPlace), model.state.value.search.results)
        assertTrue(model.state.value.search.completed)
        assertFalse(model.state.value.search.loading)
    }

    @Test
    fun `foreground ticks advance recommendations without fetching and stop in background`() = runTest {
        val repository = FakeRepository().apply { onForecast = { sampleForecast(it) } }
        val model = createModel(repository)
        runCurrent()
        model.onResume()
        runCurrent()

        clock.current = base.plusSeconds(60)
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(clock.current, model.state.value.now)
        assertEquals(base.plusSeconds(3_600), model.state.value.analysis!!.next!!.start)
        assertEquals(1, repository.forecastRequests.size)

        model.onBackground()
        clock.current = base.plusSeconds(120)
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(base.plusSeconds(60), model.state.value.now)
        assertEquals(1, repository.forecastRequests.size)
    }

    @Test
    fun `a session is removed just after its start without another request`() = runTest {
        clock.current = base.plusSeconds(3_560)
        val repository = FakeRepository().apply {
            onForecast = { sampleForecast(it).copy(fetchedAt = clock.current) }
        }
        val model = createModel(repository)
        runCurrent()
        model.onResume()
        try {
            runCurrent()

            clock.current = base.plusSeconds(3_599)
            advanceTimeBy(39_000)
            runCurrent()
            assertEquals(base.plusSeconds(3_600), model.state.value.analysis!!.next!!.start)

            clock.current = base.plusSeconds(3_600)
            advanceTimeBy(1_000)
            runCurrent()
            assertEquals(base.plusSeconds(3_600), model.state.value.analysis!!.next!!.start)

            clock.current = base.plusSeconds(3_600).plusMillis(1)
            advanceTimeBy(1)
            runCurrent()
            assertEquals(base.plusSeconds(7_200), model.state.value.analysis!!.next!!.start)
            assertEquals(1, repository.forecastRequests.size)
        } finally {
            model.onBackground()
        }
    }

    @Test
    fun `a forecast arriving during the timer wait schedules its approaching boundary`() = runTest {
        clock.current = base.plusSeconds(3_560)
        val pending = CompletableDeferred<Forecast>()
        val repository = FakeRepository().apply { onForecast = { pending.await() } }
        val model = createModel(repository)
        runCurrent()
        model.onResume()
        try {
            runCurrent()

            clock.current = base.plusSeconds(3_590)
            advanceTimeBy(30_000)
            pending.complete(sampleForecast(Place.TOWNSVILLE).copy(fetchedAt = clock.current))
            runCurrent()
            assertEquals(base.plusSeconds(3_600), model.state.value.analysis!!.next!!.start)

            clock.current = base.plusSeconds(3_600).plusMillis(1)
            advanceTimeBy(10_001)
            runCurrent()
            assertEquals(base.plusSeconds(7_200), model.state.value.analysis!!.next!!.start)
            assertEquals(1, repository.forecastRequests.size)
        } finally {
            model.onBackground()
        }
    }

    @Test
    fun `refresh recalculates retained data before a slow response arrives`() = runTest {
        val original = sampleForecast(Place.TOWNSVILLE)
        val repository = FakeRepository().apply { onForecast = { original } }
        val model = createModel(repository)
        runCurrent()
        val pending = CompletableDeferred<Forecast>()
        repository.onForecast = { pending.await() }

        clock.current = base.plusMillis(1)
        model.refresh()
        runCurrent()
        assertTrue(model.state.value.loading)
        assertSame(original, model.state.value.forecast)
        assertEquals(base.plusSeconds(3_600), model.state.value.analysis!!.next!!.start)

        pending.complete(original.copy(fetchedAt = clock.current))
        runCurrent()
        assertFalse(model.state.value.loading)
    }

    @Test
    fun `unexpected failures show a general error and allow recovery`() = runTest {
        val repository = FakeRepository().apply {
            onForecast = { throw IllegalStateException("Unexpected forecast failure") }
            onSearch = { throw IllegalStateException("Unexpected search failure") }
        }
        val model = createModel(repository)
        runCurrent()
        assertEquals(
            "Something went wrong while loading weather data. Please try again.",
            model.state.value.error,
        )
        assertFalse(model.state.value.loading)

        model.search("Cairns")
        advanceTimeBy(350)
        runCurrent()
        assertEquals(
            "Something went wrong while loading places. Please try again.",
            model.state.value.search.error,
        )
        assertFalse(model.state.value.search.loading)

        repository.onForecast = { sampleForecast(it) }
        model.refresh()
        runCurrent()
        assertNotNull(model.state.value.forecast)
        assertNull(model.state.value.error)
    }

    @Test
    fun `resume rechecks age immediately and old data stays expired after refresh failure`() = runTest {
        val original = sampleForecast(Place.TOWNSVILLE)
        val repository = FakeRepository().apply { onForecast = { original } }
        val model = createModel(repository)
        runCurrent()

        clock.current = base.plus(Duration.ofHours(3))
        repository.onForecast = { throw IOException("Offline") }
        model.onResume()
        assertTrue(model.state.value.expired)
        assertTrue(model.state.value.loading)
        runCurrent()

        assertEquals(2, repository.forecastRequests.size)
        assertTrue(model.state.value.expired)
        assertSame(original, model.state.value.forecast)
        assertNotNull(model.state.value.error)
        model.onBackground()
    }

    @Test
    fun `elapsed time expires a forecast even when the wall clock moves backwards`() = runTest {
        val repository = FakeRepository().apply { onForecast = { sampleForecast(it) } }
        val model = createModel(repository)
        runCurrent()

        // The app was backgrounded for three hours, while the user reset the wall clock.
        elapsedMillis = Duration.ofHours(3).toMillis()
        clock.current = base.minusSeconds(60)
        repository.onForecast = { throw IOException("Offline") }
        model.onResume()
        runCurrent()
        assertTrue(model.state.value.expired)
        assertTrue(model.state.value.clockBeforeForecast)
        assertEquals(2, repository.forecastRequests.size)

        clock.current = base.plusSeconds(60)
        model.setSessionHours(1)
        assertFalse(model.state.value.clockBeforeForecast)
        assertTrue(model.state.value.expired)
        model.onBackground()
    }

    @Test
    fun `an observed expired forecast stays expired after a forward then backward clock correction`() = runTest {
        val repository = FakeRepository().apply { onForecast = { sampleForecast(it) } }
        val model = createModel(repository)
        runCurrent()
        clock.current = base.plus(Duration.ofHours(4))
        model.setSessionHours(1)
        assertTrue(model.state.value.expired)

        clock.current = base.plusSeconds(60)
        model.setSessionHours(2)
        assertTrue(model.state.value.expired)

        repository.onForecast = { sampleForecast(it).copy(fetchedAt = clock.current) }
        model.refresh()
        runCurrent()
        assertFalse(model.state.value.stale)
        assertFalse(model.state.value.expired)
    }

    private fun createModel(repository: WeatherRepository): AppViewModel =
        AppViewModel(repository, clock) { elapsedMillis }.also { model ->
            stores += ViewModelStore().apply { put("subject", model) }
        }

    private fun sampleForecast(place: Place, wind: Double = 2.0) = Forecast(
        place = place,
        zone = ZoneOffset.UTC,
        fetchedAt = base,
        points = List(49) { index ->
            ForecastPoint(base.plusSeconds(index * 3_600L), wind, 4.0, 10)
        },
    )

    private class FakeRepository : WeatherRepository {
        val forecastRequests = mutableListOf<Place>()
        val searchRequests = mutableListOf<String>()
        var onForecast: suspend (Place) -> Forecast = { error("Forecast fixture not set") }
        var onSearch: suspend (String) -> List<Place> = { emptyList() }

        override suspend fun forecast(place: Place): Forecast {
            forecastRequests += place
            return onForecast(place)
        }

        override suspend fun searchPlaces(query: String): List<Place> {
            searchRequests += query
            return onSearch(query)
        }
    }

    private class MutableClock(
        var current: Instant,
        private val clockZone: ZoneId = ZoneOffset.UTC,
    ) : Clock() {
        override fun instant(): Instant = current
        override fun getZone(): ZoneId = clockZone
        override fun withZone(zone: ZoneId): Clock = Clock.fixed(current, zone)
    }
}
