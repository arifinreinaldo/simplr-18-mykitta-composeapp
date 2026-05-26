package com.simplr.mykitta2.data.net.dto

import com.simplr.mykitta2.domain.OrderStatus
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class HistoryDtosTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = false }

    /** Verbatim sample from live PH backend (May 2026). Numeric fields arrive
     *  as JSON strings; row schema is lowercase except `PrincipalName`. The
     *  `itemCount` per invoice comes from `objectData[1]`, not the header. */
    private val historyResponseBody = """
        {
          "getObjectResult": {
            "errorData": { "code": 0, "description": "" },
            "hasMoreRecords": 1,
            "objectData": [
              [
                {"isCancel":false,"invNo":"INV/10/240502/000001","invDt":"2024-05-02","principalId":"10","PrincipalName":"COLUMBIA","subTotal":"23.7","discount":"0","gst":"2.53","total":"23.7","paid":0,"shipAdd":"Default","shipCity":"Makati","shipZip":"200","invStatus":"Waiting","CustId":"9995045287"},
                {"isCancel":false,"invNo":"INV/35/251010/000001","invDt":"2025-10-10","principalId":"35","PrincipalName":"SELECTA","subTotal":"784","discount":"0","gst":"84","total":"784","paid":0,"shipAdd":"Default","shipCity":"Makati","shipZip":"200","invStatus":"Waiting","CustId":"9995045287"}
              ],
              [
                {"invNo":"INV/10/240502/000001","line":1,"qty":1,"productId":"848","productDesc":"AMERICAN GUMBALL","productUrl":"https://x/g.png"},
                {"invNo":"INV/35/251010/000001","line":2,"qty":8,"productId":"P-B","productDesc":"P10 ICE POP","productUrl":"https://x/b.png"},
                {"invNo":"INV/35/251010/000001","line":1,"qty":3,"productId":"P-A","productDesc":"P10 CHOCKY STICK","productUrl":"https://x/a.png"}
              ]
            ]
          }
        }
    """.trimIndent()

    @Test fun decodesEnvelopeAndJoinsHeaderRowsWithItemCounts() {
        val response = json.decodeFromString(HistoryServerResponse.serializer(), historyResponseBody)
        val rows = response.rows(currency = "PHP")
        assertEquals(2, rows.size)
        assertEquals("INV/10/240502/000001", rows[0].invNo)
        assertEquals("2024-05-02", rows[0].invDate)
        assertEquals(OrderStatus.WAITING, rows[0].status)
        assertEquals("COLUMBIA", rows[0].principalName)
        assertEquals(23.7, rows[0].total)
        assertEquals("PHP", rows[0].currency)
        assertEquals(1, rows[0].itemCount)
        // SELECTA invoice has two detail lines -> itemCount == 2.
        assertEquals(2, rows[1].itemCount)
        assertEquals("SELECTA", rows[1].principalName)

        // First-line preview: COLUMBIA's only line.
        assertEquals("AMERICAN GUMBALL", rows[0].firstProduct?.name)
        assertEquals(1, rows[0].firstProduct?.qty)
        // SELECTA's two detail rows arrive out of order; the lowest `line` wins.
        assertEquals("P10 CHOCKY STICK", rows[1].firstProduct?.name)
        assertEquals(3, rows[1].firstProduct?.qty)
    }

    @Test fun hasMoreParsesOneAsTrueAndZeroAsFalse() {
        val response = json.decodeFromString(HistoryServerResponse.serializer(), historyResponseBody)
        assertEquals(true, response.hasMore())
    }

    @Test fun toDomainDropsUnknownStatuses() {
        val dto = HistoryDto(
            invNo = "INV-X",
            invDt = "2026-05-20",
            invStatus = "Refunded",   // not in OrderStatus
            principalName = "COLUMBIA",
            totalText = "99.0",
        )
        assertNull(dto.toDomain(currency = "PHP", itemCount = 1, firstProduct = null))
    }

    @Test fun toDomainMapsKnownStatusAndParsesAmount() {
        val dto = HistoryDto(
            invNo = "INV-1",
            invDt = "2026-05-20",
            invStatus = "Waiting",
            principalName = "SELECTA",
            totalText = "784",
        )
        val order = dto.toDomain(currency = "PHP", itemCount = 2, firstProduct = null)
        assertNotNull(order)
        assertEquals(OrderStatus.WAITING, order.status)
        assertEquals("INV-1", order.invNo)
        assertEquals(784.0, order.total)
        assertEquals(2, order.itemCount)
        assertEquals("SELECTA", order.principalName)
    }

    @Test fun rowsOnEmptyResponseIsEmpty() {
        val empty = """
            {"getObjectResult":{"errorData":{"code":0,"description":""},"hasMoreRecords":0,"objectData":[[],[]]}}
        """.trimIndent()
        val response = json.decodeFromString(HistoryServerResponse.serializer(), empty)
        assertEquals(emptyList(), response.rows(currency = "PHP"))
    }
}
