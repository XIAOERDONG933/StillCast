package com.stillcast.app

import androidx.lifecycle.Lifecycle
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.Duration
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real Activity lifecycle + production ViewModel/planner, with repeatable repository outcomes. */
@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before fun launch() {
        TestDependencies.reset()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.onNodeWithTag("windowTime").assertIsDisplayed()
    }

    @After fun close() { scenario.close() }

    @Test fun recreateKeepsSelectedTabSettingsAndForecastWithoutRefetching() {
        compose.onNodeWithTag("settingsTab").performClick()
        compose.onNodeWithTag("settingsList").performScrollToNode(hasTestTag("duration-3"))
        compose.onNodeWithTag("duration-3").performClick()
        scenario.recreate()
        compose.onNodeWithTag("settingsTab").assertIsSelected()
        compose.onNodeWithTag("settingsList").performScrollToNode(hasTestTag("duration-3"))
        compose.onNodeWithTag("duration-3").assertIsSelected()
        compose.onNodeWithTag("fieldTab").performClick()
        compose.onNodeWithText("Today · 3-hour session").assertIsDisplayed()
        compose.onNodeWithTag("windowTime").assertTextEquals("11:00 – 14:00")
        compose.runOnIdle { assertEquals(1, TestDependencies.repository.calls) }
    }

    @Test fun stoppedActivityRechecksAgeOnResumeAndRecoversAfterFailedRefresh() {
        scenario.moveToState(Lifecycle.State.CREATED)
        TestDependencies.clock.now = TestDependencies.base.plus(Duration.ofHours(4))
        TestDependencies.elapsedMillis = Duration.ofHours(4).toMillis()
        TestDependencies.repository.offline = true
        scenario.moveToState(Lifecycle.State.RESUMED)
        compose.onNodeWithText("Time for a\nfresh forecast.").assertIsDisplayed()
        compose.onNodeWithTag("windowTime").assertDoesNotExist()
        compose.onNodeWithTag("hourlyHeading").assertDoesNotExist()
        compose.runOnIdle { assertEquals(2, TestDependencies.repository.calls) }
        compose.runOnIdle { TestDependencies.repository.offline = false }
        compose.onNodeWithTag("refresh").performClick()
        compose.onNodeWithTag("windowTime").assertTextEquals("15:00 – 17:00")
        compose.runOnIdle { assertEquals(3, TestDependencies.repository.calls) }
    }

    @Test fun selectingCityDuringRefreshSurvivesActivityRecreation() {
        val pending = CompletableDeferred<Unit>()
        compose.runOnIdle { TestDependencies.repository.pending = pending }
        compose.onNodeWithTag("refresh").performClick()
        compose.onNodeWithTag("settingsTab").performClick()
        compose.onNodeWithTag("citySearch").performTextInput("Kyoto")
        compose.onNodeWithTag("citySearch").performImeAction()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasTestTag("place-1857910")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("settingsList").performScrollToNode(hasTestTag("place-1857910"))
        compose.onNodeWithTag("place-1857910").performClick()
        scenario.recreate()
        compose.onNodeWithTag("fieldTab").performClick()
        compose.onNodeWithText("Kyoto").assertIsDisplayed()
        compose.onNodeWithTag("windowTime").assertDoesNotExist()
        compose.runOnIdle { pending.complete(Unit) }
        compose.onNodeWithTag("windowTime").assertIsDisplayed()
        compose.onNodeWithText("Kyoto").assertIsDisplayed()
        compose.runOnIdle { assertEquals(3, TestDependencies.repository.calls) }
    }
}
