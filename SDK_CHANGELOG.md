# Wemap SDKs for Android — Change Log

This log covers the **Wemap SDKs for Android** the sample apps depend on. The samples' own changes are in
[`CHANGELOG.md`](CHANGELOG.md). It starts at 1.0.0-beta.1; earlier SDK versions have no public change log.

---

## 1.0.0-beta.1

**This is a pre-release.** Depend on it by its exact version, `1.0.0-beta.1`. The offline VPS module
`wemap-vps-local` keeps a version of its own, `1.0.0-alpha.4` — see § Changed.

### Breaking changes

**v1 is a breaking release.** The migration guide is ordered so that each step leaves your project compiling
— see the [Wemap SDKs for Android documentation](https://developers.getwemap.com/docs/android-native/getting-started).

* SDKs: static singletons replaced by per-instance sessions — `WemapCoreSDK.instance`, `WemapMapSDK.instance`,
  `WemapMapSDK.mapData`, `ServiceFactory` and `DependencyManager` are gone; every view and location source takes
  a `CoreSession` or `MapSession`, created by a suspending `create` and shared across a screen's map, AR view and
  location source
* SDKs: global mutable constants replaced by immutable configs — `CoreConstants`, `MapConstants`, `ARConstants`,
  `VPSControllerConstants` and `StateManagerConstants` are gone, and the `Environment` sealed class with
  `IEnvironment` is replaced by an enum
* SDKs: `Coordinate` is an immutable value type built around a GeoJSON `Point`, levels are a `Levels` value, and
  `Level` moved from the Core SDK to the Map SDK
* SDKs: every manager and location-source listener replaced by a `Flow` — `PointOfInterestManagerListener`,
  `LocationSourceListener`, `WemapVPSARCoreLocationSourceListener` and the `…Observer` interfaces are gone, and
  views report loading through `LoadPhase` in place of `getMapViewAsync` / `getARViewAsync` / `isLoaded`
* SDKs: the public API follows Kotlin idiom
  * acronyms are no longer all-caps — `addPOI` -> `addPoi`, `WemapVPSARCoreLocationSource` ->
    `VpsARCoreLocationSource`, `imageURL` -> `imageUrl`
  * interfaces dropped their `I` prefix — `INavigationManager` -> `NavigationManager`,
    `IPointOfInterestManager` -> `PointOfInterestManager`, `IUserLocationProvider` -> `UserLocationProvider`, and
    so on for every interface
  * every public `Float` scalar is a `Double`, time-valued members are `kotlin.time.Duration`, and errors are
    sealed hierarchies matched with `is`
* SDKs: services and managers no longer take ids per call, and several were renamed — `ItineraryServiceError` ->
  `DirectionsServiceError`, `IPackdataManager` -> `PackdataService`, `PointOfInterestWithInfo` ->
  `PointOfInterestWithItineraryInfo`, `ItineraryManager.getItineraries` -> `computeItineraries`,
  `IStringConvertibleCompact` -> `CompactStringConvertible` and `NavigationInfo.shortDescription` ->
  `toCompactString()`
* SDKs: map metadata is read from the session — `MapData`, and the symbols that were public only for
  cross-module needs, are no longer part of the public API. A declaration left in an `*.internal.*` package
  requires an opt-in and is not for use by apps
* GeoAR: no SceneView type is part of the public API — `GeoARView` holds a `SceneView` instead of extending one,
  so its inherited `SceneView` members are gone, and `GeoNode` and `GeoCameraNode` are internal
* Pos(VPS): `VpsARCoreLocationSource.bind(context, surfaceView)` and `unbind()` are the public surface; the GL
  plumbing and the navigation-interceptor callbacks are internal
* Core: `DirectionsServiceError.NoItinerariesFound` carries the reason reported by the server, and a request the
  server rejects for any other cause throws the new `RequestFailed(code, reason)`
* SDKs: the consumer build contract changed — Kotlin 2.2, `jvmTarget` 11 (17 for GeoAR), `compileSdk` 37, and
  `minSdk` 24 for `wemap-vps-arcore` and `wemap-vps-local` (every other module stays at 23)
* SDKs: Retrofit, the Retrofit serialization converter, Coroutines Android, the OkHttp logging interceptor and
  Play Services Base are no longer exposed transitively — declare them yourself if your app uses them

### Added

* Map/GeoAR: native Jetpack Compose support, in two new artifacts
  * `com.getwemap.sdk:map-compose` — `WemapMap`, with two-way camera state through
    `rememberMapCameraPositionState`
  * `com.getwemap.sdk:geo-ar-compose` — `WemapGeoAR`. `minSdk` 24 and Java 17, matching `geo-ar`
* Map/GeoAR: location and attitude updates are exposed to the integrator
* Map/GeoAR: initial point-of-interest loading reports errors and times out instead of hanging
* Core: `LoadPhase` (`Loading` / `Ready` / `Failed(error)`) and `LoadPhaseProvider`, with the `awaitLoaded()`
  extension, implemented by both `WemapMapView` and `GeoARView`
* Map: `UserLocationManager.locationState` and `locationStates` report whether the position the map is showing
  is still current
* Map: camera reading and observation — `MapCameraState`, `WemapMapView.cameraStates(frequency)`, the read-only
  `WemapMapView.proxy`, and `WemapMapView.initialCamera` for the camera the map opens at
* Map: `WemapMapView.touchedPoints` reports taps on the map that selected no point of interest
* Map: `MapError.FailedToLoadMap`, reported through `loadPhases` when the map or its style fails to load
* Map: the lifecycle of `WemapMapView` is driven by a lifecycle observer
* Core: double-precision `Quaternion` and `Double3` are public in `com.getwemap.sdk.core.math`, so
  `Attitude.quaternion` can be read without an opt-in
* SDKs: every public error type declares a non-null `message`
* SDKs: every public config and options type can be constructed with no arguments from Java as well as Kotlin
* Pos(VPS Local): the offline visual positioning source `VpsLocalLocationSource` — see § Changed for how it is
  versioned
  * pedestrian dead reckoning between visual fixes, and movement-aware scan cadence
  * texture and blur gates that skip unusable frames, and a pause while the phone is held flat
  * opt-in automatic back/front camera switching, and opt-in background scanning through a foreground service

### Changed

* Pos(VPS Local): `com.getwemap.sdk.positioning:wemap-vps-local` is published to the public Wemap Maven
  repository alongside every other module, on a version of its own — `1.0.0-alpha.4`, not `1.0.0-beta.1`. Its
  whole public API is `@AlphaVpsLocalApi` and **does not follow semantic versioning**: any declaration may change
  or disappear in any release, patches included. It needs a map database for your venue, which Wemap builds
* Core: `LocationSource.deinit()` unregisters the source from the session that holds it, so a screen no longer
  has to clear the location manager's source before releasing it
* Core: `TravelMode` compares by value
* Core: `itinerariesInfoToMultipleDestinations` and `itinerariesInfoToMultiplePois` throw when the server rejects
  the request, instead of returning every itinerary information as `null`
* Map: `itineraryOptions` is nullable in `MapNavigationManager` functions — `null` keeps the current options (or
  the defaults when there is no itinerary yet), a value replaces them
* Map: the camera mode switches to `TRACKING_COMPASS` when navigation starts

### Fixed

* Map: the user location indicator stayed blue for about 5 seconds after tracking was lost; it greys
  immediately now, and VPS no longer reports a position on the frame where it declares tracking lost
* Map: the user location indicator stayed grey with fixes still arriving, on a location source slower than the
  stale-state timeout
* Map: coming back from the background left the user location indicator blue on a position from before the app
  was backgrounded
* Map: itinerary options were ignored on `startNavigation`
* Map: no building was focused until the first pan or zoom, so a map that opened over one showed no levels and
  no level-based point-of-interest filtering
* Map: a tap on a point of interest before the map had loaded crashed instead of being ignored
* Map/GeoAR: every map or AR view after the first in a Jetpack Compose host never got a render surface — the map
  stayed loading and the AR view rendered nothing
* Map/GeoAR: a `WemapMapView` and a `GeoARView` sharing one session could stop each other's positioning; the
  shared location source is reference-counted now
* Core: replacing a `WemapMapView` or a `GeoARView` with another of the same kind on a shared session left the
  new view with no points of interest and no navigation route
* Core: a location source the app started itself could be stopped by a view releasing its own request
* Core: a superseded navigation's arrival check could stop a newer navigation
* Core: a navigation started while the user already stood at its destination could report `Arrived` before
  `Started`
* Core: an itinerary request the server rejected reported an unusable `HTTP 400` failure instead of the reason it
  gave, such as a search rule unavailable for the map
* Core: a coordinate's `time` was encoded in milliseconds on Android and in Unix seconds on iOS; both now use
  Unix seconds
* Core: printing a `NavigationInfo`, `Itinerary` or `Leg` dumped the whole itinerary geometry, freezing the app
  on a long route, and printing an outdoor `Coordinate` or a `TravelMode` showed an identity hash
* GeoAR: a `NullPointerException` when the AR view was displayed with the map already loaded
* GeoAR: AR content floated instead of sticking to the world when the app rendered the location source's camera
  preview into a view of its own
* GeoAR: leaving an AR screen stopped the camera and positioning of a location source whose preview the app
  renders itself
* GeoAR: replacing the location source of a live `GeoARView` stopped the camera of the incoming source instead
  of the outgoing one
* GeoAR: calling `GeoARView.destroy()` twice threw `IllegalStateException`
* Pos(VPS): a black screen when the map was loaded with the VPS location source
* Pos(VPS): the camera preview lagged during a scan, and dropped to a few frames per second with the phone
  tilted towards the floor
* Pos(VPS): `VpsARCoreLocationSource` never started when a `GeoARView` was the one binding its camera
* Pos(VPS): coming back from the background resumed positioning from the fix taken before the app stopped,
  instead of staying lost until the next scan
* SDKs: the published `-javadoc` and `-html-docs` artifacts had each other's contents

### Removed

* Core
  * `PointOfInterest.customerID`
  * `NavigationInstructions.direction` — read the direction from `Step.direction`
  * `Coordinate.ecefToEnuRot` and `Coordinate.enuToEcefRot`
* Map: `MapPointOfInterestManager.defaultZoom` can no longer be changed
* Pos(VPS): `CameraTrackingState.copy()` and `UserLocalizationUpdate.copy()`

### Dependencies

* All
  * Kotlin 2.4.20, with `kotlin-stdlib` pinned at 2.2.21
* Core
  * OkHttp 5.4.0 -> 5.5.0
  * Serialization 1.6.3 -> 1.11.0
  * Coroutines 1.10.2 -> 1.11.0
* Map
  * MapLibre 12.2.2 -> 13.6.0
* GeoAR
  * CameraX 1.6.1 -> 1.6.2
* Pos(Adaptive)
  * Play Services Location 21.3.0 -> 21.4.0
* Pos(VPS)
  * ARCore 1.54.0 -> 1.56.0
* Pos(VPS Local)
  * CameraX 1.6.1 -> 1.6.2

### Compatibility

* Android 6.0 (API 23) or newer — Android 7.0 (API 24) for `geo-ar`, `geo-ar-compose`, `wemap-vps-arcore` and
  `wemap-vps-local`
* Kotlin 2.2 or newer
* `compileSdk` 37
* Compose BOM 2026.06.01 or newer for `map-compose` and `geo-ar-compose`
