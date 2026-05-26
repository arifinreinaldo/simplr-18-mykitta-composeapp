package com.simplr.mykitta2.data.net.dto

import com.simplr.mykitta2.domain.NotificationType
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NotificationDtosTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test fun listResponse_parsesItems_andDtoToDomain() {
        // Live wire shape (lowercase + snake_case, Int id, is_read=-1 for unread).
        val payload = """
            {"getObjectResult":{"errorData":{"code":0,"description":""},"hasMoreRecords":1,
             "objectData":[[
               {"id":1487,"title":"MyKitaa Registration Status",
                "description":"Your Registration for URC is approved",
                "type":"Principal",
                "payload":"{\"PrincipalId\":\"9\"}",
                "created_at":"2023-05-02T12:44:01.973","is_read":1,"ts":"AAA"}
             ]]}}
        """.trimIndent()
        val parsed = json.decodeFromString<NotificationListServerResponse>(payload)
        val items = parsed.items()
        assertEquals(1, items.size)
        val domain = items[0].toDomain()
        assertEquals("1487", domain.id)
        assertEquals(NotificationType.PRINCIPAL, domain.type)
        assertEquals(true, domain.isRead)
        assertEquals("2023-05-02T12:44:01.973", domain.createdAt)
    }

    @Test fun listResponse_unreadSentinel_minusOne_isFalse() {
        // `is_read = -1` is the live "unread" sentinel; only `1` means read.
        val payload = """
            {"getObjectResult":{"errorData":{"code":0,"description":""},"hasMoreRecords":0,
             "objectData":[[
               {"id":3152,"title":"Order Notification : Approve",
                "description":"Your order has been approved",
                "type":"Order","payload":"{}","created_at":"2024-05-08T14:40:03.967",
                "is_read":-1,"ts":""}
             ]]}}
        """.trimIndent()
        val items = json.decodeFromString<NotificationListServerResponse>(payload).items()
        val domain = items.single().toDomain()
        assertEquals(false, domain.isRead)
        assertEquals(NotificationType.ORDER, domain.type)
    }

    @Test fun listResponse_emptyObjectData_returnsEmptyList() {
        val payload = """{"getObjectResult":{"errorData":{"code":0,"description":""},
            "hasMoreRecords":0,"objectData":[[]]}}""".trimIndent()
        assertTrue(json.decodeFromString<NotificationListServerResponse>(payload).items().isEmpty())
    }

    @Test fun markReadRequest_serializes_withOnlyNotifIDField() {
        val req = MarkNotificationReadRequest(notifId = "N1")
        val str = json.encodeToString(MarkNotificationReadRequest.serializer(), req)
        assertEquals("""{"NotifID":"N1"}""", str)
    }
}
