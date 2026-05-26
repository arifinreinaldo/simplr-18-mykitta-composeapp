package com.simplr.mykitta2.data.net.dto

import com.simplr.mykitta2.domain.NotificationType
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NotificationDtosTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test fun listResponse_parsesItems_andDtoToDomain() {
        val payload = """
            {"getObjectResult":{"errorData":{"code":0,"description":""},"hasMoreRecords":1,
             "objectData":[[
               {"Id":"N1","Title":"New brand","Description":"Acme available now",
                "Type":"Principal","Payload":"{\"principalId\":\"P-1\"}",
                "IsRead":0,"CreatedAt":"2026-05-26T14:32:00Z"}
             ]]}}
        """.trimIndent()
        val parsed = json.decodeFromString<NotificationListServerResponse>(payload)
        val items = parsed.items()
        assertEquals(1, items.size)
        val domain = items[0].toDomain()
        assertEquals("N1", domain.id)
        assertEquals(NotificationType.PRINCIPAL, domain.type)
        assertEquals(false, domain.isRead)
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
