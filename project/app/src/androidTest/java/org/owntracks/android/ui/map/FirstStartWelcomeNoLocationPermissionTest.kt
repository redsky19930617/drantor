package org.owntracks.android.ui.map


import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.preference.PreferenceManager
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.not
import org.hamcrest.TypeSafeMatcher
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.owntracks.android.R

@LargeTest
@RunWith(AndroidJUnit4::class)
@Ignore
class FirstStartWelcomeNoLocationPermissionTest {

    @Rule
    @JvmField
    var activityScenarioRule = ActivityScenarioRule(MapActivity::class.java).apply {
        val context = getInstrumentation().targetContext
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .clear()
                .putBoolean("firstStart", false)
                .putBoolean("setupNotCompleted", true)
                .commit()
        context.getSharedPreferences("org.owntracks.android.preferences.private", Context.MODE_PRIVATE).edit().clear().apply()
    }

    @Test
    fun welcomeTest() {
        val textView = onView(
                allOf(withId(R.id.screen_heading), withText("Welcome"),
                        childAtPosition(
                                allOf(withId(R.id.welcome_fragment),
                                        withParent(withId(R.id.viewPager))),
                                1),
                        isDisplayed()))
        textView.check(matches(withText("Welcome")))
        onView(withId(R.id.btn_next)).check(matches(isEnabled()))
        onView(withId(R.id.btn_next)).perform(click())
        onView(withId(R.id.btn_next)).perform(click())
        onView(withId(R.id.btn_next)).check(matches(not(isDisplayed())))
        onView(withId(R.id.fix_permissions_button)).check(matches(isDisplayed()))
    }

    private fun childAtPosition(
            parentMatcher: Matcher<View>, position: Int): Matcher<View> {

        return object : TypeSafeMatcher<View>() {
            override fun describeTo(description: Description) {
                description.appendText("Child at position $position in parent ")
                parentMatcher.describeTo(description)
            }

            public override fun matchesSafely(view: View): Boolean {
                val parent = view.parent
                return parent is ViewGroup && parentMatcher.matches(parent)
                        && view == parent.getChildAt(position)
            }
        }
    }
}
