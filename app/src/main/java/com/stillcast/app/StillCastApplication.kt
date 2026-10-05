package com.stillcast.app

import android.app.Application
import com.stillcast.app.data.OpenMeteoRepository
import com.stillcast.app.domain.WeatherRepository

/** A small application-scoped dependency container; no service lookups inside screens. */
class StillCastApplication : Application() {
    val container by lazy { AppContainer() }
}

class AppContainer {
    val weatherRepository: WeatherRepository by lazy { OpenMeteoRepository.create() }
}
