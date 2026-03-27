# Mobile GIS Plugin for NativePHP

A NativePHP Mobile plugin that provides GPS location access and GIS capabilities for iOS and Android.

---

## Phases

| Phase | Feature | Status |
|-------|---------|--------|
| 1 | GPS – Get Current Location | ✅ Done |
| 2 | Leaflet JS – Map, Polygon & Markers | 🔜 Planned |

---

## Installation

```bash
composer require nativephp/mobile-gis
```

The service provider is auto-discovered by Laravel.

---

## Phase 1 — GPS: Get Current Location

Retrieves the device's current GPS coordinates. All results are returned asynchronously via events.

### Required Permissions

**Android** — added automatically via `nativephp.json`:
- `ACCESS_FINE_LOCATION`
- `ACCESS_COARSE_LOCATION`

**iOS** — added automatically via `nativephp.json`:
- `NSLocationWhenInUseUsageDescription`
- `NSLocationAlwaysAndWhenInUseUsageDescription`

---

### Usage

#### PHP / Livewire

```php
use Native\Mobile\Gis;

// Basic: one-shot live GPS fix
Gis::getCurrentLocation();

// With accuracy preference
Gis::getCurrentLocation(accuracy: 'balanced'); // 'high' (default) or 'balanced'

// With lastKnown fallback enabled
// → starts background tracking while the app is open
// → if device goes offline, returns the last cached location instead of an error
Gis::getCurrentLocation(lastKnown: true);

// All options
Gis::getCurrentLocation(
    id: 'my-request',
    accuracy: 'high',
    lastKnown: true,
);
```

#### JavaScript

```js
// Basic
NativePHP.Gis.GetCurrentLocation({ accuracy: 'high' });

// With lastKnown fallback
NativePHP.Gis.GetCurrentLocation({ lastKnown: true });

// All options
NativePHP.Gis.GetCurrentLocation({ id: 'my-request', accuracy: 'high', lastKnown: true });
```

---

### Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `id` | `string` | `null` | Optional identifier echoed back in all events |
| `accuracy` | `string` | `'high'` | `'high'` uses GPS chip; `'balanced'` uses cell/Wi-Fi (saves battery) |
| `lastKnown` | `bool` | `false` | See [Last Known Location](#last-known-location-fallback) below |
| `event` | `string` | `null` | Override the dispatched event class |

---

### Last Known Location Fallback

When `lastKnown: true` is passed:

1. **Background tracking starts** — the plugin begins silently collecting location fixes in the foreground while the app is open (no extra permission required, pauses automatically when the app is backgrounded).
2. **Live fix attempted first** — the plugin still requests the current GPS location normally.
3. **Offline / GPS unavailable** — if the live fix fails (no signal, airplane mode, GPS disabled), the plugin falls back in order:
   - In-session cache (last fix collected by background tracking)
   - OS-level last known location (system cache, persists across app sessions on Android)
4. **`isLastKnown` flag** — the `LocationReceived` event payload always includes `isLastKnown: bool` so you can distinguish live from cached results.
5. **No duplicate tracking** — calling `GetCurrentLocation(lastKnown: true)` multiple times only starts the background tracker once.

> **Battery note:** Background tracking runs at the requested `accuracy` level using a 10-second update interval (minimum 5 seconds). Use `accuracy: 'balanced'` if you want the fallback cache with lower battery impact.

#### Flow diagram

```
GetCurrentLocation(lastKnown: true)
        │
        ├─ Start background tracker (if not already running)
        │
        ├─ Try live GPS fix
        │       ├─ ✅ Success → fire LocationReceived (isLastKnown: false)
        │       └─ ❌ Fail
        │               ├─ In-session cache available? → fire LocationReceived (isLastKnown: true)
        │               ├─ OS last-known available?    → fire LocationReceived (isLastKnown: true)
        │               └─ Nothing available           → fire LocationError

GetCurrentLocation(lastKnown: false)  [default]
        │
        ├─ Try live GPS fix
        │       ├─ ✅ Success → fire LocationReceived (isLastKnown: false)
        │       └─ ❌ Fail   → fire LocationError
```

---

### Events

#### `Native\Mobile\Events\Gis\LocationReceived`

Fired when a location is obtained (live or cached).

```php
use Native\Mobile\Events\Gis\LocationReceived;

protected $listeners = [
    'native:' . LocationReceived::class => 'onLocationReceived',
];

public function onLocationReceived(
    float $latitude,
    float $longitude,
    float $accuracy,      // horizontal accuracy in metres
    float $altitude,      // metres above sea level
    float $bearing,       // degrees (0–360), 0 if unavailable
    float $speed,         // metres per second, 0 if unavailable
    bool  $isLastKnown,   // true if returned from cache, false if live
    ?string $id           // echoed back from the original request
): void {
    if ($isLastKnown) {
        // Inform the user that this is a cached position
    }
}
```

**JavaScript:**

```js
Livewire.on('native:Native\\Mobile\\Events\\Gis\\LocationReceived', (payload) => {
    console.log(payload.latitude, payload.longitude);
    console.log('Is cached?', payload.isLastKnown);
});
```

---

#### `Native\Mobile\Events\Gis\LocationPermissionDenied`

Fired when the user denies location permission.

```php
use Native\Mobile\Events\Gis\LocationPermissionDenied;

protected $listeners = [
    'native:' . LocationPermissionDenied::class => 'onPermissionDenied',
];

public function onPermissionDenied(?string $id): void
{
    // Prompt user to enable location in settings
}
```

---

#### `Native\Mobile\Events\Gis\LocationError`

Fired when the location request fails and no fallback is available.

```php
use Native\Mobile\Events\Gis\LocationError;

protected $listeners = [
    'native:' . LocationError::class => 'onLocationError',
];

public function onLocationError(string $message, ?string $id): void
{
    // Handle error — only reaches here if lastKnown=false or no cache exists
}
```

---

### Full Livewire Example

```php
<?php

namespace App\Livewire;

use Livewire\Component;
use Native\Mobile\Gis;
use Native\Mobile\Events\Gis\LocationReceived;
use Native\Mobile\Events\Gis\LocationPermissionDenied;
use Native\Mobile\Events\Gis\LocationError;

class MyMap extends Component
{
    public ?float $latitude = null;
    public ?float $longitude = null;
    public bool $isOfflineLocation = false;
    public ?string $error = null;

    protected $listeners = [
        'native:' . LocationReceived::class        => 'onLocationReceived',
        'native:' . LocationPermissionDenied::class => 'onPermissionDenied',
        'native:' . LocationError::class            => 'onLocationError',
    ];

    public function getLocation(): void
    {
        $this->error = null;

        // Enable lastKnown so the app works offline
        Gis::getCurrentLocation(id: 'map-center', lastKnown: true);
    }

    public function onLocationReceived(
        float $latitude,
        float $longitude,
        float $accuracy,
        float $altitude,
        float $bearing,
        float $speed,
        bool $isLastKnown,
        ?string $id
    ): void {
        $this->latitude = $latitude;
        $this->longitude = $longitude;
        $this->isOfflineLocation = $isLastKnown;
    }

    public function onPermissionDenied(?string $id): void
    {
        $this->error = 'Location permission was denied. Please enable it in your device settings.';
    }

    public function onLocationError(string $message, ?string $id): void
    {
        $this->error = 'Could not get location: ' . $message;
    }

    public function render()
    {
        return view('livewire.my-map');
    }
}
```

---

## Event Payload Reference

| Event | Field | Type | Description |
|-------|-------|------|-------------|
| `LocationReceived` | `latitude` | `float` | Latitude in decimal degrees |
| `LocationReceived` | `longitude` | `float` | Longitude in decimal degrees |
| `LocationReceived` | `accuracy` | `float` | Horizontal accuracy in metres |
| `LocationReceived` | `altitude` | `float` | Altitude in metres above sea level |
| `LocationReceived` | `bearing` | `float` | Direction in degrees (0–360) |
| `LocationReceived` | `speed` | `float` | Speed in metres per second |
| `LocationReceived` | `isLastKnown` | `bool` | `true` if returned from cache, `false` if live |
| `LocationReceived` | `id` | `string\|null` | Echoed request ID |
| `LocationPermissionDenied` | `id` | `string\|null` | Echoed request ID |
| `LocationError` | `message` | `string` | Error description |
| `LocationError` | `id` | `string\|null` | Echoed request ID |

---

## License

MIT — [NativePHP](https://nativephp.com)
