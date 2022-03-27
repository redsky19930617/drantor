package org.owntracks.android.ui.map

import android.content.Context
import android.location.Location
import android.os.Looper
import androidx.lifecycle.LiveData
import com.google.android.gms.location.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import org.owntracks.android.gms.location.toGMSLocationRequest
import org.owntracks.android.location.LocationRequest
import timber.log.Timber
import java.util.concurrent.TimeUnit

class LocationLiveData(
    private val locationProviderClient: FusedLocationProviderClient,
    private val coroutineScope: CoroutineScope
) :
    LiveData<Location>() {
    constructor(
        context: Context,
        coroutineScope: CoroutineScope
    ) : this(LocationServices.getFusedLocationProviderClient(context), coroutineScope)

    private val locationCallback = Callback()

    private val lock = Semaphore(1)
    suspend fun requestLocationUpdates() {
        // We don't want to kick off another request while we're doing this one
        lock.acquire()
        locationProviderClient.removeLocationUpdates(locationCallback).continueWith { task ->
            Timber.d("Removing previous locationupdate task complete.  Success=${task.isSuccessful} Cancelled=${task.isCanceled}")
            locationProviderClient.requestLocationUpdates(
                LocationRequest(
                    smallestDisplacement = 1f,
                    priority = LocationRequest.PRIORITY_HIGH_ACCURACY,
                    interval = TimeUnit.SECONDS.toMillis(2)
                ).toGMSLocationRequest(), locationCallback, Looper.getMainLooper()
            ).addOnCompleteListener {
                Timber.d("LocationLiveData location update request completed: Success=${it.isSuccessful} Cancelled=${it.isCanceled}")
                lock.release()
            }
        }
    }

    private suspend fun removeLocationUpdates() {
        lock.acquire()
        locationProviderClient.removeLocationUpdates(locationCallback).addOnCompleteListener {
            Timber.d("LocationLiveData removing location updates completed: Success=${it.isSuccessful} Cancelled=${it.isCanceled}")
            lock.release()
        }
    }

    override fun onActive() {
        super.onActive()
        coroutineScope.launch { requestLocationUpdates() }
    }

    override fun onInactive() {
        coroutineScope.launch { removeLocationUpdates() }
        super.onInactive()
    }

    inner class Callback : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            super.onLocationResult(locationResult)
            value = locationResult.lastLocation
        }

        override fun onLocationAvailability(locationAvailability: LocationAvailability) {
            Timber.d("LocationLiveData location availability: $locationAvailability")
            super.onLocationAvailability(locationAvailability)
        }
    }
}