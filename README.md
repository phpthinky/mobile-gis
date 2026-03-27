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

// Get current location (high accuracy by default)
Gis::getCurrentLocation();

// With optional parameters
Gis::getCurrentLocation(
    id: 'my-location-request',
    accuracy: 'balanced', // 'high' (GPS) or 'balanced' (network). Default: 'high'
);
```

#### JavaScript

```js
// Trigger via NativePHP JS bridge
NativePHP.Gis.GetCurrentLocation({ accuracy: 'high' });

// With optional ID
NativePHP.Gis.GetCurrentLocation({ id: 'my-request', accuracy: 'balanced' });
```

---

### Events

#### `Native\Mobile\Events\Gis\LocationReceived`

Fired when the device successfully obtains a GPS location.

```php
use Native\Mobile\Events\Gis\LocationReceived;

protected $listeners = [
    'native:' . LocationReceived::class => 'onLocationReceived',
];

public function onLocationReceived(
    float $latitude,
    float $longitude,
    float $accuracy,   // horizontal accuracy in metres
    float $altitude,   // metres above sea level
    float $bearing,    // degrees (0–360), 0 if unavailable
    float $speed,      // metres per second, 0 if unavailable
    ?string $id        // echoed back from the original request
): void {
    // use coordinates
}
```

**JavaScript:**

```js
Livewire.on('native:Native\\Mobile\\Events\\Gis\\LocationReceived', (payload) => {
    console.log(payload.latitude, payload.longitude);
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

Fired when the location request fails (e.g. GPS unavailable, timeout).

```php
use Native\Mobile\Events\Gis\LocationError;

protected $listeners = [
    'native:' . LocationError::class => 'onLocationError',
];

public function onLocationError(string $message, ?string $id): void
{
    // Handle error
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
    public ?string $error = null;

    protected $listeners = [
        'native:' . LocationReceived::class => 'onLocationReceived',
        'native:' . LocationPermissionDenied::class => 'onPermissionDenied',
        'native:' . LocationError::class => 'onLocationError',
    ];

    public function getLocation(): void
    {
        $this->error = null;
        Gis::getCurrentLocation(id: 'map-center');
    }

    public function onLocationReceived(
        float $latitude,
        float $longitude,
        float $accuracy,
        float $altitude,
        float $bearing,
        float $speed,
        ?string $id
    ): void {
        $this->latitude = $latitude;
        $this->longitude = $longitude;
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
| `LocationReceived` | `id` | `string\|null` | Echoed request ID |
| `LocationPermissionDenied` | `id` | `string\|null` | Echoed request ID |
| `LocationError` | `message` | `string` | Error description |
| `LocationError` | `id` | `string\|null` | Echoed request ID |

---

## License

MIT — [NativePHP](https://nativephp.com)
