package com.simplr.mykitta2.domain

/**
 * Product entry returned by `User/GetObject` with `functionName=GetItem|GetLastOrder|...`.
 *
 * `unitPrice` / `basicPrice` arrive as strings from the backend; kept as strings
 * here to preserve formatting decisions made server-side (currency, decimals).
 * Compose binders convert to Double only at the formatting site.
 */
data class Item(
    val productId: String,
    val productDesc: String,
    val productLong: String,
    val productUrl: String,
    val principalId: String,
    val totalOrder: Int,
    val basicPrice: String,
    val unitPrice: String,
    val baseUom: String,
    val salesUom: String,
    val invQty: Int,
) {
    val isSoldOut: Boolean get() = invQty <= 0
    val displayUom: String get() = salesUom.ifEmpty { baseUom }
}
