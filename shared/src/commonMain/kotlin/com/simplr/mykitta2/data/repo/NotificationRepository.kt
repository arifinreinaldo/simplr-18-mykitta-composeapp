package com.simplr.mykitta2.data.repo

import com.simplr.mykitta2.core.env.BuildEnv
import com.simplr.mykitta2.core.error.ErrorMapper
import com.simplr.mykitta2.core.result.Outcome
import com.simplr.mykitta2.data.net.api.CatalogApi
import com.simplr.mykitta2.data.net.dto.GetRequest
import com.simplr.mykitta2.data.net.dto.MarkNotificationReadRequest
import com.simplr.mykitta2.data.prefs.CountryStore
import com.simplr.mykitta2.data.prefs.SessionStore
import com.simplr.mykitta2.domain.Country
import com.simplr.mykitta2.domain.Notification
import com.simplr.mykitta2.domain.NotificationType
import com.simplr.mykitta2.shared.db.MyKittaDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

private const val PAGE_SIZE = 20

/**
 * Single source of truth for the unread-notification count via [unreadCount].
 * HomeStore subscribes to that flow for the top-bar badge; NotificationStore
 * calls [markAsRead] which decrements the flow — HomeStore updates automatically
 * with no cross-feature import.
 *
 * Pagination state lives in NotificationStore, not here. [loadPage] is a pure
 * function from (offset) → page; the repository owns no list state.
 */
interface NotificationRepository {
    val unreadCount: StateFlow<Int>
    suspend fun refreshCount(): Outcome<Int>
    suspend fun loadPage(offset: Int): Outcome<NotificationPage>
    suspend fun markAsRead(id: String): Outcome<Unit>
}

data class NotificationPage(
    val items: List<Notification>,
    val hasMore: Boolean,
    val fromCache: Boolean = false,
)

class DefaultNotificationRepository(
    private val catalogApi: CatalogApi,
    private val sessionStore: SessionStore,
    private val countryStore: CountryStore,
    private val db: MyKittaDatabase,
) : NotificationRepository {

    private val _unreadCount = MutableStateFlow(0)
    override val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    override suspend fun refreshCount(): Outcome<Int> =
        throw NotImplementedError("Task 8")

    override suspend fun loadPage(offset: Int): Outcome<NotificationPage> =
        throw NotImplementedError("Task 9")

    override suspend fun markAsRead(id: String): Outcome<Unit> =
        throw NotImplementedError("Task 11")

    private suspend fun baseUrl(): String =
        BuildEnv.baseUrlFor(countryStore.read() ?: Country.PH)

    private suspend fun supervisorRequest(
        functionName: String,
        offset: Int = 0,
    ) = GetRequest(
        functionName = functionName,
        offset = offset,
        recordsize = sessionStore.pagination(),
        search = "all",
        sort = "0",
        user = sessionStore.read()?.supervisorCode ?: FALLBACK_USER,
    )

    private inline fun <T> runCall(block: () -> T): Outcome<T> = try {
        Outcome.Success(block())
    } catch (t: Throwable) {
        Outcome.Failure(ErrorMapper.from(t))
    }

    private fun upsertCache(items: List<Notification>) {
        db.notificationQueries.transaction {
            items.forEach {
                db.notificationQueries.upsert(
                    id = it.id, title = it.title, description = it.description,
                    type = it.type.name, payload = it.payload,
                    isRead = if (it.isRead) 1L else 0L, createdAt = it.createdAt,
                )
            }
        }
    }

    private fun readCacheFirstPage(): List<Notification> =
        db.notificationQueries.selectFirstPage(PAGE_SIZE.toLong()).executeAsList().map { row ->
            Notification(
                id = row.id, title = row.title, description = row.description,
                type = NotificationType.fromWire(row.type), payload = row.payload,
                isRead = row.isRead == 1L, createdAt = row.createdAt,
            )
        }

    private companion object { const val FALLBACK_USER = "M1" }
}
