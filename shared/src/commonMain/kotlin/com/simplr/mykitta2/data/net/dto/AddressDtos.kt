package com.simplr.mykitta2.data.net.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `GetShipmentAddress` (via `User/GetObject`) — the shipment-address read
 * endpoint. Response is the standard double-wrapped envelope; rows live at
 * `objectData[0]`.
 *
 * Wire field names are PascalCase on read (verified against live response on
 * 2026-05-27). This differs from the `User/AddAddress` *write* payload, which
 * uses camelCase per the legacy contract — annoying but real. The two casings
 * stay separate so each side serialises against what the server actually sends
 * / accepts.
 *
 * No `isSelected` on the wire — the backend signals the default address by
 * setting [customerAddressId] to the literal string `"Default"`. The legacy
 * app's "tap a star to set default" was local-only; we read the server's
 * marker but don't surface a setter in v1 (deferred per the plan).
 *
 * Every field is nullable so the parser survives partial / sparse responses
 * (e.g. SG users with blank PH fields, or pre-seed rows missing fields).
 *
 * `CustNo` is included for parsing tolerance but not surfaced — the address
 * is already scoped to the authenticated user.
 */
@Serializable
data class AddressListServerResponse(
    @SerialName("getObjectResult") val getObjectResult: GetObjectResult<AddressDto>,
)

@Serializable
data class AddressDto(
    @SerialName("CustNo") val custNo: String? = null,
    @SerialName("CustomerAddressID") val customerAddressId: String? = null,
    @SerialName("Name") val name: String? = null,
    @SerialName("Address1") val address1: String? = null,
    @SerialName("Address2") val address2: String? = null,
    @SerialName("Zipcode") val zipcode: String? = null,
    @SerialName("City") val city: String? = null,
    @SerialName("Phone") val phone: String? = null,
    @SerialName("Contact") val contact: String? = null,
    @SerialName("Barangay") val barangay: String? = null,
    @SerialName("Province") val province: String? = null,
    @SerialName("Subdivision") val subdivision: String? = null,
)

/**
 * `User/AddAddress` request body — covers both insert and update. Send an
 * empty [customerAddressId] for a new address; populate it to edit an
 * existing row. PH-specific fields ([barangay], [province], [subdivision])
 * stay blank for SG users.
 *
 * Wire field names match the legacy contract verbatim. PH-specific fields
 * are Pascal-cased on the wire (see `@SerialName`).
 */
@Serializable
data class AddressRequest(
    val customerAddressId: String,
    val name: String,
    val address1: String,
    val address2: String,
    val zipcode: String,
    val city: String,
    val phone: String,
    val contact: String,
    @SerialName("Barangay") val barangay: String,
    @SerialName("Province") val province: String,
    @SerialName("Subdivision") val subdivision: String,
)
