package com.simplr.mykitta2.data.net.dto

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Wire-format coverage for the shipment-address DTOs. Guards against accidental
 * field-name drift; the legacy `AddressRequest` constructor had a positional-
 * order trap that we sidestepped by switching to `kotlinx.serialization` names,
 * but the names themselves still matter on the wire.
 */
class AddressDtosTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test fun addressList_deserializesPascalCaseLiveResponse() {
        // Verbatim fixture captured from a live response on 2026-05-27 — the
        // backend uses PascalCase on read (different from the camelCase write
        // payload to `User/AddAddress`). Both Pascal field names and the
        // `CustomerAddressID == "Default"` sentinel matter.
        val body = """
            {"getObjectResult":{"errorData":{"code":0,"description":""},"hasMoreRecords":0,"objectData":[[
                {"CustNo":"1111111111","CustomerAddressID":"","Name":"boj","Address1":"123","Phone":"1230981230","Province":"123","Barangay":"123","Subdivision":"123","City":"213","Zipcode":"123"},
                {"CustNo":"1111111111","CustomerAddressID":"Default","Name":"Demo person","Address1":"Demo Address","Phone":"1111111111","Province":"La Union","Barangay":"Bucayab","Subdivision":"Demo sub division","City":"Bauang","Zipcode":"645464"}
            ]]}}
        """.trimIndent()
        val response = json.decodeFromString<AddressListServerResponse>(body)
        val rows = response.getObjectResult.objectData.firstOrNull().orEmpty()
        assertEquals(2, rows.size)

        val anchor = rows[0]
        assertEquals("", anchor.customerAddressId)
        assertEquals("boj", anchor.name)
        assertEquals("123", anchor.address1)
        assertEquals("1230981230", anchor.phone)
        assertEquals("123", anchor.province)
        assertEquals("123", anchor.barangay)
        assertEquals("123", anchor.subdivision)
        assertEquals("213", anchor.city)
        assertEquals("123", anchor.zipcode)

        val default = rows[1]
        assertEquals("Default", default.customerAddressId)
        assertEquals("Demo person", default.name)
        assertEquals("La Union", default.province)
        assertEquals("Bucayab", default.barangay)
    }

    @Test fun addressList_emptyOuter_isEmpty() {
        val body = """{"getObjectResult":{"errorData":{"code":0,"description":""},"hasMoreRecords":0,"objectData":[]}}"""
        val response = json.decodeFromString<AddressListServerResponse>(body)
        assertEquals(emptyList(), response.getObjectResult.objectData.firstOrNull().orEmpty())
    }

    @Test fun addressList_missingOptionalFields_areNull() {
        // Backend can omit a field entirely (Contact and Address2 don't appear
        // on the anchor row in the live response). DTO must absorb without
        // throwing — every wire field is nullable for that reason.
        val body = """
            {"getObjectResult":{"errorData":{"code":0,"description":""},"hasMoreRecords":0,"objectData":[[
                {"CustomerAddressID":"A-1","Name":"Home"}
            ]]}}
        """.trimIndent()
        val response = json.decodeFromString<AddressListServerResponse>(body)
        val a1 = response.getObjectResult.objectData.first().first()
        assertEquals("A-1", a1.customerAddressId)
        assertEquals("Home", a1.name)
        assertEquals(null, a1.address1)
        assertEquals(null, a1.address2)
        assertEquals(null, a1.contact)
    }

    @Test fun addressRequest_serializesWithExactWireFieldNames() {
        val request = AddressRequest(
            customerAddressId = "A-1",
            name = "Home",
            address1 = "1 Main St",
            address2 = "#2",
            zipcode = "1000",
            city = "Manila",
            phone = "0900",
            contact = "Juan",
            barangay = "Poblacion",
            province = "NCR",
            subdivision = "Block A",
        )
        val encoded = json.encodeToString(AddressRequest.serializer(), request)
        // Lowercase fields stay lowercase; PH fields are Pascal-cased per the
        // legacy contract. Loss of these prefixes would silently break PH
        // address parsing on the server.
        assertTrue(encoded.contains("\"customerAddressId\":\"A-1\""), encoded)
        assertTrue(encoded.contains("\"name\":\"Home\""), encoded)
        assertTrue(encoded.contains("\"address1\":\"1 Main St\""), encoded)
        assertTrue(encoded.contains("\"address2\":\"#2\""), encoded)
        assertTrue(encoded.contains("\"zipcode\":\"1000\""), encoded)
        assertTrue(encoded.contains("\"city\":\"Manila\""), encoded)
        assertTrue(encoded.contains("\"phone\":\"0900\""), encoded)
        assertTrue(encoded.contains("\"contact\":\"Juan\""), encoded)
        assertTrue(encoded.contains("\"Barangay\":\"Poblacion\""), encoded)
        assertTrue(encoded.contains("\"Province\":\"NCR\""), encoded)
        assertTrue(encoded.contains("\"Subdivision\":\"Block A\""), encoded)
    }

    @Test fun addressRequest_emptyIdSerializes_asCreateSentinel() {
        // Empty `customerAddressId` is how the backend distinguishes
        // create from update — must round-trip as "" not null/omitted.
        val request = AddressRequest(
            customerAddressId = "",
            name = "x", address1 = "y", address2 = "", zipcode = "z",
            city = "z", phone = "1234567", contact = "z",
            barangay = "", province = "", subdivision = "",
        )
        val encoded = json.encodeToString(AddressRequest.serializer(), request)
        assertTrue(encoded.contains("\"customerAddressId\":\"\""), encoded)
    }

    @Test fun messageServerResponse_deserializesSparseBody() {
        val body = """{"resultCode":0,"resultMsg":"ok"}"""
        val response = json.decodeFromString<MessageServerResponse>(body)
        assertEquals(0, response.resultCode)
        assertEquals("ok", response.resultMsg)
    }

    @Test fun messageServerResponse_missingFields_areTolerated() {
        val body = """{}"""
        val response = json.decodeFromString<MessageServerResponse>(body)
        assertEquals(null, response.resultCode)
        assertEquals(null, response.resultMsg)
    }
}
