package com.simplr.mykitta2.feature.auth

import com.simplr.mykitta2.domain.Country
import platform.Foundation.NSLocale
import platform.Foundation.NSLocaleCountryCode
import platform.Foundation.currentLocale

class IosCountryDetector : CountryDetector {
    override suspend fun detect(): Country? {
        val iso = NSLocale.currentLocale.objectForKey(NSLocaleCountryCode) as? String
        return Country.fromIso(iso)
    }
}
