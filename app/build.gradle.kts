import java.util.Base64


plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val stableDebugKeystore = layout.buildDirectory.file("stable-debug.keystore").get().asFile
val stableDebugKeystoreBase64 = file("debug.keystore.base64")
if (stableDebugKeystoreBase64.exists()) {
    stableDebugKeystore.parentFile.mkdirs()
    stableDebugKeystore.writeBytes(
        Base64.getDecoder().decode(stableDebugKeystoreBase64.readText().trim())
    )
}

android {
    namespace = "pl.footballaipro"
    compileSdk = 35

    defaultConfig {
        applicationId = "pl.footballaipro"
        minSdk = 23
        targetSdk = 35
        versionCode = 4
        versionName = "2.0.3"
    }

    signingConfigs {
        create("stableDebug") {
            storeFile = stableDebugKeystore
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("stableDebug")
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))

    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("io.coil-kt:coil-compose:2.7.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}
