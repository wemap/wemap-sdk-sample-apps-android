pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://s3.eu-west-1.amazonaws.com/mobile.getwemap.com/releases/android") }
        // Private `alpha` channel on the credential-gated bucket — hosts the offline VPS
        // (wemap-vps-local). Needs read AWS credentials via env vars; unlike the public
        // prod bucket above, this bucket is not anonymously reachable over https.
        maven {
            url = uri("s3://mobile-dev.getwemap.com/alpha/wemap/sdk/android")
            credentials(AwsCredentials::class) {
                accessKey = System.getenv("AWS_ACCESS_KEY_ID") ?: ""
                secretKey = System.getenv("AWS_SECRET_ACCESS_KEY") ?: ""
            }
        }
    }
}

rootProject.name = "WemapSDKSampleApps"

// Examples
include(":common")
include(":common-map")
include(":map")
include(":map-positioning")
include(":positioning-ar")
include(":positioning")
