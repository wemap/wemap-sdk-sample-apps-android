# Change Log

---

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
