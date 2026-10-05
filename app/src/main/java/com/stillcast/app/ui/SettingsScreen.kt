package com.stillcast.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.stillcast.app.domain.Place
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    state: StillCastUiState,
    onSearch: (String) -> Unit,
    onPlaceSelected: (Place) -> Unit,
    onSessionHours: (Int) -> Unit,
    onWindLimit: (Double) -> Unit,
    onGustLimit: (Double) -> Unit,
    onRainLimit: (Int) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focus = LocalFocusManager.current
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.widthIn(max = 680.dp).fillMaxSize().testTag("settingsList"),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item {
                Text("Make it your window.", style = MaterialTheme.typography.headlineMedium)
                Text("Changes apply immediately. Preferences last for this app session.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp))
            }
            item {
                SettingsSection("01", "Recording location") {
                    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(16.dp)) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Rounded.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column {
                                Text(state.settings.place.name, style = MaterialTheme.typography.titleMedium)
                                Text(state.settings.place.label, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    OutlinedTextField(
                        value = state.search.query,
                        onValueChange = onSearch,
                        modifier = Modifier.fillMaxWidth().testTag("citySearch"),
                        label = { Text("Search for a city") },
                        placeholder = { Text("For example, Kyoto") },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        trailingIcon = if (state.search.query.isNotEmpty()) {
                            { IconButton(onClick = { onSearch("") }) { Icon(Icons.Rounded.Close, "Clear search") } }
                        } else null,
                        supportingText = { Text("Enter at least 3 characters. Choose the correct region.") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        shape = RoundedCornerShape(14.dp),
                    )
                    if (state.search.loading) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator(modifier = Modifier.widthIn(max = 20.dp), strokeWidth = 2.dp)
                            Text("Finding places…", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (state.search.error != null) {
                        Notice(state.search.error, "Retry search", { onSearch(state.search.query) })
                    }
                    state.search.results.forEach { place ->
                        Surface(onClick = { focus.clearFocus(); onPlaceSelected(place) }, shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.fillMaxWidth().testTag("place-${place.id}")) {
                            Column(Modifier.padding(16.dp)) {
                                Text(place.name, style = MaterialTheme.typography.titleMedium)
                                Text(place.label, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    if (state.search.completed && state.search.error == null && state.search.results.isEmpty()) {
                        Text("No places found. Try a nearby city or a different spelling.", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            item {
                SettingsSection("02", "Session length") {
                    Text("Every hour of the session must meet all three limits.", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        (1..3).forEach { hours ->
                            FilterChip(selected = state.settings.sessionHours == hours, onClick = { onSessionHours(hours) },
                                label = { Text("$hours ${if (hours == 1) "hour" else "hours"}") }, modifier = Modifier.testTag("duration-$hours"))
                        }
                    }
                }
            }
            item {
                SettingsSection("03", "Your weather limits") {
                    Text("Start with these preferences, then adjust for your location and recording setup. They are not equipment ratings.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LimitSlider("Wind", "Steady wind at 10 metres", state.settings.maxWindMs.toFloat(),
                        "${decimal(state.settings.maxWindMs)} m/s", 1f..8f, 13, "windLimit") {
                        onWindLimit((it * 2).roundToInt() / 2.0)
                    }
                    LimitSlider("Gust", "Short bursts of wind", state.settings.maxGustMs.toFloat(),
                        "${decimal(state.settings.maxGustMs)} m/s", 2f..16f, 27, "gustLimit") {
                        onGustLimit((it * 2).roundToInt() / 2.0)
                    }
                    LimitSlider("Precipitation chance", "Maximum probability in any one hour", state.settings.maxPrecipitationProbability.toFloat(),
                        "${state.settings.maxPrecipitationProbability}%", 0f..80f, 7, "rainLimit") {
                        onRainLimit((it / 10).roundToInt() * 10)
                    }
                    TextButton(onClick = onReset, modifier = Modifier.testTag("resetLimits")) { Text("Reset limits and session length") }
                }
            }
            item {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(Modifier.padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("About your forecast", style = MaterialTheme.typography.titleMedium)
                    Text("The earliest complete session within the next 24 hours is shown. Forecasts use the selected place’s local time. Missing readings never count as a match.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("No microphone or GPS access. City searches and selected coordinates are sent to Open-Meteo to retrieve results. Preferences are kept in memory for this session.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Window recommendations calculated by StillCast. Weather: Open-Meteo. Location data: GeoNames, via Open-Meteo. Data licensed under CC BY 4.0.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    WebLink("Forecast source & methods ↗", "https://open-meteo.com/en/docs")
                    WebLink("Open-Meteo privacy & terms ↗", "https://open-meteo.com/en/terms")
                    WebLink("GeoNames ↗", "https://www.geonames.org/")
                    WebLink("CC BY 4.0 licence ↗", "https://creativecommons.org/licenses/by/4.0/")
                    Text("StillCast 1.0 · Field recording weather", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(number: String, title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(number, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleLarge)
        }
        content()
    }
}

@Composable
private fun LimitSlider(
    title: String,
    explanation: String,
    value: Float,
    display: String,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    tag: String,
    onValue: (Float) -> Unit,
) {
    Column(Modifier.padding(top = 6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Text(display, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
        Text(explanation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(value = value, onValueChange = onValue, valueRange = range, steps = steps,
            modifier = Modifier.fillMaxWidth().testTag(tag).semantics { contentDescription = "$title limit, $display" })
    }
}
