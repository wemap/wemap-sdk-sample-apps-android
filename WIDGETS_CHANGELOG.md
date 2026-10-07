# Wemap map widgets for Android — Change Log

This log covers `com.getwemap.sdk:map-widgets`, on a `0.x` version line of its own: any release may change
its API. Each release names the Wemap SDKs release it is built against, whose changes are in
[`SDK_CHANGELOG.md`](SDK_CHANGELOG.md). The samples' own changes are in [`CHANGELOG.md`](CHANGELOG.md).

---

## 0.1.0

The first release of the Wemap map widgets for Android: `com.getwemap.sdk:map-widgets:0.1.0`, ready-made map
controls for Jetpack Compose, each with a `View` host for View-based screens. The widgets are on a `0.x` version
line of their own, so any release may change their API — declare the exact version.

### Added

* Widgets(Map): `LevelsSwitcher`, the levels rail, with `LevelsSwitcherView` for View-based screens
  * `rememberLevelsSwitcherState` drives it from a map's `BuildingManager`
  * it marks the level the user is on with a dot, pulsing while their position is being updated and a static
    ring once it goes stale, and says both to TalkBack; pass `null` as `userLocationManager` to leave it unmarked
  * its scroll chevrons appear only when more levels exist than fit, and follow right-to-left layouts
  * `LevelsSwitcherStyle`, including `userIndicatorColor` and `selectedUserIndicatorColor`
* Widgets(Map): `ItineraryForm`, a card that defines an itinerary, computes it and draws it as its two ends
  change, with `ItineraryFormView` for View-based screens
  * `rememberItineraryFormState(mapView, …)` wires the form to a map: it fills the origin from the user's
    position, resolves the level of a point picked on the map, and computes, draws and frames the itinerary —
    on the floor the route starts from
  * `MapPointPicker` and `MapPointPickerPin` pick a point on the map from either end of the form; each chosen
    end stays marked on the map while the form is open, and fades to
    `MapPointPickerStyle.outOfActiveLevelOpacity` while it sits on another level
  * `ItineraryEndpoint` with its `Role`, and a conversion from a computed leg's `Destination`
  * `onFailure` reports an `ItineraryFormFailure` — the error, plus the `retry` that asks the same itinerary
    again when the form can
  * the form works on a map with no location source at all: both ends are picked on the map, and
    *My position* is not offered until the map has reported a fix
  * `ItineraryFormStyle`, `MapPointPickerStyle` and `WidgetTheme.surfaceVariantColor`
* Widgets(Map): `WidgetTheme`, which styles every widget from Wemap design tokens rather than `MaterialTheme`
* Widgets(Map): the widgets ship all eight of the Core SDK's languages — en, fr, de, es, it, nl, pt, ru
* Widgets(Map): getting started and the API reference cover the widgets

### Dependencies

* Wemap SDKs 1.0.0-beta.1 (`com.getwemap.sdk:map`), built and tested against it. The POM requires it as a
  minimum: an app that also declares a newer Wemap SDKs release gets that one, untested with these widgets.
* Compose BOM 2026.06.01
