import Foundation
import UIKit
import WebKit

// MARK: - LeafletMap Function Namespace

/// Functions related to Leaflet map operations.
/// Namespace: "Gis.Map.*"
enum LeafletMapFunctions {

    // MARK: - Gis.Map.Show

    /// Open a full-screen Leaflet map overlay.
    ///
    /// Parameters:
    ///   - id:        (optional) string — Request identifier echoed in MapReady / MapClosed
    ///   - latitude:  (optional) float  — Initial centre latitude.  Defaults to 0.0
    ///   - longitude: (optional) float  — Initial centre longitude. Defaults to 0.0
    ///   - zoom:      (optional) int    — Initial zoom level (1–19). Defaults to 13
    ///
    /// Events:
    ///   - Fires "Native\Mobile\Events\Gis\MapReady"  when tiles are loaded
    ///     Payload: { id? }
    ///   - Fires "Native\Mobile\Events\Gis\MapClosed" when the user dismisses the map
    ///     Payload: { id? }
    class Show: BridgeFunction {
        func execute(parameters: [String: Any]) throws -> [String: Any] {
            let id  = parameters["id"]        as? String
            let lat = (parameters["latitude"]  as? NSNumber)?.doubleValue ?? 0.0
            let lng = (parameters["longitude"] as? NSNumber)?.doubleValue ?? 0.0
            let zoom = (parameters["zoom"]     as? NSNumber)?.intValue    ?? 13

            print("🗺️ Opening map id=\(id ?? "nil") lat=\(lat) lng=\(lng) zoom=\(zoom)")

            DispatchQueue.main.async {
                let vc = MapViewController(id: id, latitude: lat, longitude: lng, zoom: zoom)
                vc.modalPresentationStyle = .fullScreen

                if let root = UIApplication.shared.connectedScenes
                    .compactMap({ $0 as? UIWindowScene })
                    .flatMap({ $0.windows })
                    .first(where: { $0.isKeyWindow })?.rootViewController {
                    root.present(vc, animated: true)
                }
            }

            return [:]
        }
    }

    // MARK: - Gis.Map.AddMarker

    /// Add (or replace) a marker on the active map.
    ///
    /// Parameters:
    ///   - id:        string           — Unique marker identifier
    ///   - latitude:  float            — Marker latitude
    ///   - longitude: float            — Marker longitude
    ///   - label:     (optional) string — Tooltip text shown on tap
    ///   - color:     (optional) string — CSS colour, e.g. "#e74c3c". Defaults to blue pin
    ///
    /// Events:
    ///   - Fires "Native\Mobile\Events\Gis\MarkerClicked" when the marker is tapped
    ///     Payload: { id, latitude, longitude, label? }
    class AddMarker: BridgeFunction {
        func execute(parameters: [String: Any]) throws -> [String: Any] {
            guard let id = parameters["id"] as? String else {
                print("⚠️ LeafletMapFunctions.AddMarker: missing required parameter 'id'")
                return [:]
            }
            let lat   = (parameters["latitude"]  as? NSNumber)?.doubleValue ?? 0.0
            let lng   = (parameters["longitude"] as? NSNumber)?.doubleValue ?? 0.0
            let label = parameters["label"] as? String
            let color = parameters["color"] as? String

            print("📍 Adding marker id=\(id) lat=\(lat) lng=\(lng)")

            MapController.shared.sendCommand("addMarker", params: [
                "id": id, "lat": lat, "lng": lng,
                "label": label as Any, "color": color as Any
            ])

            return [:]
        }
    }

    // MARK: - Gis.Map.RemoveMarker

    /// Remove a marker from the active map.
    ///
    /// Parameters:
    ///   - id: string — Marker ID to remove
    class RemoveMarker: BridgeFunction {
        func execute(parameters: [String: Any]) throws -> [String: Any] {
            guard let id = parameters["id"] as? String else { return [:] }
            print("🗑️ Removing marker id=\(id)")
            MapController.shared.sendCommand("removeMarker", params: ["id": id])
            return [:]
        }
    }

    // MARK: - Gis.Map.AddPolygon

    /// Add (or replace) a polygon on the active map.
    ///
    /// Parameters:
    ///   - id:          string — Unique polygon identifier
    ///   - coordinates: array  — Array of { latitude: float, longitude: float } dicts
    ///   - color:       (optional) string — Stroke colour. Defaults to "#3388ff"
    ///   - fillColor:   (optional) string — Fill colour.   Defaults to stroke colour
    ///   - fillOpacity: (optional) float  — Fill opacity 0–1. Defaults to 0.2
    ///
    /// Events:
    ///   - Fires "Native\Mobile\Events\Gis\PolygonClicked" when the polygon is tapped
    ///     Payload: { id }
    class AddPolygon: BridgeFunction {
        func execute(parameters: [String: Any]) throws -> [String: Any] {
            guard let id = parameters["id"] as? String else {
                print("⚠️ LeafletMapFunctions.AddPolygon: missing required parameter 'id'")
                return [:]
            }
            guard let coordinates = parameters["coordinates"] else {
                print("⚠️ LeafletMapFunctions.AddPolygon: missing required parameter 'coordinates'")
                return [:]
            }
            let color       = parameters["color"]       as? String
            let fillColor   = parameters["fillColor"]   as? String
            let fillOpacity = (parameters["fillOpacity"] as? NSNumber)?.doubleValue

            print("🔷 Adding polygon id=\(id)")

            MapController.shared.sendCommand("addPolygon", params: [
                "id":          id,
                "coordinates": coordinates,
                "color":       color       as Any,
                "fillColor":   fillColor   as Any,
                "fillOpacity": fillOpacity as Any
            ])

            return [:]
        }
    }

    // MARK: - Gis.Map.RemovePolygon

    /// Remove a polygon from the active map.
    ///
    /// Parameters:
    ///   - id: string — Polygon ID to remove
    class RemovePolygon: BridgeFunction {
        func execute(parameters: [String: Any]) throws -> [String: Any] {
            guard let id = parameters["id"] as? String else { return [:] }
            print("🗑️ Removing polygon id=\(id)")
            MapController.shared.sendCommand("removePolygon", params: ["id": id])
            return [:]
        }
    }

    // MARK: - Gis.Map.SetView

    /// Pan and/or zoom the active map.
    ///
    /// Parameters:
    ///   - latitude:  float          — New centre latitude
    ///   - longitude: float          — New centre longitude
    ///   - zoom:      (optional) int — New zoom level. Keeps current zoom if omitted
    class SetView: BridgeFunction {
        func execute(parameters: [String: Any]) throws -> [String: Any] {
            let lat  = (parameters["latitude"]  as? NSNumber)?.doubleValue ?? 0.0
            let lng  = (parameters["longitude"] as? NSNumber)?.doubleValue ?? 0.0
            let zoom = (parameters["zoom"]      as? NSNumber)?.intValue

            print("🔍 Setting view lat=\(lat) lng=\(lng) zoom=\(zoom.map(String.init) ?? "nil")")
            MapController.shared.sendCommand("setView", params: [
                "lat": lat, "lng": lng, "zoom": zoom as Any
            ])
            return [:]
        }
    }
}

// MARK: - MapController

/// Singleton that holds the active MapViewController's WKWebView reference.
/// Bridge functions call sendCommand() to inject JS into the live map.
final class MapController {

    static let shared = MapController()
    private init() {}

    weak var webView: WKWebView?

    /// Serialise a command and evaluate it in the active WebView. No-op when no map is open.
    func sendCommand(_ action: String, params: [String: Any]) {
        guard let webView = webView else {
            print("⚠️ MapController: no active map — command '\(action)' dropped")
            return
        }
        var merged = params
        merged["action"] = action
        guard let json = toJSONString(merged) else { return }
        let escaped = json
            .replacingOccurrences(of: "\\", with: "\\\\")
            .replacingOccurrences(of: "'",  with: "\\'")
        DispatchQueue.main.async {
            webView.evaluateJavaScript("window.NativePHPMapCommand('\(escaped)')", completionHandler: nil)
        }
    }

    // ── JSON helpers ─────────────────────────────────────────────────────────

    private func toJSONString(_ value: Any) -> String? {
        let cleaned = cleanForJSON(value)
        guard JSONSerialization.isValidJSONObject(cleaned) else { return nil }
        guard let data = try? JSONSerialization.data(withJSONObject: cleaned) else { return nil }
        return String(data: data, encoding: .utf8)
    }

    /// Strips NSNull and non-serialisable types recursively.
    func cleanForJSON(_ value: Any) -> Any {
        switch value {
        case is NSNull:
            return NSNull()
        case let dict as [String: Any]:
            return dict.compactMapValues { v -> Any? in
                if v is NSNull { return nil }
                return cleanForJSON(v)
            }
        case let arr as [Any]:
            return arr.map { cleanForJSON($0) }
        default:
            return value
        }
    }
}

// MARK: - MapViewController

/// Full-screen view controller hosting the Leaflet WebView.
///
/// Registers itself with MapController on viewDidAppear and unregisters
/// on deinit, firing MapClosed.
final class MapViewController: UIViewController, WKNavigationDelegate, WKScriptMessageHandler {

    private let requestId:  String?
    private let latitude:   Double
    private let longitude:  Double
    private let zoom:       Int

    private var webView: WKWebView!

    // MARK: - Init

    init(id: String?, latitude: Double, longitude: Double, zoom: Int) {
        self.requestId = id
        self.latitude  = latitude
        self.longitude = longitude
        self.zoom      = zoom
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) not supported") }

    // MARK: - Lifecycle

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .systemBackground
        setupWebView()
        loadMap()
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        MapController.shared.webView = webView
        print("✅ MapViewController registered with MapController")
    }

    override func viewDidDisappear(_ animated: Bool) {
        super.viewDidDisappear(animated)
        if MapController.shared.webView === webView {
            MapController.shared.webView = nil
        }
    }

    deinit {
        print("🗺️ MapViewController deallocated — firing MapClosed")
        var payload: [String: Any] = [:]
        if let id = requestId { payload["id"] = id }
        LaravelBridge.shared.send?("Native\\Mobile\\Events\\Gis\\MapClosed", payload)
        webView?.configuration.userContentController.removeScriptMessageHandler(forName: "nativePHPMap")
    }

    // MARK: - Setup

    private func setupWebView() {
        let config = WKWebViewConfiguration()
        config.userContentController.add(self, name: "nativePHPMap")
        // Allow loading local files
        config.preferences.javaScriptEnabled = true

        webView = WKWebView(frame: .zero, configuration: config)
        webView.navigationDelegate = self
        webView.scrollView.isScrollEnabled = false
        webView.translatesAutoresizingMaskIntoConstraints = false

        view.addSubview(webView)
        NSLayoutConstraint.activate([
            webView.topAnchor.constraint(equalTo: view.topAnchor),
            webView.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            webView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            webView.trailingAnchor.constraint(equalTo: view.trailingAnchor)
        ])
    }

    private func loadMap() {
        // NativePHP copies plugin resources to the app bundle under NativePHP/Gis/
        if let url = Bundle.main.url(forResource: "map",
                                     withExtension: "html",
                                     subdirectory: "NativePHP/Gis/leaflet") {
            webView.loadFileURL(url, allowingReadAccessTo: url.deletingLastPathComponent())
        } else {
            print("❌ MapViewController: could not find map.html in bundle")
        }
    }

    // MARK: - WKNavigationDelegate

    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        // Send init command once the page is fully loaded
        var params: [String: Any] = [
            "action": "init",
            "lat":    latitude,
            "lng":    longitude,
            "zoom":   zoom
        ]
        if let id = requestId { params["id"] = id }

        guard let json = try? JSONSerialization.data(withJSONObject: MapController.shared.cleanForJSON(params)),
              let jsonString = String(data: json, encoding: .utf8) else { return }

        let escaped = jsonString
            .replacingOccurrences(of: "\\", with: "\\\\")
            .replacingOccurrences(of: "'",  with: "\\'")
        webView.evaluateJavaScript("window.NativePHPMapCommand('\(escaped)')", completionHandler: nil)
    }

    // MARK: - WKScriptMessageHandler

    /// Receives { event: "Native\\...", data: { … } } messages from Leaflet JS.
    func userContentController(_ userContentController: WKUserContentController,
                                didReceive message: WKScriptMessage) {
        guard message.name == "nativePHPMap",
              let body = message.body as? String,
              let data = body.data(using: .utf8),
              let obj  = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let event = obj["event"] as? String else { return }

        let payload = (obj["data"] as? [String: Any]) ?? [:]
        print("📨 JS→Native event=\(event) payload=\(payload)")
        LaravelBridge.shared.send?(event, payload)
    }
}
