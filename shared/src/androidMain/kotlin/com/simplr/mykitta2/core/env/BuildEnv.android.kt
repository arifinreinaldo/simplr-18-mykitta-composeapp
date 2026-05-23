package com.simplr.mykitta2.core.env

internal object BuildEnvHolder {
    var flavor: Flavor = Flavor.Dev
    var baseUrl: String = ""
    var versionName: String = ""
    var isDebug: Boolean = false
    var appName: String = ""
}

actual object BuildEnv {
    actual val flavor: Flavor get() = BuildEnvHolder.flavor
    actual val baseUrl: String get() = BuildEnvHolder.baseUrl
    actual val versionName: String get() = BuildEnvHolder.versionName
    actual val isDebug: Boolean get() = BuildEnvHolder.isDebug
    actual val appName: String get() = BuildEnvHolder.appName
}

fun initBuildEnv(
    flavor: Flavor,
    baseUrl: String,
    versionName: String,
    isDebug: Boolean,
    appName: String,
) {
    BuildEnvHolder.flavor = flavor
    BuildEnvHolder.baseUrl = baseUrl
    BuildEnvHolder.versionName = versionName
    BuildEnvHolder.isDebug = isDebug
    BuildEnvHolder.appName = appName
}
