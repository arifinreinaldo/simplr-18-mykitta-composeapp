package com.simplr.mykitta2.data.net.dto

import com.simplr.mykitta2.domain.Notification
import com.simplr.mykitta2.domain.NotificationType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `User/GetObject` response wrapper for `functionName = "GetNotificationData"`.
 *
 * `hasMoreRecords` is intentionally NOT exposed. Legacy contract is unreliable
 * (`Repository.getNotificationList` forces `hasMore=true`); we rely solely on
 * "page returned fewer items than pageSize" as the end-of-list signal.
 */
@Serializable
data class NotificationListServerResponse(
    @SerialName("getObjectResult") val getObjectResult: GetObjectResult<NotificationDto>,
) {
    fun items(): List<NotificationDto> = getObjectResult.objectData.firstOrNull().orEmpty()
}

@Serializable
data class NotificationDto(
    @SerialName("Id") val id: String? = null,
    @SerialName("Title") val title: String? = null,
    @SerialName("Description") val description: String? = null,
    @SerialName("Type") val type: String? = null,
    @SerialName("Payload") val payload: String? = null,
    @SerialName("IsRead") val isRead: Int = 0,
    @SerialName("CreatedAt") val createdAt: String? = null,
) {
    fun toDomain() = Notification(
        id = id.orEmpty(),
        title = title.orEmpty(),
        description = description.orEmpty(),
        type = NotificationType.fromWire(type),
        payload = payload.orEmpty(),
        isRead = isRead == 1,
        createdAt = createdAt.orEmpty(),
    )
}

/**
 * Body for `POST Notification/ReadNotification`. Dedicated endpoint, NOT routed
 * through `User/GetObject`. Field name `NotifID` matches legacy `NotificationRequest`.
 * Notification IDs are sent as strings even though legacy uses Int; the wire field
 * has always been string-formatted (`notifID.toString()`).
 */
@Serializable
data class MarkNotificationReadRequest(
    @SerialName("NotifID") val notifId: String,
)

@Serializable
data class MarkNotificationReadResponse(
    val errorData: ErrorData = ErrorData(),
)
