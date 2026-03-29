package com.nativephp.gis

import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.fragment.app.FragmentActivity
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices

/**
 * Singleton that manages continuous foreground location updates while the app is open.
 *
 * Purpose:
 *   When GetCurrentLocation is called with lastKnown=true, this tracker starts running
 *   in the background of the foreground session. Every location fix it receives is stored
 *   in [lastLocation]. If a subsequent GetCurrentLocation call fails (e.g. device goes
 *   offline or GPS is temporarily unavailable), GpsFunctions falls back to this cache
 *   instead of firing a LocationError.
 *
 * Lifecycle:
 *   - Starts on first GetCurrentLocation(lastKnown=true) call.
 *   - Runs for the entire app session (no automatic stop).
 *   - No-op if already running ([start] is safe to call multiple times).
 *   - Call [stop] to explicitly end tracking (e.g. on app destroy if needed).
 *
 * Permissions required (declared in nativephp.json):
 *   - ACCESS_FINE_LOCATION or ACCESS_COARSE_LOCATION
 */
object LocationTracker {

    private const val TAG = "LocationTracker"
    private const val UPDATE_INTERVAL_MS = 10_000L    // request update every 10 s
    private const val MIN_UPDATE_INTERVAL_MS = 5_000L // accept update as fast as every 5 s

    /** The most recently received location from continuous tracking. Null until first fix. */
    @Volatile
    var lastLocation: Location? = null
        private set

    private var locationCallback: LocationCallback? = null

    @Volatile
    private var isTracking = false

    val isRunning: Boolean get() = isTracking

    /**
     * Start continuous location updates. Safe to call multiple times — subsequent calls
     * are no-ops if tracking is already running.
     *
     * @param activity  Used to obtain the FusedLocationProviderClient context.
     * @param priority  com.google.android.gms.location.Priority constant:
     *                  PRIORITY_HIGH_ACCURACY or PRIORITY_BALANCED_POWER_ACCURACY
     */
    fun start(activity: FragmentActivity, priority: Int) {
        if (isTracking) {
            Log.d(TAG, "▶️ Already tracking — skip start")
            return
        }

        Log.d(TAG, "▶️ Starting continuous location tracking (priority=$priority)")

        val fusedClient = LocationServices.getFusedLocationProviderClient(activity)

        val request = LocationRequest.Builder(priority, UPDATE_INTERVAL_MS)
            .setMinUpdateIntervalMillis(MIN_UPDATE_INTERVAL_MS)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    Log.d(TAG, "📍 Cache updated: ${location.latitude}, ${location.longitude} (acc=${location.accuracy}m)")
                    lastLocation = location
                }
            }
        }

        try {
            fusedClient.requestLocationUpdates(request, locationCallback!!, Looper.getMainLooper())
            isTracking = true
        } catch (e: SecurityException) {
            Log.e(TAG, "❌ Missing location permission — cannot start tracking: ${e.message}")
            locationCallback = null
        }
    }

    /**
     * Stop continuous location updates and release resources.
     * The [lastLocation] cache is preserved after stopping.
     *
     * @param activity Used to obtain the FusedLocationProviderClient context.
     */
    fun stop(activity: FragmentActivity) {
        val callback = locationCallback ?: run {
            Log.d(TAG, "⏹️ Not tracking — skip stop")
            return
        }
        Log.d(TAG, "⏹️ Stopping continuous location tracking")
        LocationServices.getFusedLocationProviderClient(activity).removeLocationUpdates(callback)
        locationCallback = null
        isTracking = false
    }
}
