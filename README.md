# Wemap SDK Sample apps Android

![Wemap](icon.png)

## Requirements

* Android Studio Narwhal 3 Feature Drop (2025.1.3) or newer — the first release that supports the project's
  Android Gradle Plugin 8.13
* JDK 17 to run Gradle

## Installation

* download repository

* open project in `Android Studio`

* specify `mapId` and `token` in [`common Constants`](./common/src/main/java/com/getwemap/example/common/Constants.kt)

* build and run desired example app

## Upgrading to 1.0

**1.0 is a breaking release.** Every sample here is written against the new API, so it doubles as a
worked example of the migration. The three changes you will meet first:

* **Sessions replace the singletons.** `WemapCoreSDK.instance` and `WemapMapSDK.instance` are gone; a
  screen creates a `CoreSession` or `MapSession` with the suspending `create` and passes it to the views and
  location sources that need it. `InitialFragment` in each app shows the creation, and the sharing of one
  session across a screen's view and its location source.
* **`Flow` replaces the listeners.** `PointOfInterestManagerListener`, `LocationSourceListener` and
  `getMapViewAsync` are gone — views report loading through `LoadPhase`, and managers and location sources
  publish events as `Flow` properties collected from a lifecycle-aware coroutine.
* **Immutable configs replace the global constants.** `MapConstants`, `ARConstants` and friends are gone;
  each app's `Config.kt` builds a config at session or view creation time.

The full guide, ordered so each step leaves your project compiling, is part of the
[Wemap SDKs for Android documentation](https://developers.getwemap.com/docs/android-native/getting-started).

## Examples

* Map
  * Levels - Switches indoor levels, outlines a 100 m radius with a style layer, and restores map state across recreation
  * Points of interest - Filters POIs by tag, hides and shows them, selects them, and lists them by distance or travel time
  * Navigation - Navigates between two points long-pressed on the map, reporting progress along the way
  * Map in Compose - The map on its own in Compose — camera state that survives rotation, and the user's position read off the loaded view. The only sample that needs no other Wemap module
  * Custom credits - Overrides the credits bottom sheet, restyles the credits button, and sets up accessibility

* Map+Positioning. Shows how to connect different location sources to the Map SDK (`com.getwemap.sdk:map`) — VPS (ARCore), simulator, system default (Android fused adaptive), GPS, Fused GMS and **VPS Local (offline)**.

  ### VPS Local (offline)

  A fully on-device visual positioning source (`com.getwemap.sdk.positioning:wemap-vps-local`) — no network VPS service and no ARCore. It matches the phone camera against a pre-built map database stored on the device, so it works offline once that database has been downloaded.

  #### Setup — values provided by Wemap

  The library is published to the public Wemap Maven repository with the other SDKs, on a version of its own
  (`wemapVpsLocal` in [`gradle/libs.versions.toml`](./gradle/libs.versions.toml)), so no credentials are needed
  to build.

  Wemap builds the map database for your venue and sends you its map id and dataset URL. Fill them into the
  `DATASETS` list of [`VpsLocalMapDownloader`](./map-positioning/src/main/java/com/getwemap/example/map/positioning/VpsLocalMapDownloader.kt),
  which holds a placeholder by default — one `Dataset` per venue:

  * `mapId` — the Wemap map id of your venue
  * `name` — the label shown in the app
  * `baseUrl` — the URL of your offline map-database files

  Until the placeholder is replaced, the sample reports that no offline VPS dataset is configured and cannot
  download anything.

  #### In the app

  1. Select **VPS Local (offline)** in the Location Source selector, then pick your venue in the dataset selector. The map id field is filled in from it.
  2. Tap **Download** to fetch the offline map database. Progress is shown in the status line; once it reads that the database is ready, the files are on the device (you only need to do this once).
  3. Open the map and point the camera at shops, signs, or other distinctive surroundings to get localized. You get one position fix per successful scan; between fixes the blue dot is carried by dead reckoning (your steps advance the position along the last VPS-anchored heading).

  #### Offline map

  VPS Local needs no network to position, and the map it positions on does not need one either. Switch the
  initial screen from **Online** to **Offline** and tap **Download** to fetch the venue's packdata — its map,
  buildings, points of interest and routing graph in one file, through
  `MapSession.createPackdataService(mapId)`. Offline, **Load Map** creates the session from that file with
  `MapSession.create(context, offlineZip)` instead of the backend, and scan history opens a recorded session
  the same way. Once a packdata is stored, the button checks for a newer one instead. The venue must have a
  packdata published by Wemap; one without it reports so when you tap **Download**.

  #### Single-shot vs continuous scan

  The scan overlay has a **"Keep scanning to auto-correct (continuous)"** switch:

  * **On (default)** — after the first fix the overlay hides but the SDK keeps scanning in the background, so every new visual fix re-anchors the position and corrects dead-reckoning drift as you walk.
  * **Off (single-shot)** — scanning stops after the first fix; the position then rides purely on dead reckoning until you open the scanner and localize again.

* Positioning. Shows how to use the VPS location source (`com.getwemap.sdk.positioning:wemap-vps-arcore`) without the Map SDK — for example to connect it to your own map.

* Positioning+AR. Shows how to connect different location sources to the GeoAR SDK (`com.getwemap.sdk:geo-ar`) — simulator, VPS (ARCore), Android fused adaptive, Fused GMS and GPS — plus an **AR in Compose** sample: the AR scene on its own in Compose, driven by the simulator.
