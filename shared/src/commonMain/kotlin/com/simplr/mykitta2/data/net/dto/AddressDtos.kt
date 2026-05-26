package com.simplr.mykitta2.data.net.dto

import com.simplr.mykitta2.domain.Address
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `GetShipmentAddress` (via `User/GetObject`) — the legacy shipment-address
 * read endpoint. Response is the standard double-wrapped envelope; rows live
 * at `objectData[0]`.
 *
 * Wire field names mirror the legacy contract verbatim. PH-specific fields
 * (`Barangay`, `Province`, `Subdivision`) come back Pascal-cased from the
 * backend; lowercase fields stayed lowercase. Every field is nullable so the
 * parser survives partial / sparse responses (e.g. SG users have blank
 * `Barangay`/`Province`/`Subdivision`).
 */
@Serializable
data class AddressListServerResponse(
    @SerialName("getObjectResult") val getObjectResult: GetObjectResult<AddressDto>,
)

@Serializable
data class AddressDto(
    val customerAddressId: String? = null,
    val name: String? = null,
    val address1: String? = null,
    val address2: String? = null,
    val zipcode: String? = null,
    val city: String? = null,
    val phone: String? = null,
    val contact: String? = null,
    @SerialName("Barangay") val barangay: String? = null,
    @SerialName("Province") val province: String? = null,
    @SerialName("Subdivision") val subdivision: String? = null,
    val isSelected: Boolean? = null,
) {
    fun toDomain() = Address(
        customerAddressId = customerAddressId.orEmpty(),
        name = name.orEmpty(),
        address1 = address1.orEmpty(),
        address2 = address2.orEmpty(),
        zipcode = zipcode.orEmpty(),
        city = city.orEmpty(),
        phone = phone.orEmpty(),
        contact = contact.orEmpty(),
        barangay = barangay.orEmpty(),
        province = province.orEmpty(),
        subdivision = subdivision.orEmpty(),
        isSelected = isSelected == true,
    )
}

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
