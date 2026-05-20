# Wemap SDK Sample apps Android

![Wemap](icon.png)

## Requirements

* AndroidStudio 2021.3.1+

## Installation

* download repository

* open project in `Android Studio`

* specify `mapId` and `token` in [`common Constants`](./common/src/main/java/com/getwemap/example/common/Constants.kt)

* build and run desired example app

## Examples

* Map
  * Levels - Shows how to switch between levels and perform POI selection on different levels
  * Points of interests - Shows how to hide/show and select/unselect POIs
  * Navigation - Shows how to start/stop navigation to user-created annotations
  * Custom credits - Shows how to override the attribution (ⓘ) button with a custom accessible credits sheet

* Map+Positioning. Shows how to connect different Location Sources to `WemapMapSDK`.

* Positioning. Shows how to work VPS Location source without `WemapMapSDK`. For example if you want to connect `WemapPositioningSDK/VPSARCore` to your own map.

* Positioning+AR. Shows how to connect different Location Sources to `WemapGeoARSDK`.
