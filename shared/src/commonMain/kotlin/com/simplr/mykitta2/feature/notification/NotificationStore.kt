package com.simplr.mykitta2.feature.notification

import com.arkivanov.mvikotlin.core.store.Reducer
import com.arkivanov.mvikotlin.core.store.Store
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.CoroutineBootstrapper
import com.arkivanov.mvikotlin.extensions.coroutines.CoroutineExecutor
import com.simplr.mykitta2.core.error.ErrorMapper
import com.simplr.mykitta2.core.result.Outcome
import com.simplr.mykitta2.data.repo.NotificationRepository
import com.simplr.mykitta2.data.repo.PrincipalRepository
import com.simplr.mykitta2.domain.Notification
import com.simplr.mykitta2.domain.NotificationType
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

interface NotificationStore : Store<NotificationStore.Intent, NotificationStore.State, NotificationStore.Label> {

    data class State(
        val items: List<Notification> = emptyList(),
        val offset: Int = 0,
        val endReached: Boolean = false,
        val firstLoadInFlight: Boolean = true,
        val loadingMore: Boolean = false,
        val error: String? = null,
        val showingCache: Boolean = false,
    )

    sealed interface Intent {
        data object LoadNextPage : Intent
        data object Refresh : Intent
        data class TapItem(val notification: Notification) : Intent
        data object DismissError : Intent
    }

    sealed interface Label {
        data class NavigateToPrincipal(val principalId: String, val principalName: String) : Label
        data object NavigateUnsupportedType : Label
        data class ShowSnackbar(val text: String) : Label
    }
}

class NotificationStoreFactory(
    private val storeFactory: StoreFactory,
    private val notificationRepository: NotificationRepository,
    private val principalRepository: PrincipalRepository,
) {
    fun create(): NotificationStore =
        object : NotificationStore,
            Store<NotificationStore.Intent, NotificationStore.State, NotificationStore.Label>
            by storeFactory.create(
                name = "NotificationStore",
                initialState = NotificationStore.State(),
                bootstrapper = BootstrapperImpl(),
                executorFactory = { ExecutorImpl() },
                reducer = ReducerImpl,
            ) {}

    private sealed interface Action {
        data object LoadFirstPage : Action
    }

    private sealed interface Message {
        data object FirstLoadStarted : Message
        data class PageLoaded(
            val replace: Boolean,
            val items: List<Notification>,
            val advanceBy: Int,
            val endReached: Boolean,
            val fromCache: Boolean,
        ) : Message
        data class LoadFailed(val error: String) : Message
        data object LoadingMoreStarted : Message
        data class MarkedRead(val id: String) : Message
        data class ErrorSet(val error: String?) : Message
        data object Reset : Message
    }

    private inner class BootstrapperImpl : CoroutineBootstrapper<Action>() {
        override fun invoke() {
            dispatch(Action.LoadFirstPage)
        }
    }

    private inner class ExecutorImpl :
        CoroutineExecutor<NotificationStore.Intent, Action, NotificationStore.State, Message, NotificationStore.Label>() {

        override fun executeAction(action: Action) {
            when (action) {
                Action.LoadFirstPage -> loadPage(offset = 0, isFirstLoad = true)
            }
        }

        override fun executeIntent(intent: NotificationStore.Intent) {
            when (intent) {
                NotificationStore.Intent.LoadNextPage -> {
                    val s = state()
                    if (s.loadingMore || s.endReached || s.firstLoadInFlight) return
                    loadPage(offset = s.offset, isFirstLoad = false)
                }
                NotificationStore.Intent.Refresh -> {
                    dispatch(Message.Reset)
                    loadPage(offset = 0, isFirstLoad = true)
                }
                is NotificationStore.Intent.TapItem -> handleTap(intent.notification)
                NotificationStore.Intent.DismissError -> dispatch(Message.ErrorSet(null))
            }
        }

        /** Tap handler: optimistically marks the item read (fail-open — UI flips
         *  regardless), then type-routes to a nav label. PRINCIPAL needs a cache
         *  lookup so we can pass the human-readable brand name; misses degrade
         *  to a "brand not available" snackbar. */
        private fun handleTap(notification: Notification) {
            scope.launch {
                when (val markOutcome = notificationRepository.markAsRead(notification.id)) {
                    is Outcome.Success -> dispatch(Message.MarkedRead(notification.id))
                    is Outcome.Failure -> {
                        // Optimistic UI: still flip the dot even though the server
                        // didn't confirm. The next refresh will reconcile.
                        dispatch(Message.MarkedRead(notification.id))
                        publish(NotificationStore.Label.ShowSnackbar(
                            ErrorMapper.message(markOutcome.error)
                        ))
                    }
                    Outcome.Idle, Outcome.Loading -> Unit
                }
                when (notification.type) {
                    NotificationType.PRINCIPAL -> {
                        val principalId = parsePrincipalId(notification.payload)
                        val cached = principalId?.let { principalRepository.findById(it) }
                        if (principalId != null && cached != null) {
                            publish(NotificationStore.Label.NavigateToPrincipal(
                                principalId = principalId,
                                principalName = cached.principalName,
                            ))
                        } else {
                            publish(NotificationStore.Label.NavigateUnsupportedType)
                            publish(NotificationStore.Label.ShowSnackbar("Brand not available"))
                        }
                    }
                    NotificationType.ORDER,
                    NotificationType.UNKNOWN ->
                        publish(NotificationStore.Label.NavigateUnsupportedType)
                }
            }
        }

        private fun parsePrincipalId(payload: String): String? = try {
            json.parseToJsonElement(payload).jsonObject["principalId"]?.jsonPrimitive?.content
        } catch (t: Throwable) {
            null
        }

        private fun loadPage(offset: Int, isFirstLoad: Boolean) {
            if (isFirstLoad) dispatch(Message.FirstLoadStarted)
            else dispatch(Message.LoadingMoreStarted)
            scope.launch {
                when (val outcome = notificationRepository.loadPage(offset)) {
                    is Outcome.Success -> {
                        val page = outcome.value
                        dispatch(
                            Message.PageLoaded(
                                replace = isFirstLoad,
                                items = page.items,
                                advanceBy = page.items.size,
                                endReached = !page.hasMore,
                                fromCache = page.fromCache,
                            )
                        )
                    }
                    is Outcome.Failure ->
                        dispatch(Message.LoadFailed(ErrorMapper.message(outcome.error)))
                    Outcome.Idle, Outcome.Loading -> Unit
                }
            }
        }
    }

    private object ReducerImpl : Reducer<NotificationStore.State, Message> {
        override fun NotificationStore.State.reduce(msg: Message): NotificationStore.State = when (msg) {
            Message.FirstLoadStarted -> copy(firstLoadInFlight = true, error = null)
            is Message.PageLoaded -> copy(
                items = if (msg.replace) msg.items else items + msg.items,
                offset = (if (msg.replace) 0 else offset) + msg.advanceBy,
                endReached = msg.endReached,
                firstLoadInFlight = false,
                loadingMore = false,
                showingCache = msg.fromCache,
                error = null,
            )
            is Message.LoadFailed -> copy(
                firstLoadInFlight = false,
                loadingMore = false,
                error = msg.error,
            )
            Message.LoadingMoreStarted -> copy(loadingMore = true)
            is Message.MarkedRead -> copy(
                items = items.map { if (it.id == msg.id) it.copy(isRead = true) else it },
            )
            is Message.ErrorSet -> copy(error = msg.error)
            Message.Reset -> NotificationStore.State()
        }
    }

    private companion object {
        // Tolerant parser for payload JSON — server occasionally tacks on extra
        // fields we don't model.
        val json = Json { ignoreUnknownKeys = true }
    }
}
