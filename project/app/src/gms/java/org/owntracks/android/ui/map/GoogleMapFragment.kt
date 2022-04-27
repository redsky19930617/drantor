package org.owntracks.android.ui.map

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.Bitmap
import android.location.Location
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Observer
import com.google.android.gms.maps.*
import com.google.android.gms.maps.GoogleMap.*
import com.google.android.gms.maps.GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE
import com.google.android.gms.maps.model.*
import org.owntracks.android.R
import org.owntracks.android.data.WaypointModel
import org.owntracks.android.databinding.GoogleMapFragmentBinding
import org.owntracks.android.gms.location.toGMSLatLng
import org.owntracks.android.location.toLatLng
import org.owntracks.android.support.ContactImageBindingAdapter
import org.owntracks.android.support.Preferences
import timber.log.Timber

class GoogleMapFragment internal constructor(
    private val preferences: Preferences,
    contactImageBindingAdapter: ContactImageBindingAdapter
) :
    MapFragment<GoogleMapFragmentBinding>(contactImageBindingAdapter),
    OnMapReadyCallback,
    OnMapsSdkInitializedCallback {

    data class RegionOnMap(val marker: Marker, val circle: Circle)

    override val layout: Int
        get() = R.layout.google_map_fragment

    private var locationObserver: Observer<Location>? = null
    private val googleMapLocationSource: LocationSource by lazy {
        object : LocationSource {
            override fun activate(onLocationChangedListener: LocationSource.OnLocationChangedListener) {
                locationObserver = object : Observer<Location> {
                    override fun onChanged(location: Location) {
                        onLocationChangedListener.onLocationChanged(location)
                        viewModel.setMapLocation(location.toLatLng())
                        if (viewModel.viewMode == MapViewModel.ViewMode.Device) {
                            updateCamera(location.toLatLng())
                        }
                    }
                }
                locationObserver?.run {
                    viewModel.currentLocation.observe(viewLifecycleOwner, this)
                }
            }

            override fun deactivate() {
                locationObserver?.run(viewModel.currentLocation::removeObserver)
            }
        }
    }

    private var googleMap: GoogleMap? = null
    private val markersOnMap: MutableMap<String, Marker> = HashMap()
    private val regionsOnMap: MutableList<RegionOnMap> = mutableListOf()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val root = super.onCreateView(inflater, container, savedInstanceState)
        binding.googleMapView.onCreate(savedInstanceState)
        binding.googleMapView.getMapAsync(this)
        return root
    }

    override fun onMapReady(googleMap: GoogleMap) {
        this.googleMap = googleMap
        initMap()
        viewModel.onMapReady()
    }

    private fun setMapStyle() {
        if (resources.configuration.uiMode.and(Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES) {
            googleMap?.setMapStyle(
                MapStyleOptions.loadRawResourceStyle(
                    requireContext(),
                    R.raw.google_maps_night_theme
                )
            )
        }
    }

    @SuppressLint("MissingPermission")
    override fun initMap() {
        MapsInitializer.initialize(requireContext(), MapsInitializer.Renderer.LATEST, this)
        this.googleMap?.run {
            val myLocationEnabled =
                (requireActivity() as MapActivity).checkAndRequestMyLocationCapability(false)
            Timber.d("GoogleMapFragment initMap hasLocationCapability=$myLocationEnabled")
            isIndoorEnabled = false
            isMyLocationEnabled = myLocationEnabled
            uiSettings.isMyLocationButtonEnabled = false
            uiSettings.setAllGesturesEnabled(true)

            setLocationSource(googleMapLocationSource)

            setMapStyle()

            moveCamera(
                CameraUpdateFactory.newLatLngZoom(
                    viewModel.getMapLocation().toGMSLatLng(),
                    ZOOM_LEVEL_STREET
                )
            )

            setOnMarkerClickListener {
                it.tag?.run {
                    onMarkerClicked(this as String)
                    true
                } ?: false
            }

            setOnMapClickListener { onMapClick() }
            setOnCameraMoveStartedListener { reason ->
                if (reason == REASON_GESTURE) {
                    onMapClick()
                }
            }

            viewModel.mapLayerStyle.value?.run {
                setMapLayerType(this)
            }

            // We need to specifically re-draw any contact markers and regions now that we've re-init the map
            viewModel.allContacts.value?.values?.toSet()?.run(::updateAllMarkers)
            viewModel.regions.value?.toSet()?.run(::drawRegions)
        }
    }

    override fun updateCamera(latLng: org.owntracks.android.location.LatLng) {
        googleMap?.moveCamera(CameraUpdateFactory.newLatLng(latLng.toGMSLatLng()))
    }

    override fun clearMarkers() {
        this.googleMap?.clear()
        markersOnMap.clear()
    }

    override fun updateMarkerOnMap(
        id: String,
        latLng: org.owntracks.android.location.LatLng,
        image: Bitmap
    ) {
        googleMap?.run { // If we don't have a google Map, we can't add markers to it
            // Remove null markers from the collection
            markersOnMap.values.removeAll { it.tag == null }
            markersOnMap.getOrPut(id) {
                addMarker(
                    MarkerOptions()
                        .position(latLng.toGMSLatLng())
                        .anchor(0.5f, 0.5f).visible(false)
                )!!.also { it.tag = id }
            }.run {
                position = latLng.toGMSLatLng()
                setIcon(BitmapDescriptorFactory.fromBitmap(image))
                isVisible = true
            }
        }
    }

    override fun removeMarkerFromMap(id: String) {
        markersOnMap[id]?.remove()
    }

    override fun onResume() {
        super.onResume()
        binding.googleMapView.onResume()
        setMapStyle()
    }

    override fun onLowMemory() {
        binding.googleMapView.onLowMemory()
        super.onLowMemory()
    }

    override fun onPause() {
        binding.googleMapView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        binding.googleMapView.onDestroy()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        binding.googleMapView.onSaveInstanceState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onStart() {
        super.onStart()
        binding.googleMapView.onStart()
    }

    override fun onStop() {
        binding.googleMapView.onStop()
        super.onStop()
    }


    override fun onMapsSdkInitialized(renderer: MapsInitializer.Renderer) {
        Timber.d("Maps SDK initialized with renderer: ${renderer.name}")
    }

    override fun drawRegions(regions: Set<WaypointModel>) {
        if (preferences.showRegionsOnMap) {
            googleMap?.run {
                Timber.d("Drawing regions on map")
                regionsOnMap.forEach {
                    it.circle.remove()
                    it.marker.remove()
                }
                regions.forEach { region ->
                    RegionOnMap(
                        MarkerOptions().apply {
                            position(region.location.toLatLng().toGMSLatLng())
                            anchor(0.5f, 1.0f)
                            title(region.description)
                        }.let { addMarker(it)!! },
                        CircleOptions().apply {
                            center(region.location.toLatLng().toGMSLatLng())
                            radius(region.geofenceRadius.toDouble())
                            fillColor(getRegionColor())
                            strokeWidth(0.0f)
                        }.let { addCircle(it) }
                    ).run(regionsOnMap::add)
                }
            }
        }
    }

    companion object {
        private const val ZOOM_LEVEL_STREET: Float = 15f
    }

    override fun setMapLayerType(mapLayerStyle: MapLayerStyle) {
        when (mapLayerStyle) {
            MapLayerStyle.GoogleMapDefault -> {
                googleMap?.mapType = MAP_TYPE_NORMAL
            }
            MapLayerStyle.GoogleMapHybrid -> {
                googleMap?.mapType = MAP_TYPE_HYBRID
            }
            MapLayerStyle.GoogleMapSatellite -> {
                googleMap?.mapType = MAP_TYPE_SATELLITE
            }
            MapLayerStyle.GoogleMapTerrain -> {
                googleMap?.mapType = MAP_TYPE_TERRAIN
            }
            else -> {
                Timber.w("Unsupported map layer type $mapLayerStyle")
            }
        }
    }
}