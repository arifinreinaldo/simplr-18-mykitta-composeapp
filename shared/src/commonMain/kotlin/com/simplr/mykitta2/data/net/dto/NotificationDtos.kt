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

/**
 * Live wire format is lowercase / snake_case despite legacy's Pascal-case
 * convention elsewhere in the catalog. `id` is an Int on the wire; we coerce to
 * String at the domain boundary so the mark-read endpoint (which takes a string
 * id) can use it directly. `is_read` is `-1` for unread, `1` for read — only
 * `1` counts as read. Other values (`0`, missing) fall through to unread.
 */
@Serializable
data class NotificationDto(
    @SerialName("id") val id: Int? = null,
    @SerialName("title") val title: String? = null,
    @SerialName("description") val description: String? = null,
    @SerialName("type") val type: String? = null,
    @SerialName("payload") val payload: String? = null,
    @SerialName("is_read") val isRead: Int = -1,
    @SerialName("created_at") val createdAt: String? = null,
) {
    fun toDomain() = Notification(
        id = id?.toString().orEmpty(),
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
