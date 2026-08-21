import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("com.google.gms.google-services")
}

// Keystore properties
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

android {
    namespace = "com.alhaq.amnshield"
    compileSdk = 36

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    defaultConfig {
        applicationId = "com.alhaq.deenshield"
        minSdk = 26
        targetSdk = 36
        versionCode = 134
        versionName = "1.0.4-closed.04 (2026.08.18)"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // Fix for 16 KB page size devices (Android 15+)
        ndk {
            // Enable 16 KB page size alignment for native libraries
            abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64"))
        }
    }
    
    packaging {
        jniLibs {
            keepDebugSymbols.add("**/lib*.so")
            // Align native libraries to 16 KB for Android 15+ devices
            useLegacyPackaging = false
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-DEBUG"
            isDebuggable = true
            isMinifyEnabled = false
            resValue("string", "app_package_id", "com.alhaq.deenshield.debug")
        }

        create("staging") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".staging"
            versionNameSuffix = "-STAGING"
            matchingFallbacks.add("debug")
            resValue("string", "app_package_id", "com.alhaq.deenshield.staging")
        }
        
        release {
            applicationIdSuffix = ""
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            ndk {
                debugSymbolLevel = "FULL"
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
            resValue("string", "app_package_id", "com.alhaq.deenshield")
        }
    }

    flavorDimensions.add("distribution")
    productFlavors {
        create("playstore") {
            dimension = "distribution"
            buildConfigField("Boolean", "IS_PLAYSTORE", "true")
            buildConfigField("String", "PLAYSTORE_BASE64_PUBLIC_KEY", "\"MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAvXXPe/sroGr8FaVSMilwzOlWc6D7PaszfIjm1s5OAKFhNkqyKIBoMRTe8CRzxAv4uQ8EUsfoO0m41yORhqvGKQ6M7/MaVFrW1xiUvXokWGtyofrTXyDTEASxeuHWcFaeoPyYA6J9NzMTLAFkM/i0ubep1B0fboqndaejWE8t+dN+EjUWoD8aY8OAk95HRzhQyf8aK7GPuyxWunjTDG2KkLCmRazycs4/K1HjSbWmWaGeM4h40WuTlv6Ko75bpguxE0ytPJ0IVow3/a8QEPWAw8oQ3jtSL7xejiib7+cSt3xrtQuigKNtOwqtvqFPF3D1r05yhX47nFqMj/injJQEpwIDAQAB\"")
        }
        create("fdroid") {
            dimension = "distribution"
            buildConfigField("Boolean", "IS_PLAYSTORE", "false")
            buildConfigField("String", "PLAYSTORE_BASE64_PUBLIC_KEY", "\"\"")
        }
        create("universal") {
            dimension = "distribution"
            buildConfigField("Boolean", "IS_PLAYSTORE", "false")
            buildConfigField("String", "PLAYSTORE_BASE64_PUBLIC_KEY", "\"\"")
        }
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/java", "src/sync/java")
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
        aidl = true
        compose = true
        resValues = true
    }
    lint {
        baseline = file("lint-baseline.xml")
        abortOnError = false
    }
}


dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.work.runtime.ktx)

   
    implementation(libs.gson)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("com.google.firebase:firebase-messaging-ktx:24.1.0")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("com.google.zxing:core:3.5.3")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    implementation(libs.mpandroidchart)
    implementation(libs.timerangepicker)
    
    add("playstoreImplementation", libs.play.services.auth)
    add("playstoreImplementation", libs.billing.ktx)
    add("universalImplementation", libs.play.services.auth)
    add("universalImplementation", libs.billing.ktx)

    // Jetpack Compose Dependencies
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.runtime)
    implementation("androidx.compose.material:material-icons-extended:1.7.5")
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
