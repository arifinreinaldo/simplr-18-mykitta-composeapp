package com.simplr.mykitta2.feature.splash

import com.simplr.mykitta2.core.mvi.ScreenViewModel

class SplashViewModel(
    storeFactory: SplashStoreFactory,
) : ScreenViewModel<SplashStore.Intent, SplashStore.State, SplashStore.Label>(
    store = storeFactory.create(),
)
