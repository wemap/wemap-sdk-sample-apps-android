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

* Map+Positioning. Shows how to connect different Location Sources to `WemapMapSDK` — VPS ARCore, **VPS Local (offline)**, GPS and Fused.

  ### VPS Local (offline)

  A fully on-device visual positioning source (`WemapPositioningSDK/VPSLocal`) — no network VPS service and no ARCore. It matches the phone camera against a pre-built map database stored on the device, so it works offline once that database has been downloaded.

  #### Setup — values provided by Wemap

  Wemap sends you, for your venue:

  * AWS credentials to access the private Maven channel that hosts the offline VPS library. Export them before building, otherwise `map-positioning` will not resolve its dependencies:

    ```sh
    export AWS_ACCESS_KEY_ID=...
    export AWS_SECRET_ACCESS_KEY=...
    ```

  * The two values to fill into [`VpsLocalMapDownloader`](./map-positioning/src/main/java/com/getwemap/example/map/positioning/VpsLocalMapDownloader.kt), which are placeholders by default:

    * `MAP_ID` — the Wemap map id of your venue
    * `DATASET_BASE_URL` — the URL of your offline map-database files

  Until both are set, the sample throws a descriptive error when you try to download the database.

  #### In the app

  1. Select **VPS Local (offline)** in the Location Source selector. The map id field is prefilled from `MAP_ID`.
  2. Tap **Download** to fetch the offline map database. Progress is shown in the status line; once it reads that the database is ready, the files are on the device (you only need to do this once).
  3. Open the map and point the camera at shops, signs, or other distinctive surroundings to get localized. You get one position fix per successful scan; between fixes the blue dot is carried by dead reckoning (your steps advance the position along the last VPS-anchored heading).

  #### Single-shot vs continuous scan

  The scan overlay has a **"Keep scanning to auto-correct (continuous)"** switch:

  * **On (default)** — after the first fix the overlay hides but the SDK keeps scanning in the background, so every new visual fix re-anchors the position and corrects dead-reckoning drift as you walk.
  * **Off (single-shot)** — scanning stops after the first fix; the position then rides purely on dead reckoning until you open the scanner and localize again.

* Positioning. Shows how to work VPS Location source without `WemapMapSDK`. For example if you want to connect `WemapPositioningSDK/VPSARCore` to your own map.

* Positioning+AR. Shows how to connect different Location Sources to `WemapGeoARSDK`.
