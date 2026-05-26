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

    @Serializable
    data object Search : Destination

    /** Drill-down from the My Profile tab's "Profile" menu row. Top-level so
     *  the bottom-nav is hidden while the user is on the detail screen
     *  (matches the Search pattern). */
    @Serializable
    data object ProfileDetail : Destination

    /** Reached from the 🔔 button on Home. Top-level so the bottom-nav is
     *  hidden while the user is on the notification list. */
    @Serializable
    data object Notifications : Destination
}
