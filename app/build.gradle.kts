plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.traillink.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.traillink.app"
        minSdk = 26 // Android 8.1 Oreo — Vivo 1820 (Y91i) and up
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
        multiDexEnabled = false
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
    }

    packaging {
        resources.excludes.add("META-INF/*")
    }

    splits {
        abi {
            isEnable = false
        }
    }
}

dependencies {
    // Deliberately minimal — no Play Services, no Firebase SDK, no map library.
    // Location comes from the plain android.location API and networking is
    // plain HttpURLConnection, so there is nothing heavy pulling in Google
    // Play Services transitively (a common source of bloat + old-device lag).
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.webkit:webkit:1.11.0")
}
