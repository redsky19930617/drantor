package org.owntracks.android.ui.map.osm

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent.ACTION_BUTTON_RELEASE
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.preference.PreferenceManager
import dagger.hilt.android.AndroidEntryPoint
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.TilesOverlay
import org.osmdroid.views.overlay.mylocation.IMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import org.owntracks.android.R
import org.owntracks.android.data.repos.LocationRepo
import org.owntracks.android.databinding.OsmMapFragmentBinding
import org.owntracks.android.location.LatLng
import org.owntracks.android.location.toGeoPoint
import org.owntracks.android.location.toOSMLocationSource
import org.owntracks.android.ui.map.MapActivity
import org.owntracks.android.ui.map.MapActivity.Companion.STARTING_LATITUDE
import org.owntracks.android.ui.map.MapActivity.Companion.STARTING_LONGITUDE
import org.owntracks.android.ui.map.MapFragment
import org.owntracks.android.ui.map.MapLocationSource
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class OSMMapFragment internal constructor() : MapFragment() {
    private var locationSource: IMyLocationProvider? = null
    private var mapView: MapView? = null
    private var binding: OsmMapFragmentBinding? = null

    @Inject
    lateinit var locationRepo: LocationRepo

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        Configuration.getInstance().apply {
            load(context, PreferenceManager.getDefaultSharedPreferences(context))
            osmdroidBasePath.resolve("tiles").run {
                if (exists()) {
                    deleteRecursively()
                }
            }
            osmdroidTileCache = requireContext().noBackupFilesDir.resolve("osmdroid/tiles")
        }
        binding = DataBindingUtil.inflate(inflater, R.layout.osm_map_fragment, container, false)
        if (requireActivity() is MapActivity) {
            if (locationSource == null) {
                locationSource = (activity as MapActivity).mapLocationSource.toOSMLocationSource()
            }
        }
        initMap()
        ((requireActivity()) as MapActivity).onMapReady()
        return binding!!.root
    }

    private fun setMapStyle() {
        if (resources.configuration.uiMode.and(android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES) {
            mapView?.run {
                overlayManager.tilesOverlay.setColorFilter(TilesOverlay.INVERT_COLORS)
            }
        } else {
            mapView?.run {
                overlayManager.tilesOverlay.setColorFilter(null)
            }
        }
    }

    private fun initMap() {
        val myLocationEnabled =
            (requireActivity() as MapActivity).checkAndRequestMyLocationCapability(false)
        Timber.d("OSMMapFragment initMap locationEnabled=$myLocationEnabled")
        mapView = this.binding!!.osmMapView.apply {
            setTileSource(TileSourceFactory.MAPNIK)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT)
            controller.setZoom(ZOOM_STREET_LEVEL)
            if (locationRepo.currentLocation != null) {
                controller.setCenter(
                    GeoPoint(
                        locationRepo.currentLocation!!.latitude,
                        locationRepo.currentLocation!!.longitude
                    )
                )
            } else {
                controller.setCenter(GeoPoint(STARTING_LATITUDE, STARTING_LONGITUDE))
            }


            locationSource?.also {
                overlays.add(
                    MyLocationNewOverlay(it, this)
                )
            }

            setMultiTouchControls(true)
            setOnClickListener {
                (activity as MapActivity).onMapClick()
            }
            setOnTouchListener { v, motionEvent ->
                if (motionEvent.action == ACTION_BUTTON_RELEASE) {
                    v.performClick()
                }
                (activity as MapActivity).onMapClick()
                false
            }
        }
        setMapStyle()
    }

    override fun clearMarkers() {
        mapView?.overlays?.clear()
    }

    override fun updateCamera(latLng: LatLng) {
        mapView?.controller?.run {
            setCenter(latLng.toGeoPoint())
        }
    }

    override fun updateMarker(id: String, latLng: LatLng) {
        mapView?.run {
            val existingMarker: Marker? =
                overlays.firstOrNull { it is Marker && it.id == id } as Marker?
            if (existingMarker != null) {
                existingMarker.position = latLng.toGeoPoint()
            } else {
                overlays.add(0, Marker(this).apply {
                    this.id = id
                    position = latLng.toGeoPoint()
                    infoWindow = null
                    setOnMarkerClickListener { marker, _ ->
                        (activity as MapActivity).onMarkerClicked(marker.id)
                        true
                    }
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                })
            }
        }
    }

    override fun removeMarker(id: String) {
        mapView?.run {
            overlays.removeAll { it is Marker && it.id == id }
        }
    }

    override fun setMarkerImage(id: String, bitmap: Bitmap) {
        mapView?.run {
            overlays.firstOrNull { it is Marker && it.id == id }?.run {
                (this as Marker).icon = BitmapDrawable(resources, bitmap)
            }
        }
    }

    override fun myLocationEnabled() {
        initMap()
    }

    override fun setMapLocationSource(mapLocationSource: MapLocationSource) {
        this.locationSource = mapLocationSource.toOSMLocationSource()
    }

    override fun onResume() {
        super.onResume()
        mapView?.onResume()
        setMapStyle()
    }

    override fun onPause() {
        mapView?.onPause()
        super.onPause()
    }

    override fun onDetach() {
        mapView?.onDetach()
        super.onDetach()
    }

    companion object {
        private const val ZOOM_STREET_LEVEL: Double = 16.0
    }
}