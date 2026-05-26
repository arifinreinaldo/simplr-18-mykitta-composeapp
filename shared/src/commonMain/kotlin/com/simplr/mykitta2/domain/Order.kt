package com.simplr.mykitta2.domain

/**
 * One row in the History list. Pure domain — no SQLDelight, no DTO types.
 *
 * `invDate` is the raw server string (yyyy-MM-dd or whatever the backend emits)
 * — formatting lives at the UI layer so the domain stays UI-agnostic.
 */
data class Order(
    val invNo: String,
    val invDate: String,
    val status: OrderStatus,
    val custName: String,
    val total: Double,
    val currency: String,
    val itemCount: Int,
)
