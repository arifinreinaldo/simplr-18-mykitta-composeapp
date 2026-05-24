package com.simplr.mykitta2.domain

enum class Country(
    val iso: String,
    val wireFormat: String,
    val dialCode: String,
    val apiCountryCode: String,
    val flagEmoji: String,
) {
    PH(iso = "PH", wireFormat = "PHILIPPINE", dialCode = "+63", apiCountryCode = "63", flagEmoji = "🇵🇭"),
    SG(iso = "SG", wireFormat = "SINGAPORE", dialCode = "+65", apiCountryCode = "65", flagEmoji = "🇸🇬");

    companion object {
        fun fromIso(iso: String?): Country? = entries.firstOrNull { it.iso.equals(iso, ignoreCase = true) }
        fun fromWireFormat(wire: String?): Country? = entries.firstOrNull { it.wireFormat.equals(wire, ignoreCase = true) }
    }
}
