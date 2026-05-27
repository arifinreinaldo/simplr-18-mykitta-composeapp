package com.simplr.mykitta2.feature.address

import com.simplr.mykitta2.domain.Country
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Direct tests for [validate] — pure-Kotlin function, no Store fixture needed.
 * Higher payoff than testing through MVI because validation rules are where
 * users hit friction and where the SG↔PH branch matters most.
 */
class AddressFormValidationTest {

    private val sgFilled = AddressFormStore.FormFields(
        name = "Home", contact = "Juan", phone = "12345678",
        address1 = "1 Orchard Rd", address2 = "",
        city = "Singapore", zipcode = "238888",
    )

    private val phFilled = sgFilled.copy(
        province = "Metro Manila", barangay = "Poblacion", subdivision = "",
    )

    // ---- SG branch ---------------------------------------------------------

    @Test fun sg_allRequiredFilled_isValid() {
        val (errors, valid) = validate(sgFilled, Country.SG)
        assertTrue(valid)
        assertTrue(errors.isEmpty())
    }

    @Test fun sg_blankProvinceAndBarangay_doNotBlockValidity() {
        // SG users never see those fields, so blanks must never fail validation.
        val fields = sgFilled.copy(province = "", barangay = "", subdivision = "")
        val (errors, valid) = validate(fields, Country.SG)
        assertTrue(valid)
        assertNull(errors[AddressFormStore.Field.PROVINCE])
        assertNull(errors[AddressFormStore.Field.BARANGAY])
    }

    @Test fun sg_missingName_isInvalid() {
        val fields = sgFilled.copy(name = "")
        val (errors, valid) = validate(fields, Country.SG)
        assertFalse(valid)
        assertEquals("Required", errors[AddressFormStore.Field.NAME])
    }

    @Test fun sg_missingContact_isInvalid() {
        val fields = sgFilled.copy(contact = "")
        val (errors, valid) = validate(fields, Country.SG)
        assertFalse(valid)
        assertEquals("Required", errors[AddressFormStore.Field.CONTACT])
    }

    @Test fun sg_missingCity_isInvalid() {
        val fields = sgFilled.copy(city = "")
        val (errors, valid) = validate(fields, Country.SG)
        assertFalse(valid)
        assertEquals("Required", errors[AddressFormStore.Field.CITY])
    }

    @Test fun sg_missingZipcode_isInvalid() {
        val fields = sgFilled.copy(zipcode = "")
        val (errors, valid) = validate(fields, Country.SG)
        assertFalse(valid)
        assertEquals("Required", errors[AddressFormStore.Field.ZIPCODE])
    }

    @Test fun sg_missingAddress1_isInvalid() {
        val fields = sgFilled.copy(address1 = "")
        val (errors, valid) = validate(fields, Country.SG)
        assertFalse(valid)
        assertEquals("Required", errors[AddressFormStore.Field.ADDRESS1])
    }

    @Test fun sg_address2Optional_doesNotBlockValidity() {
        val fields = sgFilled.copy(address2 = "")
        val (_, valid) = validate(fields, Country.SG)
        assertTrue(valid)
    }

    // ---- Phone -------------------------------------------------------------

    @Test fun phone_lettersOnly_isInvalid() {
        val fields = sgFilled.copy(phone = "abcdefg")
        val (errors, _) = validate(fields, Country.SG)
        assertEquals("Digits only", errors[AddressFormStore.Field.PHONE])
    }

    @Test fun phone_mixedDigitsAndLetters_isInvalid() {
        val fields = sgFilled.copy(phone = "12345abc")
        val (errors, _) = validate(fields, Country.SG)
        assertEquals("Digits only", errors[AddressFormStore.Field.PHONE])
    }

    @Test fun phone_belowSevenDigits_isInvalid() {
        val fields = sgFilled.copy(phone = "123456")
        val (errors, valid) = validate(fields, Country.SG)
        assertFalse(valid)
        assertEquals("Too short", errors[AddressFormStore.Field.PHONE])
    }

    @Test fun phone_exactlySevenDigits_isValid() {
        val fields = sgFilled.copy(phone = "1234567")
        val (errors, valid) = validate(fields, Country.SG)
        assertTrue(valid)
        assertNull(errors[AddressFormStore.Field.PHONE])
    }

    @Test fun phone_blank_isRequired_notDigitChecked() {
        // Blank phone fails the Required check, NOT the digit-only check.
        val fields = sgFilled.copy(phone = "")
        val (errors, _) = validate(fields, Country.SG)
        assertEquals("Required", errors[AddressFormStore.Field.PHONE])
    }

    // ---- PH branch ---------------------------------------------------------

    @Test fun ph_allRequiredFilled_isValid() {
        val (errors, valid) = validate(phFilled, Country.PH)
        assertTrue(valid)
        assertTrue(errors.isEmpty())
    }

    @Test fun ph_missingProvince_isInvalid() {
        val fields = phFilled.copy(province = "")
        val (errors, valid) = validate(fields, Country.PH)
        assertFalse(valid)
        assertEquals("Required", errors[AddressFormStore.Field.PROVINCE])
    }

    @Test fun ph_missingBarangay_isInvalid() {
        val fields = phFilled.copy(barangay = "")
        val (errors, valid) = validate(fields, Country.PH)
        assertFalse(valid)
        assertEquals("Required", errors[AddressFormStore.Field.BARANGAY])
    }

    @Test fun ph_subdivisionOptional_doesNotBlockValidity() {
        val (_, valid) = validate(phFilled.copy(subdivision = ""), Country.PH)
        assertTrue(valid)
    }

    @Test fun ph_whitespaceOnly_isStillBlank() {
        val fields = phFilled.copy(province = "   ")
        val (errors, _) = validate(fields, Country.PH)
        assertEquals("Required", errors[AddressFormStore.Field.PROVINCE])
    }

    // ---- FormFields.set ----------------------------------------------------

    @Test fun set_updatesEachFieldIndependently() {
        val empty = AddressFormStore.FormFields()
        assertEquals(
            "foo",
            empty.set(AddressFormStore.Field.NAME, "foo").name,
        )
        assertEquals(
            "1 St",
            empty.set(AddressFormStore.Field.ADDRESS1, "1 St").address1,
        )
        assertEquals(
            "BGY",
            empty.set(AddressFormStore.Field.BARANGAY, "BGY").barangay,
        )
        assertEquals(
            "SUB",
            empty.set(AddressFormStore.Field.SUBDIVISION, "SUB").subdivision,
        )
    }
}
