package com.stillcast.app.data

import com.google.gson.GsonBuilder
import com.google.gson.JsonParseException
import com.google.gson.stream.MalformedJsonException
import com.stillcast.app.domain.Forecast
import com.stillcast.app.domain.ForecastPoint
import com.stillcast.app.domain.Place
import com.stillcast.app.domain.WeatherRepository
import com.stillcast.app.domain.isValidWind
import java.io.EOFException
import java.io.IOException
import java.time.Clock
import java.time.DateTimeException
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/** Invalid upstream content is distinct from a valid forecast with missing observations. */
class ForecastDataException(message: String, cause: Throwable? = null) : IOException(message, cause)

class OpenMeteoRepository(
    private val forecastApi: ForecastApi,
    private val geocodingApi: GeocodingApi,
    private val clock: Clock = Clock.systemUTC(),
) : WeatherRepository {
    override suspend fun forecast(place: Place): Forecast {
        val response = readResponse { forecastApi.forecast(place.latitude, place.longitude) }
        if (response.error == true) invalid("The weather service could not provide a forecast.")
        val zone = try {
            ZoneId.of(response.timezone ?: invalid("The forecast has no time zone."))
        } catch (exception: DateTimeException) {
            throw ForecastDataException("The forecast time zone is invalid.", exception)
        }
        val units = response.hourlyUnits ?: invalid("The forecast has no measurement units.")
        if (units.time != "unixtime" || units.wind != "m/s" ||
            units.gust != "m/s" || units.precipitation != "%"
        ) {
            invalid("The forecast uses unexpected measurement units.")
        }
        val hourly = response.hourly ?: invalid("The forecast has no hourly data.")
        val times = hourly.time ?: invalid("The forecast has no timestamps.")
        val wind = hourly.wind ?: invalid("The forecast has no wind series.")
        val gust = hourly.gust ?: invalid("The forecast has no gust series.")
        val precipitation = hourly.precipitation
            ?: invalid("The forecast has no precipitation probability series.")
        if (times.size !in 2..MAX_POINTS ||
            listOf(wind.size, gust.size, precipitation.size).any { it != times.size }
        ) {
            invalid("The hourly forecast data is incomplete or misaligned.")
        }
        val timestamps = times.map { value ->
            // Also rejects millisecond timestamps accidentally supplied as epoch seconds.
            if (value == null || value !in EARLIEST_EPOCH..LATEST_EPOCH) {
                invalid("The forecast contains an invalid timestamp.")
            }
            value
        }
        if (timestamps.zipWithNext().any { (left, right) ->
                right <= left || (right - left) % HOUR_SECONDS != 0L
            }
        ) {
            invalid("The forecast timestamps are not in hourly order.")
        }
        val points = timestamps.mapIndexed { index, time ->
            ForecastPoint(
                time = Instant.ofEpochSecond(time),
                windMs = checkedWind(wind[index]),
                gustMs = checkedWind(gust[index]),
                precipitationProbability = checkedProbability(precipitation[index]),
            )
        }
        return Forecast(place, zone, clock.instant(), points)
    }

    override suspend fun searchPlaces(query: String): List<Place> {
        val normalized = query.trim()
        if (normalized.length < 2) return emptyList()
        val response = readResponse { geocodingApi.search(normalized) }
        if (response.error == true) invalid("The location service could not complete the search.")
        return response.results.orEmpty().map { result ->
            val item = result ?: invalid("The location service returned an invalid result.")
            val name = item.name?.trim()?.takeIf(String::isNotBlank)
                ?: invalid("A location has no name.")
            val latitude = item.latitude ?: invalid("A location has no latitude.")
            val longitude = item.longitude ?: invalid("A location has no longitude.")
            if (!latitude.isFinite() || latitude !in -90.0..90.0 ||
                !longitude.isFinite() || longitude !in -180.0..180.0
            ) {
                invalid("A location has invalid coordinates.")
            }
            Place(
                id = item.id ?: invalid("A location has no identifier."),
                name = name,
                region = item.admin1.orEmpty().trim(),
                country = item.country.orEmpty().trim(),
                latitude = latitude,
                longitude = longitude,
            )
        }.distinctBy(Place::id)
    }

    private fun checkedWind(value: Double?): Double? {
        if (value != null && !value.isValidWind()) invalid("The forecast contains an invalid wind value.")
        return value
    }

    private fun checkedProbability(value: Double?): Int? {
        if (value == null) return null
        if (!value.isFinite() || value !in 0.0..100.0 || value % 1.0 != 0.0) {
            invalid("The forecast contains an invalid precipitation probability.")
        }
        return value.toInt()
    }

    private suspend fun <T : Any> readResponse(block: suspend () -> Response<T>): T = try {
        val response = block()
        if (!response.isSuccessful) {
            response.errorBody()?.close()
            throw IOException("The service returned HTTP ${response.code()}.")
        }
        response.body() ?: invalid("The service returned an empty response.")
    } catch (exception: CancellationException) {
        // Cancelling a replaced location search or a destroyed ViewModel must cancel the request.
        throw exception
    } catch (exception: JsonParseException) {
        throw ForecastDataException("The weather service returned unreadable data.", exception)
    } catch (exception: MalformedJsonException) {
        throw ForecastDataException("The weather service returned unreadable data.", exception)
    } catch (exception: EOFException) {
        throw ForecastDataException("The weather service returned incomplete data.", exception)
    }

    private fun invalid(message: String): Nothing = throw ForecastDataException(message)

    companion object {
        private const val HOUR_SECONDS = 3_600L
        private const val MAX_POINTS = 2_000
        private const val EARLIEST_EPOCH = 946_684_800L // 2000-01-01, inclusive.
        private const val LATEST_EPOCH = 4_102_444_800L // 2100-01-01, inclusive.

        fun create(): WeatherRepository {
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .callTimeout(20, TimeUnit.SECONDS)
                .build()
            val converter = GsonConverterFactory.create(GsonBuilder().create())
            fun retrofit(baseUrl: String): Retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(converter)
                .build()
            return OpenMeteoRepository(
                forecastApi = retrofit("https://api.open-meteo.com/").create(ForecastApi::class.java),
                geocodingApi = retrofit("https://geocoding-api.open-meteo.com/")
                    .create(GeocodingApi::class.java),
            )
        }
    }
}
