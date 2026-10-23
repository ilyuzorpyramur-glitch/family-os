plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.familyos.guard"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0"
        buildConfigField("String", "SERVER_HINT", "\"https://herb-rosa-moscow-checking.trycloudflare.com\"")
    }

    // Два приложения из одного кода: детское (зелёное) и родительское (бирюзовое).
    flavorDimensions += "role"
    productFlavors {
        create("child") {
            dimension = "role"
            applicationId = "app.familyos.guard"
            resValue("string", "app_name", "Family OS")
            buildConfigField("String", "ROLE", "\"child\"")
            buildConfigField("String", "ACCENT", "\"#34D399\"")
            buildConfigField("String", "ACCENT_DARK", "\"#10352A\"")
        }
        create("parent") {
            dimension = "role"
            applicationId = "app.familyos.parent"
            resValue("string", "app_name", "Family OS Parent")
            buildConfigField("String", "ROLE", "\"parent\"")
            buildConfigField("String", "ACCENT", "\"#2DD4BF\"")
            buildConfigField("String", "ACCENT_DARK", "\"#0E3B3A\"")
        }
    }

    signingConfigs {
        create("stableDebug") {
            storeFile = rootProject.file("keystore/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("stableDebug")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}
