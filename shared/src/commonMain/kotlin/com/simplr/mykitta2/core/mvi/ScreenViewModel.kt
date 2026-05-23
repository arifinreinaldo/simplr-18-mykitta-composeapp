package com.simplr.mykitta2.core.mvi

import androidx.lifecycle.ViewModel
import com.arkivanov.mvikotlin.core.store.Store
import com.arkivanov.mvikotlin.extensions.coroutines.labels
import com.arkivanov.mvikotlin.extensions.coroutines.stateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

@OptIn(ExperimentalCoroutinesApi::class)
abstract class ScreenViewModel<I : Any, S : Any, L : Any>(
    protected val store: Store<I, S, L>,
) : ViewModel() {
    val state: StateFlow<S> = store.stateFlow
    val labels: Flow<L> = store.labels

    fun accept(intent: I) { store.accept(intent) }

    override fun onCleared() {
        store.dispose()
        super.onCleared()
    }
}
