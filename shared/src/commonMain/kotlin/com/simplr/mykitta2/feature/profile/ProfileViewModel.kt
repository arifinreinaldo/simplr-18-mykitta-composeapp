package com.simplr.mykitta2.feature.profile

import com.simplr.mykitta2.core.mvi.ScreenViewModel

class ProfileViewModel(
    storeFactory: ProfileStoreFactory,
) : ScreenViewModel<ProfileStore.Intent, ProfileStore.State, ProfileStore.Label>(
    store = storeFactory.create(),
)
