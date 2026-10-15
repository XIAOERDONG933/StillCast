package com.stillcast.app

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import com.stillcast.app.domain.Forecast
import com.stillcast.app.domain.ForecastPoint
import com.stillcast.app.domain.Place
import com.stillcast.app.domain.WeatherRepository
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred

/** Only instrumentation replaces the app container. MainActivity and its ViewModel stay real. */
class StillCastTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader, className: String, context: Context): Application =
        super.newApplication(cl, TestApplication::class.java.name, context)
}

class TestApplication : StillCastApplication() {
    override val container: AppContainer get() = TestDependencies.container
}

internal object TestDependencies {
    val base: Instant = Instant.parse("2026-10-15T00:30:00Z")
    lateinit var clock: TestClock
    lateinit var repository: ControlledRepository
    lateinit var container: AppContainer
    var elapsedMillis = 0L

    fun reset() {
        clock = TestClock(base)
        elapsedMillis = 0L
        repository = ControlledRepository(clock)
        container = AppContainer(repository, clock) { elapsedMillis }
    }
}

internal class TestClock(var now: Instant) : Clock() {
    override fun instant(): Instant = now
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = Clock.fixed(now, zone)
}

/** Controls network outcomes only; it does not implement screen state or window calculations. */
internal class ControlledRepository(private val clock: Clock) : WeatherRepository {
    var calls = 0
    var offline = false
    var pending: CompletableDeferred<Unit>? = null
    val kyoto = Place(1857910, "Kyoto", "Kyoto", "Japan", 35.02, 135.75)

    override suspend fun searchPlaces(query: String): List<Place> = listOf(kyoto)

    override suspend fun forecast(place: Place): Forecast {
        calls++
        pending?.await()
        if (offline) throw IOException("Offline fixture")
        val now = clock.instant()
        val start = now.epochSecond / 3_600 * 3_600
        return Forecast(place, ZoneId.of("Australia/Brisbane"), now,
            List(49) { ForecastPoint(Instant.ofEpochSecond(start + it * 3_600L), 1.0, 2.0, 10) })
    }
}
