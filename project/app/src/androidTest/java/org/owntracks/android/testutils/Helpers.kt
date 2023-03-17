package org.owntracks.android.testutils

import android.Manifest
import android.app.Activity
import android.os.Build
import android.view.View
import android.widget.Checkable
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.*
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.adevinta.android.barista.interaction.BaristaDialogInteractions.clickDialogPositiveButton
import com.adevinta.android.barista.interaction.BaristaDrawerInteractions.openDrawer
import com.adevinta.android.barista.interaction.BaristaEditTextInteractions
import com.adevinta.android.barista.interaction.PermissionGranter
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import org.hamcrest.BaseMatcher
import org.hamcrest.CoreMatchers.isA
import org.hamcrest.Description
import org.owntracks.android.R
import org.owntracks.android.preferences.Preferences
import org.owntracks.android.ui.clickOnAndWait
import org.owntracks.android.ui.map.MapActivity
import org.owntracks.android.ui.preferences.load.LoadActivity
import timber.log.Timber

fun scrollToPreferenceWithText(textResource: Int) {
    onView(withId(androidx.preference.R.id.recycler_view)).perform(
        RecyclerViewActions.actionOnItem<RecyclerView.ViewHolder>(
            hasDescendant(withText(textResource)),
            ViewActions.scrollTo()
        )
    )
}

fun writeToPreference(textResource: Int, value: String) {
    scrollToPreferenceWithText(textResource)
    clickOnAndWait(textResource)
    BaristaEditTextInteractions.writeTo(android.R.id.edit, value)
    clickDialogPositiveButton()
}

fun setNotFirstStartPreferences() {
    val context = getInstrumentation().targetContext
    PreferenceManager.getDefaultSharedPreferences(context)
        .edit()
        .putBoolean(Preferences::firstStart.name, false)
        .putBoolean(Preferences::setupCompleted.name, true)
        .apply()
}

fun reportLocationFromMap(locationIdlingResource: IdlingResource?, mockLocationFunction: () -> Unit = {}) {
    if (getCurrentActivity() !is MapActivity && getCurrentActivity() !is LoadActivity) {
        openDrawer()
        clickOnAndWait(R.string.title_activity_map)
    }
    waitUntilActivityVisible<MapActivity>()
    mockLocationFunction()
    clickOnAndWait(R.id.menu_monitoring)
    clickOnAndWait(R.id.fabMonitoringModeMove)
    locationIdlingResource.with {
        clickOnAndWait(R.id.fabMyLocation)
        clickOnAndWait(R.id.menu_report)
    }
}

const val TIMEOUT = 5000L
const val CONDITION_CHECK_INTERVAL = 100L
const val OWNTRACKS_ICON_BASE64: String =
    "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" // ktlint-disable max-line-length

inline fun <reified T : Activity> waitUntilActivityVisible() {
    val startTime = System.currentTimeMillis()
    Timber.d("Waiting for ${T::class.java.simpleName} to be visible")
    while (getCurrentActivity() !is T) {
        Thread.sleep(CONDITION_CHECK_INTERVAL)
        if (System.currentTimeMillis() - startTime >= TIMEOUT) {
            throw AssertionError(
                "Activity ${T::class.java.simpleName} not visible after $TIMEOUT milliseconds"
            )
        }
    }
    Timber.d("${T::class.java.simpleName} is now visible")
}

fun getCurrentActivity(): Activity? {
    var currentActivity: Activity? = null
    getInstrumentation().runOnMainSync {
        run {
            currentActivity = ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED)
                .elementAtOrNull(0)
        }
    }
    return currentActivity
}

/**
 * Runs the given code block once the [IdlingResource] is idle
 *
 * @param timeout time to wait for the [IdlingResource] to be idle
 * @param block function to execute once idle
 */
inline fun IdlingResource?.with(timeout: Duration = 30.seconds, block: () -> Unit) {
    if (this == null) {
        Timber.w("Idling resource is null")
    }
    IdlingPolicies.setIdlingResourceTimeout(timeout.inWholeSeconds, TimeUnit.SECONDS)
    try {
        this?.run {
            Timber.i("Registering idling resource ${this.name}")
            IdlingRegistry.getInstance()
                .register(this)
        }
        block()
    } finally {
        this?.run {
            Timber.i("Unregistering idling resource ${this.name}")
            IdlingRegistry.getInstance()
                .unregister(this)
        }
    }
}

fun disableDeviceLocation() {
    val cmd = if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O) {
        "settings put secure location_mode 0"
    } else {
        "settings put secure location_providers_allowed -gps"
    }

    getInstrumentation().uiAutomation.executeShellCommand(cmd)
        .close()
}

fun stopAndroidSetupProcess() {
    listOf("com.google.android.setupwizard", "com.android.systemui", "com.android.vending").forEach {
        getInstrumentation().uiAutomation.executeShellCommand("am force-stop $it")
            .close()
    }
}

fun disableHeadsupNotifications() {
    getInstrumentation().uiAutomation.executeShellCommand("settings put global heads_up_notifications_enabled 0")
        .close()
}

fun enableDeviceLocation() {
    val cmd = if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O) {
        "settings put secure location_mode 3"
    } else {
        "settings put secure location_providers_allowed +gps"
    }

    getInstrumentation().uiAutomation.executeShellCommand(cmd)
        .close()
}

fun grantMapActivityPermissions() {
    PermissionGranter.allowPermissionsIfNeeded(Manifest.permission.POST_NOTIFICATIONS)
    PermissionGranter.allowPermissionsIfNeeded(Manifest.permission.ACCESS_FINE_LOCATION)
}
