package com.simplr.mykitta2.feature.principal

import com.simplr.mykitta2.core.mvi.ScreenViewModel

class PrincipalViewModel(
    storeFactory: PrincipalStoreFactory,
) : ScreenViewModel<PrincipalStore.Intent, PrincipalStore.State, PrincipalStore.Label>(
    store = storeFactory.create(),
)
