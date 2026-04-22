plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

val appNamespace = "$group.positioning.ar"

android {
    namespace = appNamespace
    compileSdk = 36

    defaultConfig {
        minSdk = 24
        targetSdk = 36
        applicationId = appNamespace

        val commonVersionCode = (rootProject.properties["commonVersionCode"] as? String)?.toInt() ?: 0
        versionCode = commonVersionCode
        versionName = project.version.toString()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

    implementation(libs.wemap.geoar)
    implementation(libs.wemap.positioning.vps.arcore)
    implementation(libs.wemap.positioning.android.fused.adaptive)
    implementation(libs.wemap.positioning.gps)
    implementation(libs.wemap.positioning.fused.gms)
}
