package com.simplr.mykitta2.feature.auth

import com.simplr.mykitta2.domain.Country
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AuthCountryFormatterTest {

    // --- clean() ---

    @Test fun phClean_keepsDigitsOnly() {
        assertEquals("9171234567", AuthCountryFormatter.clean(Country.PH, "917 123-4567"))
    }

    @Test fun phClean_stripsLeadingZero() {
        assertEquals("9171234567", AuthCountryFormatter.clean(Country.PH, "09171234567"))
    }

    @Test fun phClean_truncatesToTenDigits() {
        assertEquals("9171234567", AuthCountryFormatter.clean(Country.PH, "91712345670000"))
    }

    @Test fun sgClean_doesNotStripLeadingZero() {
        // Unlike PH, SG numbers don't carry a national-trunk leading zero.
        assertEquals("01234567", AuthCountryFormatter.clean(Country.SG, "01234567"))
    }

    @Test fun sgClean_truncatesToEightDigits() {
        assertEquals("12345678", AuthCountryFormatter.clean(Country.SG, "1234567890"))
    }

    // --- format() ---

    @Test fun phFormat_fullMask() {
        assertEquals("917 123 4567", AuthCountryFormatter.format(Country.PH, "9171234567"))
    }

    @Test fun phFormat_partial() {
        assertEquals("917 12", AuthCountryFormatter.format(Country.PH, "91712"))
    }

    @Test fun phFormat_singleDigit() {
        assertEquals("9", AuthCountryFormatter.format(Country.PH, "9"))
    }

    @Test fun phFormat_empty() {
        assertEquals("", AuthCountryFormatter.format(Country.PH, ""))
    }

    @Test fun sgFormat_fullMask() {
        assertEquals("8123 4567", AuthCountryFormatter.format(Country.SG, "81234567"))
    }

    @Test fun sgFormat_partial() {
        assertEquals("812", AuthCountryFormatter.format(Country.SG, "812"))
    }

    // --- toE164() ---

    @Test fun phToE164_prependsPlus63() {
        assertEquals("+639171234567", AuthCountryFormatter.toE164(Country.PH, "9171234567"))
    }

    @Test fun phToE164_stripsLeadingZeroFirst() {
        assertEquals("+639171234567", AuthCountryFormatter.toE164(Country.PH, "09171234567"))
    }

    @Test fun sgToE164_prependsPlus65() {
        assertEquals("+6581234567", AuthCountryFormatter.toE164(Country.SG, "81234567"))
    }

    // --- isValid() ---

    @Test fun phIsValid_trueAtTenDigits() {
        assertTrue(AuthCountryFormatter.isValid(Country.PH, "9171234567"))
    }

    @Test fun phIsValid_falseAtNineDigits() {
        assertFalse(AuthCountryFormatter.isValid(Country.PH, "917123456"))
    }

    @Test fun phIsValid_trueAfterStrippingLeadingZero() {
        // "09171234567" → strip 0 → 10 digits → valid
        assertTrue(AuthCountryFormatter.isValid(Country.PH, "09171234567"))
    }

    @Test fun sgIsValid_trueAtEightDigits() {
        assertTrue(AuthCountryFormatter.isValid(Country.SG, "81234567"))
    }

    @Test fun sgIsValid_falseAtSevenDigits() {
        assertFalse(AuthCountryFormatter.isValid(Country.SG, "8123456"))
    }

    @Test fun emptyIsNeverValid() {
        assertFalse(AuthCountryFormatter.isValid(Country.PH, ""))
        assertFalse(AuthCountryFormatter.isValid(Country.SG, ""))
    }
}
