package com.nativephp.gis

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import com.nativephp.mobile.bridge.NativeActionCoordinator
import org.json.JSONObject

/**
 * Full-screen Activity that hosts the Leaflet map in a WebView.
 *
 * Lifecycle:
 *   - Registers itself with LeafletMapFunctions.MapController on start so that
 *     bridge functions (AddMarker, AddPolygon, …) can inject JS into this view.
 *   - Unregisters on destroy and fires MapClosed.
 *
 * JS → Native messages arrive via the "NativePHPMap" JavascriptInterface and
 * are forwarded to NativeActionCoordinator as Laravel events.
 */
class MapActivity : AppCompatActivity(), LeafletMapFunctions.MapController.MapBridge {

    companion object {
        const val EXTRA_ID   = "id"
        const val EXTRA_LAT  = "lat"
        const val EXTRA_LNG  = "lng"
        const val EXTRA_ZOOM = "zoom"

        private const val TAG = "MapActivity"
    }

    private lateinit var webView: WebView
    private var requestId: String? = null

    // ── Lifecycle ────────────────────────────────────────────────────────────

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestId = intent.getStringExtra(EXTRA_ID)
        val lat  = intent.getDoubleExtra(EXTRA_LAT,  0.0)
        val lng  = intent.getDoubleExtra(EXTRA_LNG,  0.0)
        val zoom = intent.getIntExtra(EXTRA_ZOOM, 13)

        webView = WebView(this).also { wv ->
            wv.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                setSupportZoom(true)
                builtInZoomControls = false
            }

            wv.addJavascriptInterface(NativeBridge(), "NativePHPMap")

            wv.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = false

                override fun onPageFinished(view: WebView, url: String) {
                    // Send the init command after the page has loaded
                    val params = mutableMapOf<String, Any?>(
                        "action" to "init",
                        "lat"    to lat,
                        "lng"    to lng,
                        "zoom"   to zoom
                    )
                    if (requestId != null) params["id"] = requestId
                    val json = LeafletMapFunctions.MapController.toJsonObject(params).toString()
                    val escaped = json.replace("\\", "\\\\").replace("'", "\\'")
                    wv.evaluateJavascript("window.NativePHPMapCommand('$escaped')", null)
                }
            }

            wv.loadUrl("file:///android_asset/nativephp/gis/leaflet/map.html")
        }

        setContentView(webView)
    }

    override fun onStart() {
        super.onStart()
        LeafletMapFunctions.MapController.bridge = this
        Log.d(TAG, "✅ MapActivity registered with MapController")
    }

    override fun onStop() {
        super.onStop()
        // Only clear if this activity is still the registered bridge
        if (LeafletMapFunctions.MapController.bridge === this) {
            LeafletMapFunctions.MapController.bridge = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "🗺️ MapActivity destroyed — firing MapClosed")
        val payload = mutableMapOf<String, Any>()
        if (requestId != null) payload["id"] = requestId!!
        NativeActionCoordinator.dispatchEvent("Native\\Mobile\\Events\\Gis\\MapClosed", payload)

        webView.destroy()
    }

    // ── MapBridge impl ───────────────────────────────────────────────────────

    override fun evaluate(js: String) {
        runOnUiThread { webView.evaluateJavascript(js, null) }
    }

    // ── JavascriptInterface ──────────────────────────────────────────────────

    inner class NativeBridge {

        /**
         * Called by the Leaflet page for every event (MapReady, MarkerClicked, …).
         * Payload JSON: { "event": "Native\\...", "data": { … } }
         */
        @JavascriptInterface
        fun postMessage(json: String) {
            try {
                val obj   = JSONObject(json)
                val event = obj.getString("event")
                val data  = obj.optJSONObject("data") ?: JSONObject()

                val payload = mutableMapOf<String, Any>()
                data.keys().forEach { key ->
                    val v = data.get(key)
                    // JSONObject / JSONArray come through as-is; primitives are boxed
                    payload[key] = v
                }

                Log.d(TAG, "📨 JS→Native event=$event payload=$payload")
                NativeActionCoordinator.dispatchEvent(event, payload)

            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to parse JS message: $json", e)
            }
        }
    }
}
