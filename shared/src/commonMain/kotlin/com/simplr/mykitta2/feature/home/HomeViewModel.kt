package com.simplr.mykitta2.feature.home

import com.simplr.mykitta2.core.mvi.ScreenViewModel

class HomeViewModel(
    storeFactory: HomeStoreFactory,
) : ScreenViewModel<HomeStore.Intent, HomeStore.State, HomeStore.Label>(
    store = storeFactory.create(),
)
