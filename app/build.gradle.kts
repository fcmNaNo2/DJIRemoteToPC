plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.dji.remotetopc"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.dji.remotetopc"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // DJI App Key
        manifestPlaceholders["DJI_API_KEY"] = "d8384fbc1cc0eb48cb974651"

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
        resources {
            pickFirsts += listOf(
                "META-INF/rxjava.properties",
                "META-INF/LICENSE.md",
                "META-INF/NOTICE.md"
            )
        }
    }
}

dependencies {
    // AndroidX
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")

    // DJI SDK 已移除 - 直接读取 /dev/input/event4

    // Coroutines for async operations
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
