plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

group = "com.getwemap.example"

val appNamespace = "$group.map.positioning"

android {
    namespace = appNamespace
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = 24
        targetSdk = libs.versions.targetSdk.get().toInt()
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        viewBinding = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11
    }
}

dependencies {
    implementation(project(":common"))
    implementation(project(":common-map"))

    implementation(libs.wemap.map)
    implementation(libs.wemap.positioning.vps.arcore)
    implementation(libs.wemap.positioning.vps.local)
    implementation(libs.wemap.positioning.gps)
    implementation(libs.wemap.positioning.fused.gms)
    implementation(libs.wemap.positioning.android.fused.adaptive)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.preference.ktx)

    implementation(libs.maplibre.annotation.v9)
}
