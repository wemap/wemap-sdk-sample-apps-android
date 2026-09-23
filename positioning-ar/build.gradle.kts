plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

group = "com.getwemap.example"

val appNamespace = "$group.positioning.ar"

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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        viewBinding = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    implementation(project(":common"))

    implementation(libs.wemap.geoar)
    implementation(libs.wemap.geoar.compose)
    implementation(libs.wemap.positioning.vps.arcore)
    implementation(libs.wemap.positioning.android.fused.adaptive)
    implementation(libs.wemap.positioning.gps)
    implementation(libs.wemap.positioning.fused.gms)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.preference.ktx)
}
