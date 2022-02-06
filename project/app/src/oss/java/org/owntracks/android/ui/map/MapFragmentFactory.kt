package org.owntracks.android.ui.map

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentFactory
import org.owntracks.android.support.ContactImageBindingAdapter
import org.owntracks.android.ui.map.osm.OSMMapFragment
import javax.inject.Inject

/**
 * An implementation of an [FragmentFactory] that always returns an [OSMMapFragment]
 *
 * @property contactImageBindingAdapter A binding adapter that can render contact images in views
 */
class MapFragmentFactory @Inject constructor(
    private val contactImageBindingAdapter: ContactImageBindingAdapter
) : FragmentFactory() {
    override fun instantiate(classLoader: ClassLoader, className: String): Fragment {
        return if (MapFragment::class.java.isAssignableFrom(classLoader.loadClass(className))) {
            OSMMapFragment(contactImageBindingAdapter)
        } else {
            super.instantiate(classLoader, className)
        }
    }
}