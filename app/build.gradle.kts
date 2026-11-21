import java.util.Properties

plugins {
    id("com.google.devtools.ksp")
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

//read from local properties
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

val placesApiKey = localProperties.getProperty("PLACES_API_KEY", "")


android {
    namespace = "com.example.nextin"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.nextin"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "PLACES_API_KEY", "\"$placesApiKey\"")
    }

    buildTypes { release { isMinifyEnabled = false } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.room:room-runtime:2.7.2")
    ksp("androidx.room:room-compiler:2.7.2")
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose:2.9.0")
    implementation("androidx.core:core-ktx:1.16.0")
    testImplementation("junit:junit:4.13.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")

    implementation("com.google.android.gms:play-services-wearable:19.0.0")

    // Google Code Scanner provides a camera-based QR scanner without an app camera permission.
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")

    // Google loc
    // google places api
    implementation("com.google.android.libraries.places:places:4.2.0")
    //android location services
    implementation("com.google.android.gms:play-services-location:21.3.0")
    //datastore
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    //material comp, needed by sdk
    implementation("com.google.android.material:material:1.12.0")

}


ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}