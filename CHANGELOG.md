# Change Log

---

## [0.8.1]

### Fixed

* CoreSDK: Fix low frequency position updates (regression appeared in 0.8.0)

## [0.8.0]

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
