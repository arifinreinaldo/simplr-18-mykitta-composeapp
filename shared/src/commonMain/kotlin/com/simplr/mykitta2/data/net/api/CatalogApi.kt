package com.simplr.mykitta2.data.net.api

import com.simplr.mykitta2.data.net.dto.AddressListServerResponse
import com.simplr.mykitta2.data.net.dto.AddressRequest
import com.simplr.mykitta2.data.net.dto.BannerServerResponse
import com.simplr.mykitta2.data.net.dto.ConfigListResponse
import com.simplr.mykitta2.data.net.dto.GetRequest
import com.simplr.mykitta2.data.net.dto.HistoryServerResponse
import com.simplr.mykitta2.data.net.dto.ItemServerResponse
import com.simplr.mykitta2.data.net.dto.LoyaltyPointsServerResponse
import com.simplr.mykitta2.data.net.dto.MarkNotificationReadRequest
import com.simplr.mykitta2.data.net.dto.MarkNotificationReadResponse
import com.simplr.mykitta2.data.net.dto.MessageServerResponse
import com.simplr.mykitta2.data.net.dto.NotifCountServerResponse
import com.simplr.mykitta2.data.net.dto.NotificationListServerResponse
import com.simplr.mykitta2.data.net.dto.PrincipalServerResponse
import com.simplr.mykitta2.data.net.dto.ProfileServerResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import io.ktor.http.takeFrom

/**
 * Authenticated list-fetch endpoints. List reads go through `POST User/GetObject`
 * with a [GetRequest] body where `functionName` selects the actual procedure.
 * Writes (currently only [markNotificationRead]) hit dedicated endpoints via
 * [KtorCatalogApi.callPath].
 */
interface CatalogApi {
    suspend fun getBanners(baseUrl: String, request: GetRequest): BannerServerResponse
    suspend fun getItems(baseUrl: String, request: GetRequest): ItemServerResponse
    suspend fun getConfigList(baseUrl: String, request: GetRequest): ConfigListResponse
    suspend fun getNotificationCount(baseUrl: String, request: GetRequest): NotifCountServerResponse
    suspend fun getNotificationList(baseUrl: String, request: GetRequest): NotificationListServerResponse
    suspend fun markNotificationRead(baseUrl: String, request: MarkNotificationReadRequest): MarkNotificationReadResponse
    suspend fun getPrincipals(baseUrl: String, request: GetRequest): PrincipalServerResponse
    suspend fun getLoyaltyPoints(baseUrl: String, request: GetRequest): LoyaltyPointsServerResponse
    suspend fun getProfile(baseUrl: String, request: GetRequest): ProfileServerResponse
    suspend fun getHistory(baseUrl: String, request: GetRequest): HistoryServerResponse
    suspend fun getShipmentAddresses(baseUrl: String, request: GetRequest): AddressListServerResponse
    suspend fun saveAddress(baseUrl: String, request: AddressRequest): MessageServerResponse
}

class KtorCatalogApi(private val client: HttpClient) : CatalogApi {

    private suspend inline fun <reified R> call(baseUrl: String, request: GetRequest): R {
        val url = URLBuilder().takeFrom(baseUrl).appendPathSegments("User", "GetObject").build()
        return client.post(url) {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    /** Sibling of [call] for endpoints not routed through `User/GetObject`. */
    private suspend inline fun <reified Body : Any, reified R> callPath(
        baseUrl: String,
        path: List<String>,
        body: Body,
    ): R {
        val url = URLBuilder().takeFrom(baseUrl).appendPathSegments(path).build()
        return client.post(url) {
            contentType(ContentType.Application.Json)
            setBody(body)
        }.body()
    }

    override suspend fun getBanners(baseUrl: String, request: GetRequest) =
        call<BannerServerResponse>(baseUrl, request)

    override suspend fun getItems(baseUrl: String, request: GetRequest) =
        call<ItemServerResponse>(baseUrl, request)

    override suspend fun getConfigList(baseUrl: String, request: GetRequest) =
        call<ConfigListResponse>(baseUrl, request)

    override suspend fun getNotificationCount(baseUrl: String, request: GetRequest) =
        call<NotifCountServerResponse>(baseUrl, request)

    override suspend fun getNotificationList(baseUrl: String, request: GetRequest) =
        call<NotificationListServerResponse>(baseUrl, request)

    override suspend fun markNotificationRead(baseUrl: String, request: MarkNotificationReadRequest) =
        callPath<MarkNotificationReadRequest, MarkNotificationReadResponse>(
            baseUrl, listOf("Notification", "ReadNotification"), request,
        )

    override suspend fun getPrincipals(baseUrl: String, request: GetRequest) =
        call<PrincipalServerResponse>(baseUrl, request)

    override suspend fun getLoyaltyPoints(baseUrl: String, request: GetRequest) =
        call<LoyaltyPointsServerResponse>(baseUrl, request)

    override suspend fun getProfile(baseUrl: String, request: GetRequest) =
        call<ProfileServerResponse>(baseUrl, request)

    override suspend fun getHistory(baseUrl: String, request: GetRequest) =
        call<HistoryServerResponse>(baseUrl, request)

    override suspend fun getShipmentAddresses(baseUrl: String, request: GetRequest) =
        call<AddressListServerResponse>(baseUrl, request)

    override suspend fun saveAddress(baseUrl: String, request: AddressRequest) =
        callPath<AddressRequest, MessageServerResponse>(
            baseUrl, listOf("User", "AddAddress"), request,
        )
}
