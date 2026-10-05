package com.stillcast.app.domain

/** Keeps HTTP and response parsing out of the ViewModel and Compose UI. */
interface WeatherRepository {
    suspend fun forecast(place: Place): Forecast
    suspend fun searchPlaces(query: String): List<Place>
}
