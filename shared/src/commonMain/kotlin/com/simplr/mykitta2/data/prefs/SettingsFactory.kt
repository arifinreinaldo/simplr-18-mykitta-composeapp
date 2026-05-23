package com.simplr.mykitta2.data.prefs

import com.russhwolf.settings.Settings

expect class SettingsFactory {
    fun secureSettings(name: String): Settings
    fun plainSettings(name: String): Settings
}
