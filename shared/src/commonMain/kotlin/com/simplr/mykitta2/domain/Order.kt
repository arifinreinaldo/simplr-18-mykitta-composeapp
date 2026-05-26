package com.simplr.mykitta2.domain

/**
 * One row in the History list. Pure domain — no SQLDelight, no DTO types.
 *
 * `invDate` is the raw server string (yyyy-MM-dd or whatever the backend emits)
 * — formatting lives at the UI layer so the domain stays UI-agnostic.
 *
 * `principalName` is the brand the order was placed against (e.g. "COLUMBIA",
 * "SELECTA"). The wire format doesn't carry an outlet/customer name today;
 * the brand is the most user-meaningful subtitle.
 *
 * `currency` is supplied at the repository boundary from CountryStore — the
 * GetHistory wire format doesn't include one because each backend instance
 * is country-scoped.
 *
 * `itemCount` is derived from the line-item array in the response, grouped by
 * `invNo`. Zero means "details list was empty" (legitimate edge case).
 *
 * `firstProduct` previews the first line item (lowest `line` number) so the
 * History card can show a representative product image, name, and qty.
 * Null when the invoice has no detail rows.
 */
data class Order(
    val invNo: String,
    val invDate: String,
    val status: OrderStatus,
    val principalName: String,
    val total: Double,
    val currency: String,
    val itemCount: Int,
    val firstProduct: OrderItemPreview? = null,
)

data class OrderItemPreview(
    val name: String,
    val imageUrl: String,
    val qty: Int,
)
