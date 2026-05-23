package com.simplr.mykitta2.data.prefs

import com.russhwolf.settings.Settings
import com.simplr.mykitta2.domain.Country

interface CountryStore {
    suspend fun read(): Country?
    suspend fun write(country: Country)
    suspend fun clear()
}

class SettingsCountryStore(private val settings: Settings) : CountryStore {
    override suspend fun read(): Country? =
        settings.getStringOrNull(KEY)?.let(Country::fromWireFormat)

    override suspend fun write(country: Country) {
        settings.putString(KEY, country.wireFormat)
    }

    override suspend fun clear() {
        settings.remove(KEY)
    }

    private companion object {
        const val KEY = "user.country"
    }
}
