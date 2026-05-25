package com.simplr.mykitta2.data.net.dto

import com.simplr.mykitta2.domain.Banner
import com.simplr.mykitta2.domain.Item
import com.simplr.mykitta2.domain.Principal
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `User/GetObject` is the universal list endpoint. Every catalog read
 * (`GetBanner`, `GetItem`, `GetLastOrder`, `GetPrincipal`, `GetMultiListConfig`,
 * `GetNotificationCount`, …) goes through this same POST URL with the
 * `functionName` field selecting the actual server-side procedure.
 *
 * `user` is the `supervisorCode` from [com.simplr.mykitta2.domain.Session]
 * (with the legacy `"M1"` fallback when no session is loaded).
 * `recordsize` follows the legacy default of 15 unless the caller overrides.
 */
@Serializable
data class GetRequest(
    val functionName: String,
    val offset: Int,
    val recordsize: Int,
    val search: String,
    val sort: String,
    val user: String,
    val ts: String = "",
    @SerialName("CustNo") val custNo: String = "",
    val exclude: String = "",
)

/**
 * Envelope for every `User/GetObject` response.
 *
 * `objectData` is double-wrapped — `List<List<T>>`. Real list always sits at
 * `objectData.firstOrNull().orEmpty()`. Empty outer list means "no data";
 * never assume index [0] exists.
 *
 * `hasMoreRecords` is `0`/`1`, NOT a boolean — legacy contract preserved.
 */
@Serializable
data class GetObjectResult<T>(
    val errorData: ErrorData = ErrorData(),
    val hasMoreRecords: Int = 0,
    val objectData: List<List<T>> = emptyList(),
)

@Serializable
data class ErrorData(
    val code: Int = 0,
    val description: String = "",
)

@Serializable
data class BannerServerResponse(
    @SerialName("getObjectResult") val getObjectResult: GetObjectResult<BannerDto>,
)

@Serializable
data class BannerDto(
    val bannerId: String,
    @SerialName("BannerName") val bannerName: String,
    val bannerImg: String,
    @SerialName("PrincipalId") val principalId: String = "",
    @SerialName("StartDate") val startDate: String = "",
    @SerialName("EndDate") val endDate: String = "",
) {
    fun toDomain() = Banner(
        bannerId = bannerId,
        bannerName = bannerName,
        bannerImg = bannerImg,
        principalId = principalId,
        startDate = startDate,
        endDate = endDate,
    )
}

@Serializable
data class ItemServerResponse(
    @SerialName("getObjectResult") val getObjectResult: GetObjectResult<ItemDto>,
)

@Serializable
data class ItemDto(
    val productId: String,
    val productDesc: String,
    val productLong: String = "",
    val principalId: String,
    val productUrl: String = "",
    val totalOrder: Int? = null,
    val unitPrice: String = "0",
    val basicPrice: String = "0",
    val baseUOM: String = "PCS",
    val salesUOM: String = "PCS",
    @SerialName("InvQty") val invQty: Int = 0,
) {
    fun toDomain() = Item(
        productId = productId,
        productDesc = productDesc,
        productLong = productLong,
        productUrl = productUrl,
        principalId = principalId,
        totalOrder = totalOrder ?: 0,
        basicPrice = basicPrice,
        unitPrice = unitPrice,
        baseUom = baseUOM,
        salesUom = salesUOM,
        invQty = invQty,
    )
}

@Serializable
data class ConfigListResponse(
    @SerialName("getObjectResult") val getObjectResult: GetObjectResult<ConfigDto>,
)

/**
 * One entry per server-driven home rail. `SystemValue` is the `functionName`
 * to call next (e.g. `GetMostBuy`, `GetLastOrder`); `Description` is the
 * user-visible rail title; `DisplayNo` is the desired vertical order.
 */
@Serializable
data class ConfigDto(
    @SerialName("SystemValue") val systemValue: String,
    @SerialName("Description") val description: String,
    @SerialName("DisplayNo") val displayNo: Int = 0,
)

@Serializable
data class NotifCountServerResponse(
    @SerialName("getObjectResult") val getObjectResult: GetObjectResult<NotifCountDto>,
) {
    fun count(): Int = getObjectResult.objectData.firstOrNull()?.firstOrNull()?.count ?: 0
}

@Serializable
data class NotifCountDto(val count: Int)

/**
 * `GetLoyaltyPoints` payload — legacy contract extracts the balance from
 * `objectData[0][0].points`. Missing rows (e.g. customer has no loyalty record
 * yet) collapse to 0 rather than null so the UI can render an unconditional
 * count.
 */
@Serializable
data class LoyaltyPointsServerResponse(
    @SerialName("getObjectResult") val getObjectResult: GetObjectResult<LoyaltyPointsDto>,
) {
    fun points(): Int = getObjectResult.objectData.firstOrNull()?.firstOrNull()?.points ?: 0
}

@Serializable
data class LoyaltyPointsDto(@SerialName("Points") val points: Int = 0)

@Serializable
data class PrincipalServerResponse(
    @SerialName("getObjectResult") val getObjectResult: GetObjectResult<PrincipalDto>,
)

@Serializable
data class PrincipalDto(
    val principalId: String,
    val principalName: String,
    val principalImg: String,
    @SerialName("IsActive") val isActive: Boolean? = false,
    @SerialName("IsProcess") val isProcess: Int? = 2,
) {
    fun toDomain() = Principal(
        principalId = principalId,
        principalName = principalName,
        principalImg = principalImg,
        isActive = isActive == true,
    )
}
