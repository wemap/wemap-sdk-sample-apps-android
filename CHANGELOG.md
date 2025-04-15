# Change Log

---

## [0.22.0]

### Added

* CoreSDK: Enhance offline graph calculation with more considerations
  * Added new option [`ItinerarySearchOption.isWheelchair`][5]

### Dependencies

* Map
  * MapLibre 11.8.4 -> 11.8.6

## [0.21.0]

### Added

* SDKs: any LocationSource can project user coordinate on the graph or itinerary
* PosSDK(VPS): Add ability to set itinerary from outside of SDK to VPSLocationSource
* MapSDK: accept already drawn itinerary as a new navigation

### Fixed

* PosSDK(VPS): level is changed few times in a row when scanned image from another level
* PosSDK(VPS): stability improvements

### Deprecated

* CoreSDK
  * `SimulatorLocationSource.constructor(options: SimulationOptions)` is replaced by `SimulatorLocationSource.constructor(mapData: MapData, options: SimulationOptions)`. If you continue to use deprecated method, user location projection on the graph will not work.
  * `LocationSourceListener.onError(error: Error)` is replaced by `LocationSourceListener.onError(error: Throwable)`.
* PosSDK(VPS)
  * `WemapVPSARCoreLocationSource.constructor(context: Context, serviceUrl: String)` is replaced by `VPSARKitLocationSource.constructor(context: Context, mapData: MapData)`. If you continue to use deprecated method, user location projection on the graph will not work.
  * `WemapVPSARCoreLocationSourceListener.onError(error: WemapVPSARCoreLocationSourceError)` will be removed. VPS Location Source errors are now forwarded to `LocationSourceListener.onError(error: Throwable)` if VPS is used on its own, or to `UserLocationManagerListener.onError(error: Throwable)` when used with the `MapSDK`.
* PosSDK(Polestar)
  * `PolestarLocationSource.constructor(context: Context, polestarApiKey: String)` is replaced by `PolestarLocationSource.constructor(context: Context, mapData: MapData, polestarApiKey: String)`. If you continue to use deprecated method, user location projection on the graph will not work.
* PosSDK(Fused-GMS)
  * `GmsFusedLocationSource.constructor(context: Context)` is replaced by `GmsFusedLocationSource.constructor(context: Context, mapData: MapData)`. If you continue to use deprecated method, user location projection on the graph will not work.

### Dependencies

* Plugins
  * Gradle 8.9.0 -> 8.9.1
* Map
  * MapLibre 11.8.2 -> 11.8.4

## [0.20.3]

### Added

* MapSDK: write to logs and throw exception if map property accessed when map is destroyed or not yet loaded

## [0.20.2]

### Changed

* PosSDK(VPS): send images in color by default for VPS

### Fixed

* MapSDK: sometimes level switches too early
* MapSDK: crash on destroy mapView before loading is finished
* Examples(VPS): map view overlapped by surface view even if visibility == INVISIBLE on some devices
* Examples(VPS): ARCore related crash on Samsung S8
* MapSDK: crash on attempt to add the same itinerary twice (as navigation and as itinerary)

### Dependencies

* Plugins
  * Gradle 8.8.0 -> 8.9.0
* Map
  * MapLibre 11.8.1 -> 11.8.2
* VPS
  * ARCore 1.47.0 -> 1.48.0

## [0.20.1]

### Fixed

* CoreSDK: arrived callback is triggered even if user is on another level

### Deprecated

* CoreSDK
  * `WemapCoreSDK.setEnvironment(environment: IEnvironment): Boolean` will be changed to `WemapCoreSDK.setEnvironment(environment: IEnvironment)`
  * `WemapCoreSDK.setItinerariesEnvironment(environment: IEnvironment): Boolean` will be changed to `WemapCoreSDK.setItinerariesEnvironment(environment: IEnvironment)`

### Dependencies

* Map
  * MapLibre 11.8.0 -> 11.8.1

## [0.20.0]

### Added

* PosSDK(VPS): ability to check distance to VPS coverage
  * [`WemapVPSARCoreLocationSource.distanceToVPSCoverageFrom(coordinate: Coordinate): Single<Double>`][1]
* CoreSDK: ability to project user position on the graph
  * Disabled by default. To enable use [`CoreConstants.USER_LOCATION_PROJECTION_ON_GRAPH_ENABLED = true`][2], before `WemapMapView` instance creation.

### Deprecated

* PosSDK(VPS)
  * [`WemapVPSARCoreLocationSource.checkVPSAvailability(location: Location)`][3] changed to [`WemapVPSARCoreLocationSource.isVPSAvailableAt(coordinate: Coordinate)`][4]

### Dependencies

* Plugins
  * Gradle 8.7.3 -> 8.8.0
* Core
  * Serialization 1.7.3 -> 1.8.0
  * KotlinMath 1.5.3 -> 1.6.0
* Map
  * MapLibre 11.7.1 -> 11.8.0
* VPS
  * ARCore 1.46.0 -> 1.47.0

## [0.19.2]

### Added

* Examples: add new sample app to demonstrate VPS LS without Map

### Fixed

* PosSDK(VPS): VPS scan doesn't work on some Xiaomi devices

### Removed

* CoreSDK: unused `ItineraryError` class

## [0.19.1]

### Added

* CoreSDK: add availability to specify the language for navigation instructions
* CoreSDK: add navigation instruction translations to Dutch, German, Portuguese and Russian

### Changed

* SDKs: improve logger and add category, type

### Dependencies

* Plugins
  * Kotlin 2.0.0 -> 2.0.20
* Map
  * MapLibre 11.6.1 -> 11.7.1

## [0.19.0]

### Breaking changes

* CoreSDK
  * Removed `NavigationOptions.StopOptions` data class
    * `NavigationOptions.StopOptions.stopWhenArrivedAtDestination: Boolean` moved to `NavigationOptions.stopWhenArrivedAtDestination: Boolean`
    * `NavigationOptions.StopOptions.stopDistanceThreshold: Float` moved to `NavigationOptions.arrivedDistanceThreshold: Float`
  * `NavigationOptions(stopNavigationOptions: StopOptions, userPositionThreshold: Float, navigationRecalculationTimeInterval: Long)` changed to `NavigationOptions(stopWhenArrivedAtDestination: Boolean, arrivedDistanceThreshold: Float, userPositionThreshold: Float, navigationRecalculationTimeInterval: Long)`

### Added

* CoreSDK: remaining/traveled distance is calculated according to the user's projection constant

### Changed

* PosSDK(VPS): Use jpg images for VPS by default
* PosSDK(VPS): minInclinationAngle default is 65 degrees

### Dependencies

* Plugins
  * Gradle 8.7.1 -> 8.7.3
* Core
  * RxJava 3.1.9 -> 3.1.10
* Map
  * MapLibre 11.5.2 -> 11.6.1

## [0.18.1]

### Fixed

* CoreSDK: handle level change type - incline plane

## [0.18.0]

### Breaking changes

* MapSDK
  * By default it is possible to select only one POI at a time. To enable multiple POIs selection, you have to change `selectionMode` to `MULTIPLE` in `PointOfInterestManager`.
  * `PointOfInterestManager.isSelectionEnabled` changed to `isUserSelectionEnabled`
    * Now this option applies only to the user actions on the map. Programmatically you're still able to select/unselect POIs
  * `MapConstants` properties moved to `CoreConstants`:
    * `ITINERARY_RECALCULATION_ENABLED`
    * `USER_LOCATION_PROJECTION_ON_ITINERARY_ENABLED`
  * `OnActiveLevelChangeListener` and `OnBuildingFocusChangeListener` merged into one `BuildingManagerListener`:
    * `OnActiveLevelChangeListener.onActiveLevelChange(building: Building, level: Level)` changed to `BuildingManagerListener.onActiveLevelChanged(building: Building, level: Level)`
    * `OnBuildingFocusChangeListener.onBuildingFocusChange(building: Building?)` changed to `BuildingManagerListener.onFocusedBuildingChanged(building: Building?)`
  * `WemapMapView` changed:
    * `fun getWemapMapAsync(callback: (mapView: WemapMapView, map: MapLibreMap, data: MapData) -> Unit)` changed to `fun getMapViewAsync(callback: (mapView: WemapMapView, map: MapLibreMap, style: Style, data: MapData) -> Unit)`
    * `val pointOfInterestManager` changed type from `PointOfInterestManager` to `MapPointOfInterestManager`
    * `val navigationManager` changed type from `NavigationManager` to `MapNavigationManager`
      * `isSelectionEnabled` changed to `isUserSelectionEnabled`. Also changed its logic. Previously this property was used to disable all ways to selecting POIs - programmatically and by user clicking on POI on the map.
        Now this property applies only to user actions - if `isUserSelectionEnabled = false` - user will not be able to select POI, but POI can still be selected programmatically.
      * `func startNavigation(origin: Coordinate?, destination: Coordinate, travelMode: TravelMode, options: NavigationOptions, searchOptions: ItinerarySearchOptions, timeout: DispatchTimeInterval) -> Single<Itinerary>` changed to `func startNavigation(origin: Coordinate?, destination: Coordinate, travelMode: TravelMode, options: NavigationOptions, searchOptions: ItinerarySearchOptions, timeout: DispatchTimeInterval) -> Single<Navigation>`
      * `func startNavigation(_ itinerary: Itinerary, options: NavigationOptions, searchOptions: ItinerarySearchOptions) -> Single<Itinerary>` changed to `func startNavigation(_ itinerary: Itinerary, options: NavigationOptions, searchOptions: ItinerarySearchOptions) -> Single<Navigation>`
      * `func stopNavigation() -> Result<Itinerary, NavigationError>` changed to `func stopNavigation() -> Result<Navigation, Error>`
  * `OnWemapMapReadyCallback.onMapLoaded(mapView: WemapMapView, map: MapLibreMap, data: MapData)` changed to `OnWemapMapReadyCallback.onMapViewReady(mapView: WemapMapView, map: MapLibreMap, style: Style, data: MapData)`
  * `BuildingManager` changed:
    * removed:
      * `fun addOnBuildingFocusChangeListener(listener: OnBuildingFocusChangeListener)`
      * `fun removeOnBuildingFocusChangeListener(listener: OnBuildingFocusChangeListener)`
      * `fun addOnActiveLevelChangeListener(listener: OnActiveLevelChangeListener)`
      * `fun removeOnActiveLevelChangeListener(listener: OnActiveLevelChangeListener)`
    * added:
      `fun addListener(listener: BuildingManagerListener): Boolean`
      `fun removeListener(listener: BuildingManagerListener): Boolean`
  * `LocationManager` renamed to `UserLocationManager`
    * `var source: LocationSource` renamed to `var locationSource: LocationSource`
  * Removed `BuildingData`
  * Moved from `WemapMapSDK` to `WemapCoreSDK`:
    * `Category`
    * `Tag`
    * `UseTags`
    * `SimulatorLocationSource`
    * `SimulationOptions`
    * `Extras` moved to `MapData.Extras`
    * `PointOfInterestManager` class changed to interface `IPointOfInterestManager`
      * `fun addPointOfInterestManagerListener(listener: PointOfInterestManagerListener)` changed to `fun addListener(listener: PointOfInterestManagerListener): Boolean`
      * `fun removePointOfInterestManagerListener(listener: PointOfInterestManagerListener)` changed to `fun removeListener(listener: PointOfInterestManagerListener): Boolean`
    * `PointOfInterestManagerListener`
    * `PointOfInterestWithInfo` changed from `Pair<PointOfInterest, ItineraryInfo>` to `Pair<PointOfInterest, ItineraryInfo?>`
    * `NavigationManager` class changed to interface `INavigationManager`
      * `fun addNavigationManagerListener(listener: NavigationManagerListener)` changed to `fun addListener(listener: NavigationManagerListener): Boolean`
      * `fun removeNavigationManagerListener(listener: NavigationManagerListener)` changed to `fun removeListener(listener: NavigationManagerListener): Boolean`
    * `NavigationManagerListener`
      * `fun onArrivedAtDestination(itinerary: Itinerary)` changed to `fun onArrivedAtDestination(navigation: Navigation)`
      * `fun onNavigationRecalculated(itinerary: Itinerary)` changed to `fun onNavigationRecalculated(navigation: Navigation)`
      * `fun onNavigationStarted(itinerary: Itinerary)` changed to `fun onNavigationStarted(navigation: Navigation)`
      * `fun onNavigationStopped(itinerary: Itinerary)` changed to `fun onNavigationStopped(navigation: Navigation)`
    * `NavigationError`
      * `failedToAddItineraryToMap` removed
      * `failedToRemoveItineraryFromMap` renamed to `failedToRemoveNavigation`
    * `NavigationOptions`
      * `val cameraMode: Int?` removed. You can use `mapView.locationManager.cameraMode` instead
      * `val itineraryOptions: ItineraryOptions` removed. Now it should be provided as independent parameter to `MapNavigationManager.startNavigation()`
      * `val renderMode: Int?` removed. You can use `mapView.locationManager.renderMode` instead
      * `val zoomWhileTracking: Double?` removed. You can use `mapView.zoomWhileTracking` instead
* CoreSDK
  * `ServiceFactory`
    * To obtain an instance implementing `IItineraryService` use `ServiceFactory.getItineraryService()` instead of `ServiceFactory.createService(IItineraryService::class.java)`
    * To obtain an instance implementing `IPointOfInterestService` use `ServiceFactory.getPointOfInterestService()` instead of `ServiceFactory.createService(IPointOfInterestService::class.java)`
  * `LocationSourceListener`
    * `fun onLocationChanged(location: Coordinate)` renamed to `fun onCoordinateChanged(coordinate: Coordinate)`
    * `fun onError(error: Error?)` changed to `fun onError(error: Error)`
* PosSDK(VPS)
  * `WemapVPSARCoreLocationSource` changed:
    * `val listeners: MutableSet<WemapVPSARCoreLocationSourceListener>` renamed to `val vpsListeners: MutableSet<WemapVPSARCoreLocationSourceListener>`
    * `fun bind(surfaceView: SurfaceView)` changed to `fun bind(context: Context, surfaceView: SurfaceView)`
    * `ScanReason` renamed to `NotPositioningReason`
    * `State` cases changed accordingly:
      * `SCAN_REQUIRED` renamed to `NOT_POSITIONING`
      * added `DEGRADED_POSITIONING`
      * `NORMAL` renamed to `ACCURATE_POSITIONING`
      * removed `NO_TRACKING`

### Added

* PosSDK(VPS): Enhancement of lifecycle
* CoreSDK: add single PoI selection mode and it is used by default (instead of multiple PoIs selection)
* MapSDK: make camera mode and render mode accessible directly from LocationManager
* MapSDK: make PoIs loaded before returning Map async
* MapSDK: Make the camera zoom when user tracking mode is changed to follow/tracking
* MapSDK: Set userTrackingMode to None when BuildingManager.setLevel is called
* PosSDK(VPS): Trigger the reason of rescan necessary. Ex.: because of conveying detected

### Changed

* CoreSDK: Remove maths duplicates in favour of dev.romainguy.kotlin.math

### Fixed

* CoreSDK: handle sorting by graph distance/duration error in BE response
* MapSDK: getting navigation failed error twice after disposing map view
* Samples: sometimes levels switcher doesn't show levels at startup
* Samples: memory leaks

### Dependencies

* Android SDK 34 -> 35
* Gradle-Wrapper 8.6 -> 8.9
* Plugins
  * Kotlin 1.9.0 -> 2.0.0
  * Gradle 8.4.1 -> 8.7.1
  * Serialization 1.8.20 -> 2.0.0
* Core
  * RxJava 3.1.8 -> 3.1.9
  * Turf 6.0.0 -> 6.0.1
  * Serialization 1.6.3 -> 1.7.3
  * GeoJSON 6.0.0 -> 6.0.1
  * KotlinMath 1.5.3
* Map
  * MapLibre 11.0.0 -> 11.5.2
* Fused-GMS
  * GMS 21.2.0 -> 21.3.0
* VPS
  * ARCore 1.43.0 -> 1.46.0

## [0.17.0]

### Breaking changes

* Due to migration from MapLibre 10.3.1 to [11.0.0][0] additional changes needed:
  * Change package of all classes from `com.mapbox.mapboxsdk` to `org.maplibre.android`. This means you will need to fix your imports.
    > To migrate:
    > In your imports in each of your project files, replace `com.mapbox.mapboxsdk.` with `org.maplibre.android.*`.
  * Rename several classes to no longer contain the word "Mapbox". You will need to migrate by renaming references.
    > To migrate:  
    > Each affected occurrence will be marked as an error during compilation. Replace each occurrence of "Mapbox" with "MapLibre" and let your IDE do the import.
    >
    > These are the most important classes that have been renamed:
    >
    > * `Mapbox` → `MapLibre`
    > * `MapboxMap` → `MapLibreMap`
  * Turf and GeoJson: Change package prefix from `com.mapbox.*` to `org.maplibre.*`.

### Added

* PosSDK: Add "isAvailable" method to LocationSource

### Fixed

* MapSDK: Multi-level itinerary segments are shown for all levels
* PosSDK(VPS): Switch to SCAN_REQUIRED state when user is static in an elevator or escalator in navigation mode

### Dependencies

* Core
  * Turf 5.9.0 -> 6.0.0
  * GeoJson 5.9.0 -> 6.0.0
* Map
  * MapLibre 10.3.1 -> [11.0.0][0]
* Examples
  * PluginAnnotation 2.0.2 -> 3.0.0

## [0.16.1]

### Fixed

* MapSDK: buildings that are not related to the current map have been loaded

## [0.16.0]

### Breaking changes

* `NavigationInstructions` class has been moved from `com.getwemap.sdk.map.navigation.instructions` to `com.getwemap.sdk.core.navigation.instructions`
* `Direction` class has been moved from `com.getwemap.sdk.map.navigation.instructions` to `com.getwemap.sdk.core.navigation.instructions`
* `Step.getNavigationInstructions` has been moved from `com.getwemap.sdk.map.extensions` to `com.getwemap.sdk.core.model.entities`

### Added

* CoreSDK: expose mediaUrl, mediaType of POI

### Changed

* CoreSDK: move Step.getNavigationInstructions to CoreSDK

### Dependencies

* Gradle 8.3.2 -> 8.4.1
* Core-ktx 1.12.0 -> 1.13.1
* Map
  * MapLibre 10.3.0 -> 10.3.1
* VPS
  * ARCore 1.42.0 -> 1.43.0

## [0.15.9]

### Fixed

* CoreSDK: sortPOIsByGraphDistance/Duration doesn't work in some cases

## [0.15.8]

### Added

* CoreSDK: navigation instruction translations to Dutch, German, Portuguese and Russian

## [0.15.7]

### Fixed

* CoreSDK: handle level change type - incline plane

## [0.15.4]

### Fixed

* PosSDK(VPS): Switch to SCAN_REQUIRED state when user is static in an elevator or escalator in navigation mode
* PosSDK(VPS): Change VPS request timeout to 20s

## [0.15.2]

### Fixed

* MapSDK: buildings that are not related to the current map have been loaded

## [0.15.1]

### Fixed

* MapSDK: hideAllPOIs() and showAllPOIs() does not work
* CoreSDK: accept VPS endpoint without '/' at the end

## [0.15.0]

### Breaking changes

* `WemapCoreSDK.name` has been renamed to `WemapCoreSDK.NAME`
* `MapConstants` has been moved from `com.getwemap.sdk.map` to `com.getwemap.sdk.map.helpers`
* `Coordinate.distanceTo(other: Coordinate): Float?` has been changed to `Coordinate.distanceTo(other: Coordinate): Double?`
* `Itinerary.distance: Float` has been changed to `Itinerary.distance: Double`
* `Leg.distance: Float` has been changed to `Leg.distance: Double`
* `LevelChange.direction: Direction` has been changed to `LevelChange.direction: Incline`
* `Projection.distance: Float` has been changed to `Projection.distance: Double`
* `Segment.distance: Float` has been changed to `Segment.distance: Double`
* `Step` has been changed:
  * `val angle: Double` has been changed to `val angle: Float`
  * `val previousBearing: Double` has been changed to `val previousBearing: Float`
  * `val nextBearing: Double` has been changed to `val nextBearing: Float`
  * `val isGate: Boolean` has been moved to `extras.isGate`
  * `val subwayEntranceName: String?` has been moved to `extras.subwayEntranceName`
* `ItinerariesParametersMultipleDestinations` has been renamed to `ItinerariesParametersMultiDestinations`
* `NavigationInfo` has been changed:
  * `val traveledDistance: Float` has been changed to `val traveledDistance: Double`
  * `val remainingDistance: Float` has been changed to `val remainingDistance: Double`
  * `val remainingStepDistance: Float?` has been changed to `val remainingStepDistance: Double?`
* `ItineraryInfo.distance: Float` has been changed to `ItineraryInfo.distance: Double`
* `Destination.coords: Coordinate` has been changed to `Destination.coordinate: Coordinate`
* `ItinerarySearchOptions` has been replaced everywhere from nullable parameter to parameter with a default value

### Added

* PosSDK(VPS): add checkVpsAvailability() method
* MapSDK: offline maps support

### Dependencies

* MapLibre 10.2.0 -> 10.3.0
* Gradle 8.3.1 -> 8.3.2
* Retrofit 2.10.0 -> 2.11.0
* Polestar
  * NAOSDK 4.11.14 -> 4.11.15
* VPS
  * ARCore 1.41.0 -> 1.42.0

## [0.14.4]

### Fixed

* CoreSDK: handle sorting by graph distance/duration error

## [0.14.2]

### Fixed

* MapSDK: centerToPOI fails if POI doesn't have level or there is no building in focus

## [0.14.1]

### Fixed

* MapSDK: allow POIs that are not attached to the building to be shown on the map

### Dependencies

* MapLibre 10.2.0 -> 10.3.0
* Retrofit 2.9.0 -> 2.10.0
* Gradle 8.3.0 -> 8.3.1
* Fused-GMS
  * GMS 21.1.0 -> 21.2.0

## [0.14.0]

### Breaking changes

* `NavigationInfo` and `NavigationInfoHandler` have been moved from `com.getwemap.sdk.map.navigation` to `com.getwemap.sdk.core.navigation`

### Added

* CoreSDK: add optional mapId parameter to ItineraryParameters

### Changed

* CoreSDK: Make NavigationInfoHandler usable without MapSDK

### Dependencies

* Serialization 1.6.2 -> 1.6.3
* Gradle 8.2.2 -> 8.3.0

## [0.13.0]

### Breaking changes

* `WemapVPSARCoreLocationSource.State.LIMITED_CORRECTION` has been removed
* `WemapMapView.onMapViewClickListener` property has been removed
  * `OnMapViewClickListener.onFeatureClick` method has been removed. Use `PointOfInterestManagerListener.onPointOfInterestSelected(poi: PointOfInterest)` or `PointOfInterestManagerListener.onPointOfInterestClicked(poi: PointOfInterest)` instead.
* `WemapMapSDK` has been changed:
  * `setEnvironment(environment: IEnvironment)` has been moved to `WemapCoreSDK.setEnvironment(environment: IEnvironment)`
  * `map(id: Int, token: String): Single<MapData>` has been renamed to `mapData(id: Int, token: String): Single<MapData>`
* `ItinerariesResponse` has been changed:
  * `val from: Coordinate` has been removed
  * `val to: Coordinate` has been removed
  * `val error: String?` has been replaced by `val status: Status`
* `Itinerary` has been changed:
  * `val from: Coordinate` has been renamed to `val origin: Coordinate`
  * `val to: Coordinate` has been renamed to `val destination: Coordinate`
  * `val mode: TravelMode` has been renamed to `val transitMode: TravelMode`
* `ItinerarySearchOptions` has been changed:
  * `val useStairs: Boolean` has been replaced by `val avoidStairs: Boolean`
  * `val useEscalators: Boolean` has been replaced by `val avoidEscalators: Boolean`
  * `val useElevators: Boolean` has been replaced by `val avoidElevators: Boolean`
* `Leg` has been changed:
  * `val from: Destination` has been renamed to `val start: Destination`
  * `val to: Destination` has been renamed to `val end: Destination`
  * `val mode: TravelMode` has been renamed to `val transitMode: TravelMode`
* `TravelMode` has been changed:
  * `WALK` has been changed to `Walk()`
  * `CAR` has been changed to `Car()`
  * `BIKE` has been changed to `Bike(preference: TravelMode.Preference)`
* `ItineraryParameters` has been changed:
  * `val waypoints: List<Coordinate>` has been replaced by `val origin: Coordinate` and `val destination: Coordinate`
  * `val mode: TravelMode` has been renamed to `val travelMode: TravelMode`
  * `val options: ItinerarySearchOptions` has been changed to `val searchOptions: ItinerarySearchOptions?`
* `ItinerariesParametersMultipleDestinations` has been changed:
  * `fromPOIs(origin: Coordinate, destinations: List<PointOfInterest>, mapID: Int, mode: TravelMode, searchOptions: ItinerarySearchOptions)` has been changed to `fromPOIs(origin: Coordinate, destinations: List<PointOfInterest>, mapID: Int, travelMode: TravelMode, searchOptions: ItinerarySearchOptions?)`
  * `fromCoordinates(origin: Coordinate, destinations: List<Coordinate>, mapID: Int?, mode: TravelMode, searchOptions: ItinerarySearchOptions)` has been changed to `fromCoordinates(origin: Coordinate, destinations: List<Coordinate>, mapID: Int?, travelMode: TravelMode, searchOptions: ItinerarySearchOptions?)`
* `ItineraryManager.getItineraries(from: Coordinate, to: Coordinate, mode: TravelMode, searchOptions: ItinerarySearchOptions)` has been changed to `ItineraryManager.getItineraries(origin: Coordinate, destination: Coordinate, travelMode: TravelMode, searchOptions: ItinerarySearchOptions?)`
* `NavigationManager` has been changed:
  * `startNavigation(from: Coordinate, to: Coordinate, options: NavigationOptions, timeout: Long, itinerarySearchOptions: ItinerarySearchOptions)` has been changed to `startNavigation(origin: Coordinate?, destination: Coordinate, travelMode: TravelMode, options: NavigationOptions, searchOptions: ItinerarySearchOptions?, timeout: Long)`
  * `startNavigation(itinerary: Itinerary, options: NavigationOptions, itinerarySearchOptions: ItinerarySearchOptions)` has been changed to  `startNavigation(itinerary: Itinerary, options: NavigationOptions, searchOptions: ItinerarySearchOptions?)`

### Added

* CoreSDK: Add FASTEST, SAFEST, TOURISM preferences for Bike travel mode
* MapSDK: expose pitch from the back office, take into account the initial value
* MapSDK: expose bearing from the back office, take into account the initial value

### Changed

* CoreSDK: migrate to Itineraries API v2
* MapSDK: replace onFeatureClick by onPOIClick

### Dependencies

* Itineraries API v1 -> v2
* Gradle 8.0.2 -> 8.2.2

## [0.12.0]

### Breaking changes

* `PointOfInterest` has been changed:
  * `latitude` has been moved to `coordinate.latitude`
  * `longitude` has been moved to `coordinate.longitude`
  * `levelID` has been moved to `coordinate.levels`
  * `latLng` has been moved to `coordinate.latLng`
* `ItineraryOptions` has been changed:
  * `width` has been moved to `indoorLine.width`
  * `opacity` has been moved to `indoorLine.opacity`
  * `color` has been moved to `ItineraryOptions.indoorLine.color`
  * `projectionOptions.width` has been moved by `projectionLine.width`
  * `projectionOptions.opacity` has been moved by `projectionLine.opacity`
  * `projectionOptions.color` has been moved by `projectionLine.color`
  * `projectionOptions.dashPattern` has been moved by `projectionLine.dashPattern`

### Added

* MapSDK: Add remaining distance to the step in Itinerary info of NavigationInfo
* MapSDK/CoreSDK: Let the possibility to sort PoIs by travel time/distance from UserPosition in a "batch" version
* MapSDK: Let the possibility to the developer to disable/enable PoI selection
* MapSDK: Add the possibility to change the user location icon dynamically
* MapSDK: ability to change color of outdoor part of itinerary

### Changed

* CoreSDK: use coordinate for POI instead of latitude, longitude and levelID

### Fixed

* MapSDK: filterByTag method do the opposite of the desired effect
* MapSDK: SDK version in the (i) button is not at the good version for Android
* MapSDK: building's active level is reset when viewport has significantly changed even if the building is still in focus
* MapSDK: outdoor part of itinerary is visible only when selected level 0
* MapSDK: blue dot greyed outdoor when camera is following the user

### Dependencies

* Polestar
  * NAOSDK 4.11.11 -> 4.11.14
* Fused-GMS
  * GMS 21.0.1 -> 21.1.0

## [0.11.0]

### Breaking changes

* `LocationSource` and `LocationSourceListener` have been moved from `com.getwemap.sdk.core` to `com.getwemap.sdk.core.location`
* `SimulatorLocationSource` has been moved from `com.getwemap.sdk.map.location` to `com.getwemap.sdk.map.location.simulation`
* `polestar`, `fused-gms` and `wemap-vps-arcore` libraries are moved from `com.getwemap.sdk.locationsources:` to `com.getwemap.sdk.positioning:`
  * `GmsFusedLocationSource` has been moved from `com.getwemap.sdk.locationsources` to `com.getwemap.sdk.positioning.fusedgms`
  * `PolestarLocationSource` has been moved from `com.getwemap.sdk.locationsources` to `com.getwemap.sdk.positioning.polestar`
  * `WemapVPSARCore*` classes have been moved from `com.getwemap.sdk.locationsources.vps.*` to `com.getwemap.sdk.positioning.wemapvpsarcore.*`

### Added

* MapSDK: have a method to startNavigation with itineraries as parameter
* Map+PosExample: sample app with WemapVPSARCoreLocationSource

### Changed

* Samples: update icons and names of sample apps
* MapSDK: Refactor (i) button popup

### Dependencies

* ARCore 1.40.0 -> 1.41.0
* LoggingInterceptor 4.11.0 -> 4.12.0
* Serialization 1.6.0 -> 1.6.2

## [0.10.0]

### Added

* PositioningExample: add an example app to demonstrate VPS functionality
* MapSDK: Create filter by tags
* MapSDK: Extend MapData with Extras

### Fixed

* MapSDK: fix automatic level change when CameraMode is not tracking
* MapSDK: automatic level switch on user movements freezes/lags the app

### Dependencies

* RxJava 3.1.7 -> 3.1.8
* ARCore 1.39.0 -> 1.40.0

## [0.9.0]

### Added

* MapSDK: Add helper method to translate step data into textual instructions
* CoreSDK: add itinerary search options for backend
* MapSDK: Take into account heading from LocationSource
* MapSDK: Add MapData.externalId
* MapSDK: Make MapData and its children Parcelable
* MapSDK: Add NavigationManager.isActive
* CoreSDK: Add PointOfInterest.coordinate
* MapSDK: Create a default GPS (fused) LocationSource

### Changed

* MapSDK: improve building selection implementation
* MapSDK: use Polestar recommendation to detect outdoor location
* MapSDK: revert back onFeatureClick listener method
* MapExample: simplify samples

### Fixed

* CoreSDK: fix CompressedCoordinateSerializer for indoor coordinates
* MapSDK: fix when an outdoor PoI is clicked, the event propagation is not stopped
* MapExample: Bluetooth permission is not asked in the sample app
* MapSDK: user position is not projected on stairs
* MapSDK: outdoors, the user's location annotation is displayed in gray

### Deprecated

* MapSDK: `OnMapViewClickListener.onFeatureClick` listener method has been deprecated and will be removed soon. Use `PointOfInterestManagerListener.onPointOfInterestSelected(poi: PointOfInterest)` instead.

### Dependencies

* Gradle 8.1.0 -> 8.0.2
* Core-ktx 1.10.1 -> 1.12.0
* RxJava 3.1.6 -> 3.1.7
* Serialization 1.5.1 -> 1.6.0

## [0.8.1]

### Fixed

* CoreSDK: Fix low frequency position updates (regression appeared in 0.8.0)

## [0.8.0]

We created a `PositioningSDK` to handle multiple sources of positioning. This corresponds to a new transitive dependency. Some positioning systems can be used on the shelf directly in v0.8 by

* `com.getwemap.sdk.locationsources:polestar:0.8.0`
* `com.getwemap.sdk.locationsources:fusedgms:0.8.0`

and used as:

``` kotlin
mapView.locationManager.apply {
    source = PolestarLocationSource(requireContext(), "emulator")
    // source = GmsFusedLocationSource(requireContext())
    isEnabled = true // To start the localization process
}
```

### Breaking changes

* `IndoorLocationProvider` has been renamed to `LocationSource` and moved from `com.getwemap.sdk.map.locationProviders` to `com.getwemap.sdk.core.LocationSource`
* `IndoorLocationProviderListener` has been renamed to `LocationSourceListener` and moved from `com.getwemap.sdk.map.locationProviders` to `com.getwemap.sdk.core.LocationSource`
* `Polestar` `onLocationChanged` should take into account `hasAltitude()` for proper calculation as shown below

    ``` kotlin
    override fun onLocationChanged(location: Location) {

        val coordinate = polestarLocationToCoordinate(location)
        listener?.onLocationChanged(coordinate)
    }

    fun polestarLocationToCoordinate(location: Location) : Coordinate {
        val standardLocation = Location("PoleStar")
        standardLocation.latitude = location.latitude
        standardLocation.longitude = location.longitude
        standardLocation.time = System.currentTimeMillis()

        val verticalAccuracy = location.extras?.getFloat("vertical_accuracy")
        val isOutdoor = !location.hasAltitude() || location.hasAltitude() && verticalAccuracy == -500f

        val altitude = location.altitude
        return Coordinate(standardLocation, if (isOutdoor) emptyList() else listOf((altitude / 5).toFloat()))
    }
    ```

* `OnMapViewClickListener.onFeatureClick(feature: Feature)` is removed in favor of `PointOfInterestManagerListener.onPointOfInterestSelected(poi: PointOfInterest)`.
  To receive events from `PointOfInterestManager` you have to implement interface `PointOfInterestManagerListener` and add it to manager by `mapView.pointOfInterestManager.addPointOfInterestManagerListener()`

### Added

* MapSDK: switch level automatically on selectPOI if shouldCenter is true
* MapSDK: add new events when a POI is selected/unselected

### Fixed

* MapSDK: Stop event did not reach even if remaining distance is less than threshold
* MapSDK: Navigation info is wrong when itinerary contains indoor and outdoor parts
* MapSDK: multi-level itinerary is shown for every levels at the initialization
* MapSDK: click on the PoI symbol (shape) does not select the PoI

### Dependencies

* Kotlin 1.8.21 -> 1.9.0
* Gradle 7.4.2 -> 8.1.0

## [0.7.2]

### Fixed

* CoreSDK: fix pointOfInterestManager.getPOIs()

## [0.7.1]

### Fixed

* MapSDK: user position is not projected on stairs
* MapSDK: outdoors, the user's location annotation is displayed in gray
* MapSDK: Stop event did not reach even if remaining distance is less than threshold
* MapSDK: Navigation info is wrong when itinerary contains indoor and outdoor parts
* MapSDK: multi-level itinerary is shown for every levels at the initialization
* MapSDK: it's possible to remove navigation itinerary using itinerary manager (it should not)

### Dependencies

* Kotlin 1.8.21 -> 1.9.0
* Gradle 7.4.2 -> 8.0.2

[0]: https://github.com/maplibre/maplibre-native/releases/tag/android-v11.0.0
[1]: https://developers.getwemap.com/android-native-sdk/positioning/wemap-vps-arcore/com.getwemap.sdk.positioning.wemapvpsarcore/-wemap-v-p-s-a-r-core-location-source/distance-to-v-p-s-coverage-from
[2]: https://developers.getwemap.com/android-native-sdk/core/com.getwemap.sdk.core/-core-constants/-u-s-e-r_-l-o-c-a-t-i-o-n_-p-r-o-j-e-c-t-i-o-n_-o-n_-g-r-a-p-h_-e-n-a-b-l-e-d
[3]: https://developers.getwemap.com/android-native-sdk/positioning/wemap-vps-arcore/com.getwemap.sdk.positioning.wemapvpsarcore/-wemap-v-p-s-a-r-core-location-source/check-v-p-s-availability
[4]: https://developers.getwemap.com/android-native-sdk/positioning/wemap-vps-arcore/com.getwemap.sdk.positioning.wemapvpsarcore/-wemap-v-p-s-a-r-core-location-source/is-v-p-s-available-at
[5]: https://developers.getwemap.com/android-native-sdk/core/com.getwemap.sdk.core.model.services.parameters/-itinerary-search-options/is-wheelchair
