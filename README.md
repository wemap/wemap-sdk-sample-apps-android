# Wemap SDK Sample apps Android

![Wemap](icon.png)

## Requirements

* AndroidStudio 2021.3.1+

## Installation

* download repository

* open project in `Android Studio`

* specify `accessKey` and `secretKey` in [`settings.gradle`](settings.gradle)

    ``` gradle
    maven {
        url "s3://mobile-dev.getwemap.com/wemap/sdk/android"
        credentials(AwsCredentials) {
            accessKey System.getenv("AWS_ACCESS_KEY_ID") // put your access key here or export it as an env variable
            secretKey System.getenv("AWS_SECRET_ACCESS_KEY") // put your secret key here or export it as an env variable
        }
    }
    ```

* specify `mapID` and `token` and optionally `polestarApiKey` and `vpsEndpoint` in [`common Constants`](./common/src/main/java/com/getwemap/example/common/Constants.kt)

* build and run desired example app

## Examples

* Map

  * Levels - shows how to set custom indoor location provider and switch between levels
  * Points of interests - shows how to perform selection of POIs on the map
  * Navigation - shows how to start navigation to some user-created annotation

* Map+Positioning. Shows how to connect different Location Sources to `WemapMapSDK`.

* Positioning. Shows how to work VPS Location source without `WemapMapSDK`. For example if you want to connect `WemapPositioningSDK/VPSARCore` to your own map.

* Positioning+AR. Shows how to connect different Location Sources to `WemapGeoARSDK`.
