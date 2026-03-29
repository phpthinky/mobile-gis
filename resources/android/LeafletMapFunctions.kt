package com.nativephp.gis

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.fragment.app.FragmentActivity
import com.nativephp.mobile.bridge.BridgeFunction
import com.nativephp.mobile.bridge.NativeActionCoordinator
import org.json.JSONArray
import org.json.JSONObject

/**
 * Functions related to Leaflet map operations.
 * Namespace: "Gis.Map.*"
 *
 * All map commands are relayed to the active MapActivity WebView via
 * MapController. If no map is currently open, commands are queued and
 * replayed when the map becomes ready.
 */
object LeafletMapFunctions {

    // ── MapController ────────────────────────────────────────────────────────

    /**
     * Singleton that holds the active map WebView reference and handles
     * command serialisation / injection. MapActivity registers itself here
     * when it starts and unregisters when it stops.
     */
    object MapController {

        /** Set by MapActivity.onResume / cleared by MapActivity.onDestroy */
        @Volatile
        var bridge: MapBridge? = null

        /** Interface implemented by MapActivity */
        interface MapBridge {
            fun evaluate(js: String)
        }

        /**
         * Serialise a command and send it to the active WebView.
         * No-op when no map is open.
         */
        fun sendCommand(action: String, params: Map<String, Any?>) {
            val bridge = this.bridge ?: run {
                Log.w("MapController", "⚠️ No active map — command '$action' dropped")
                return
            }
            val obj = toJsonObject(params + ("action" to action))
            val escaped = obj.toString().replace("\\", "\\\\").replace("'", "\\'")
            Handler(Looper.getMainLooper()).post {
                bridge.evaluate("window.NativePHPMapCommand('$escaped')")
            }
        }

        // ── JSON helpers ─────────────────────────────────────────────────────

        @Suppress("UNCHECKED_CAST")
        fun toJsonObject(map: Map<String, Any?>): JSONObject {
            val obj = JSONObject()
            map.forEach { (k, v) ->
                when {
                    v == null               -> { /* omit null keys */ }
                    v is Map<*, *>          -> obj.put(k, toJsonObject(v as Map<String, Any?>))
                    v is List<*>            -> obj.put(k, toJsonArray(v))
                    else                    -> obj.put(k, v)
                }
            }
            return obj
        }

        @Suppress("UNCHECKED_CAST")
        fun toJsonArray(list: List<*>): JSONArray {
            val arr = JSONArray()
            list.forEach { v ->
                when {
                    v == null      -> arr.put(JSONObject.NULL)
                    v is Map<*, *> -> arr.put(toJsonObject(v as Map<String, Any?>))
                    v is List<*>   -> arr.put(toJsonArray(v))
                    else           -> arr.put(v)
                }
            }
            return arr
        }
    }

    // ── Event helpers (static, reusable across all bridge functions) ─────────

    internal fun fireEvent(eventClass: String, payload: Map<String, Any>) {
        NativeActionCoordinator.dispatchEvent(eventClass, payload)
    }

    // ────────────────────────────────────────────────────────────────────────
    // Gis.Map.Show
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Open a full-screen Leaflet map overlay.
     *
     * Parameters:
     *   - id:        (optional) string  — Request identifier echoed in MapReady / MapClosed
     *   - latitude:  (optional) float   — Initial centre latitude.  Defaults to 0.0
     *   - longitude: (optional) float   — Initial centre longitude. Defaults to 0.0
     *   - zoom:      (optional) int     — Initial zoom level (1–19). Defaults to 13
     *
     * Events:
     *   - Fires "Native\Mobile\Events\Gis\MapReady"  when tiles are loaded
     *     Payload: { id? }
     *   - Fires "Native\Mobile\Events\Gis\MapClosed" when the user dismisses the map
     *     Payload: { id? }
     */
    class Show(private val activity: FragmentActivity) : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            val id  = parameters["id"]        as? String
            val lat = (parameters["latitude"]  as? Number)?.toDouble() ?: 0.0
            val lng = (parameters["longitude"] as? Number)?.toDouble() ?: 0.0
            val zoom = (parameters["zoom"]     as? Number)?.toInt()    ?: 13

            Log.d("LeafletMapFunctions.Show", "🗺️ Opening map id=$id lat=$lat lng=$lng zoom=$zoom")

            Handler(Looper.getMainLooper()).post {
                val intent = android.content.Intent(activity, MapActivity::class.java).apply {
                    putExtra(MapActivity.EXTRA_ID,  id)
                    putExtra(MapActivity.EXTRA_LAT, lat)
                    putExtra(MapActivity.EXTRA_LNG, lng)
                    putExtra(MapActivity.EXTRA_ZOOM, zoom)
                }
                activity.startActivity(intent)
            }

            return emptyMap()
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // Gis.Map.AddMarker
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Add (or replace) a marker on the active map.
     *
     * Parameters:
     *   - id:        string           — Unique marker identifier
     *   - latitude:  float            — Marker latitude
     *   - longitude: float            — Marker longitude
     *   - label:     (optional) string — Tooltip text shown on tap
     *   - color:     (optional) string — CSS colour, e.g. "#e74c3c". Defaults to blue pin
     *
     * Events:
     *   - Fires "Native\Mobile\Events\Gis\MarkerClicked" when the marker is tapped
     *     Payload: { id, latitude, longitude, label? }
     */
    class AddMarker(private val activity: FragmentActivity) : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            val id = parameters["id"] as? String ?: run {
                Log.w("LeafletMapFunctions.AddMarker", "⚠️ Missing required parameter: id")
                return emptyMap()
            }
            val lat   = (parameters["latitude"]  as? Number)?.toDouble() ?: 0.0
            val lng   = (parameters["longitude"] as? Number)?.toDouble() ?: 0.0
            val label = parameters["label"] as? String
            val color = parameters["color"] as? String

            Log.d("LeafletMapFunctions.AddMarker", "📍 Adding marker id=$id lat=$lat lng=$lng")

            MapController.sendCommand("addMarker", mapOf(
                "id" to id, "lat" to lat, "lng" to lng, "label" to label, "color" to color
            ))

            return emptyMap()
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // Gis.Map.RemoveMarker
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Remove a marker from the active map.
     *
     * Parameters:
     *   - id: string — Marker ID to remove
     */
    class RemoveMarker(private val activity: FragmentActivity) : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            val id = parameters["id"] as? String ?: return emptyMap()
            Log.d("LeafletMapFunctions.RemoveMarker", "🗑️ Removing marker id=$id")
            MapController.sendCommand("removeMarker", mapOf("id" to id))
            return emptyMap()
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // Gis.Map.AddPolygon
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Add (or replace) a polygon on the active map.
     *
     * Parameters:
     *   - id:          string — Unique polygon identifier
     *   - coordinates: array  — Array of { latitude: float, longitude: float } objects
     *   - color:       (optional) string — Stroke colour. Defaults to "#3388ff"
     *   - fillColor:   (optional) string — Fill colour.   Defaults to stroke colour
     *   - fillOpacity: (optional) float  — Fill opacity 0–1. Defaults to 0.2
     *
     * Events:
     *   - Fires "Native\Mobile\Events\Gis\PolygonClicked" when the polygon is tapped
     *     Payload: { id }
     */
    class AddPolygon(private val activity: FragmentActivity) : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            val id = parameters["id"] as? String ?: run {
                Log.w("LeafletMapFunctions.AddPolygon", "⚠️ Missing required parameter: id")
                return emptyMap()
            }
            val coordinates = parameters["coordinates"] ?: run {
                Log.w("LeafletMapFunctions.AddPolygon", "⚠️ Missing required parameter: coordinates")
                return emptyMap()
            }
            val color       = parameters["color"]       as? String
            val fillColor   = parameters["fillColor"]   as? String
            val fillOpacity = (parameters["fillOpacity"] as? Number)?.toDouble()

            Log.d("LeafletMapFunctions.AddPolygon", "🔷 Adding polygon id=$id")

            MapController.sendCommand("addPolygon", mapOf(
                "id"          to id,
                "coordinates" to coordinates,
                "color"       to color,
                "fillColor"   to fillColor,
                "fillOpacity" to fillOpacity
            ))

            return emptyMap()
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // Gis.Map.RemovePolygon
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Remove a polygon from the active map.
     *
     * Parameters:
     *   - id: string — Polygon ID to remove
     */
    class RemovePolygon(private val activity: FragmentActivity) : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            val id = parameters["id"] as? String ?: return emptyMap()
            Log.d("LeafletMapFunctions.RemovePolygon", "🗑️ Removing polygon id=$id")
            MapController.sendCommand("removePolygon", mapOf("id" to id))
            return emptyMap()
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // Gis.Map.SetView
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Pan and/or zoom the active map.
     *
     * Parameters:
     *   - latitude:  float          — New centre latitude
     *   - longitude: float          — New centre longitude
     *   - zoom:      (optional) int — New zoom level. Keeps current zoom if omitted
     */
    class SetView(private val activity: FragmentActivity) : BridgeFunction {
        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            val lat  = (parameters["latitude"]  as? Number)?.toDouble() ?: 0.0
            val lng  = (parameters["longitude"] as? Number)?.toDouble() ?: 0.0
            val zoom = (parameters["zoom"]      as? Number)?.toInt()

            Log.d("LeafletMapFunctions.SetView", "🔍 Setting view lat=$lat lng=$lng zoom=$zoom")
            MapController.sendCommand("setView", mapOf("lat" to lat, "lng" to lng, "zoom" to zoom))
            return emptyMap()
        }
    }
}
