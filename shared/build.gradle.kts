import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinx.serialization)
    alias(libs.plugins.sqldelight)
}

// Read .env at the repo root and generate a Kotlin object with per-flavor
// BASE_URL / APP_NAME constants into commonMain. Fails the build if .env is
// missing or any required key is absent.
abstract class GenerateBuildEnvTask : org.gradle.api.DefaultTask() {
    // Custom existence check is preferable to Gradle's @InputFile validation
    // because we want a friendly error when .env is missing.
    @get:org.gradle.api.tasks.Internal
    abstract val envFile: org.gradle.api.file.RegularFileProperty

    @get:org.gradle.api.tasks.Input
    @get:org.gradle.api.tasks.Optional
    val envFingerprint: String
        get() {
            val f = envFile.get().asFile
            return if (f.exists()) "${f.length()}:${f.lastModified()}" else "missing"
        }

    @get:org.gradle.api.tasks.OutputDirectory
    abstract val outputDir: org.gradle.api.file.DirectoryProperty

    @org.gradle.api.tasks.TaskAction
    fun run() {
        val required = listOf(
            "BASE_URL_DEV", "BASE_URL_STAGING", "BASE_URL_PROD",
            "APP_NAME_DEV", "APP_NAME_STAGING", "APP_NAME_PROD",
        )
        val file = envFile.get().asFile
        if (!file.exists()) {
            throw org.gradle.api.GradleException(
                "Missing .env at ${file.absolutePath}. Copy .env.example and fill in values."
            )
        }
        val entries = file.readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .associate { line ->
                val idx = line.indexOf('=')
                require(idx > 0) { "Invalid .env line (missing '='): $line" }
                line.substring(0, idx).trim() to line.substring(idx + 1).trim()
            }
        val missing = required.filter { entries[it].isNullOrEmpty() }
        if (missing.isNotEmpty()) {
            throw org.gradle.api.GradleException(
                "Missing required key(s) in .env: ${missing.joinToString()}."
            )
        }
        fun esc(s: String): String = s.replace("\\", "\\\\").replace("\"", "\\\"")
        val outFile = outputDir.get().asFile.resolve("com/simplr/mykitta2/core/env/FlavorConfig.kt")
        outFile.parentFile.mkdirs()
        outFile.writeText(
            """
            |// Generated from .env by :shared:generateBuildEnv — do not edit by hand.
            |package com.simplr.mykitta2.core.env
            |
            |internal object FlavorConfig {
            |    val baseUrl: Map<Flavor, String> = mapOf(
            |        Flavor.Dev to "${esc(entries.getValue("BASE_URL_DEV"))}",
            |        Flavor.Staging to "${esc(entries.getValue("BASE_URL_STAGING"))}",
            |        Flavor.Prod to "${esc(entries.getValue("BASE_URL_PROD"))}",
            |    )
            |    val appName: Map<Flavor, String> = mapOf(
            |        Flavor.Dev to "${esc(entries.getValue("APP_NAME_DEV"))}",
            |        Flavor.Staging to "${esc(entries.getValue("APP_NAME_STAGING"))}",
            |        Flavor.Prod to "${esc(entries.getValue("APP_NAME_PROD"))}",
            |    )
            |}
            |""".trimMargin()
        )
    }
}

val generatedBuildEnvDir = layout.buildDirectory.dir("generated/buildenv/commonMain/kotlin")

val generateBuildEnv = tasks.register<GenerateBuildEnvTask>("generateBuildEnv") {
    envFile.set(rootProject.layout.projectDirectory.file(".env"))
    outputDir.set(generatedBuildEnvDir)
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
            export(libs.mvikotlin.core)
        }
    }

    androidLibrary {
        namespace = "com.simplr.mykitta2.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
        androidResources {
            enable = true
        }
        withHostTest {
            isIncludeAndroidResources = true
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.sqldelight.driver.android)
            implementation(libs.androidx.security.crypto)
            implementation(libs.koin.android)
            implementation(libs.kermit.crashlytics)
        }
        commonMain {
            kotlin.srcDir(generateBuildEnv.map { generatedBuildEnvDir })
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.androidx.navigation.compose)

            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)

            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.contentNegotiation)
            implementation(libs.ktor.client.auth)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.serialization.kotlinxJson)

            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)

            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)

            api(libs.mvikotlin.core)
            implementation(libs.mvikotlin.main)
            implementation(libs.mvikotlin.logging)
            implementation(libs.mvikotlin.timetravel)
            implementation(libs.mvikotlin.extensionsCoroutines)

            implementation(libs.multiplatformSettings.core)
            implementation(libs.multiplatformSettings.coroutines)

            implementation(libs.kermit)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
            implementation(libs.koin.test)
            implementation(libs.multiplatformSettings.test)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
            implementation(libs.sqldelight.driver.native)
        }
    }
}

sqldelight {
    databases {
        create("MyKittaDatabase") {
            packageName.set("com.simplr.mykitta2.shared.db")
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}
