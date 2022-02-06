package org.owntracks.android.ui.map

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentFactory
import org.owntracks.android.support.ContactImageBindingAdapter
import org.owntracks.android.support.Preferences
import org.owntracks.android.ui.map.osm.OSMMapFragment
import timber.log.Timber
import javax.inject.Inject

/**
 * An [FragmentFactory] instance that can either provide a GoogleMap fragment or an OSM fragment
 * depending on whether the use-osm-map preference is set.
 *
 * @property preferences can be used to decide what implementation of [MapFragment] should be used.
 * @property contactImageBindingAdapter A binding adapter that can render contact images in views
 */

class MapFragmentFactory @Inject constructor(
    private val preferences: Preferences,
    private val contactImageBindingAdapter: ContactImageBindingAdapter
) : FragmentFactory() {
    override fun instantiate(classLoader: ClassLoader, className: String): Fragment {
        Timber.d("Instantiating Fragment for $className")

        return if (MapFragment::class.java.isAssignableFrom(classLoader.loadClass(className))) {
            if (preferences.experimentalFeatures.contains(Preferences.EXPERIMENTAL_FEATURE_USE_OSM_MAP)) {
                OSMMapFragment(contactImageBindingAdapter)
            } else {
                GoogleMapFragment(contactImageBindingAdapter)
            }
        } else {
            super.instantiate(classLoader, className)
        }
    }
}