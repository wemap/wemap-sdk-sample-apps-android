# Change Log

---

## [0.8.1]

### Fixed

* CoreSDK: Fix low frequency position updates

## [0.8.0]

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
