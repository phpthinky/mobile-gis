import Foundation
import CoreLocation

// MARK: - GIS Function Namespace

/// Functions related to GPS/location operations
/// Namespace: "Gis.*"
enum GpsFunctions {

    // MARK: - Gis.GetCurrentLocation

    /// Get the device's current GPS location
    /// Parameters:
    ///   - id: (optional) string - Optional ID to track this specific location request
    ///   - event: (optional) string - Custom event class to fire (defaults to "Native\Mobile\Events\Gis\LocationReceived")
    ///   - accuracy: (optional) string - "high" (GPS) or "balanced" (network). Defaults to "high"
    /// Returns:
    ///   - (empty map - results are returned via events)
    /// Events:
    ///   - Fires "Native\Mobile\Events\Gis\LocationReceived" when location is obtained
    ///     Payload: { latitude, longitude, accuracy, altitude, bearing, speed, id? }
    ///   - Fires "Native\Mobile\Events\Gis\LocationPermissionDenied" when permission is denied
    ///     Payload: { id? }
    ///   - Fires "Native\Mobile\Events\Gis\LocationError" when location cannot be obtained
    ///     Payload: { message, id? }
    class GetCurrentLocation: BridgeFunction {
        func execute(parameters: [String: Any]) throws -> [String: Any] {
            let id = parameters["id"] as? String
            let event = parameters["event"] as? String
            let accuracy = parameters["accuracy"] as? String ?? "high"

            print("📍 Getting current location with id=\(id ?? "nil"), accuracy=\(accuracy)")

            let desiredAccuracy: CLLocationAccuracy = (accuracy == "high")
                ? kCLLocationAccuracyBest
                : kCLLocationAccuracyHundredMeters

            // Delegate handles permission flow and location updates
            DispatchQueue.main.async {
                GpsLocationDelegate.shared.requestLocation(
                    id: id,
                    event: event,
                    desiredAccuracy: desiredAccuracy
                )
            }

            return [:]
        }
    }
}

// MARK: - GpsLocationDelegate

/// Singleton CLLocationManager delegate that handles location requests
final class GpsLocationDelegate: NSObject, CLLocationManagerDelegate {

    static let shared = GpsLocationDelegate()

    private let locationManager = CLLocationManager()
    private var pendingId: String?
    private var pendingEvent: String?

    private override init() {
        super.init()
        locationManager.delegate = self
    }

    /// Request a single location fix
    func requestLocation(id: String?, event: String?, desiredAccuracy: CLLocationAccuracy) {
        pendingId = id
        pendingEvent = event
        locationManager.desiredAccuracy = desiredAccuracy

        let status = locationManager.authorizationStatus

        switch status {
        case .authorizedWhenInUse, .authorizedAlways:
            print("✅ Location permission already granted, requesting location")
            locationManager.requestLocation()

        case .notDetermined:
            print("🔄 Requesting location permission")
            locationManager.requestWhenInUseAuthorization()
            // locationManager(_:didChangeAuthorization:) will trigger requestLocation after grant

        case .denied, .restricted:
            print("❌ Location permission denied or restricted")
            firePermissionDenied(id: id)

        @unknown default:
            print("❌ Unknown location authorization status")
            firePermissionDenied(id: id)
        }
    }

    // MARK: - CLLocationManagerDelegate

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        switch manager.authorizationStatus {
        case .authorizedWhenInUse, .authorizedAlways:
            print("✅ Location permission granted, requesting location")
            manager.requestLocation()

        case .denied, .restricted:
            print("❌ Location permission denied")
            firePermissionDenied(id: pendingId)
            clearPending()

        default:
            break
        }
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let location = locations.last else {
            fireLocationError(message: "No location data received", id: pendingId)
            clearPending()
            return
        }

        print("✅ Location obtained: \(location.coordinate.latitude), \(location.coordinate.longitude)")
        fireLocationReceived(location: location, id: pendingId, customEvent: pendingEvent)
        clearPending()
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        print("❌ Location error: \(error.localizedDescription)")
        fireLocationError(message: error.localizedDescription, id: pendingId)
        clearPending()
    }

    // MARK: - Event Helpers

    private func fireLocationReceived(location: CLLocation, id: String?, customEvent: String?) {
        let eventClass = customEvent ?? "Native\\Mobile\\Events\\Gis\\LocationReceived"
        var payload: [String: Any] = [
            "latitude": location.coordinate.latitude,
            "longitude": location.coordinate.longitude,
            "accuracy": location.horizontalAccuracy,
            "altitude": location.altitude,
            "bearing": location.course >= 0 ? location.course : 0.0,
            "speed": location.speed >= 0 ? location.speed : 0.0
        ]
        if let id = id { payload["id"] = id }
        LaravelBridge.shared.send?(eventClass, payload)
    }

    private func firePermissionDenied(id: String?) {
        let eventClass = "Native\\Mobile\\Events\\Gis\\LocationPermissionDenied"
        var payload: [String: Any] = [:]
        if let id = id { payload["id"] = id }
        LaravelBridge.shared.send?(eventClass, payload)
    }

    private func fireLocationError(message: String, id: String?) {
        let eventClass = "Native\\Mobile\\Events\\Gis\\LocationError"
        var payload: [String: Any] = ["message": message]
        if let id = id { payload["id"] = id }
        LaravelBridge.shared.send?(eventClass, payload)
    }

    private func clearPending() {
        pendingId = nil
        pendingEvent = nil
    }
}
