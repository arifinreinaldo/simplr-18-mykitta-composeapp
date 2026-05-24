package com.simplr.mykitta2.ui.nav

import kotlinx.serialization.Serializable

/**
 * Route arguments stay primitive-typed (String/Int/etc.) so Compose Navigation's
 * NavType derivation works without a custom typeMap. `countryIso` is converted
 * back to a Country via Country.fromIso(...) inside the composable.
 */
sealed interface Destination {
    @Serializable
    data object LoginOtp : Destination

    @Serializable
    data class OtpVerify(
        val phoneE164: String,
        val userIdDigits: String,
        val countryIso: String,
    ) : Destination

    @Serializable
    data object SignedIn : Destination

    @Serializable
    data object Home : Destination
}
