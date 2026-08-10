import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.baxailab.cadebot"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.baxailab.cadebot"
        minSdk = 22
        targetSdk = 22
        versionCode = 2
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += "armeabi-v7a"
        }

        val localProps = Properties()
        rootProject.file("local.properties").takeIf { it.exists() }?.reader()?.use { localProps.load(it) }
        buildConfigField("String", "GROQ_API_KEY", "\"${localProps.getProperty("groq.api.key", "")}\"")
        buildConfigField("String", "CADEBOT_API_URL", "\"${localProps.getProperty("cadebot.api.url", "https://duybao.tdbao-brian.work")}\"")
        buildConfigField("String", "PAYMENT_API_URL", "\"${localProps.getProperty("payment.api.url", "http://localhost:8080")}\"")
    }

    // Pinned to the machine's existing debug keystore (unchanged since before the
    // Cruzr port) so release and debug builds share the exact certificate already
    // installed on Cruzr. Do NOT point this at a freshly generated keystore.
    val cruzrKeystore = file(System.getProperty("user.home") + "/.android/debug.keystore")
    signingConfigs {
        getByName("debug") {
            storeFile = cruzrKeystore
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        create("release") {
            storeFile = cruzrKeystore
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    // targetSdk is deliberately pinned to 22 for Cruzr (Android 5.1.1) compatibility,
    // which trips AGP's Play Store "ExpiredTargetSdkVersion" lintVital gate. This
    // build is not distributed via Play Store, so that check does not apply here.
    lint {
        checkReleaseBuilds = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.compose)

    implementation(libs.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.mockwebserver)
    testImplementation("org.json:json:20231013")
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
