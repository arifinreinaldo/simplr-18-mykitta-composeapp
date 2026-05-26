package com.simplr.mykitta2.data.net.dto

import com.simplr.mykitta2.domain.Order
import com.simplr.mykitta2.domain.OrderStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Response shape for `POST User/GetObject` with `functionName=GetHistory`.
 *
 * The legacy `GetHistory` endpoint is the one read in the catalog that uses
 * BOTH inner lists of the double-wrapped `objectData`:
 *   - `objectData[0]` = invoice header rows ([HistoryDto] — what we consume).
 *   - `objectData[1]` = invoice detail/line rows (different schema; ignored
 *     in the list-only slice).
 *
 * Because the two inner lists have different shapes, we can't reuse the
 * generic [GetObjectResult] envelope — decoding would fail trying to parse
 * detail rows as [HistoryDto]. We type the inner lists as raw [JsonArray]
 * and decode only `objectData[0]` here via [headers].
 *
 * Field names mirror the live backend wire format inferred from
 * `llm_wiki/features/orders.md` + `llm_wiki/deep/repository.md`. **Verify
 * against staging via Chucker before merging** — if names drift, only this
 * file changes.
 */
@Serializable
data class HistoryServerResponse(
    @SerialName("getObjectResult") val getObjectResult: HistoryEnvelope,
) {
    /** Header rows (the user-visible list). Skips `objectData[1]` (details)
     *  which we don't consume in this slice. */
    fun headers(): List<HistoryDto> {
        val first = getObjectResult.objectData.firstOrNull() ?: return emptyList()
        return first.mapNotNull { element ->
            (element as? JsonObject)?.let { historyJson.decodeFromJsonElement(HistoryDto.serializer(), it) }
        }
    }

    /** Server's `hasMoreRecords` is `0`/`1`. True when more pages exist. */
    fun hasMore(): Boolean = getObjectResult.hasMoreRecords == 1
}

@Serializable
data class HistoryEnvelope(
    val errorData: ErrorData = ErrorData(),
    val hasMoreRecords: Int = 0,
    /** Raw inner lists — index 0 is headers, index 1 is details. Detail rows
     *  have a different schema; typing this as [JsonArray] lets the envelope
     *  decode without forcing all rows to fit one DTO shape. */
    val objectData: List<JsonArray> = emptyList(),
)

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

/**
 * File-scoped JSON instance used only to lazy-decode the inner `objectData[0]`
 * array into [HistoryDto]s. Configured to match the codebase's main client
 * config (see `KtorClientFactory`): unknown keys ignored, lenient mode off.
 */
private val historyJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = false
}
