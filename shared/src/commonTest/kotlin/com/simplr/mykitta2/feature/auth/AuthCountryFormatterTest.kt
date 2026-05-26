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

    // --- formattedOffsetFor() — raw → display cursor ---
    // Guards the login-screen bug where the caret landed before the last digit
    // whenever formatting injected a space.

    @Test fun phFormattedOffset_emptyStaysAtZero() {
        assertEquals(0, AuthCountryFormatter.formattedOffsetFor(Country.PH, 0))
    }

    @Test fun phFormattedOffset_beforeFirstSpace() {
        // raw "917" cursor=3 → display "917" cursor=3 (no space inserted yet)
        assertEquals(3, AuthCountryFormatter.formattedOffsetFor(Country.PH, 3))
    }

    @Test fun phFormattedOffset_pastFirstSpace() {
        // raw "9171" cursor=4 → display "917 1" cursor=5
        assertEquals(5, AuthCountryFormatter.formattedOffsetFor(Country.PH, 4))
    }

    @Test fun phFormattedOffset_pastSecondSpace() {
        // raw "9171234" cursor=7 → display "917 123 4" cursor=9
        assertEquals(9, AuthCountryFormatter.formattedOffsetFor(Country.PH, 7))
    }

    @Test fun phFormattedOffset_atEnd() {
        // raw "9171234567" cursor=10 → display "917 123 4567" cursor=12
        assertEquals(12, AuthCountryFormatter.formattedOffsetFor(Country.PH, 10))
    }

    @Test fun sgFormattedOffset_pastSpace() {
        // raw "81234" cursor=5 → display "8123 4" cursor=6
        assertEquals(6, AuthCountryFormatter.formattedOffsetFor(Country.SG, 5))
    }

    @Test fun sgFormattedOffset_atEnd() {
        // raw "81234567" cursor=8 → display "8123 4567" cursor=9
        assertEquals(9, AuthCountryFormatter.formattedOffsetFor(Country.SG, 8))
    }

    // --- rawOffsetFor() — display → raw cursor ---

    @Test fun phRawOffset_emptyStaysAtZero() {
        assertEquals(0, AuthCountryFormatter.rawOffsetFor(Country.PH, 0))
    }

    @Test fun phRawOffset_atFirstGroupBoundary() {
        // display "917 ..." cursor=3 → raw cursor=3
        assertEquals(3, AuthCountryFormatter.rawOffsetFor(Country.PH, 3))
    }

    @Test fun phRawOffset_insideFirstSpaceSnapsToGroupEnd() {
        // display "917 1" cursor=4 (inside the space) → raw cursor=3
        assertEquals(3, AuthCountryFormatter.rawOffsetFor(Country.PH, 4))
    }

    @Test fun phRawOffset_pastFirstSpace() {
        // display "917 1" cursor=5 → raw "9171" cursor=4
        assertEquals(4, AuthCountryFormatter.rawOffsetFor(Country.PH, 5))
    }

    @Test fun phRawOffset_atEnd() {
        // display "917 123 4567" cursor=12 → raw cursor=10
        assertEquals(10, AuthCountryFormatter.rawOffsetFor(Country.PH, 12))
    }

    @Test fun sgRawOffset_pastSpace() {
        // display "8123 4" cursor=6 → raw "81234" cursor=5
        assertEquals(5, AuthCountryFormatter.rawOffsetFor(Country.SG, 6))
    }

    @Test fun sgRawOffset_atEnd() {
        // display "8123 4567" cursor=9 → raw "81234567" cursor=8
        assertEquals(8, AuthCountryFormatter.rawOffsetFor(Country.SG, 9))
    }

    // --- inversion property — round-trip must be lossless on group boundaries ---

    @Test fun phRoundTripIsIdentityForEveryRawOffset() {
        for (n in 0..10) {
            val transformed = AuthCountryFormatter.formattedOffsetFor(Country.PH, n)
            assertEquals(n, AuthCountryFormatter.rawOffsetFor(Country.PH, transformed))
        }
    }

    @Test fun sgRoundTripIsIdentityForEveryRawOffset() {
        for (n in 0..8) {
            val transformed = AuthCountryFormatter.formattedOffsetFor(Country.SG, n)
            assertEquals(n, AuthCountryFormatter.rawOffsetFor(Country.SG, transformed))
        }
    }
}
