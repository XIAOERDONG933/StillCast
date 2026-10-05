package com.stillcast.app.data

import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query

interface ForecastApi {
    @GET("v1/forecast")
    suspend fun forecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("hourly") hourly: String = "wind_speed_10m,wind_gusts_10m,precipitation_probability",
        @Query("wind_speed_unit") windSpeedUnit: String = "ms",
        @Query("timeformat") timeFormat: String = "unixtime",
        @Query("timezone") timezone: String = "auto",
        @Query("forecast_days") forecastDays: Int = 3,
    ): ForecastResponse
}

interface GeocodingApi {
    @GET("v1/search")
    suspend fun search(
        @Query("name") name: String,
        @Query("count") count: Int = 5,
        @Query("language") language: String = "en",
        @Query("format") format: String = "json",
    ): GeocodingResponse
}

// Every wire field is nullable: missing JSON must never silently become a credible zero.
data class ForecastResponse(
    val timezone: String? = null,
    @SerializedName("hourly_units") val hourlyUnits: HourlyUnits? = null,
    val hourly: HourlyResponse? = null,
    val error: Boolean? = null,
)

data class HourlyUnits(
    val time: String? = null,
    @SerializedName("wind_speed_10m") val wind: String? = null,
    @SerializedName("wind_gusts_10m") val gust: String? = null,
    @SerializedName("precipitation_probability") val precipitation: String? = null,
)

data class HourlyResponse(
    val time: List<Long?>? = null,
    @SerializedName("wind_speed_10m") val wind: List<Double?>? = null,
    @SerializedName("wind_gusts_10m") val gust: List<Double?>? = null,
    @SerializedName("precipitation_probability") val precipitation: List<Double?>? = null,
)

data class GeocodingResponse(
    val results: List<GeocodingPlace?>? = null,
    val error: Boolean? = null,
)

data class GeocodingPlace(
    val id: Int? = null,
    val name: String? = null,
    val admin1: String? = null,
    val country: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
)
