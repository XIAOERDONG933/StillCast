package com.stillcast.app.data

import com.stillcast.app.domain.Place
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class OpenMeteoRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: OpenMeteoRepository
    private val fetchedAt = Instant.parse("2026-09-28T01:30:00Z")
    private val firstEpoch = Instant.parse("2026-09-28T00:00:00Z").epochSecond
    private val fixedClock = Clock.fixed(fetchedAt, ZoneOffset.UTC)

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(
                OkHttpClient.Builder()
                    .retryOnConnectionFailure(false)
                    .callTimeout(2, TimeUnit.SECONDS)
                    .build(),
            )
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        repository = OpenMeteoRepository(
            retrofit.create(ForecastApi::class.java),
            retrofit.create(GeocodingApi::class.java),
            fixedClock,
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `real HTTP request asks for correct variables units time format and coverage`() {
        server.enqueue(json(forecastJson()))
        val result = runBlocking { repository.forecast(Place.TOWNSVILLE) }
        val request = server.takeRequest(1, TimeUnit.SECONDS)!!
        val url = request.requestUrl!!
        assertEquals("/v1/forecast", url.encodedPath)
        assertEquals(Place.TOWNSVILLE.latitude.toString(), url.queryParameter("latitude"))
        assertEquals(Place.TOWNSVILLE.longitude.toString(), url.queryParameter("longitude"))
        assertEquals(
            "wind_speed_10m,wind_gusts_10m,precipitation_probability",
            url.queryParameter("hourly"),
        )
        assertEquals("ms", url.queryParameter("wind_speed_unit"))
        assertEquals("unixtime", url.queryParameter("timeformat"))
        assertEquals("auto", url.queryParameter("timezone"))
        assertEquals("3", url.queryParameter("forecast_days"))
        assertEquals(fetchedAt, result.fetchedAt)
        assertEquals("Australia/Brisbane", result.zone.id)
        assertEquals(3, result.points.size)
        assertEquals(Instant.ofEpochSecond(firstEpoch), result.points[0].time)
    }

    @Test
    fun `forecast preserves requested place rather than replacing it with model grid coordinates`() {
        val selected = Place(42, "Recording location", "", "Australia", -19.0, 146.0)
        server.enqueue(json(forecastJson())) // Contains different grid coordinates.
        assertSame(selected, runBlocking { repository.forecast(selected) }.place)
    }

    @Test
    fun `epoch timestamp is UTC and is not shifted by returned local offset`() {
        server.enqueue(json(forecastJson()))
        val first = runBlocking { repository.forecast(Place.TOWNSVILLE) }.points.first()
        assertEquals(firstEpoch, first.time.epochSecond)
    }

    @Test
    fun `null readings remain absent instead of becoming zero`() {
        server.enqueue(json(forecastJson(wind = "[1.0,null,2.0]", rain = "[10, null, 20]")))
        val result = runBlocking { repository.forecast(Place.TOWNSVILLE) }
        assertNull(result.points[1].windMs)
        assertNull(result.points[1].precipitationProbability)
        assertEquals(20, result.points[2].precipitationProbability)
    }

    @Test
    fun `gaps stay gaps for planner to handle`() {
        server.enqueue(json(forecastJson(times = "[$firstEpoch,${firstEpoch + 7_200},${firstEpoch + 10_800}]")))
        val result = runBlocking { repository.forecast(Place.TOWNSVILLE) }
        assertEquals(7_200L, result.points[1].time.epochSecond - result.points[0].time.epochSecond)
        assertEquals(3, result.points.size)
    }

    @Test
    fun `wrong measurement units are rejected before display`() {
        server.enqueue(json(forecastJson().replace("m/s", "km/h")))
        expectInvalidForecast()
    }

    @Test
    fun `missing hourly fields are rejected rather than silently defaulted`() {
        server.enqueue(json("""{"timezone":"UTC","hourly":{}}"""))
        expectInvalidForecast()
    }

    @Test
    fun `different array lengths cannot misalign weather readings`() {
        server.enqueue(json(forecastJson(wind = "[1.0,2.0]")))
        expectInvalidForecast()
    }

    @Test
    fun `invalid time zone cannot fall back silently to device time`() {
        server.enqueue(json(forecastJson().replace("Australia/Brisbane", "Not/A_Zone")))
        expectInvalidForecast()
    }

    @Test
    fun `duplicate and unordered timestamps are invalid`() {
        listOf(
            "[$firstEpoch,$firstEpoch,${firstEpoch + 3_600}]",
            "[${firstEpoch + 3_600},$firstEpoch,${firstEpoch + 7_200}]",
            "[$firstEpoch,${firstEpoch + 1_800},${firstEpoch + 7_200}]",
        ).forEach { times ->
            server.enqueue(json(forecastJson(times = times)))
            expectInvalidForecast()
        }
    }

    @Test
    fun `millisecond and null timestamps are invalid`() {
        listOf(
            "[${firstEpoch * 1_000},${(firstEpoch + 3_600) * 1_000},${(firstEpoch + 7_200) * 1_000}]",
            "[$firstEpoch,null,${firstEpoch + 7_200}]",
        ).forEach { times ->
            server.enqueue(json(forecastJson(times = times)))
            expectInvalidForecast()
        }
    }

    @Test
    fun `out of range and fractional precipitation probabilities are rejected`() {
        listOf("[-1,10,20]", "[101,10,20]", "[2.5,10,20]").forEach { rain ->
            server.enqueue(json(forecastJson(rain = rain)))
            expectInvalidForecast()
        }
    }

    @Test
    fun `negative impossible and nonfinite wind readings are rejected`() {
        listOf("[-0.1,1,2]", "[151,1,2]", "[\"NaN\",1,2]").forEach { wind ->
            server.enqueue(json(forecastJson(wind = wind)))
            expectInvalidForecast()
        }
    }

    @Test
    fun `HTTP errors reach callers as IOException`() {
        server.enqueue(MockResponse().setResponseCode(503).setBody("Temporarily unavailable"))
        val exception = assertThrows(IOException::class.java) {
            runBlocking { repository.forecast(Place.TOWNSVILLE) }
        }
        assertTrue(exception.message!!.contains("503"))
    }

    @Test
    fun `connection failure reaches callers as IOException`() {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        assertThrows(IOException::class.java) {
            runBlocking { repository.forecast(Place.TOWNSVILLE) }
        }
    }

    @Test
    fun `malformed JSON reaches callers as an understandable data exception`() {
        server.enqueue(json("{ broken json"))
        expectInvalidForecast()
    }

    @Test
    fun `empty successful response is a data failure`() {
        server.enqueue(MockResponse().setResponseCode(204))
        expectInvalidForecast()
    }

    @Test
    fun `service error flag is respected even on a successful status`() {
        server.enqueue(json("""{"error":true,"reason":"unavailable"}"""))
        expectInvalidForecast()
    }

    @Test
    fun `search trims query encodes it and requests five English results`() {
        server.enqueue(json("""{"results":[{"id":7,"name":"São Paulo","latitude":-23.55,"longitude":-46.63,"country":"Brazil"}]}"""))
        val result = runBlocking { repository.searchPlaces("  São Paulo  ") }
        val request = server.takeRequest(1, TimeUnit.SECONDS)!!
        assertEquals("/v1/search", request.requestUrl!!.encodedPath)
        assertEquals("São Paulo", request.requestUrl!!.queryParameter("name"))
        assertEquals("5", request.requestUrl!!.queryParameter("count"))
        assertEquals("en", request.requestUrl!!.queryParameter("language"))
        assertEquals("json", request.requestUrl!!.queryParameter("format"))
        assertEquals("São Paulo, Brazil", result.single().label)
        assertEquals("", result.single().region)
    }

    @Test
    fun `missing optional country region and results are supported`() {
        server.enqueue(json("""{"results":[{"id":7,"name":"Island","latitude":1,"longitude":2}]}"""))
        assertEquals("Island", runBlocking { repository.searchPlaces("Island") }.single().label)
        server.enqueue(json("{}"))
        assertTrue(runBlocking { repository.searchPlaces("Nowhere") }.isEmpty())
        server.enqueue(json("""{"results":null}"""))
        assertTrue(runBlocking { repository.searchPlaces("Nowhere") }.isEmpty())
    }

    @Test
    fun `short searches do not make network requests`() {
        assertTrue(runBlocking { repository.searchPlaces(" a ") }.isEmpty())
        assertTrue(runBlocking { repository.searchPlaces(" ") }.isEmpty())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `invalid location coordinates fail instead of producing a wrong destination`() {
        server.enqueue(json("""{"results":[{"id":7,"name":"Invalid","latitude":91,"longitude":0}]}"""))
        assertThrows(ForecastDataException::class.java) {
            runBlocking { repository.searchPlaces("Invalid") }
        }
    }

    @Test
    fun `missing mandatory location field is not a zero coordinate`() {
        server.enqueue(json("""{"results":[{"id":7,"name":"Invalid","longitude":0}]}"""))
        assertThrows(ForecastDataException::class.java) {
            runBlocking { repository.searchPlaces("Invalid") }
        }
    }

    @Test
    fun `coroutine cancellation is not converted into a user facing service error`() {
        val cancellation = CancellationException("Search replaced")
        val cancellingApi = object : GeocodingApi {
            override suspend fun search(name: String, count: Int, language: String, format: String): GeocodingResponse {
                throw cancellation
            }
        }
        val unusedForecastApi = object : ForecastApi {
            override suspend fun forecast(
                latitude: Double,
                longitude: Double,
                hourly: String,
                windSpeedUnit: String,
                timeFormat: String,
                timezone: String,
                forecastDays: Int,
            ): ForecastResponse = error("Not used")
        }
        val cancellingRepository = OpenMeteoRepository(unusedForecastApi, cancellingApi, fixedClock)
        val exception = assertThrows(CancellationException::class.java) {
            runBlocking { cancellingRepository.searchPlaces("Townsville") }
        }
        assertSame(cancellation, exception)
    }

    private fun expectInvalidForecast() {
        assertThrows(ForecastDataException::class.java) {
            runBlocking { repository.forecast(Place.TOWNSVILLE) }
        }
    }

    private fun json(body: String) = MockResponse()
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    private fun forecastJson(
        times: String = "[$firstEpoch,${firstEpoch + 3_600},${firstEpoch + 7_200}]",
        wind: String = "[1.0,2.0,3.0]",
        rain: String = "[10,20,30]",
    ): String = """
        {
          "latitude": -19.30,
          "longitude": 146.76,
          "timezone": "Australia/Brisbane",
          "utc_offset_seconds": 36000,
          "hourly_units": {
            "time": "unixtime",
            "wind_speed_10m": "m/s",
            "wind_gusts_10m": "m/s",
            "precipitation_probability": "%"
          },
          "hourly": {
            "time": $times,
            "wind_speed_10m": $wind,
            "wind_gusts_10m": [2.0,3.0,4.0],
            "precipitation_probability": $rain
          }
        }
    """.trimIndent()
}
