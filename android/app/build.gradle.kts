plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.astra.agent"
    compileSdk = 36
    defaultConfig {
        // Keep the applicationId unchanged so signed updates retain Astra data.
        applicationId = "com.astra.agent"
        minSdk = 33
        targetSdk = 35
        versionCode = System.getenv("ASTRA_RELEASE_VERSION_CODE")
            ?.takeIf { it.isNotBlank() }
            ?.toIntOrNull()
            ?.also { require(it >= 27) { "Release versionCode must be at least 27." } }
            ?: 27
        versionName = System.getenv("ASTRA_RELEASE_VERSION_NAME")
            ?.takeIf { it.isNotBlank() } ?: "0.12.0"
    }
    // The bundled llama.cpp Android binding supports arm64 (Android 13+).
    defaultConfig { ndk { abiFilters += listOf("arm64-v8a") } }
    androidResources { noCompress += "gguf" }
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
    implementation(project(":llama"))
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

// The full build includes GGUF weights. The lite build contains only Android
// code and native inference; the owner imports a model already on the phone.
// Use a clean source asset directory for lite builds to prevent accidental
// 500 MB uploads disguised as low-data updates.
val bundleModel = System.getenv("ASTRA_BUNDLE_MODEL") != "false"

// Refuse to ship a mislabeled full or lite APK.
val verifyBundledModel by tasks.registering {
    group = "verification"
    doLast {
        val model = file("src/main/assets/qwen3-0.6b-q4km.gguf")
        if (bundleModel) {
            require(model.isFile && model.length() > 450_000_000L) {
                "Full APK requires bundled model. Run python3 scripts/prepare_offline_android.py."
            }
        } else {
            require(!model.exists()) {
                "Lite APK must not contain model weights; remove model asset before building."
            }
        }
    }
}
tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Assets") }
    .configureEach { dependsOn(verifyBundledModel) }
