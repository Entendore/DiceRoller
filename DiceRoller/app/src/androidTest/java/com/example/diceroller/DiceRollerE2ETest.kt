package com.example.diceroller

import android.content.Context
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onData
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isPlatformPopup
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import kotlinx.coroutines.runBlocking
import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.instanceOf
import org.hamcrest.CoreMatchers.`is`
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DiceRollerE2ETest {

    private val device: UiDevice =
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    private lateinit var scenario: ActivityScenario<MainActivity>

    /**
     * DataStore persists config across tests (and across process restarts on
     * device), so every test resets state THROUGH the live repo instance before
     * launching a fresh Activity — no cross-test contamination, no flakes.
     */
    @Before
    fun resetStateAndLaunch() = runBlocking {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val app = ctx.applicationContext as DiceApp
        app.repo.saveTheme("Classic")
        app.repo.saveConfig(RollConfig())           // d6, 2 dice, fair, normal
        app.repo.saveWeights(emptyMap())
        app.repo.savePresets(emptyList())
        ctx.getSharedPreferences("dice_meta", Context.MODE_PRIVATE).edit().clear().commit()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitDiceCount(2)
    }

    // ---------- helpers ----------

    private fun selectSpinner(spinnerId: Int, option: String) {
        onView(withId(spinnerId)).perform(scrollTo(), click())
        onData(allOf(instanceOf(String::class.java), `is`(option))).perform(click())
    }

    private fun waitTotal() {
        onView(isRoot()).perform(waitFor(withTextMatching("Total: \\d+")))
    }

    private fun rollAndWait() {
        onView(withId(R.id.rollButton)).perform(scrollTo(), click())
        waitTotal()
    }

    private fun waitDiceCount(n: Int) {
        onView(isRoot()).perform(waitFor(allOf(withId(R.id.diceContainer), withChildCount(n))))
    }

    private fun waitForDialog(text: String) {
        assertNotNull(device.wait(Until.hasObject(By.textContains(text)), 5000))
    }

    private fun assertToast(part: String) {
        onView(withText(containsString(part))).inRoot(isPlatformPopup()).check(matches(isDisplayed()))
    }

    private fun diceValues(): List<Int> {
        var vals = listOf<Int>()
        scenario.onActivity { act ->
            val c = act.findViewById<LinearLayout>(R.id.diceContainer)
            vals = (0 until c.childCount).map { (c.getChildAt(it) as DiceView).value }
        }
        return vals
    }

    // ---------- launch & basic roll ----------

    @Test fun launch_showsTitleAndTwoDice() {
        onView(withText("🎲 RPG Dice Roller")).check(matches(isDisplayed()))
        onView(withId(R.id.diceContainer)).check(matches(withChildCount(2)))
    }

    @Test fun defaultRoll_totalBetween2And12() {
        rollAndWait()
        val range = (2..12).joinToString("|")
        onView(withId(R.id.totalText)).check(matches(withTextMatching("Total: ($range)")))
        onView(withId(R.id.resultText)).check(matches(withText(containsString("2×d6"))))
    }

    // ---------- notation ----------

    @Test fun notationRoll_2d6plus3_appliesModifier() {
        onView(withId(R.id.notationInput))
            .perform(scrollTo(), typeText("2d6+3"), closeSoftKeyboard())
        onView(withId(R.id.rollNotationButton)).perform(scrollTo(), click())
        waitTotal()
        val range = (5..15).joinToString("|")
        onView(withId(R.id.totalText)).check(matches(withTextMatching("Total: ($range)")))
        onView(withId(R.id.resultText)).check(matches(withText(containsString("+3"))))
    }

    @Test fun invalidNotation_showsToastInsteadOfCrashing() {
        onView(withId(R.id.notationInput))
            .perform(scrollTo(), typeText("banana"), closeSoftKeyboard())
        onView(withId(R.id.rollNotationButton)).perform(scrollTo(), click())
        assertToast("Invalid notation")
    }

    // ---------- dice configuration ----------

    @Test fun diceCountSpinner_changesNumberOfDice() {
        selectSpinner(R.id.diceCountSpinner, "3 dice")
        waitDiceCount(3)
        rollAndWait()
        onView(withId(R.id.diceContainer)).check(matches(withChildCount(3)))
    }

    @Test fun allDieTypes_rollWithoutCrashing() {
        for (t in listOf("d4", "d8", "d10", "d12", "d20", "d100")) {
            selectSpinner(R.id.diceTypeSpinner, t)
            rollAndWait()
        }
    }

    @Test fun d1_alwaysRollsOne() {
        selectSpinner(R.id.diceTypeSpinner, "d1")
        selectSpinner(R.id.diceCountSpinner, "1 die")
        rollAndWait()
        onView(withId(R.id.totalText)).check(matches(withText("Total: 1")))
    }

    @Test fun orderOptions_sortTheDice() {
        selectSpinner(R.id.diceCountSpinner, "3 dice")
        selectSpinner(R.id.orderSpinner, "Descending")
        rollAndWait()
        val desc = diceValues()
        assertEquals(desc.sortedDescending(), desc)
        selectSpinner(R.id.orderSpinner, "Ascending")
        rollAndWait()
        val asc = diceValues()
        assertEquals(asc.sorted(), asc)
    }

    // ---------- RPG modes ----------

    @Test fun advantageMode_showsDroppedDie() {
        selectSpinner(R.id.diceCountSpinner, "1 die")
        selectSpinner(R.id.modeSpinner, "Advantage (roll 2, keep best)")
        rollAndWait()
        onView(withId(R.id.resultText)).check(matches(withText(containsString("advantage"))))
        onView(withId(R.id.resultText)).check(matches(withText(containsString("dropped:"))))
    }

    @Test fun loadedDie_showsWeightTag() {
        selectSpinner(R.id.fairnessSpinner, "🍀 Loaded (high faces)")
        rollAndWait()
        onView(withId(R.id.resultText)).check(matches(withText(containsString("⚖️"))))
    }

    // ---------- history ----------

    @Test fun historyKeepsOnlyLastFive_newestFirst() {
        selectSpinner(R.id.diceCountSpinner, "1 die")
        repeat(5) { rollAndWait() }
        selectSpinner(R.id.diceCountSpinner, "2 dice")
        rollAndWait()
        var count = -1
        var firstText = ""
        scenario.onActivity { act ->
            val rv = act.findViewById<RecyclerView>(R.id.historyRecycler)
            count = rv.childCount
            firstText = rv.getChildAt(0)
                .findViewById<TextView>(R.id.historyText).text.toString()
        }
        assertEquals("history should cap at 5 rows", 5, count)
        assertTrue("newest should be the 2-dice roll, got: $firstText", firstText.contains("2×d6"))
    }

    // ---------- quick-roll presets ----------

    @Test fun savePreset_rerollIt_thenDeleteIt() {
        onView(withId(R.id.notationInput))
            .perform(scrollTo(), typeText("1d20+5"), closeSoftKeyboard())
        onView(withId(R.id.savePresetButton)).perform(scrollTo(), click())
        waitForDialog("Save quick roll")
        device.findObject(UiSelector().className("android.widget.EditText")).setText("Longsword")
        device.findObject(UiSelector().text("Save")).click()

        onView(withText(containsString("Longsword")))
            .check(matches(withText(containsString("1d20+5"))))

        onView(withText(containsString("Longsword"))).perform(scrollTo(), click())
        waitTotal()

        onView(withText("✕")).perform(scrollTo(), click())
        waitForDialog("Delete quick roll")
        device.findObject(UiSelector().text("Delete")).click()
        onView(withText(containsString("Longsword"))).check(doesNotExist())
    }

    // ---------- fairness tools ----------

    @Test fun weightEditor_opensAndResetFairShowsToast() {
        onView(withId(R.id.editWeightsButton)).perform(scrollTo(), click())
        waitForDialog("Weights for d6")
        assertTrue(device.findObject(UiSelector().textContains("Face 1")).exists())
        device.findObject(UiSelector().text("Reset fair")).click()
        assertToast("reset to fair")
    }

    @Test fun fairnessReport_showsSimulationAndCloses() {
        onView(withId(R.id.testFairnessButton)).perform(scrollTo(), click())
        waitForDialog("Fairness report")
        assertTrue(device.findObject(UiSelector().textContains("Simulated 2000 rolls")).exists())
        device.findObject(UiSelector().text("Done")).click()
    }

    // ---------- theme & images ----------

    @Test fun themeSwitchToNeon_appliesAndPersists() {
        selectSpinner(R.id.themeSpinner, "Theme: Neon")
        device.wait(Until.hasObject(By.text("🎲 RPG Dice Roller")), 5000)
        onView(withText("🎲 RPG Dice Roller")).check(matches(isDisplayed()))

        // saveTheme is async — poll the mirror until it lands (5s budget).
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val deadline = System.currentTimeMillis() + 5000
        var saved: String? = null
        while (System.currentTimeMillis() < deadline) {
            saved = ctx.getSharedPreferences("dice_theme", Context.MODE_PRIVATE)
                .getString("theme", null)
            if (saved == "Neon") break
            Thread.sleep(100)
        }
        assertEquals("Neon", saved)
    }

    @Test fun imagesSwitch_usesImageViewsForD6() {
        onView(withId(R.id.imagesSwitch)).perform(scrollTo(), click())
        scenario.onActivity { act ->
            val c = act.findViewById<LinearLayout>(R.id.diceContainer)
            assertTrue(c.childCount > 0)
            assertTrue(
                "expected an ImageView, got ${c.getChildAt(0).javaClass.simpleName}",
                c.getChildAt(0) is ImageView
            )
        }
        rollAndWait()
    }
}
