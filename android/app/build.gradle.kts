plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.astra.agent"
    compileSdk = 35
    defaultConfig {
        // Keep the applicationId unchanged so signed updates retain Astra data.
        applicationId = "com.astra.agent"
        minSdk = 26
        targetSdk = 35
        versionCode = System.getenv("ASTRA_RELEASE_VERSION_CODE")
            ?.takeIf { it.isNotBlank() }
            ?.toIntOrNull()
            ?.also { require(it >= 10) { "Release versionCode must be at least 10." } }
            ?: 10
        versionName = System.getenv("ASTRA_RELEASE_VERSION_NAME")
            ?.takeIf { it.isNotBlank() } ?: "0.9.5"
    }
    // The signing key is injected only by an owner-initiated workflow.
    // PR/debug builds have no access to release credentials.
    val keystorePath = System.getenv("ASTRA_KEYSTORE_PATH")?.takeIf { it.isNotBlank() }
    if (keystorePath != null) {
        fun requiredSecret(name: String): String =
            System.getenv(name)?.takeIf { it.isNotBlank() }
                ?: error("Missing required release signing configuration: $name")
        signingConfigs {
            create("ownerRelease") {
                storeFile = file(keystorePath)
                storePassword = requiredSecret("ASTRA_KEYSTORE_PASSWORD")
                keyAlias = requiredSecret("ASTRA_KEY_ALIAS")
                keyPassword = requiredSecret("ASTRA_KEY_PASSWORD")
            }
        }
        buildTypes {
            getByName("release") {
                signingConfig = signingConfigs.getByName("ownerRelease")
            }
        }
    }
    buildFeatures { compose = true; buildConfig = true }
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
