package com.simplr.mykitta2.data.prefs

import com.russhwolf.settings.KeychainSettings
import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.Settings
import platform.Foundation.NSUserDefaults

actual class SettingsFactory {
    actual fun secureSettings(name: String): Settings = KeychainSettings(service = name)

    actual fun plainSettings(name: String): Settings {
        val defaults = NSUserDefaults(suiteName = name)
        return NSUserDefaultsSettings(defaults)
    }
}
