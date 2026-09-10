import java.util.Properties

// Loaded once and shared by the signing config and the BuildConfig fields below.
// local.properties is gitignored, so nothing here reaches version control.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

fun releaseSecret(name: String): String? =
    providers.environmentVariable(name).orElse(providers.gradleProperty(name)).orNull

val releaseStorePath = releaseSecret("RELEASE_STORE_FILE")
val releaseStorePassword = releaseSecret("RELEASE_STORE_PASSWORD")
val releaseKeyAlias = releaseSecret("RELEASE_KEY_ALIAS")
val releaseKeyPassword = releaseSecret("RELEASE_KEY_PASSWORD")

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
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
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Populated from environment variables or user-level Gradle properties.
    // Left unconfigured on machines that
    // have no keystore, so assembleRelease still succeeds and simply emits an
    // unsigned APK rather than failing the build.
    signingConfigs {
        create("release") {
            if (!releaseStorePath.isNullOrBlank() && file(releaseStorePath).isFile) {
                storeFile = file(releaseStorePath)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        val hfKey = localProperties.getProperty("HUGGINGFACE_API_KEY") ?: ""

        // A string constant in BuildConfig is recoverable from any installed APK
        // with `apktool`/`strings` in seconds — compiling a credential into a
        // build you intend to hand to anyone else publishes it. The debug build
        // keeps the convenience; the release build refuses.
        //
        // Override deliberately with -PallowEmbeddedKey=true if you are building
        // a private release for yourself and accept that the key ships with it.
        val allowEmbeddedKey = (project.findProperty("allowEmbeddedKey") as? String)?.toBoolean() == true
        // Overridable so the app can be pointed at a backend proxy that holds the
        // credential, instead of shipping one inside the APK. Set
        // MANDATE_API_BASE_URL in local.properties; must end with a trailing slash.
        // The legacy api-inference.huggingface.co host no longer resolves.
        val apiBaseUrl = localProperties.getProperty("MANDATE_API_BASE_URL")
            ?: "https://router.huggingface.co/"
        // Vision-capable and served by several providers. Override with
        // MANDATE_MODEL_ID; see https://router.huggingface.co/v1/models
        val modelId = localProperties.getProperty("MANDATE_MODEL_ID")
            ?: "google/gemma-4-31B-it"

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
            // Always empty unless explicitly overridden: see the guard above.
            buildConfigField(
                "String",
                "HUGGINGFACE_API_KEY",
                "\"${if (allowEmbeddedKey) hfKey else ""}\""
            )
            buildConfigField("String", "MANDATE_API_BASE_URL", "\"$apiBaseUrl\"")
            buildConfigField("String", "MANDATE_MODEL_ID", "\"$modelId\"")
            signingConfig = signingConfigs.getByName("release").takeIf { it.storeFile != null }
        }
    }
    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/java")
        }
        getByName("androidTest") {
            assets.srcDirs("$projectDir/schemas")
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
}

val embeddedReleaseKey = localProperties.getProperty("HUGGINGFACE_API_KEY").orEmpty()
val embeddedKeyOverride = providers.gradleProperty("allowEmbeddedKey").orNull?.toBoolean() == true
val productionReleaseRequested = providers.gradleProperty("productionRelease").orNull?.toBoolean() == true

val verifyReleaseConfiguration by tasks.registering {
    group = "verification"
    description = "Rejects credentials that must never be embedded in a distributable build."
    inputs.file(rootProject.file("local.properties")).optional()
    doLast {
        if (embeddedReleaseKey.isNotBlank() && !embeddedKeyOverride) {
            throw GradleException(
                "MM_RELEASE_EMBEDDED_API_KEY_FORBIDDEN: HUGGINGFACE_API_KEY must not be embedded in a release."
            )
        }
    }
}

val verifyProductionSigning by tasks.registering {
    group = "verification"
    description = "Fails unless all production signing credentials are configured."
    dependsOn(verifyReleaseConfiguration)
    doLast {
        val missing = buildList {
            if (releaseStorePath.isNullOrBlank() || !file(releaseStorePath).isFile) add("RELEASE_STORE_FILE")
            if (releaseStorePassword.isNullOrBlank()) add("RELEASE_STORE_PASSWORD")
            if (releaseKeyAlias.isNullOrBlank()) add("RELEASE_KEY_ALIAS")
            if (releaseKeyPassword.isNullOrBlank()) add("RELEASE_KEY_PASSWORD")
        }
        if (missing.isNotEmpty()) {
            throw GradleException("MM_RELEASE_SIGNING_CONFIGURATION_MISSING: ${missing.joinToString()}")
        }
    }
}

tasks.configureEach {
    if (name in setOf("assembleRelease", "bundleRelease", "lintRelease")) {
        dependsOn(verifyReleaseConfiguration)
    }
    if (name == "bundleRelease" && productionReleaseRequested) {
        dependsOn(verifyProductionSigning)
    }
}

ksp {
    // Exported schemas (app/schemas) are what MigrationTest reads to verify that
    // every version upgrade preserves rows. Keep them in version control.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
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
    implementation(libs.androidx.exifinterface)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    testImplementation(libs.json)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
