package com.simplr.mykitta2.feature.home

import com.arkivanov.mvikotlin.core.store.Reducer
import com.arkivanov.mvikotlin.core.store.Store
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.CoroutineBootstrapper
import com.arkivanov.mvikotlin.extensions.coroutines.CoroutineExecutor
import com.simplr.mykitta2.core.error.ErrorMapper
import com.simplr.mykitta2.core.result.Outcome
import com.simplr.mykitta2.data.repo.HomeRepository
import com.simplr.mykitta2.domain.Banner
import com.simplr.mykitta2.domain.CategoryRail
import com.simplr.mykitta2.domain.Item
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

interface HomeStore : Store<HomeStore.Intent, HomeStore.State, HomeStore.Label> {

    data class State(
        val banners: List<Banner> = emptyList(),
        val bannersLoading: Boolean = true,
        val rails: List<CategoryRail> = emptyList(),
        val railsLoading: Boolean = true,
        val notifCount: Int = 0,
        val error: String? = null,
        val refreshing: Boolean = false,
    )

    sealed interface Intent {
        data object Refresh : Intent
        data class ItemClicked(val item: Item) : Intent
        data class BannerClicked(val banner: Banner) : Intent
    }

    sealed interface Label {
        // Item / brand detail screens don't exist yet — surface a transient
        // notice instead of dropping the tap silently.
        data class ShowSnackbar(val text: String) : Label
    }
}

class HomeStoreFactory(
    private val storeFactory: StoreFactory,
    private val homeRepository: HomeRepository,
) {
    fun create(): HomeStore =
        object : HomeStore,
            Store<HomeStore.Intent, HomeStore.State, HomeStore.Label>
            by storeFactory.create(
                name = "HomeStore",
                initialState = HomeStore.State(),
                bootstrapper = BootstrapperImpl(),
                executorFactory = { ExecutorImpl() },
                reducer = ReducerImpl,
            ) {}

    private sealed interface Action {
        data object LoadAll : Action
    }

    private sealed interface Message {
        data object RefreshStarted : Message
        data object RefreshFinished : Message
        data object BannersLoading : Message
        data class BannersLoaded(val banners: List<Banner>) : Message
        data class RailsDeclared(val rails: List<CategoryRail>) : Message
        data class RailItemsLoaded(val functionName: String, val items: List<Item>) : Message
        data class RailItemsFailed(val functionName: String) : Message
        data class NotifCountLoaded(val count: Int) : Message
        data class ErrorSet(val message: String?) : Message
    }

    private inner class BootstrapperImpl : CoroutineBootstrapper<Action>() {
        override fun invoke() {
            dispatch(Action.LoadAll)
        }
    }

    private inner class ExecutorImpl :
        CoroutineExecutor<HomeStore.Intent, Action, HomeStore.State, Message, HomeStore.Label>() {

        override fun executeAction(action: Action) {
            when (action) {
                Action.LoadAll -> loadAll()
            }
        }

        override fun executeIntent(intent: HomeStore.Intent) {
            when (intent) {
                HomeStore.Intent.Refresh -> {
                    if (state().refreshing) return
                    dispatch(Message.RefreshStarted)
                    loadAll(onFinished = { dispatch(Message.RefreshFinished) })
                }
                is HomeStore.Intent.ItemClicked ->
                    publish(HomeStore.Label.ShowSnackbar("${intent.item.productDesc} — detail page coming soon"))
                is HomeStore.Intent.BannerClicked ->
                    publish(HomeStore.Label.ShowSnackbar(intent.banner.bannerName))
            }
        }

        // Fans out three independent loads. Rails go through two phases:
        // (1) declare the list of rails (titles + loading=true) so the UI can
        // render their headers immediately, then (2) fetch each rail's items.
        // Notif count is a stand-alone fire-and-forget. Errors per-rail don't
        // poison the screen — they just leave that rail empty + non-loading.
        private fun loadAll(onFinished: (() -> Unit)? = null) {
            dispatch(Message.BannersLoading)
            scope.launch {
                when (val outcome = homeRepository.loadBanners()) {
                    is Outcome.Success -> dispatch(Message.BannersLoaded(outcome.value))
                    is Outcome.Failure -> {
                        dispatch(Message.BannersLoaded(emptyList()))
                        dispatch(Message.ErrorSet(ErrorMapper.message(outcome.error)))
                    }
                    Outcome.Idle, Outcome.Loading -> Unit
                }
            }

            scope.launch {
                when (val outcome = homeRepository.loadConfigRails()) {
                    is Outcome.Success -> {
                        dispatch(Message.RailsDeclared(outcome.value))
                        // joinAll so onFinished waits for every rail's items to
                        // settle (success or failure) before flipping refreshing
                        // off — otherwise pull-to-refresh "completes" while the
                        // rails are still spinning their per-row indicators.
                        val railJobs = outcome.value.map { rail ->
                            scope.launch {
                                when (val items = homeRepository.loadRailItems(rail.functionName)) {
                                    is Outcome.Success ->
                                        dispatch(Message.RailItemsLoaded(rail.functionName, items.value))
                                    is Outcome.Failure ->
                                        dispatch(Message.RailItemsFailed(rail.functionName))
                                    Outcome.Idle, Outcome.Loading -> Unit
                                }
                            }
                        }
                        railJobs.joinAll()
                    }
                    is Outcome.Failure -> {
                        dispatch(Message.RailsDeclared(emptyList()))
                        dispatch(Message.ErrorSet(ErrorMapper.message(outcome.error)))
                    }
                    Outcome.Idle, Outcome.Loading -> Unit
                }
                onFinished?.invoke()
            }

            scope.launch {
                when (val outcome = homeRepository.loadNotificationCount()) {
                    is Outcome.Success -> dispatch(Message.NotifCountLoaded(outcome.value))
                    else -> Unit
                }
            }
        }
    }

    private object ReducerImpl : Reducer<HomeStore.State, Message> {
        override fun HomeStore.State.reduce(msg: Message): HomeStore.State = when (msg) {
            Message.RefreshStarted -> copy(refreshing = true, error = null)
            Message.RefreshFinished -> copy(refreshing = false)
            Message.BannersLoading -> copy(bannersLoading = true)
            is Message.BannersLoaded -> copy(banners = msg.banners, bannersLoading = false)
            is Message.RailsDeclared -> copy(rails = msg.rails, railsLoading = false)
            is Message.RailItemsLoaded -> copy(
                rails = rails.map {
                    if (it.functionName == msg.functionName) it.copy(items = msg.items, loading = false) else it
                }
            )
            is Message.RailItemsFailed -> copy(
                rails = rails.map {
                    if (it.functionName == msg.functionName) it.copy(loading = false) else it
                }
            )
            is Message.NotifCountLoaded -> copy(notifCount = msg.count)
            is Message.ErrorSet -> copy(error = msg.message)
        }
    }
}
