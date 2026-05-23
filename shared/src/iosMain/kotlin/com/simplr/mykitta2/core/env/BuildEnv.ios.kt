package com.simplr.mykitta2.core.env

import platform.Foundation.NSBundle

private fun infoString(key: String): String =
    NSBundle.mainBundle.objectForInfoDictionaryKey(key) as? String ?: ""

actual object BuildEnv {
    actual val flavor: Flavor by lazy {
        when (infoString("MYKITTA_FLAVOR").lowercase()) {
            "staging" -> Flavor.Staging
            "prod" -> Flavor.Prod
            else -> Flavor.Dev
        }
    }
    actual val baseUrl: String get() = FlavorConfig.baseUrl.getValue(flavor)
    actual val appName: String get() = FlavorConfig.appName.getValue(flavor)
    actual val versionName: String by lazy {
        infoString("CFBundleShortVersionString").ifEmpty { "0.0" }
    }
    actual val isDebug: Boolean by lazy {
        infoString("MYKITTA_IS_DEBUG").equals("true", ignoreCase = true)
    }
}
