plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
// Owner-controlled release signing is configured only when all four secrets
// are provided by a trusted runner. No keystore or passwords live in the APK
// source, Gradle repository, or GitHub Actions artifacts.
val releaseStoreFile = System.getenv("ASTRA_SIGNING_STORE_FILE")
val releaseStorePassword = System.getenv("ASTRA_SIGNING_STORE_PASSWORD")
val releaseKeyAlias = System.getenv("ASTRA_SIGNING_KEY_ALIAS")
val releaseKeyPassword = System.getenv("ASTRA_SIGNING_KEY_PASSWORD")
val canSignRelease = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.isNullOrBlank() }

android {
    namespace = "com.astra.agent"
    compileSdk = 35
    defaultConfig { applicationId = "com.astra.agent"; minSdk = 26; targetSdk = 35; versionCode = 9; versionName = "0.9.4" }
    signingConfigs {
        if (canSignRelease) {
            create("astraOwnerRelease") {
                storeFile = file(requireNotNull(releaseStoreFile))
                storePassword = requireNotNull(releaseStorePassword)
                keyAlias = requireNotNull(releaseKeyAlias)
                keyPassword = requireNotNull(releaseKeyPassword)
            }
        }
    }
    buildTypes {
        getByName("release") {
            signingConfigs.findByName("astraOwnerRelease")?.let {
                signingConfig = it
            }
        }
    }
    buildFeatures { compose = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
