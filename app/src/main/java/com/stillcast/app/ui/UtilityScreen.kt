package com.stillcast.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stillcast.app.domain.RecordingHour
import com.stillcast.app.domain.RecordingSettings
import com.stillcast.app.domain.WindowPlanner
import com.stillcast.app.ui.theme.Forest
import com.stillcast.app.ui.theme.Lime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun UtilityScreen(
    state: StillCastUiState,
    onRefresh: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAllHours by rememberSaveable(state.settings.place.id) { mutableStateOf(false) }
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.widthIn(max = 680.dp).fillMaxSize().testTag("fieldList"),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Rounded.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f)) {
                        Text(state.settings.place.name, style = MaterialTheme.typography.titleMedium)
                        Text(listOf(state.settings.place.region, state.settings.place.country).filter { it.isNotBlank() }.distinct().joinToString(", "),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = onSettings) { Text("Change") }
                }
            }
            if (state.error != null) item {
                Notice(
                    if (state.forecast != null) "${state.error} Showing the last retrieved forecast." else state.error,
                    if (!state.loading) "Try again" else null, onRefresh,
                )
            }
            if (state.stale && !state.expired && state.error == null) item {
                Notice("This forecast was retrieved over an hour ago. Refresh before making plans.",
                    if (!state.loading) "Refresh" else null, onRefresh)
            }
            item { WindowCard(state, onSettings, onRefresh) }
            item {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Your limits", style = MaterialTheme.typography.titleMedium)
                            TextButton(onClick = onSettings) { Text("Adjust") }
                        }
                        Text("Wind ≤ ${decimal(state.settings.maxWindMs)} m/s  ·  Gust ≤ ${decimal(state.settings.maxGustMs)} m/s",
                            style = MaterialTheme.typography.bodyMedium)
                        Text("Precipitation ≤ ${state.settings.maxPrecipitationProbability}%  ·  ${state.settings.sessionHours} hour session",
                            style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
            val forecast = state.forecast
            val analysis = state.analysis
            if (forecast != null && analysis != null) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("The next 24 hours", style = MaterialTheme.typography.headlineMedium)
                        Text("From the next complete forecast hour · ${forecast.zone.id.replace('_', ' ')}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("✓ Within limits   – Above limits   ? Missing data",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth()) {
                        Text("HOUR", Modifier.weight(1.4f), style = MaterialTheme.typography.labelSmall)
                        listOf("WIND", "GUST", "CHANCE").forEach {
                            Text(it, Modifier.weight(1f), textAlign = TextAlign.End, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                items(if (showAllHours) analysis.hours else analysis.hours.take(6), key = { it.start.epochSecond }) { hour ->
                    HourRow(hour, state.settings, forecast.zone)
                }
                if (analysis.hours.size > 6) item {
                    TextButton(onClick = { showAllHours = !showAllHours }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (showAllHours) "Show fewer hours" else "Show all ${analysis.hours.size} hours")
                    }
                }
                item {
                    Text("Wind and gusts in m/s. Chance is the precipitation probability for each hour. ${analysis.completeHourCount}/${analysis.hours.size} hours have complete data.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Retrieved ${DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.UK).withZone(forecast.zone).format(forecast.fetchedAt)} · Times local to selected place",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp))
                }
            }
            item {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text("Plan the weather. Listen to the place.", style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 18.dp, bottom = 6.dp))
                Text("Local shelter, traffic and microphone protection still matter. StillCast does not measure sound levels.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                WebLink("Weather data: Open-Meteo ↗", "https://open-meteo.com/")
            }
        }
    }
}

@Composable
private fun WindowCard(state: StillCastUiState, onSettings: () -> Unit, onRefresh: () -> Unit) {
    val window = state.analysis?.next
    val zone = state.forecast?.zone
    Surface(color = Forest, contentColor = Color.White, shape = RoundedCornerShape(26.dp),
        modifier = Modifier.fillMaxWidth().testTag("windowCard")) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("YOUR NEXT RECORDING WINDOW", style = MaterialTheme.typography.labelSmall, color = Lime,
                    modifier = Modifier.weight(1f))
                Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = Lime)
            }
            when {
                state.forecast == null && state.loading -> {
                    Text("Checking the\nfield conditions…", style = MaterialTheme.typography.headlineMedium)
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Lime,
                        trackColor = Color.White.copy(alpha = 0.15f))
                    Text("Looking for ${state.settings.sessionHours} consecutive hours within your limits.",
                        style = MaterialTheme.typography.bodyMedium, color = Color(0xFFD6E2D9))
                }
                state.forecast == null -> {
                    Text("Let’s check\nthe conditions.", style = MaterialTheme.typography.headlineMedium)
                    Text("A live forecast is needed to find your next window.", style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFD6E2D9))
                    HeroButton("Load forecast", onRefresh)
                }
                state.expired -> {
                    Text("Time for a\nfresh forecast.", style = MaterialTheme.typography.headlineMedium)
                    Text("The saved forecast is over three hours old. Refresh to see a new recommendation.",
                        style = MaterialTheme.typography.bodyMedium, color = Color(0xFFD6E2D9))
                    if (!state.loading) HeroButton("Refresh forecast", onRefresh)
                }
                window != null && zone != null -> {
                    Text("${dayLabel(window.start, state.now, zone)} · ${state.settings.sessionHours} hour session",
                        style = MaterialTheme.typography.bodyMedium, color = Color(0xFFD6E2D9))
                    Text("${clockTime(window.start, zone)} – ${clockTime(window.end, zone)}",
                        style = MaterialTheme.typography.headlineLarge, fontSize = 32.sp,
                        modifier = Modifier.testTag("windowTime"))
                    if (window.start.atZone(zone).toLocalDate() != window.end.atZone(zone).toLocalDate()) {
                        Text("Ends ${dayLabel(window.end, state.now, zone).lowercase(Locale.UK)}",
                            style = MaterialTheme.typography.bodySmall, color = Color(0xFFD6E2D9))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Icon(Icons.Rounded.Check, contentDescription = null, tint = Lime)
                        Text("Every hour meets your limits", style = MaterialTheme.typography.labelLarge, color = Lime)
                    }
                    HorizontalDivider(color = Color.White.copy(alpha = 0.18f))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        WindowMetric("Wind", decimal(window.maxWindMs), "m/s max", Modifier.weight(1f))
                        WindowMetric("Gust", decimal(window.maxGustMs), "m/s max", Modifier.weight(1f))
                        WindowMetric("Chance", "${window.maxPrecipitationProbability}%", "hourly max", Modifier.weight(1f))
                    }
                    Text("Chance is the highest hourly precipitation probability, not a whole-session probability.",
                        style = MaterialTheme.typography.bodySmall, color = Color(0xFFD6E2D9))
                }
                else -> {
                    val incomplete = state.analysis?.hasCompleteSession != true
                    Text(if (incomplete) "Not enough\nhourly data." else "No window fits\nyour limits yet.",
                        style = MaterialTheme.typography.headlineMedium)
                    Text(if (incomplete) "Some forecast readings are unavailable. Try refreshing the data."
                    else "No complete ${state.settings.sessionHours} hour session meets every limit in the next 24 hours.",
                        style = MaterialTheme.typography.bodyMedium, color = Color(0xFFD6E2D9))
                    HeroButton(if (incomplete) "Refresh forecast" else "Adjust limits", if (incomplete) onRefresh else onSettings)
                }
            }
        }
    }
}

@Composable
private fun HeroButton(label: String, onClick: () -> Unit) {
    Button(onClick = onClick, colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Forest)) {
        Text(label)
    }
}

@Composable
private fun WindowMetric(label: String, value: String, unit: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color(0xFFD6E2D9))
        Text(value, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Text(unit, style = MaterialTheme.typography.bodySmall, color = Color(0xFFD6E2D9))
    }
}

@Composable
private fun HourRow(hour: RecordingHour, settings: RecordingSettings, zone: ZoneId) {
    val matches = WindowPlanner.matches(hour, settings)
    val symbol = if (!hour.complete) "?" else if (matches) "✓" else "–"
    val date = DateTimeFormatter.ofPattern("EEE HH:mm", Locale.UK).withZone(zone).format(hour.start)
    val description = "$date, ${if (!hour.complete) "missing data" else if (matches) "within limits" else "above limits"}. " +
        "Wind ${hour.windMs?.let(::decimal) ?: "unavailable"} metres per second, " +
        "gust ${hour.gustMs?.let(::decimal) ?: "unavailable"} metres per second, " +
        "precipitation chance ${hour.precipitationProbability?.toString() ?: "unavailable"} percent."
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1.4f)) {
            Text(DateTimeFormatter.ofPattern("EEE d MMM", Locale.UK).withZone(zone).format(hour.start),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("$symbol  ${clockTime(hour.start, zone)}", style = MaterialTheme.typography.bodyMedium,
                color = if (matches) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(hour.windMs?.let(::decimal) ?: "—", Modifier.weight(1f), textAlign = TextAlign.End, style = MaterialTheme.typography.bodyMedium)
        Text(hour.gustMs?.let(::decimal) ?: "—", Modifier.weight(1f), textAlign = TextAlign.End, style = MaterialTheme.typography.bodyMedium)
        Text(hour.precipitationProbability?.let { "$it%" } ?: "—", Modifier.weight(1f), textAlign = TextAlign.End, style = MaterialTheme.typography.bodyMedium)
    }
}
