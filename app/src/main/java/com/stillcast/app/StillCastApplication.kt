package com.stillcast.app

import android.app.Application
import android.os.SystemClock
import com.stillcast.app.data.OpenMeteoRepository
import com.stillcast.app.domain.WeatherRepository
import java.time.Clock

/** A small application-scoped dependency container; no service lookups inside screens. */
open class StillCastApplication : Application() {
    open val container by lazy { AppContainer() }
}

class AppContainer(
    val weatherRepository: WeatherRepository = OpenMeteoRepository.create(),
    val clock: Clock = Clock.systemUTC(),
    val elapsedRealtimeMillis: () -> Long = SystemClock::elapsedRealtime,
)
