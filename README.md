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

* specify `mapID` and `token` and optionally `polestarKey` in [`Map Example`](map/src/main/java/com/getwemap/example/map/Constants.kt)

* specify `vpsEndpoint` in [`Positioning Example`](positioning/src/main/java/com/getwemap/example/positioning/Constants.kt)

* build and run desired example app build variant

## Examples

* MapExample

  * Levels - shows how to set custom indoor location provider and switch between levels
  * Points of interests - shows how to perform selection of POIs on the map
  * Navigation - shows how to start navigation to some user-created annotation

* PositioningExample
