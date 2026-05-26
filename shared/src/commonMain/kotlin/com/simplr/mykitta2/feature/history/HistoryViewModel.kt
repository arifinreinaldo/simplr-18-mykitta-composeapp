package com.simplr.mykitta2.feature.history

import com.simplr.mykitta2.core.mvi.ScreenViewModel

class HistoryViewModel(
    storeFactory: HistoryStoreFactory,
) : ScreenViewModel<HistoryStore.Intent, HistoryStore.State, HistoryStore.Label>(
    store = storeFactory.create(),
)
