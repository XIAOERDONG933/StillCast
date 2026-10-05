package com.stillcast.app.ui

import android.content.res.Configuration
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.stillcast.app.domain.Forecast
import com.stillcast.app.domain.ForecastPoint
import com.stillcast.app.domain.RecordingSettings
import com.stillcast.app.domain.WindowPlanner
import com.stillcast.app.ui.theme.StillCastTheme
import java.time.Instant
import java.time.ZoneId

@Preview(
    name = "Field - Light",
    showBackground = true,
    widthDp = 411,
    heightDp = 891,
    uiMode = Configuration.UI_MODE_NIGHT_NO,
)
@Preview(
    name = "Field - Dark",
    showBackground = true,
    widthDp = 411,
    heightDp = 891,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun UtilityAppPreview() {
    StillCastTheme {
        UtilityApp(
            state = previewState(),
            onRefresh = {},
            onSearch = {},
            onPlaceSelected = {},
            onSessionHours = {},
            onWindLimit = {},
            onGustLimit = {},
            onRainLimit = {},
            onReset = {},
        )
    }
}

@Preview(name = "Settings", showBackground = true, widthDp = 411, heightDp = 891)
@Composable
fun SettingsScreenPreview() {
    StillCastTheme {
        Surface {
            SettingsScreen(
                state = previewState(),
                onSearch = {},
                onPlaceSelected = {},
                onSessionHours = {},
                onWindLimit = {},
                onGustLimit = {},
                onRainLimit = {},
                onReset = {},
            )
        }
    }
}

/** Fixed demonstration data for the editor; previews never make a network request. */
private fun previewState(): StillCastUiState {
    val settings = RecordingSettings()
    val now = Instant.parse("2026-10-05T06:00:00Z")
    val forecast = Forecast(
        place = settings.place,
        zone = ZoneId.of("Australia/Brisbane"),
        fetchedAt = now,
        points = List(26) { hour ->
            ForecastPoint(
                time = now.plusSeconds(hour * 3_600L),
                windMs = 2.0,
                gustMs = 4.0,
                precipitationProbability = 10,
            )
        },
    )
    return StillCastUiState(
        settings = settings,
        forecast = forecast,
        analysis = WindowPlanner.analyze(forecast, settings, now),
        now = now,
    )
}
