package com.simplr.mykitta2.domain

/**
 * A user's saved shipment address. Mirrors the legacy `Address` Room entity
 * minus the `@Parcelize` bits — Kotlin Multiplatform doesn't need them.
 *
 * PH-specific fields ([barangay], [province], [subdivision]) are blank for
 * SG users; the form screen hides them on SG. [isSelected] reflects the
 * server's default-address flag — read-only in v1 (no setter UI yet).
 *
 * [customerAddressId] is the server-assigned primary key. Empty string is the
 * sentinel for "new address" inside [com.simplr.mykitta2.data.net.dto.AddressRequest];
 * domain instances always have a non-empty id (server-assigned post-save).
 */
data class Address(
    val customerAddressId: String,
    val name: String,
    val address1: String,
    val address2: String,
    val zipcode: String,
    val city: String,
    val phone: String,
    val contact: String,
    val barangay: String,
    val province: String,
    val subdivision: String,
    val isSelected: Boolean,
)
