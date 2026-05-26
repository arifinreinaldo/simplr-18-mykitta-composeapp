package com.simplr.mykitta2.data.net.dto

import com.simplr.mykitta2.domain.Order
import com.simplr.mykitta2.domain.OrderStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response shape for `POST User/GetObject` with `functionName=GetHistory`.
 *
 * `objectData` is double-wrapped (`List<List<T>>`); legacy GetHistory is the
 * one endpoint that uses BOTH inner lists — headers at index 0 (what we
 * consume), details at index 1 (ignored in the list-only slice).
 *
 * Field names mirror the live backend wire format inferred from
 * `llm_wiki/features/orders.md` + `llm_wiki/deep/repository.md`. **Verify
 * against staging via Chucker before merging** — if names drift, this file is
 * the only one that changes.
 */
@Serializable
data class HistoryServerResponse(
    @SerialName("getObjectResult") val getObjectResult: GetObjectResult<HistoryDto>,
) {
    /** Header rows (the user-visible list). Drops details, which live in
     *  `objectData[1]` and aren't consumed by this slice. */
    fun headers(): List<HistoryDto> =
        getObjectResult.objectData.firstOrNull().orEmpty()

    /** Server's `hasMoreRecords` is `0`/`1`. True when more pages exist. */
    fun hasMore(): Boolean = getObjectResult.hasMoreRecords == 1
}

@Serializable
data class HistoryDto(
    @SerialName("InvNo") val invNo: String,
    @SerialName("InvDate") val invDate: String,
    @SerialName("InvStatus") val invStatus: String,
    @SerialName("CustName") val custName: String,
    @SerialName("Total") val total: Double,
    @SerialName("Currency") val currency: String,
    @SerialName("ItemCount") val itemCount: Int,
    @SerialName("IsCancel") val isCancel: Boolean = false,
) {
    /** Returns null when `invStatus` doesn't map to one of the four
     *  [OrderStatus] values — repository drops these so they don't get
     *  inserted into the cache. */
    fun toDomain(): Order? {
        val status = OrderStatus.fromWire(invStatus) ?: return null
        return Order(
            invNo = invNo,
            invDate = invDate,
            status = status,
            custName = custName,
            total = total,
            currency = currency,
            itemCount = itemCount,
        )
    }
}
