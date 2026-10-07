plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}

// The samples' own release name, CalVer YYYY.M.MICRO — not an SDK version: the SDK pins are in
// gradle/libs.versions.toml, and the change log names both under each release.
val samplesVersion = "2026.10.0"

allprojects {
    group = "com.getwemap.example"
    version = samplesVersion
}