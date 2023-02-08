package org.owntracks.android.ui.map

import android.graphics.Bitmap
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.owntracks.android.R
import org.owntracks.android.data.WaypointModel
import org.owntracks.android.location.LatLng
import org.owntracks.android.model.FusedContact
import org.owntracks.android.support.ContactImageBindingAdapter
import timber.log.Timber

abstract class MapFragment<V : ViewDataBinding> internal constructor(
    private val contactImageBindingAdapter: ContactImageBindingAdapter
) : Fragment() {
    protected abstract val layout: Int
    protected lateinit var binding: V
    abstract fun updateCamera(latLng: LatLng)
    abstract fun updateMarkerOnMap(id: String, latLng: LatLng, image: Bitmap)
    abstract fun removeMarkerFromMap(id: String)
    abstract fun initMap()
    abstract fun drawRegions(regions: Set<WaypointModel>)
    abstract fun setMapLayerType(mapLayerStyle: MapLayerStyle)
    protected val viewModel: MapViewModel by activityViewModels()

    protected fun getRegionColor(): Int {
        val typedValue = TypedValue()
        requireContext().theme.resolveAttribute(R.attr.colorRegion, typedValue, true)
        return typedValue.data
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = DataBindingUtil.inflate(inflater, layout, container, false)
        binding.lifecycleOwner = this.viewLifecycleOwner

        viewModel.mapCenter.observe(viewLifecycleOwner, this::updateCamera)
        viewModel.allContacts.observe(viewLifecycleOwner) { contacts ->
            updateAllMarkers(contacts.values.toSet())

            /*
            allContacts gets fired whenever any marker location changes, so we can update the camera
            if we're following one.
             */
            if (viewModel.viewMode == MapViewModel.ViewMode.Contact(true)) {
                viewModel.currentContact.value?.latLng?.run(this::updateCamera)
            }
        }

        viewModel.regions.observe(viewLifecycleOwner) { regions ->
            drawRegions(regions.toSet())
        }
        viewModel.mapLayerStyle.observe(viewLifecycleOwner, this::setMapLayerType)
        viewModel.onMapReady()
        return binding.root
    }

    internal fun updateAllMarkers(contacts: Set<FusedContact>) {
        contacts.forEach {
            updateMarkerForContact(it)
            if (it == viewModel.currentContact.value) {
                viewModel.refreshGeocodeForContact(it)
            }
        }
    }

    private fun updateMarkerForContact(contact: FusedContact) {
        if (contact.latLng == null) {
            Timber.w("unable to update marker for $contact. no location")
            return
        }
        Timber.v("updating marker for contact: %s", contact.id)
        lifecycleScope.launch {
            contactImageBindingAdapter.run {
                updateMarkerOnMap(contact.id, contact.latLng!!, getBitmapFromCache(contact))
            }
        }
    }

    fun onMapClick() {
        viewModel.onMapClick()
    }

    fun onMarkerClicked(id: String) {
        viewModel.onMarkerClick(id)
    }
}
