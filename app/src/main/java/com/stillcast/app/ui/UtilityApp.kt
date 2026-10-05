package com.stillcast.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.stillcast.app.domain.Place

/** The assessment's two-screen scaffold; no navigation graph is needed. */
@Composable
fun UtilityApp(
    state: StillCastUiState,
    onRefresh: () -> Unit,
    onSearch: (String) -> Unit,
    onPlaceSelected: (Place) -> Unit,
    onSessionHours: (Int) -> Unit,
    onWindLimit: (Double) -> Unit,
    onGustLimit: (Double) -> Unit,
    onRainLimit: (Int) -> Unit,
    onReset: () -> Unit,
) {
    var settingsSelected by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = settingsSelected) { settingsSelected = false }
    Scaffold(
        topBar = {
            Row(
                Modifier.fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("StillCast", style = MaterialTheme.typography.headlineLarge)
                    Text("WEATHER FOR FIELD RECORDING", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!settingsSelected) {
                    IconButton(onClick = onRefresh, enabled = !state.loading, modifier = Modifier.testTag("refresh")) {
                        if (state.loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Rounded.Refresh, contentDescription = "Refresh forecast")
                    }
                } else {
                    Icon(Icons.Rounded.GraphicEq, contentDescription = null,
                        modifier = Modifier.size(30.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                NavigationBarItem(
                    selected = !settingsSelected,
                    onClick = { settingsSelected = false },
                    icon = { Icon(Icons.Rounded.GraphicEq, contentDescription = null) },
                    label = { Text("Field") },
                    modifier = Modifier.testTag("fieldTab"),
                )
                NavigationBarItem(
                    selected = settingsSelected,
                    onClick = { settingsSelected = true },
                    icon = { Icon(Icons.Rounded.Tune, contentDescription = null) },
                    label = { Text("Settings") },
                    modifier = Modifier.testTag("settingsTab"),
                )
            }
        },
    ) { padding ->
        if (settingsSelected) {
            SettingsScreen(state, onSearch, onPlaceSelected, onSessionHours, onWindLimit,
                onGustLimit, onRainLimit, onReset, Modifier.padding(padding))
        } else {
            UtilityScreen(state, onRefresh, { settingsSelected = true }, Modifier.padding(padding))
        }
    }
}
