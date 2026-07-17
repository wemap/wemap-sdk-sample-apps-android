plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

group = "com.getwemap.example"

val appNamespace = "$group.map.positioning"

android {
    namespace = appNamespace
    compileSdk = 36

    defaultConfig {
        minSdk = 23
        targetSdk = 36
        applicationId = appNamespace

        val commonVersionCode = (rootProject.properties["commonVersionCode"] as? String)?.toInt() ?: 0
        versionCode = commonVersionCode
        versionName = project.version.toString()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // The VPS Local source pulls in OpenCV + LiteRT native libs (all ABIs). Restrict packaging to
        // 64-bit only: arm64-v8a for real devices, x86_64 for emulators (the non-VPS location sources
        // still run there). Drops legacy armeabi-v7a and dead x86, keeping the APK size in check.
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(project(":common"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

    implementation(project(":common-map"))

    implementation(libs.wemap.map)
    implementation(libs.wemap.positioning.vps.arcore)
    implementation(libs.wemap.positioning.vps.local)
    implementation(libs.wemap.positioning.gps)
    implementation(libs.wemap.positioning.fused.gms)

    implementation(libs.maplibre.annotation.v9)

    implementation(libs.androidx.preference.ktx)
}
