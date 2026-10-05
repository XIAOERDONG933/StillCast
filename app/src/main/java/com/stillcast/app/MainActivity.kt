package com.stillcast.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.stillcast.app.ui.AppViewModel
import com.stillcast.app.ui.UtilityApp
import com.stillcast.app.ui.theme.StillCastTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = (application as StillCastApplication).container.weatherRepository
        setContent {
            StillCastTheme {
                val model: AppViewModel = viewModel(factory = viewModelFactory {
                    initializer { AppViewModel(repository) }
                })
                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { model.onResume() }
                LifecycleEventEffect(Lifecycle.Event.ON_STOP) { model.onBackground() }
                UtilityApp(
                    state = model.state.collectAsStateWithLifecycle().value,
                    onRefresh = model::refresh,
                    onSearch = model::search,
                    onPlaceSelected = model::selectPlace,
                    onSessionHours = model::setSessionHours,
                    onWindLimit = model::setWindLimit,
                    onGustLimit = model::setGustLimit,
                    onRainLimit = model::setRainLimit,
                    onReset = model::resetLimits,
                )
            }
        }
    }
}
