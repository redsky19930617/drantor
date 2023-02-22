package org.owntracks.android.ui.preferences

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuProvider
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import org.owntracks.android.R
import org.owntracks.android.databinding.UiPreferencesBinding
import org.owntracks.android.support.DrawerProvider
import org.owntracks.android.ui.mixins.ServiceStarter
import org.owntracks.android.ui.mixins.WorkManagerInitExceptionNotifier
import org.owntracks.android.ui.status.StatusActivity

@AndroidEntryPoint
open class PreferencesActivity :
    AppCompatActivity(),
    PreferenceFragmentCompat.OnPreferenceStartFragmentCallback,
    WorkManagerInitExceptionNotifier by WorkManagerInitExceptionNotifier.Impl(),
    ServiceStarter by ServiceStarter.Impl() {
    private lateinit var binding: UiPreferencesBinding

    @Inject
    lateinit var drawerProvider: DrawerProvider

    protected open val startFragment: Fragment?
        get() = PreferencesFragment()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.ui_preferences)
        binding =
            DataBindingUtil.setContentView<UiPreferencesBinding>(this, R.layout.ui_preferences)
                .apply {
                    appbar.toolbar.run {
                        setSupportActionBar(this)
                        drawerProvider.attach(this)
                    }
                }

        supportFragmentManager.addOnBackStackChangedListener {
            if (supportFragmentManager.fragments.isEmpty()) {
                setToolbarTitle(title)
            } else {
                setToolbarTitle(
                    (supportFragmentManager.fragments[0] as PreferenceFragmentCompat).preferenceScreen.title
                )
            }
        }
        val fragmentTransaction = supportFragmentManager.beginTransaction()
            .replace(R.id.content_frame, startFragment!!, null)
        fragmentTransaction.commit()
        supportFragmentManager.executePendingTransactions()
        // We may have come here straight from the WelcomeActivity, so start the service.
        startService(this)

        notifyOnWorkManagerInitFailure(this)
    }

    private fun setToolbarTitle(text: CharSequence?) {
        binding.appbar.toolbar.title = text
    }

    override fun onPreferenceStartFragment(
        caller: PreferenceFragmentCompat,
        pref: Preference
    ): Boolean {
        val args = pref.extras
        val fragment = supportFragmentManager.fragmentFactory.instantiate(
            classLoader,
            pref.fragment!!
        )
        fragment.arguments = args
        // Replace the existing Fragment with the new Fragment
        supportFragmentManager.beginTransaction()
            .replace(R.id.content_frame, fragment)
            .addToBackStack(pref.key)
            .commit()

        return true
    }
}
