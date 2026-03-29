# Mobile GIS Plugin for NativePHP

A NativePHP Mobile plugin that provides GPS location access and GIS capabilities for iOS and Android.

---

## Phases

| Phase | Feature | Status |
|-------|---------|--------|
| 1 | GPS – Get Current Location | ✅ Done |
| 2 | Leaflet JS – Map, Polygon & Markers | ✅ Done |

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

---

## Phase 2 — Leaflet JS: Map, Polygon & Markers

Renders a full-screen interactive map using [Leaflet](https://leafletjs.com/) inside a native WebView. Supports placing markers, drawing/displaying polygons, and receiving tap events — all driven from PHP/Livewire.

---

### Usage

#### PHP / Livewire

```php
use Native\Mobile\Gis;

// Show the map centred on a location
Gis::showMap(latitude: 51.505, longitude: -0.09, zoom: 13);

// With a request ID (echoed back in MapReady / MapClosed)
Gis::showMap(id: 'main-map', latitude: 51.505, longitude: -0.09, zoom: 13);

// Add a marker
Gis::addMarker(id: 'hq', latitude: 51.505, longitude: -0.09, label: 'HQ');

// Add a coloured marker
Gis::addMarker(id: 'site-a', latitude: 51.51, longitude: -0.1, label: 'Site A', color: '#e74c3c');

// Remove a marker
Gis::removeMarker(id: 'hq');

// Add a polygon
Gis::addPolygon(id: 'zone-1', coordinates: [
    ['latitude' => 51.509, 'longitude' => -0.08],
    ['latitude' => 51.503, 'longitude' => -0.06],
    ['latitude' => 51.51,  'longitude' => -0.047],
]);

// Add a styled polygon
Gis::addPolygon(
    id: 'zone-2',
    coordinates: [...],
    color: '#e74c3c',
    fillColor: '#e74c3c',
    fillOpacity: 0.35,
);

// Remove a polygon
Gis::removePolygon(id: 'zone-1');

// Pan / zoom the map
Gis::setMapView(latitude: 51.515, longitude: -0.1, zoom: 15);
```

#### JavaScript

```js
// Show the map
NativePHP.Gis.Map.Show({ latitude: 51.505, longitude: -0.09, zoom: 13 });

// Add a marker
NativePHP.Gis.Map.AddMarker({ id: 'hq', latitude: 51.505, longitude: -0.09, label: 'HQ' });

// Remove a marker
NativePHP.Gis.Map.RemoveMarker({ id: 'hq' });

// Add a polygon
NativePHP.Gis.Map.AddPolygon({
    id: 'zone-1',
    coordinates: [
        { latitude: 51.509, longitude: -0.08 },
        { latitude: 51.503, longitude: -0.06 },
        { latitude: 51.51,  longitude: -0.047 },
    ],
    color: '#3388ff',
    fillOpacity: 0.2,
});

// Remove a polygon
NativePHP.Gis.Map.RemovePolygon({ id: 'zone-1' });

// Pan / zoom
NativePHP.Gis.Map.SetView({ latitude: 51.515, longitude: -0.1, zoom: 15 });
```

---

### Draw Mode

The map includes a built-in **Draw Polygon** toolbar button. The user taps points on the map and double-taps to close the shape. When finished, a `PolygonCreated` event fires with the coordinates — your app can then persist and re-render the polygon via `Gis::addPolygon()`.

---

### Parameters

#### `Gis::showMap()`

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `id` | `string` | `null` | Optional identifier echoed in `MapReady` / `MapClosed` |
| `latitude` | `float` | `0.0` | Initial centre latitude |
| `longitude` | `float` | `0.0` | Initial centre longitude |
| `zoom` | `int` | `13` | Initial zoom level (1–19) |

#### `Gis::addMarker()`

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `id` | `string` | — | **Required.** Unique marker identifier |
| `latitude` | `float` | — | **Required.** Marker latitude |
| `longitude` | `float` | — | **Required.** Marker longitude |
| `label` | `string` | `null` | Tooltip text shown on tap |
| `color` | `string` | `null` | CSS colour (e.g. `"#e74c3c"`). Defaults to standard blue pin |

#### `Gis::addPolygon()`

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `id` | `string` | — | **Required.** Unique polygon identifier |
| `coordinates` | `array` | — | **Required.** Array of `['latitude' => float, 'longitude' => float]` |
| `color` | `string` | `"#3388ff"` | Stroke colour |
| `fillColor` | `string` | stroke colour | Fill colour |
| `fillOpacity` | `float` | `0.2` | Fill opacity (0–1) |

#### `Gis::setMapView()`

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `latitude` | `float` | — | **Required.** New centre latitude |
| `longitude` | `float` | — | **Required.** New centre longitude |
| `zoom` | `int` | `null` | New zoom level. Keeps current zoom if omitted |

---

### Events

#### `Native\Mobile\Events\Gis\MapReady`

Fired once the map tiles have loaded and the map is interactive.

```php
use Native\Mobile\Events\Gis\MapReady;

protected $listeners = [
    'native:' . MapReady::class => 'onMapReady',
];

public function onMapReady(?string $id): void
{
    // Safe to add markers / polygons now
    Gis::addMarker(id: 'hq', latitude: 51.505, longitude: -0.09, label: 'HQ');
}
```

---

#### `Native\Mobile\Events\Gis\MapClosed`

Fired when the user dismisses the map.

```php
use Native\Mobile\Events\Gis\MapClosed;

protected $listeners = [
    'native:' . MapClosed::class => 'onMapClosed',
];

public function onMapClosed(?string $id): void
{
    // Map was dismissed
}
```

---

#### `Native\Mobile\Events\Gis\MarkerClicked`

Fired when the user taps a marker.

```php
use Native\Mobile\Events\Gis\MarkerClicked;

protected $listeners = [
    'native:' . MarkerClicked::class => 'onMarkerClicked',
];

public function onMarkerClicked(string $id, float $latitude, float $longitude, ?string $label): void
{
    // $id is the marker ID passed to addMarker()
}
```

---

#### `Native\Mobile\Events\Gis\PolygonCreated`

Fired when the user completes drawing a polygon in draw mode.

```php
use Native\Mobile\Events\Gis\PolygonCreated;

protected $listeners = [
    'native:' . PolygonCreated::class => 'onPolygonCreated',
];

public function onPolygonCreated(array $coordinates): void
{
    // $coordinates: [['latitude' => float, 'longitude' => float], ...]
    // Persist and re-render:
    $id = 'drawn-' . uniqid();
    Gis::addPolygon(id: $id, coordinates: $coordinates, color: '#e74c3c', fillOpacity: 0.25);
}
```

---

#### `Native\Mobile\Events\Gis\PolygonClicked`

Fired when the user taps an existing polygon.

```php
use Native\Mobile\Events\Gis\PolygonClicked;

protected $listeners = [
    'native:' . PolygonClicked::class => 'onPolygonClicked',
];

public function onPolygonClicked(string $id): void
{
    // $id is the polygon ID passed to addPolygon()
}
```

---

### Full Livewire Example

```php
<?php

namespace App\Livewire;

use Livewire\Component;
use Native\Mobile\Gis;
use Native\Mobile\Events\Gis\MapReady;
use Native\Mobile\Events\Gis\MapClosed;
use Native\Mobile\Events\Gis\MarkerClicked;
use Native\Mobile\Events\Gis\PolygonCreated;
use Native\Mobile\Events\Gis\PolygonClicked;

class SiteMap extends Component
{
    public bool $mapOpen = false;
    public ?string $selectedMarker = null;
    public array $zones = [];

    protected $listeners = [
        'native:' . MapReady::class       => 'onMapReady',
        'native:' . MapClosed::class      => 'onMapClosed',
        'native:' . MarkerClicked::class  => 'onMarkerClicked',
        'native:' . PolygonCreated::class => 'onPolygonCreated',
        'native:' . PolygonClicked::class => 'onPolygonClicked',
    ];

    public function openMap(): void
    {
        $this->mapOpen = true;
        Gis::showMap(id: 'site-map', latitude: 51.505, longitude: -0.09, zoom: 13);
    }

    public function onMapReady(?string $id): void
    {
        Gis::addMarker(id: 'hq', latitude: 51.505, longitude: -0.09, label: 'HQ', color: '#2563eb');

        foreach ($this->zones as $zone) {
            Gis::addPolygon(
                id: $zone['id'],
                coordinates: $zone['coordinates'],
                color: $zone['color'],
                fillOpacity: 0.25,
            );
        }
    }

    public function onMapClosed(?string $id): void
    {
        $this->mapOpen = false;
    }

    public function onMarkerClicked(string $id, float $latitude, float $longitude, ?string $label): void
    {
        $this->selectedMarker = $id;
    }

    public function onPolygonCreated(array $coordinates): void
    {
        $zone = ['id' => 'zone-' . uniqid(), 'coordinates' => $coordinates, 'color' => '#e74c3c'];
        $this->zones[] = $zone;
        Gis::addPolygon(id: $zone['id'], coordinates: $coordinates, color: '#e74c3c', fillOpacity: 0.25);
    }

    public function onPolygonClicked(string $id): void
    {
        // Handle polygon selection
    }

    public function render()
    {
        return view('livewire.site-map');
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
| `MapReady` | `id` | `string\|null` | Echoed request ID |
| `MapClosed` | `id` | `string\|null` | Echoed request ID |
| `MarkerClicked` | `id` | `string` | Marker ID |
| `MarkerClicked` | `latitude` | `float` | Marker latitude |
| `MarkerClicked` | `longitude` | `float` | Marker longitude |
| `MarkerClicked` | `label` | `string\|null` | Marker label |
| `PolygonCreated` | `coordinates` | `array` | Array of `{latitude, longitude}` |
| `PolygonClicked` | `id` | `string` | Polygon ID |

---

## License

MIT — [NativePHP](https://nativephp.com)
