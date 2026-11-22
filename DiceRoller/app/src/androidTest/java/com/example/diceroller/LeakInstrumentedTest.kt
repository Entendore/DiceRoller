package com.example.diceroller

import android.app.Activity
import android.os.SystemClock
import android.view.View
import android.widget.LinearLayout
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onData
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.lang.ref.WeakReference
import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.CoreMatchers.instanceOf
import org.hamcrest.CoreMatchers.`is`
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LeakInstrumentedTest {

    private val device: UiDevice =
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    private fun gcUntilCleared(ref: WeakReference<*>, timeoutMs: Long = 5_000): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            if (ref.get() == null) return true
            Runtime.getRuntime().gc(); SystemClock.sleep(100)
        }
        return ref.get() == null
    }

    private fun waitDiceCount(n: Int) {
        onView(isRoot()).perform(
            waitFor(allOf(withId(R.id.diceContainer), withChildCount(n)))
        )
    }

    /** Also guards the SensorManager listener: app-scoped, so a missed
     *  unregister would pin the destroyed Activities below and fail this test. */
    @Test fun activitiesAreCollectibleAfterRecreations() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        waitDiceCount(2)
        lateinit var firstRef: WeakReference<Activity>
        scenario.onActivity { firstRef = WeakReference(it) }

        scenario.recreate(); waitDiceCount(2)
        scenario.recreate(); waitDiceCount(2)

        lateinit var lastRef: WeakReference<Activity>
        scenario.onActivity { lastRef = WeakReference(it) }
        scenario.close()

        assertTrue("Recreated Activity instances were leaked", gcUntilCleared(firstRef))
        assertTrue("Final Activity instance was leaked", gcUntilCleared(lastRef))
    }

    @Test fun openDialogDoesNotRetainActivityOnDestroy() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        waitDiceCount(2)

        lateinit var ref: WeakReference<Activity>
        scenario.onActivity { act ->
            ref = WeakReference(act)
            act.findViewById<View>(R.id.editWeightsButton).performClick()
        }
        device.wait(Until.hasObject(By.textContains("Weights for d6")), 5_000)

        scenario.moveToState(Lifecycle.State.DESTROYED)
        assertTrue(
            "Open dialog leaked the Activity — are all dialogs using showManaged()?",
            gcUntilCleared(ref)
        )
    }

    @Test fun rebuiltDiceViewsAreCollectible() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        waitDiceCount(2)

        lateinit var removedView: WeakReference<View>
        scenario.onActivity { act ->
            val container = act.findViewById<LinearLayout>(R.id.diceContainer)
            removedView = WeakReference(container.getChildAt(0))
        }

        onView(withId(R.id.diceCountSpinner)).perform(scrollTo(), click())
        onData(allOf(instanceOf(String::class.java), `is`("1 die"))).perform(click())
        waitDiceCount(1)
        scenario.close()

        assertTrue(
            "Detached DiceView was retained (animator, static, or parent not cleared)",
            gcUntilCleared(removedView)
        )
    }

    /** Anti-over-collection: the repo is app-scoped ON PURPOSE. */
    @Test fun repositoryIsApplicationScoped_notClearedWithActivity() {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        waitDiceCount(2)
        lateinit var repoRef: WeakReference<DiceRepository>
        scenario.onActivity { act ->
            repoRef = WeakReference((act.application as DiceApp).repo)
        }
        scenario.close()

        repeat(3) { Runtime.getRuntime().gc(); SystemClock.sleep(100) }
        assertNotNull("Repository became Activity-scoped — it must live in DiceApp", repoRef.get())
    }
}
