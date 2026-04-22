plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

val wemapVersion = libs.versions.wemap.get()

allprojects {
    group = "com.getwemap.example"
    version = wemapVersion
}