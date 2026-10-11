package com.example.diceroller

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.matcher.BoundedMatcher
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.util.TreeIterables
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.TypeSafeMatcher

/** Polls the whole hierarchy until [matcher] matches (handles the async roll animation). */
fun waitFor(matcher: Matcher<View>, timeoutMs: Long = 8000): ViewAction = object : ViewAction {
    override fun getConstraints(): Matcher<View> = isRoot()
    override fun getDescription(): String = "wait up to ${timeoutMs}ms for $matcher"
    override fun perform(uiController: UiController, view: View) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            if (TreeIterables.breadthFirstViewTraversal(view).any { matcher.matches(it) }) return
            if (System.currentTimeMillis() >= deadline) {
                throw AssertionError("Timed out after ${timeoutMs}ms waiting for $matcher")
            }
            uiController.loopMainThreadFor(50)
        }
    }
}

fun withChildCount(expected: Int): Matcher<View> = object : TypeSafeMatcher<View>() {
    override fun describeTo(description: Description) {
        description.appendText("a ViewGroup with exactly $expected children")
    }
    override fun matchesSafely(view: View): Boolean =
        view is ViewGroup && view.childCount == expected
}

fun withTextMatching(regex: String): Matcher<View> =
    object : BoundedMatcher<View, TextView>(TextView::class.java) {
        override fun describeTo(description: Description) {
            description.appendText("TextView with text matching /$regex/")
        }
        override fun matchesSafely(item: TextView): Boolean =
            item.text.toString().matches(Regex(regex))
    }
