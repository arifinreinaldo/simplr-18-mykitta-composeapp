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
        val groups = groups(country)
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

    // Cursor mapping helpers for VisualTransformation. The TextField holds raw
    // digits; the display string has spaces injected between groups. These two
    // functions translate cursor offsets across that injection so typing keeps
    // the caret at the end of the last digit instead of landing before it.

    fun formattedOffsetFor(country: Country, rawOffset: Int): Int {
        val groups = groups(country)
        var remaining = rawOffset.coerceAtLeast(0)
        var transformed = 0
        var first = true
        for (size in groups) {
            if (remaining <= 0) return transformed
            if (!first) transformed++ // inserted space
            val taken = minOf(remaining, size)
            transformed += taken
            remaining -= taken
            first = false
        }
        return transformed
    }

    fun rawOffsetFor(country: Country, formattedOffset: Int): Int {
        val groups = groups(country)
        var remaining = formattedOffset.coerceAtLeast(0)
        var raw = 0
        var first = true
        for (size in groups) {
            if (remaining <= 0) return raw
            if (!first) {
                // Consume the inserted space. If the caret sits inside the space
                // (only happens when transformed input is fed back), snap to the
                // raw position before the space.
                remaining--
                if (remaining < 0) return raw
            }
            val taken = minOf(remaining, size)
            raw += taken
            remaining -= taken
            first = false
        }
        return raw
    }

    private fun groups(country: Country): IntArray = when (country) {
        Country.PH -> intArrayOf(3, 3, 4)
        Country.SG -> intArrayOf(4, 4)
    }

    private fun maxDigits(country: Country): Int = when (country) {
        Country.PH -> 10
        Country.SG -> 8
    }
}
