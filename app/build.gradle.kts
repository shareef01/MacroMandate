import java.util.Properties
import java.net.URI

// Loaded once and shared by the signing config and the BuildConfig fields below.
// local.properties is gitignored, so nothing here reaches version control.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.sharek.macromandate"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.sharek.macromandate"
        minSdk = 29
        targetSdk = 37
        versionCode = 5
        versionName = "1.0.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Populated from local.properties (RELEASE_STORE_FILE, RELEASE_STORE_PASSWORD,
    // RELEASE_KEY_ALIAS, RELEASE_KEY_PASSWORD). Left unconfigured on machines that
    // have no keystore, so assembleRelease still succeeds and simply emits an
    // unsigned APK rather than failing the build.
    signingConfigs {
        create("release") {
            val storePath = System.getenv("RELEASE_STORE_FILE")
                ?: localProperties.getProperty("RELEASE_STORE_FILE")
                ?: project.findProperty("RELEASE_STORE_FILE") as? String

            if (!storePath.isNullOrBlank() && file(storePath).exists()) {
                storeFile = file(storePath)
                storePassword = System.getenv("RELEASE_STORE_PASSWORD")
                    ?: localProperties.getProperty("RELEASE_STORE_PASSWORD")
                    ?: project.findProperty("RELEASE_STORE_PASSWORD") as? String
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                    ?: localProperties.getProperty("RELEASE_KEY_ALIAS")
                    ?: project.findProperty("RELEASE_KEY_ALIAS") as? String
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
                    ?: localProperties.getProperty("RELEASE_KEY_PASSWORD")
                    ?: project.findProperty("RELEASE_KEY_PASSWORD") as? String
            }
        }
    }

    buildTypes {
        // Custom OpenAI-compatible endpoints can be configured in local.properties.
        // MANDATE_API_BASE_URL must end with a slash.
        val apiBaseUrl = localProperties.getProperty("MANDATE_API_BASE_URL")
            ?: "https://openrouter.ai/api/"
        val apiHost = runCatching { URI(apiBaseUrl.trim()).host }.getOrNull()
        val defaultModel = if (apiHost.equals("openrouter.ai", ignoreCase = true)) {
            "openrouter/free"
        } else {
            "google/gemma-4-31B-it"
        }
        val modelId = localProperties.getProperty("MANDATE_MODEL_ID")
            ?: defaultModel
        val hfKey = if (apiBaseUrl.trim() == "https://router.huggingface.co/") {
            localProperties.getProperty("HUGGINGFACE_API_KEY").orEmpty()
        } else {
            ""
        }

        debug {
            buildConfigField("String", "HUGGINGFACE_API_KEY", "\"$hfKey\"")
            buildConfigField("String", "MANDATE_API_BASE_URL", "\"$apiBaseUrl\"")
            buildConfigField("String", "MANDATE_MODEL_ID", "\"$modelId\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("String", "HUGGINGFACE_API_KEY", "\"\"")
            buildConfigField("String", "MANDATE_API_BASE_URL", "\"$apiBaseUrl\"")
            buildConfigField("String", "MANDATE_MODEL_ID", "\"$modelId\"")
            signingConfig = signingConfigs.getByName("release").takeIf { it.storeFile != null }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

ksp {
    // Keep historical Room schemas available for migration verification.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    constraints {
        implementation(libs.guava.android) {
            because("CVE-2026-102554 is fixed in Guava 33.7.2-android")
        }
    }
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.play.services.location)
    implementation(libs.coil.compose)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.mlkit.barcode)
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)
    implementation(libs.androidx.room.paging)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    testImplementation(libs.json)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
