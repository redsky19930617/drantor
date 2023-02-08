package org.owntracks.android.ui.map

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.ColorStateList
import android.hardware.Sensor
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
import android.util.TypedValue
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.appcompat.widget.PopupMenu
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.setPadding
import androidx.core.widget.ImageViewCompat
import androidx.databinding.BindingAdapter
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import androidx.test.espresso.IdlingResource
import androidx.test.espresso.idling.CountingIdlingResource
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.owntracks.android.BR
import org.owntracks.android.R
import org.owntracks.android.data.repos.LocationRepo
import org.owntracks.android.databinding.UiMapBinding
import org.owntracks.android.geocoding.GeocoderProvider
import org.owntracks.android.model.FusedContact
import org.owntracks.android.preferences.Preferences.Companion.EXPERIMENTAL_FEATURE_BEARING_ARROW_FOLLOWS_DEVICE_ORIENTATION
import org.owntracks.android.preferences.types.ConnectionMode
import org.owntracks.android.preferences.types.MonitoringMode
import org.owntracks.android.services.BackgroundService
import org.owntracks.android.services.BackgroundService.BACKGROUND_LOCATION_RESTRICTION_NOTIFICATION_TAG
import org.owntracks.android.support.ContactImageBindingAdapter
import org.owntracks.android.support.RequirementsChecker
import org.owntracks.android.support.RunThingsOnOtherThreads
import org.owntracks.android.ui.base.BaseActivity
import org.owntracks.android.ui.base.viewmodel.NoOpViewModel
import org.owntracks.android.ui.mixins.ServiceStarter
import org.owntracks.android.ui.mixins.WorkManagerInitExceptionNotifier
import org.owntracks.android.ui.welcome.WelcomeActivity
import timber.log.Timber

@AndroidEntryPoint
class MapActivity :
    BaseActivity<UiMapBinding?, NoOpViewModel>(),
    View.OnClickListener,
    View.OnLongClickListener,
    PopupMenu.OnMenuItemClickListener,
    WorkManagerInitExceptionNotifier by WorkManagerInitExceptionNotifier.Impl(),
    ServiceStarter by ServiceStarter.Impl() {
    private val mapViewModel: MapViewModel by viewModels()
    private var previouslyHadLocationPermissions: Boolean = false
    private var service: BackgroundService? = null
    lateinit var locationLifecycleObserver: LocationLifecycleObserver
    private var bottomSheetBehavior: BottomSheetBehavior<LinearLayoutCompat>? = null
    private var menu: Menu? = null
    private var sensorManager: SensorManager? = null
    private var orientationSensor: Sensor? = null

    private lateinit var locationServicesAlertDialog: AlertDialog
    private lateinit var locationPermissionsRationaleAlertDialog: AlertDialog

    @Inject
    lateinit var locationRepo: LocationRepo

    @Inject
    lateinit var runThingsOnOtherThreads: RunThingsOnOtherThreads

    @Inject
    lateinit var contactImageBindingAdapter: ContactImageBindingAdapter

    @Inject
    lateinit var geocoderProvider: GeocoderProvider

    @Inject
    lateinit var countingIdlingResource: CountingIdlingResource

    @Inject
    lateinit var requirementsChecker: RequirementsChecker

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Timber.d("Service connected to MapActivity")
            this@MapActivity.service = (service as BackgroundService.LocalBinder).service
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Timber.d("Service disconnected from MapActivity")
            service = null
        }
    }

    private var onBottomSheetLabelTextSizeChangedListener:
        AutoResizingTextViewWithListener.OnTextSizeChangedListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        EntryPointAccessors.fromActivity(
            this,
            MapActivityEntryPoint::class.java
        )
            .let {
                supportFragmentManager.fragmentFactory = it.fragmentFactory
            }

        super.onCreate(savedInstanceState)

        if (!preferences.setupCompleted) {
            startActivity(Intent(this, WelcomeActivity::class.java))
            finish()
            return
        }
        bindAndAttachContentView(R.layout.ui_map, savedInstanceState)

        binding?.also { binding ->
            setSupportToolbar(binding.appbar.toolbar, false, true)
            setDrawer(binding.appbar.toolbar)
            bottomSheetBehavior = BottomSheetBehavior.from(binding.bottomSheetLayout)
            binding.setVariable(BR.vm, mapViewModel)
            binding.contactPeek.contactRow.setOnClickListener(this)
            binding.contactPeek.contactRow.setOnLongClickListener(this)
            binding.moreButton.setOnClickListener { v: View -> showPopupMenu(v) }
            setBottomSheetHidden()

            // Need to set the appbar layout behaviour to be non-drag, so that we can drag the map
            val behavior = AppBarLayout.Behavior()
            behavior.setDragCallback(object : AppBarLayout.Behavior.DragCallback() {
                override fun canDrag(appBarLayout: AppBarLayout): Boolean {
                    return false
                }
            })

            binding.fabMyLocation.setOnClickListener {
                if (checkAndRequestLocationPermissions(true)) {
                    checkAndRequestLocationServicesEnabled(true)
                }
                if (mapViewModel.myLocationStatus.value != MyLocationStatus.DISABLED) {
                    mapViewModel.onMyLocationClicked()
                }
            }

            binding.fabMapLayers.setOnClickListener {
                MapLayerBottomSheetDialog().show(
                    supportFragmentManager,
                    "layerBottomSheetDialog"
                )
            }

            val labels = listOf(
                R.id.contactDetailsAccuracy,
                R.id.contactDetailsAltitude,
                R.id.contactDetailsBattery,
                R.id.contactDetailsBearing,
                R.id.contactDetailsSpeed,
                R.id.contactDetailsDistance
            )
                .map { binding.bottomSheetLayout.findViewById<View>(it) }
                .map { it.findViewById<AutoResizingTextViewWithListener>(R.id.label) }

            onBottomSheetLabelTextSizeChangedListener =
                object : AutoResizingTextViewWithListener.OnTextSizeChangedListener {
                    override fun onTextSizeChanged(view: View, newSize: Float) {
                        labels
                            .filter { it != view }
                            .filter { it.textSize > newSize || it.configurationChangedFlag }
                            .forEach {
                                it.setAutoSizeTextTypeUniformWithPresetSizes(
                                    intArrayOf(newSize.toInt()),
                                    TypedValue.COMPLEX_UNIT_PX
                                )
                                it.configurationChangedFlag = false
                            }
                    }
                }.also { listener -> labels.forEach { it.withListener(listener) } }
        }

        locationLifecycleObserver = LocationLifecycleObserver(activityResultRegistry)
        lifecycle.addObserver(locationLifecycleObserver)

        // Watch various things that the mapViewModel owns

        mapViewModel.currentContact.observe(this) { contact: FusedContact? ->
            contact?.let {
                binding?.contactPeek?.run {
                    image.setImageResource(0) // Remove old image before async loading the new one
                    lifecycleScope.launch {
                        contactImageBindingAdapter.run {
                            image.setImageBitmap(
                                getBitmapFromCache(it)
                            )
                        }
                    }
                }
            }
        }
        mapViewModel.bottomSheetHidden.observe(this) { o: Boolean? ->
            if (o == null || o) {
                setBottomSheetHidden()
            } else {
                setBottomSheetCollapsed()
            }
        }
        mapViewModel.currentLocation.observe(this) { location ->
            if (location == null) {
                disableLocationMenus()
            } else {
                enableLocationMenus()
                binding?.vm?.run {
                    updateActiveContactDistanceAndBearing(location)
                }
            }
        }
        mapViewModel.currentMonitoringMode.observe(this) {
            updateMonitoringModeMenu()
        }

        startService(this)

        // We've been started in the foreground, so cancel the background restriction notification
        NotificationManagerCompat.from(this)
            .cancel(BACKGROUND_LOCATION_RESTRICTION_NOTIFICATION_TAG, 0)

        notifyOnWorkManagerInitFailure(this)

        onBackPressedDispatcher.addCallback(this) {
            if (bottomSheetBehavior == null) {
                finish()
            } else {
                when (bottomSheetBehavior?.state) {
                    BottomSheetBehavior.STATE_HIDDEN -> finish()
                    BottomSheetBehavior.STATE_COLLAPSED -> {
                        setBottomSheetHidden()
                    }
                    BottomSheetBehavior.STATE_DRAGGING -> {
                        // Noop
                    }
                    BottomSheetBehavior.STATE_EXPANDED -> {
                        setBottomSheetCollapsed()
                    }
                    BottomSheetBehavior.STATE_HALF_EXPANDED -> {
                        setBottomSheetCollapsed()
                    }
                    BottomSheetBehavior.STATE_SETTLING -> {
                        // Noop
                    }
                }
            }
        }
    }

    internal fun checkAndRequestMyLocationCapability(explicitUserAction: Boolean): Boolean =
        checkAndRequestLocationPermissions(explicitUserAction) &&
            checkAndRequestLocationServicesEnabled(explicitUserAction)

    private val locationServicesLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            // We have to check permissions again here, because it may have been revoked in the
            // period between asking for location services and returning here.
            if (checkAndRequestLocationPermissions(false)) {
                mapViewModel.requestLocationUpdatesForBlueDot()
            }
        }

    /**
     * Performs a check that the device has location services enabled. This can be called either because the user has
     * explicitly done something that requires the location services (clicked on the MyLocation FAB), or because some
     * other action has happened and we need to re-check that the location service is enabled (e.g. onResume, or location
     * has become unavailable).
     *
     * This check may trigger a request and prompt the user to enable location services. This prompt should be raised
     * either if it's an explicit request, or if the user hasn't previously declined to enable location services.
     *
     * @param explicitUserAction Indicates whether or not the user has triggered something explicitly causing a location services check
     * @return indication as to whether location services are enabled. This will return false immediately if a
     * prompt to enable to raised, even if the user says "yes" to the prompt.
     */
    private fun checkAndRequestLocationServicesEnabled(explicitUserAction: Boolean): Boolean {
        return if (!requirementsChecker.isLocationServiceEnabled()) {
            Timber.d(Exception(), "Location Services disabled")
            if ((explicitUserAction || !preferences.userDeclinedEnableLocationServices)) {
                if (!this::locationServicesAlertDialog.isInitialized) {
                    locationServicesAlertDialog = MaterialAlertDialogBuilder(this)
                        .setCancelable(true)
                        .setIcon(R.drawable.ic_baseline_location_disabled_24)
                        .setTitle(getString(R.string.deviceLocationDisabledDialogTitle))
                        .setMessage(getString(R.string.deviceLocationDisabledDialogMessage))
                        .setPositiveButton(
                            getString(R.string.deviceLocationDisabledDialogPositiveButtonLabel)
                        ) { _, _ ->
                            locationServicesLauncher.launch(
                                Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                            )
                        }
                        .setNegativeButton(android.R.string.cancel) { _, _ ->
                            preferences.userDeclinedEnableLocationServices = true
                        }
                        .create()
                }
                if (!locationServicesAlertDialog.isShowing) {
                    locationServicesAlertDialog.show()
                }
            }
            false
        } else {
            true
        }
    }

    /**
     * A callback that's fired when the activity is resumed with the result of a location permissioned check resulting
     * from an expclit user action. We want to trigger a services check if the location permission was granted
     */
    val explicitLocationPermissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            when {
                permissions[ACCESS_COARSE_LOCATION] ?: false || permissions[ACCESS_FINE_LOCATION] ?: false -> {
                    checkAndRequestLocationServicesEnabled(true)
                    userGrantedPermissions()
                }
                else -> {
                    userDeclinedPermissions()
                }
            }
        }

    /**
     * A callback that's fired when the activity is resumed with the result of a location permission check not triggered
     * by an explicit user action.
     */
    val locationPermissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            when {
                permissions[ACCESS_COARSE_LOCATION] ?: false || permissions[ACCESS_FINE_LOCATION] ?: false -> {
                    userGrantedPermissions()
                }
                else -> {
                    userDeclinedPermissions()
                }
            }
        }

    /**
     * User has granted location permission. Ask the viewmodel to start requesting locations, set the viewmode to
     * [ViewMode.Device] and tell the service to reinitialize locations.
     *
     */
    private fun userGrantedPermissions() {
        mapViewModel.requestLocationUpdatesForBlueDot()
        mapViewModel.onMyLocationClicked()
        mapViewModel.updateMyLocationStatus()
        service?.reInitializeLocationRequests()
    }

    /**
     * User has declined to enable location permissions. Snackbar the user with the option of trying again (in case they
     * didn't mean to).
     *
     */
    private fun userDeclinedPermissions() {
        preferences.userDeclinedEnableLocationPermissions = true
        Snackbar.make(
            binding!!.mapCoordinatorLayout,
            getString(R.string.locationPermissionNotGrantedNotification),
            Snackbar.LENGTH_LONG
        )
            .setAction(getString(R.string.fixProblemLabel)) {
                startActivity(
                    Intent(ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                )
            }
            .show()
    }

    /**
     * Performs a check that the user has granted location permissions. This can be called either because the user has
     * explicitly done something that requires location permissions (clicked on the MyLocation FAB), or because some other
     * action has happened and we need to re-check location permissions (e.g. user has enabled location services).
     *
     * This check may trigger a request and prompt the user to grant location permissions. This prompt should be raised
     * either if it's an explicit request for something that needs permissions, or if the user hasn't previously denied
     * location permission.
     *
     * @param explicitUserAction Indicates whether or not the user has triggered something explicitly causing a permissions check
     * @return indication as to whether location permissions have been granted. This will return false immediately if a
     * prompt to enable to raised, even if the user says "yes" to the prompt.
     */
    private fun checkAndRequestLocationPermissions(explicitUserAction: Boolean): Boolean {
        if (!requirementsChecker.isLocationPermissionCheckPassed()) {
            Timber.d("No location permission")
            // We don't have location permission
            if ((explicitUserAction || !preferences.userDeclinedEnableLocationPermissions)) {
                // We should prompt for permission
                val permissionRequester =
                    if (explicitUserAction) explicitLocationPermissionRequest else locationPermissionRequest
                val permissions = arrayOf(ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION)
                if (shouldShowRequestPermissionRationale(ACCESS_FINE_LOCATION)) {
                    // The user may have denied us once already, so show a rationale
                    if (!this::locationPermissionsRationaleAlertDialog.isInitialized) {
                        locationPermissionsRationaleAlertDialog =
                            MaterialAlertDialogBuilder(this)
                                .setCancelable(true)
                                .setIcon(R.drawable.ic_baseline_location_disabled_24)
                                .setTitle(
                                    getString(R.string.locationPermissionRequestDialogTitle)
                                )
                                .setMessage(R.string.locationPermissionRequestDialogMessage)
                                .setPositiveButton(
                                    android.R.string.ok
                                ) { _, _ ->
                                    permissionRequester.launch(permissions)
                                }
                                .setNegativeButton(android.R.string.cancel) { _, _ ->
                                    preferences.userDeclinedEnableLocationPermissions = true
                                }
                                .create()
                    }
                    if (!locationPermissionsRationaleAlertDialog.isShowing) {
                        locationPermissionsRationaleAlertDialog.show()
                    }
                } else {
                    // No need to show rationale, just request
                    permissionRequester.launch(permissions)
                }
            }
            return false
        } else {
            return true
        }
    }

    override fun onResume() {
        val mapFragment =
            supportFragmentManager.fragmentFactory.instantiate(
                this.classLoader,
                MapFragment::class.java.name
            )
        supportFragmentManager.commit(true) {
            replace(R.id.mapFragment, mapFragment, "map")
        }
        if (preferences.experimentalFeatures.contains(
                EXPERIMENTAL_FEATURE_BEARING_ARROW_FOLLOWS_DEVICE_ORIENTATION
            )
        ) {
            sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
            sensorManager?.let {
                orientationSensor = it.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
                orientationSensor?.run { Timber.d("Got a rotation vector sensor") }
            }
        } else {
            sensorManager?.unregisterListener(mapViewModel.orientationSensorEventListener)
            sensorManager = null
            orientationSensor = null
        }
        super.onResume()
        updateMonitoringModeMenu()
        mapViewModel.updateMyLocationStatus()

        if (!previouslyHadLocationPermissions && requirementsChecker.isLocationPermissionCheckPassed()) {
            previouslyHadLocationPermissions = true
            mapViewModel.requestLocationUpdatesForBlueDot()
            service?.reInitializeLocationRequests()
        }
    }

    private fun handleIntentExtras(intent: Intent) {
        Timber.v("handleIntentExtras")
        val b = if (intent.hasExtra("_args")) intent.getBundleExtra("_args") else Bundle()
        if (b != null) {
            Timber.v("intent has extras from drawerProvider")
            val contactId = b.getString(BUNDLE_KEY_CONTACT_ID)
            if (contactId != null) {
                mapViewModel.setLiveContact(contactId)
            }
        }
    }

    public override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntentExtras(intent)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.activity_map, menu)
        this.menu = menu
        updateMonitoringModeMenu()
        mapViewModel.updateMyLocationStatus()
        return true
    }

    fun updateMonitoringModeMenu() {
        menu?.findItem(R.id.menu_monitoring)
            ?.run {
                when (preferences.monitoring) {
                    MonitoringMode.QUIET -> {
                        setIcon(R.drawable.ic_baseline_stop_36)
                        setTitle(R.string.monitoring_quiet)
                    }
                    MonitoringMode.MANUAL -> {
                        setIcon(R.drawable.ic_baseline_pause_36)
                        setTitle(R.string.monitoring_manual)
                    }
                    MonitoringMode.SIGNIFICANT -> {
                        setIcon(R.drawable.ic_baseline_play_arrow_36)
                        setTitle(R.string.monitoring_significant)
                    }
                    MonitoringMode.MOVE -> {
                        setIcon(R.drawable.ic_step_forward_2)
                        setTitle(R.string.monitoring_move)
                    }
                }
            }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_report -> {
                mapViewModel.sendLocation()
                true
            }
            android.R.id.home -> {
                finish()
                true
            }
            R.id.menu_monitoring -> {
                MonitoringModeBottomSheetDialog().show(
                    supportFragmentManager,
                    "modeBottomSheetDialog"
                )
                true
            }
            else -> false
        }
    }

    private fun disableLocationMenus() {
        binding?.fabMyLocation?.isEnabled = false
        menu?.run {
            findItem(R.id.menu_report).setEnabled(false).icon?.alpha = 128
        }
    }

    private fun enableLocationMenus() {
        binding?.fabMyLocation?.isEnabled = true
        menu?.run {
            findItem(R.id.menu_report).setEnabled(true).icon?.alpha = 255
        }
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_clear -> {
                mapViewModel.onClearContactClicked()
                false
            }
            R.id.menu_navigate -> {
                val c = mapViewModel.currentContact
                c.value?.latLng?.run {
                    try {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("google.navigation:q=$latitude,$longitude")
                        )
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(intent)
                    } catch (e: ActivityNotFoundException) {
                        Snackbar.make(
                            binding!!.mapCoordinatorLayout,
                            getString(R.string.noNavigationApp),
                            Snackbar.LENGTH_SHORT
                        )
                            .show()
                    }
                } ?: run {
                    Snackbar.make(
                        binding!!.mapCoordinatorLayout,
                        getString(R.string.contactLocationUnknown),
                        Snackbar.LENGTH_SHORT
                    )
                        .show()
                }
                true
            }
            else -> false
        }
    }

    override fun onLongClick(view: View): Boolean {
        mapViewModel.onBottomSheetLongClick()
        return true
    }

    private fun setBottomSheetExpanded() {
        bottomSheetBehavior!!.state = BottomSheetBehavior.STATE_EXPANDED
        binding!!.mapFragment.setPaddingRelative(0, 0, 0, binding!!.bottomSheetLayout.height)
        orientationSensor?.let {
            sensorManager?.registerListener(
                mapViewModel.orientationSensorEventListener,
                it,
                500_000
            )
        }
    }

    // BOTTOM SHEET CALLBACKS
    override fun onClick(view: View) {
        setBottomSheetExpanded()
    }

    private fun setBottomSheetCollapsed() {
        bottomSheetBehavior!!.state = BottomSheetBehavior.STATE_COLLAPSED
        binding!!.mapFragment.setPadding(0)
        sensorManager?.unregisterListener(mapViewModel.orientationSensorEventListener)
    }

    private fun setBottomSheetHidden() {
        bottomSheetBehavior!!.state = BottomSheetBehavior.STATE_HIDDEN
        binding!!.mapFragment.setPadding(0)
        menu?.run { close() }
        sensorManager?.unregisterListener(mapViewModel.orientationSensorEventListener)
    }

    private fun showPopupMenu(v: View) {
        val popupMenu = PopupMenu(this, v, Gravity.START)
        popupMenu.menuInflater.inflate(R.menu.menu_popup_contacts, popupMenu.menu)
        popupMenu.setOnMenuItemClickListener(this)
        if (preferences.mode == ConnectionMode.HTTP) {
            popupMenu.menu.removeItem(R.id.menu_clear)
        }
        if (!mapViewModel.contactHasLocation()) {
            popupMenu.menu.removeItem(R.id.menu_navigate)
        }
        popupMenu.show()
    }

    override fun onStart() {
        super.onStart()
        bindService(
            Intent(this, BackgroundService::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )
    }

    override fun onStop() {
        super.onStop()
        unbindService(serviceConnection)
    }

    @get:VisibleForTesting
    val locationIdlingResource: IdlingResource?
        get() = mapViewModel.locationIdlingResource

    @get:VisibleForTesting
    val outgoingQueueIdlingResource: IdlingResource
        get() = countingIdlingResource

    companion object {
        const val BUNDLE_KEY_CONTACT_ID = "BUNDLE_KEY_CONTACT_ID"

        @JvmStatic
        @BindingAdapter("locationIcon")
        fun setIcon(view: FloatingActionButton, status: MyLocationStatus) {
            val tint = when (status) {
                MyLocationStatus.FOLLOWING -> view.resources.getColor(R.color.fabMyLocationForegroundActiveTint, null)
                else -> view.resources.getColor(R.color.fabMyLocationForegroundInActiveTint, null)
            }
            when (status) {
                MyLocationStatus.DISABLED -> view.setImageResource(R.drawable.ic_baseline_location_disabled_24)
                MyLocationStatus.AVAILABLE -> view.setImageResource(R.drawable.ic_baseline_location_searching_24)
                MyLocationStatus.FOLLOWING -> view.setImageResource(R.drawable.ic_baseline_my_location_24)
            }
            ImageViewCompat.setImageTintList(
                view,
                ColorStateList.valueOf(tint)
            )
        }
    }
}
