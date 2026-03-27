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
    ///   - lastKnown: (optional) bool - When true, starts continuous foreground location tracking
    ///       while the app is open so the last fix is always cached. If the device goes offline or
    ///       GPS is unavailable, the cached location is returned instead of an error. Defaults to false.
    /// Returns:
    ///   - (empty map - results are returned via events)
    /// Events:
    ///   - Fires "Native\Mobile\Events\Gis\LocationReceived" when location is obtained
    ///     Payload: { latitude, longitude, accuracy, altitude, bearing, speed, isLastKnown, id? }
    ///   - Fires "Native\Mobile\Events\Gis\LocationPermissionDenied" when permission is denied
    ///     Payload: { id? }
    ///   - Fires "Native\Mobile\Events\Gis\LocationError" when location cannot be obtained
    ///     Payload: { message, id? }
    class GetCurrentLocation: BridgeFunction {
        func execute(parameters: [String: Any]) throws -> [String: Any] {
            let id = parameters["id"] as? String
            let event = parameters["event"] as? String
            let accuracy = parameters["accuracy"] as? String ?? "high"
            let lastKnown = parameters["lastKnown"] as? Bool ?? false

            print("📍 Getting location id=\(id ?? "nil"), accuracy=\(accuracy), lastKnown=\(lastKnown)")

            let desiredAccuracy: CLLocationAccuracy = (accuracy == "high")
                ? kCLLocationAccuracyBest
                : kCLLocationAccuracyHundredMeters

            DispatchQueue.main.async {
                GpsLocationDelegate.shared.requestLocation(
                    id: id,
                    event: event,
                    desiredAccuracy: desiredAccuracy,
                    useLastKnown: lastKnown
                )
            }

            return [:]
        }
    }
}

// MARK: - GpsLocationDelegate

/// Singleton CLLocationManager delegate.
///
/// Modes of operation:
///  - One-shot (lastKnown=false): calls requestLocation() — iOS fires one update then stops.
///  - Continuous (lastKnown=true): calls startUpdatingLocation() — runs for the app session,
///    caching every fix. When a pending request exists the first fix satisfies it and the
///    delegate keeps running silently to keep lastKnownLocation fresh.
final class GpsLocationDelegate: NSObject, CLLocationManagerDelegate {

    static let shared = GpsLocationDelegate()

    private let locationManager = CLLocationManager()

    // Last successfully received location — used as fallback when live GPS fails
    private(set) var lastKnownLocation: CLLocation?

    // Whether continuous background tracking is active
    private var isBackgroundTracking = false

    // Pending one-time request fields
    private var pendingId: String?
    private var pendingEvent: String?
    private var pendingUseLastKnown: Bool = false
    private var hasPendingRequest: Bool = false

    private override init() {
        super.init()
        locationManager.delegate = self
        // Pause location updates automatically when not needed (saves battery)
        locationManager.pausesLocationUpdatesAutomatically = true
        locationManager.activityType = .other
    }

    // MARK: - Public API

    /// Request a location fix. Handles permission flow, background tracking, and fallback.
    func requestLocation(id: String?, event: String?, desiredAccuracy: CLLocationAccuracy, useLastKnown: Bool) {
        pendingId = id
        pendingEvent = event
        pendingUseLastKnown = useLastKnown
        hasPendingRequest = true
        locationManager.desiredAccuracy = desiredAccuracy

        switch locationManager.authorizationStatus {
        case .authorizedWhenInUse, .authorizedAlways:
            performRequest(useLastKnown: useLastKnown)

        case .notDetermined:
            print("🔄 Requesting location permission")
            locationManager.requestWhenInUseAuthorization()
            // locationManagerDidChangeAuthorization will call performRequest after grant

        case .denied, .restricted:
            print("❌ Location permission denied or restricted")
            hasPendingRequest = false
            firePermissionDenied(id: id)

        @unknown default:
            print("❌ Unknown authorization status")
            hasPendingRequest = false
            firePermissionDenied(id: id)
        }
    }

    // MARK: - Private

    private func performRequest(useLastKnown: Bool) {
        if useLastKnown {
            // Ensure continuous tracking is running to keep cache fresh for this session
            if !isBackgroundTracking {
                print("▶️ Starting continuous foreground location tracking")
                isBackgroundTracking = true
                locationManager.startUpdatingLocation()
                // didUpdateLocations will fire when first fix arrives and satisfy hasPendingRequest
            }
            // If already tracking, the next didUpdateLocations will satisfy hasPendingRequest
        } else {
            // One-shot request — iOS stops after one fix automatically
            locationManager.requestLocation()
        }
    }

    // MARK: - CLLocationManagerDelegate

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        switch manager.authorizationStatus {
        case .authorizedWhenInUse, .authorizedAlways:
            if hasPendingRequest {
                print("✅ Permission granted — performing location request")
                performRequest(useLastKnown: pendingUseLastKnown)
            }
        case .denied, .restricted:
            if hasPendingRequest {
                print("❌ Permission denied after request")
                hasPendingRequest = false
                firePermissionDenied(id: pendingId)
                clearPending()
            }
        default:
            break
        }
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let location = locations.last else { return }

        // Always cache the latest fix (powers lastKnown fallback for future requests)
        lastKnownLocation = location

        // Satisfy a pending single-shot request with this fix
        if hasPendingRequest {
            print("✅ Location received: \(location.coordinate.latitude), \(location.coordinate.longitude)")
            hasPendingRequest = false
            let useLastKnown = pendingUseLastKnown
            fireLocationReceived(location: location, id: pendingId, customEvent: pendingEvent, isLastKnown: false)
            clearPending()

            // Stop updates only if we were in one-shot mode (requestLocation auto-stops,
            // but if for any reason we're in startUpdatingLocation we stop it)
            if !useLastKnown {
                manager.stopUpdatingLocation()
                isBackgroundTracking = false
            }
            // If useLastKnown=true, keep startUpdatingLocation() running to refresh the cache
        }
        // If no pending request: silent cache update only (no event fired)
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        guard hasPendingRequest else {
            // Background tracking error — log and keep going
            print("⚠️ Background location error (no pending request): \(error.localizedDescription)")
            return
        }

        hasPendingRequest = false
        print("❌ Location error: \(error.localizedDescription)")

        if pendingUseLastKnown, let cached = lastKnownLocation {
            // Fallback to cached location
            print("📦 Falling back to last known location: \(cached.coordinate.latitude), \(cached.coordinate.longitude)")
            fireLocationReceived(location: cached, id: pendingId, customEvent: pendingEvent, isLastKnown: true)
        } else {
            fireLocationError(message: error.localizedDescription, id: pendingId)
        }

        clearPending()
    }

    // MARK: - Event Helpers

    private func fireLocationReceived(location: CLLocation, id: String?, customEvent: String?, isLastKnown: Bool) {
        let eventClass = customEvent ?? "Native\\Mobile\\Events\\Gis\\LocationReceived"
        var payload: [String: Any] = [
            "latitude":    location.coordinate.latitude,
            "longitude":   location.coordinate.longitude,
            "accuracy":    location.horizontalAccuracy,
            "altitude":    location.altitude,
            "bearing":     location.course >= 0 ? location.course : 0.0,
            "speed":       location.speed >= 0 ? location.speed : 0.0,
            "isLastKnown": isLastKnown
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
        pendingUseLastKnown = false
    }
}
