package com.simplr.mykitta2.data.net.dto

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pin the `User/GetObject` response envelope shape — `objectData` is a
 * double-wrapped list (`List<List<T>>`), `hasMoreRecords` is 0/1 (not a bool),
 * and per-DTO field-name casing (`BannerName`, `InvQty`, `IsActive`, etc.)
 * comes straight from the legacy Retrofit/Moshi DTOs. Drift in any of these
 * breaks every authenticated list call silently.
 */
class CatalogDtosTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = false }

    // ---- BannerServerResponse ----

    @Test fun bannerServerResponse_parsesAndMapsToDomain() {
        val body = """
            {"getObjectResult":{
              "errorData":{"code":0,"description":""},
              "hasMoreRecords":0,
              "objectData":[[
                {"bannerId":"B1","BannerName":"100 PLUS","bannerImg":"http://x/y.jpg",
                 "PrincipalId":"1","StartDate":"2022-01-01","EndDate":"2022-12-31"}
              ]]
            }}
        """.trimIndent()
        val parsed = json.decodeFromString<BannerServerResponse>(body)
        val banner = parsed.getObjectResult.objectData.single().single().toDomain()
        assertEquals("B1", banner.bannerId)
        assertEquals("100 PLUS", banner.bannerName)
        assertEquals("http://x/y.jpg", banner.bannerImg)
        assertEquals("1", banner.principalId)
        assertEquals("2022-01-01", banner.startDate)
        assertEquals("2022-12-31", banner.endDate)
    }

    @Test fun bannerDto_acceptsMissingOptionalFields() {
        // PrincipalId / StartDate / EndDate are present in the legacy payload
        // but the wiki's "DEFAULT" banner row has PrincipalId="" — make sure
        // the DTO doesn't fail when the optional ones are absent entirely.
        val body = """{"bannerId":"X","BannerName":"x","bannerImg":"u"}"""
        val parsed = json.decodeFromString<BannerDto>(body)
        assertEquals("", parsed.principalId)
        assertEquals("", parsed.startDate)
        assertEquals("", parsed.endDate)
    }

    @Test fun bannerServerResponse_handlesEmptyObjectData() {
        val body = """{"getObjectResult":{"errorData":{"code":0,"description":""},"hasMoreRecords":0,"objectData":[]}}"""
        val parsed = json.decodeFromString<BannerServerResponse>(body)
        assertEquals(emptyList(), parsed.getObjectResult.objectData)
    }

    // ---- ItemServerResponse ----

    @Test fun itemServerResponse_parsesAndMapsToDomain() {
        val body = """
            {"getObjectResult":{
              "errorData":{"code":0,"description":""},
              "hasMoreRecords":1,
              "objectData":[[
                {"productId":"P1","productDesc":"Soap","productLong":"Bar Soap 100g",
                 "principalId":"1","productUrl":"http://x/p.jpg","totalOrder":5,
                 "unitPrice":"10.50","basicPrice":"12.00",
                 "baseUOM":"PCS","salesUOM":"BOX","InvQty":3}
              ]]
            }}
        """.trimIndent()
        val parsed = json.decodeFromString<ItemServerResponse>(body)
        assertEquals(1, parsed.getObjectResult.hasMoreRecords)
        val item = parsed.getObjectResult.objectData.single().single().toDomain()
        assertEquals("P1", item.productId)
        assertEquals(5, item.totalOrder)
        assertEquals("10.50", item.unitPrice)
        assertEquals("BOX", item.displayUom)
        assertEquals(3, item.invQty)
        assertEquals(false, item.isSoldOut)
    }

    @Test fun itemDto_totalOrderNullCoercesToZero() {
        val body = """
            {"productId":"P1","productDesc":"x","principalId":"1","totalOrder":null,
             "unitPrice":"1","basicPrice":"1"}
        """.trimIndent()
        val parsed = json.decodeFromString<ItemDto>(body)
        assertEquals(0, parsed.toDomain().totalOrder)
    }

    @Test fun itemDto_invQtyZeroFlagsSoldOut() {
        val body = """
            {"productId":"P1","productDesc":"x","principalId":"1","InvQty":0,
             "unitPrice":"1","basicPrice":"1"}
        """.trimIndent()
        val parsed = json.decodeFromString<ItemDto>(body)
        assertEquals(true, parsed.toDomain().isSoldOut)
    }

    @Test fun itemDto_emptySalesUomFallsBackToBaseUom() {
        val body = """
            {"productId":"P1","productDesc":"x","principalId":"1",
             "baseUOM":"PCS","salesUOM":"","unitPrice":"1","basicPrice":"1"}
        """.trimIndent()
        val parsed = json.decodeFromString<ItemDto>(body)
        assertEquals("PCS", parsed.toDomain().displayUom)
    }

    // ---- ConfigListResponse ----

    @Test fun configDto_parses() {
        val body = """
            {"getObjectResult":{
              "errorData":{"code":0,"description":""},
              "hasMoreRecords":0,
              "objectData":[[
                {"SystemValue":"GetMostBuy","Description":"Most Buy","DisplayNo":1},
                {"SystemValue":"GetLastOrder","Description":"Last Buy","DisplayNo":2}
              ]]
            }}
        """.trimIndent()
        val parsed = json.decodeFromString<ConfigListResponse>(body)
        val rows = parsed.getObjectResult.objectData.single()
        assertEquals(2, rows.size)
        assertEquals("GetMostBuy", rows[0].systemValue)
        assertEquals("Last Buy", rows[1].description)
        assertEquals(2, rows[1].displayNo)
    }

    // ---- NotifCountServerResponse ----

    @Test fun notifCountResponse_extractsCount() {
        val body = """
            {"getObjectResult":{
              "errorData":{"code":0,"description":""},
              "hasMoreRecords":0,
              "objectData":[[{"count":7}]]
            }}
        """.trimIndent()
        val parsed = json.decodeFromString<NotifCountServerResponse>(body)
        assertEquals(7, parsed.count())
    }

    @Test fun notifCountResponse_emptyDataReturnsZero() {
        val body = """{"getObjectResult":{"errorData":{"code":0,"description":""},"hasMoreRecords":0,"objectData":[]}}"""
        val parsed = json.decodeFromString<NotifCountServerResponse>(body)
        assertEquals(0, parsed.count())
    }

    @Test fun notifCountResponse_emptyInnerListReturnsZero() {
        val body = """{"getObjectResult":{"errorData":{"code":0,"description":""},"hasMoreRecords":0,"objectData":[[]]}}"""
        val parsed = json.decodeFromString<NotifCountServerResponse>(body)
        assertEquals(0, parsed.count())
    }

    // ---- PrincipalServerResponse ----

    @Test fun principalDto_mapsIsActiveBoolean() {
        val body = """
            {"principalId":"1","principalName":"P","principalImg":"u",
             "IsActive":true,"IsProcess":1}
        """.trimIndent()
        val parsed = json.decodeFromString<PrincipalDto>(body)
        val p = parsed.toDomain()
        assertEquals("1", p.principalId)
        assertEquals(true, p.isActive)
    }

    @Test fun principalDto_isActiveNullCoercesToFalse() {
        val body = """{"principalId":"1","principalName":"P","principalImg":"u"}"""
        val parsed = json.decodeFromString<PrincipalDto>(body)
        assertEquals(false, parsed.toDomain().isActive)
    }

    // ---- GetRequest ----

    @Test fun getRequest_serializesSnakeCaseCustNoButCamelCaseElsewhere() {
        val req = GetRequest(
            functionName = "GetItem",
            offset = 0,
            recordsize = 15,
            search = "all",
            sort = "0",
            user = "S1",
        )
        val encoded = json.encodeToString(GetRequest.serializer(), req)
        // Legacy contract: CustNo (capital C, capital N), `recordsize` lower
        // (NOT recordSize), `functionName` camel. Backend treats these as
        // case-sensitive.
        assertTrue(encoded.contains("\"functionName\":\"GetItem\""), encoded)
        assertTrue(encoded.contains("\"recordsize\":15"), encoded)
        assertTrue(encoded.contains("\"CustNo\":\"\""), encoded)
        assertTrue(encoded.contains("\"user\":\"S1\""), encoded)
    }
}
