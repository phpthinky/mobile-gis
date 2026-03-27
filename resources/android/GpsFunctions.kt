package com.nativephp.gis

import android.Manifest
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.nativephp.mobile.bridge.BridgeFunction
import com.nativephp.mobile.bridge.NativeActionCoordinator

/**
 * Functions related to GPS/location operations
 * Namespace: "Gis.*"
 */
object GpsFunctions {

    /**
     * Get the device's current GPS location
     * Parameters:
     *   - id: (optional) string - Optional ID to track this specific location request
     *   - event: (optional) string - Custom event class to fire (defaults to "Native\Mobile\Events\Gis\LocationReceived")
     *   - accuracy: (optional) string - "high" (GPS) or "balanced" (network). Defaults to "high"
     *   - lastKnown: (optional) boolean - When true, starts continuous background tracking while the app is open
     *       so that the last cached location is always available. If the device is offline or GPS is
     *       unavailable, the last known location is returned instead of an error. Defaults to false.
     * Returns:
     *   - (empty map - results are returned via events)
     * Events:
     *   - Fires "Native\Mobile\Events\Gis\LocationReceived" when location is obtained
     *     Payload: { latitude, longitude, accuracy, altitude, bearing, speed, isLastKnown, id? }
     *   - Fires "Native\Mobile\Events\Gis\LocationPermissionDenied" when permission is denied
     *     Payload: { id? }
     *   - Fires "Native\Mobile\Events\Gis\LocationError" when location cannot be obtained
     *     Payload: { message, id? }
     */
    class GetCurrentLocation(private val activity: FragmentActivity) : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            val id = parameters["id"] as? String
            val event = parameters["event"] as? String
            val accuracy = parameters["accuracy"] as? String ?: "high"
            val lastKnown = parameters["lastKnown"] as? Boolean ?: false

            Log.d("GpsFunctions.GetCurrentLocation", "📍 Getting location id=$id, accuracy=$accuracy, lastKnown=$lastKnown")

            Handler(Looper.getMainLooper()).post {
                try {
                    val fineGranted = ContextCompat.checkSelfPermission(
                        activity, Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED

                    val coarseGranted = ContextCompat.checkSelfPermission(
                        activity, Manifest.permission.ACCESS_COARSE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED

                    if (!fineGranted && !coarseGranted) {
                        Log.w("GpsFunctions.GetCurrentLocation", "⚠️ Location permission not granted")
                        firePermissionDenied(id)
                        return@post
                    }

                    val priority = if (accuracy == "high" && fineGranted) {
                        Priority.PRIORITY_HIGH_ACCURACY
                    } else {
                        Priority.PRIORITY_BALANCED_POWER_ACCURACY
                    }

                    // When lastKnown is enabled, start continuous background tracking so the cache
                    // stays fresh for the entire app session (no-op if already running)
                    if (lastKnown) {
                        LocationTracker.start(activity, priority)
                    }

                    val fusedClient = LocationServices.getFusedLocationProviderClient(activity)
                    val cancellationToken = CancellationTokenSource()

                    fusedClient.getCurrentLocation(priority, cancellationToken.token)
                        .addOnSuccessListener { location ->
                            if (location != null) {
                                Log.d("GpsFunctions.GetCurrentLocation", "✅ Live location: ${location.latitude}, ${location.longitude}")
                                fireLocationReceived(location, id, event, isLastKnown = false)
                            } else {
                                handleMissingLocation(fusedClient, id, event, lastKnown, reason = "Location is null")
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.e("GpsFunctions.GetCurrentLocation", "❌ Location request failed: ${e.message}", e)
                            handleMissingLocation(fusedClient, id, event, lastKnown, reason = e.message ?: "Unknown error")
                        }

                } catch (e: Exception) {
                    Log.e("GpsFunctions.GetCurrentLocation", "❌ Exception: ${e.message}", e)
                    fireLocationError(e.message ?: "Unknown error", id)
                }
            }

            return emptyMap()
        }

        /**
         * Called when live GPS fails. If lastKnown is enabled, tries in-session cache first,
         * then the OS-level system last-known location. Otherwise fires LocationError.
         */
        private fun handleMissingLocation(
            fusedClient: com.google.android.gms.location.FusedLocationProviderClient,
            id: String?,
            event: String?,
            lastKnown: Boolean,
            reason: String
        ) {
            if (!lastKnown) {
                fireLocationError("Unable to obtain location. Please ensure location services are enabled.", id)
                return
            }

            // 1st fallback: in-session tracker cache (most fresh)
            val sessionCached = LocationTracker.lastLocation
            if (sessionCached != null) {
                Log.d("GpsFunctions.GetCurrentLocation", "📦 Using session cache: ${sessionCached.latitude}, ${sessionCached.longitude}")
                fireLocationReceived(sessionCached, id, event, isLastKnown = true)
                return
            }

            // 2nd fallback: OS-level system last known (survives across sessions)
            fusedClient.lastLocation
                .addOnSuccessListener { sysLocation ->
                    if (sysLocation != null) {
                        Log.d("GpsFunctions.GetCurrentLocation", "📦 Using OS last known: ${sysLocation.latitude}, ${sysLocation.longitude}")
                        fireLocationReceived(sysLocation, id, event, isLastKnown = true)
                    } else {
                        Log.w("GpsFunctions.GetCurrentLocation", "⚠️ No cached location available. Original reason: $reason")
                        fireLocationError("Device is offline and no cached location is available.", id)
                    }
                }
                .addOnFailureListener { e ->
                    fireLocationError("Device is offline and last known location could not be retrieved: ${e.message}", id)
                }
        }

        private fun fireLocationReceived(
            location: android.location.Location,
            id: String?,
            customEvent: String?,
            isLastKnown: Boolean
        ) {
            val eventClass = customEvent ?: "Native\\Mobile\\Events\\Gis\\LocationReceived"
            val payload = mutableMapOf<String, Any>(
                "latitude" to location.latitude,
                "longitude" to location.longitude,
                "accuracy" to location.accuracy,
                "altitude" to location.altitude,
                "bearing" to location.bearing,
                "speed" to location.speed,
                "isLastKnown" to isLastKnown
            )
            if (id != null) payload["id"] = id
            NativeActionCoordinator.dispatchEvent(eventClass, payload)
        }

        private fun firePermissionDenied(id: String?) {
            val eventClass = "Native\\Mobile\\Events\\Gis\\LocationPermissionDenied"
            val payload = mutableMapOf<String, Any>()
            if (id != null) payload["id"] = id
            NativeActionCoordinator.dispatchEvent(eventClass, payload)
        }

        private fun fireLocationError(message: String, id: String?) {
            val eventClass = "Native\\Mobile\\Events\\Gis\\LocationError"
            val payload = mutableMapOf<String, Any>("message" to message)
            if (id != null) payload["id"] = id
            NativeActionCoordinator.dispatchEvent(eventClass, payload)
        }
    }
}
