package com.simplr.mykitta2.core.env

internal object BuildEnvHolder {
    var flavor: Flavor = Flavor.Dev
    var versionName: String = ""
    var isDebug: Boolean = false
}

actual object BuildEnv {
    actual val flavor: Flavor get() = BuildEnvHolder.flavor
    actual val baseUrl: String get() = FlavorConfig.baseUrl.getValue(flavor)
    actual val appName: String get() = FlavorConfig.appName.getValue(flavor)
    actual val versionName: String get() = BuildEnvHolder.versionName
    actual val isDebug: Boolean get() = BuildEnvHolder.isDebug
}

fun initBuildEnv(flavor: Flavor, versionName: String, isDebug: Boolean) {
    BuildEnvHolder.flavor = flavor
    BuildEnvHolder.versionName = versionName
    BuildEnvHolder.isDebug = isDebug
}
