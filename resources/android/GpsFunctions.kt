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
     * Returns:
     *   - (empty map - results are returned via events)
     * Events:
     *   - Fires "Native\Mobile\Events\Gis\LocationReceived" when location is obtained
     *     Payload: { latitude, longitude, accuracy, altitude, bearing, speed, id? }
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

            Log.d("GpsFunctions.GetCurrentLocation", "📍 Getting current location with id=$id, accuracy=$accuracy")

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

                    val fusedClient = LocationServices.getFusedLocationProviderClient(activity)
                    val cancellationToken = CancellationTokenSource()

                    fusedClient.getCurrentLocation(priority, cancellationToken.token)
                        .addOnSuccessListener { location ->
                            if (location != null) {
                                Log.d("GpsFunctions.GetCurrentLocation", "✅ Location obtained: ${location.latitude}, ${location.longitude}")
                                fireLocationReceived(location, id, event)
                            } else {
                                Log.w("GpsFunctions.GetCurrentLocation", "⚠️ Location is null")
                                fireLocationError("Unable to obtain location. Please ensure location services are enabled.", id)
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.e("GpsFunctions.GetCurrentLocation", "❌ Location error: ${e.message}", e)
                            fireLocationError(e.message ?: "Unknown location error", id)
                        }

                } catch (e: Exception) {
                    Log.e("GpsFunctions.GetCurrentLocation", "❌ Exception: ${e.message}", e)
                    fireLocationError(e.message ?: "Unknown error", id)
                }
            }

            return emptyMap()
        }

        private fun fireLocationReceived(location: android.location.Location, id: String?, customEvent: String?) {
            val eventClass = customEvent ?: "Native\\Mobile\\Events\\Gis\\LocationReceived"
            val payload = mutableMapOf<String, Any>(
                "latitude" to location.latitude,
                "longitude" to location.longitude,
                "accuracy" to location.accuracy,
                "altitude" to location.altitude,
                "bearing" to location.bearing,
                "speed" to location.speed
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
