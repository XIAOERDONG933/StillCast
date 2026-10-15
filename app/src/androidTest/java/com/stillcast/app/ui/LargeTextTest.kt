package com.stillcast.app.ui

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.stillcast.app.domain.Forecast
import com.stillcast.app.domain.ForecastPoint
import com.stillcast.app.domain.Place
import com.stillcast.app.domain.RecordingSettings
import com.stillcast.app.domain.WindowPlanner
import com.stillcast.app.ui.theme.StillCastTheme
import java.io.File
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Tests layout constraints and control reachability, not screen-reader speech quality. */
@RunWith(Parameterized::class)
class LargeTextTest(private val width: Int, private val height: Int, private val fontScale: Float) {
    @get:Rule val compose = createComposeRule()

    @Test fun recordingTimeAndSettingsRemainReadableAndReachable() {
        val now = Instant.parse("2026-11-01T05:00:00Z")
        val place = Place(5128581, "New York", "New York", "United States", 40.71, -74.01)
        val settings = RecordingSettings(place = place, sessionHours = 1)
        val forecast = Forecast(place, ZoneId.of("America/New_York"), now,
            List(26) { ForecastPoint(now.plusSeconds(it * 3_600L), 1.0, 2.0, 10) })
        val state = mutableStateOf(StillCastUiState(settings = settings, forecast = forecast,
            analysis = WindowPlanner.analyze(forecast, settings, now), now = now))
        var refreshed = false
        compose.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(DpSize(width.dp, height.dp)) then
                    DeviceConfigurationOverride.FontScale(fontScale) then
                    DeviceConfigurationOverride.WindowInsets(WindowInsetsCompat.Builder().build())
            ) {
                StillCastTheme {
                    Box(Modifier.testTag("sizeRoot")) {
                        UtilityApp(state.value, { refreshed = true }, { query ->
                            state.value = state.value.copy(search = PlaceSearch(query = query))
                        }, {}, { hours ->
                            state.value = state.value.copy(settings = state.value.settings.copy(sessionHours = hours))
                        }, {}, {}, {}, {})
                    }
                }
            }
        }
        compose.onNodeWithTag("refresh").assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(refreshed) }
        compose.onNodeWithTag("fieldList").performScrollToNode(hasTestTag("windowTime"))
        compose.onNodeWithTag("windowTime").performScrollTo().assertTextEquals("01:00 EDT – 01:00 EST").assertIsDisplayed()
        capture("field-time")
        assertNoTextOverflow()
        compose.onNodeWithTag("fieldList").performScrollToNode(hasText("Highest hourly chance, not the chance for the whole session."))
        compose.onNodeWithText("Highest hourly chance, not the chance for the whole session.").performScrollTo().assertIsDisplayed()
        assertNoTextOverflow()
        capture("field-metrics")
        compose.onNodeWithTag("fieldList").performScrollToNode(hasTestTag("hourlyHeading"))
        assertNoTextOverflow()
        capture("hours")

        compose.onNodeWithTag("settingsTab").performClick()
        compose.onNodeWithTag("settingsList").performScrollToNode(hasTestTag("citySearch"))
        compose.onNodeWithTag("citySearch").assertIsDisplayed().performTextInput("Os")
        compose.onNodeWithTag("citySearch").performImeAction()
        assertNoTextOverflow()
        capture("search")
        compose.onNodeWithTag("settingsList").performScrollToNode(hasTestTag("duration-3"))
        compose.onNodeWithTag("duration-3").performClick().assertIsSelected()
        assertNoTextOverflow()
        capture("duration")
        for (tag in listOf("windLimit", "gustLimit", "rainLimit")) {
            compose.onNodeWithTag("settingsList").performScrollToNode(hasTestTag(tag))
            compose.onNodeWithTag(tag).assertIsDisplayed()
            assertNoTextOverflow()
        }
        capture("limits")
    }

    private fun assertNoTextOverflow() {
        val matcher = SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult)
        val count = compose.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().size
        repeat(count) { index ->
            val node = compose.onAllNodes(matcher, useUnmergedTree = true)[index]
            if (!node.isDisplayed()) return@repeat
            val results = mutableListOf<TextLayoutResult>()
            node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
            results.forEach { result ->
                // Compose can retain a wider paragraph constraint than the final intrinsic
                // text width. Check actual lines, not that unused paragraph space.
                val clipped = (0 until result.lineCount).any { line ->
                    result.isLineEllipsized(line) || result.getLineLeft(line) < -1f ||
                        result.getLineRight(line) > result.size.width + 1f
                } || result.multiParagraph.height > result.size.height + 1f
                assertFalse("Text is clipped at ${width}x$height / $fontScale: ${result.layoutInput.text}", clipped)
            }
        }
    }

    private fun capture(name: String) {
        val directory = InstrumentationRegistry.getInstrumentation().targetContext
            .getExternalFilesDir("validation")!!
        val file = File(directory, "${width}x$height-font$fontScale-$name.png")
        file.outputStream().use {
            compose.onNodeWithTag("sizeRoot").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        listOf("mkdir -p /sdcard/Download/StillCast-validation",
            "cp ${file.absolutePath} /sdcard/Download/StillCast-validation/").forEach { command ->
            ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use { it.readBytes() }
        }
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}x{1}, font={2}")
        fun sizes() = listOf(arrayOf<Any>(320, 640, 2f), arrayOf<Any>(640, 320, 2f), arrayOf<Any>(411, 891, 1f))
    }
}
