package com.simplr.mykitta2.feature.auth

import com.simplr.mykitta2.domain.Country

object AuthCountryFormatter {

    fun clean(country: Country, raw: String): String {
        val digitsOnly = raw.filter(Char::isDigit)
        val trimmed = when (country) {
            Country.PH -> digitsOnly.trimStart('0')
            Country.SG -> digitsOnly
        }
        val max = maxDigits(country)
        return if (trimmed.length > max) trimmed.take(max) else trimmed
    }

    fun format(country: Country, raw: String): String {
        val digits = clean(country, raw)
        if (digits.isEmpty()) return ""
        val groups = when (country) {
            Country.PH -> intArrayOf(3, 3, 4)
            Country.SG -> intArrayOf(4, 4)
        }
        val sb = StringBuilder()
        var idx = 0
        for ((i, size) in groups.withIndex()) {
            if (idx >= digits.length) break
            if (i > 0) sb.append(' ')
            val end = minOf(idx + size, digits.length)
            sb.append(digits, idx, end)
            idx = end
        }
        return sb.toString()
    }

    fun toE164(country: Country, raw: String): String =
        country.dialCode + clean(country, raw)

    fun isValid(country: Country, raw: String): Boolean =
        clean(country, raw).length == maxDigits(country)

    private fun maxDigits(country: Country): Int = when (country) {
        Country.PH -> 10
        Country.SG -> 8
    }
}
