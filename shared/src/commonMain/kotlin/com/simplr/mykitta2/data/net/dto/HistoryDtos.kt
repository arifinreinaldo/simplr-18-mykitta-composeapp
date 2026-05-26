package com.simplr.mykitta2.data.net.dto

import com.simplr.mykitta2.domain.Order
import com.simplr.mykitta2.domain.OrderItemPreview
import com.simplr.mykitta2.domain.OrderStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/**
 * Response shape for `POST User/GetObject` with `functionName=GetHistory`.
 *
 * Wire shape verified against live PH backend (May 2026):
 *   - `objectData[0]` = invoice header rows ([HistoryDto]).
 *   - `objectData[1]` = invoice detail/line rows ([HistoryDetailDto]) — used
 *     to derive `itemCount` per invoice (the header doesn't carry one).
 *
 * Numeric fields on the wire are JSON strings (`"23.7"`, `"0"`) — not numbers.
 * We keep them as `String` in the DTO and parse to `Double` only in [toDomain].
 */
@Serializable
data class HistoryServerResponse(
    @SerialName("getObjectResult") val getObjectResult: HistoryEnvelope,
) {
    /** Returns the header rows alongside their per-invoice line-item counts
     *  and a first-line preview (lowest `line` number) used for the card UI.
     *  Currency must be supplied by the caller — the wire format doesn't carry
     *  one because each backend instance is country-scoped. */
    fun rows(currency: String): List<Order> {
        val headers = decodeArray(0, HistoryDto.serializer())
        val details = decodeArray(1, HistoryDetailDto.serializer())
        val itemCountsByInvNo: Map<String, Int> = details.groupingBy { it.invNo }.eachCount()
        // First line per invoice = the detail with the lowest `line` number.
        // If two share the same line we pick the first encountered.
        val firstByInvNo: Map<String, HistoryDetailDto> = details
            .groupBy { it.invNo }
            .mapValues { (_, lines) -> lines.minBy { it.line } }
        return headers.mapNotNull { dto ->
            dto.toDomain(
                currency = currency,
                itemCount = itemCountsByInvNo[dto.invNo] ?: 0,
                firstProduct = firstByInvNo[dto.invNo]?.toPreview(),
            )
        }
    }

    /** Server's `hasMoreRecords` is `0`/`1`. True when more pages exist. */
    fun hasMore(): Boolean = getObjectResult.hasMoreRecords == 1

    private fun <T> decodeArray(
        index: Int,
        serializer: kotlinx.serialization.KSerializer<T>,
    ): List<T> {
        val arr: JsonArray = getObjectResult.objectData.getOrNull(index) ?: return emptyList()
        return arr.mapNotNull { element ->
            (element as? JsonObject)?.let { historyJson.decodeFromJsonElement(serializer, it) }
        }
    }
}

@Serializable
data class HistoryEnvelope(
    val errorData: ErrorData = ErrorData(),
    val hasMoreRecords: Int = 0,
    /** Raw inner lists — index 0 is headers, index 1 is details. Schemas
     *  differ, so we keep them as [JsonArray] and decode per-index. */
    val objectData: List<JsonArray> = emptyList(),
)

@Serializable
data class HistoryDto(
    @SerialName("invNo") val invNo: String,
    @SerialName("invDt") val invDt: String,
    @SerialName("invStatus") val invStatus: String,
    @SerialName("PrincipalName") val principalName: String = "",
    @SerialName("principalId") val principalId: String = "",
    @SerialName("total") val totalText: String = "0",
    @SerialName("isCancel") val isCancel: Boolean = false,
) {
    /** Returns null when `invStatus` doesn't map to one of the four
     *  [OrderStatus] values — repository drops these so they don't get
     *  inserted into the cache. */
    fun toDomain(
        currency: String,
        itemCount: Int,
        firstProduct: OrderItemPreview?,
    ): Order? {
        val status = OrderStatus.fromWire(invStatus) ?: return null
        return Order(
            invNo = invNo,
            invDate = invDt,
            status = status,
            principalName = principalName,
            total = parseAmount(totalText),
            currency = currency,
            itemCount = itemCount,
            firstProduct = firstProduct,
        )
    }
}

@Serializable
data class HistoryDetailDto(
    @SerialName("invNo") val invNo: String,
    @SerialName("line") val line: Int = 0,
    @SerialName("productDesc") val productDesc: String = "",
    @SerialName("qty") val qty: Int = 0,
    @SerialName("productUrl") val productUrl: String = "",
) {
    fun toPreview(): OrderItemPreview = OrderItemPreview(
        name = productDesc,
        imageUrl = productUrl,
        qty = qty,
    )
}

/** Locale-stable parse: backend always uses `.` as decimal separator. */
internal fun parseAmount(text: String): Double = text.toDoubleOrNull() ?: 0.0

/**
 * File-scoped JSON instance used only to decode the inner `objectData[*]`
 * arrays into typed DTOs. Configured to match the codebase's main client
 * config (see `KtorClientFactory`): unknown keys ignored, lenient mode off.
 */
private val historyJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = false
}
