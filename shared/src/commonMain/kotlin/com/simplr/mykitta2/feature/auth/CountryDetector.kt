package com.simplr.mykitta2.feature.auth

import com.simplr.mykitta2.domain.Country

/**
 * Resolves the device's country, used at first launch to pre-fill the login chip.
 * Platform implementations: [AndroidCountryDetector], [IosCountryDetector] (in
 * `androidMain` / `iosMain`). SAM so tests can pass a lambda:
 *
 * ```
 * val detector = CountryDetector { Country.PH }
 * ```
 */
fun interface CountryDetector {
    suspend fun detect(): Country?
}
