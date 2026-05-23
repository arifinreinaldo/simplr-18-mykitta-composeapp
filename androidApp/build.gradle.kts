import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinx.serialization)
}

// Conditionally apply Google Services + Firebase Crashlytics plugins only when
// at least one google-services.json is present. Lets the project build without
// real Firebase credentials; drop a google-services.json into src/<flavor>/ or
// the module root to enable Crashlytics.
val googleServicesJsonPresent: Boolean = run {
    val candidates = listOf(
        file("google-services.json"),
        file("src/dev/google-services.json"),
        file("src/staging/google-services.json"),
        file("src/prod/google-services.json"),
    )
    candidates.any { it.exists() }
}
if (googleServicesJsonPresent) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}

dependencies {
    implementation(projects.shared)

    implementation(libs.androidx.activity.compose)
    implementation(libs.koin.android)
    implementation(libs.koin.compose)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.analytics)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
}

android {
    namespace = "com.simplr.mykitta2"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.simplr.mykitta2"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    buildFeatures {
        buildConfig = true
    }

    flavorDimensions += "env"
    productFlavors {
        create("dev") {
            dimension = "env"
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            buildConfigField("String", "FLAVOR_NAME", "\"dev\"")
            buildConfigField("String", "BASE_URL", "\"https://dev.mykitta.example/api/\"")
            buildConfigField("String", "APP_NAME", "\"MyKitta Dev\"")
        }
        create("staging") {
            dimension = "env"
            applicationIdSuffix = ".staging"
            versionNameSuffix = "-staging"
            buildConfigField("String", "FLAVOR_NAME", "\"staging\"")
            buildConfigField("String", "BASE_URL", "\"https://staging.mykitta.example/api/\"")
            buildConfigField("String", "APP_NAME", "\"MyKitta Staging\"")
        }
        create("prod") {
            dimension = "env"
            buildConfigField("String", "FLAVOR_NAME", "\"prod\"")
            buildConfigField("String", "BASE_URL", "\"https://api.mykitta.example/api/\"")
            buildConfigField("String", "APP_NAME", "\"MyKitta\"")
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
