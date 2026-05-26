package com.simplr.mykitta2.data.net.dto

import com.simplr.mykitta2.domain.OrderStatus
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class HistoryDtosTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = false }

    /** Verbatim shape of `User/GetObject` with `functionName=GetHistory`.
     *  Headers in `objectData[0]` (we use these); details in `objectData[1]`
     *  (we ignore these in the list-only slice). */
    private val historyResponseBody = """
        {
          "getObjectResult": {
            "errorData": { "code": 0, "description": "" },
            "hasMoreRecords": 1,
            "objectData": [
              [
                {"InvNo":"INV-001","InvDate":"2026-05-20","InvStatus":"Waiting","CustName":"Outlet A","Total":1200.50,"Currency":"PHP","ItemCount":3,"IsCancel":false},
                {"InvNo":"INV-002","InvDate":"2026-05-19","InvStatus":"Finished","CustName":"Outlet B","Total":750.00,"Currency":"PHP","ItemCount":1}
              ],
              [
                {"InvNo":"INV-001-detail","InvDate":"2026-05-20","InvStatus":"Waiting","CustName":"Outlet A","Total":1200.50,"Currency":"PHP","ItemCount":3,"IsCancel":false}
              ]
            ]
          }
        }
    """.trimIndent()

    @Test fun decodesEnvelopeAndPicksHeaderRows() {
        val response = json.decodeFromString(HistoryServerResponse.serializer(), historyResponseBody)
        val headers = response.headers()
        assertEquals(2, headers.size)
        assertEquals("INV-001", headers[0].invNo)
        assertEquals("Waiting", headers[0].invStatus)
        assertEquals(1200.50, headers[0].total)
        assertEquals("PHP", headers[0].currency)
        assertEquals(3, headers[0].itemCount)
        assertEquals(false, headers[0].isCancel)
        // Missing IsCancel defaults to false (DTO default).
        assertEquals(false, headers[1].isCancel)
    }

    @Test fun toDomainDropsUnknownStatuses() {
        val dto = HistoryDto(
            invNo = "INV-X",
            invDate = "2026-05-20",
            invStatus = "Refunded",   // not in OrderStatus
            custName = "Outlet C",
            total = 99.0,
            currency = "PHP",
            itemCount = 1,
        )
        assertNull(dto.toDomain())
    }

    @Test fun toDomainMapsKnownStatus() {
        val dto = HistoryDto(
            invNo = "INV-1",
            invDate = "2026-05-20",
            invStatus = "Waiting",
            custName = "Outlet A",
            total = 100.0,
            currency = "PHP",
            itemCount = 2,
        )
        val order = dto.toDomain()
        assertNotNull(order)
        assertEquals(OrderStatus.WAITING, order.status)
        assertEquals("INV-1", order.invNo)
    }

    @Test fun headersOnEmptyResponseIsEmpty() {
        val empty = """
            {"getObjectResult":{"errorData":{"code":0,"description":""},"hasMoreRecords":0,"objectData":[]}}
        """.trimIndent()
        val response = json.decodeFromString(HistoryServerResponse.serializer(), empty)
        assertEquals(emptyList(), response.headers())
    }
}
