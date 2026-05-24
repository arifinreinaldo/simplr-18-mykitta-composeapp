package com.simplr.mykitta2.feature.auth

import com.simplr.mykitta2.core.mvi.ScreenViewModel

class LoginOtpViewModel(
    storeFactory: LoginOtpStoreFactory,
) : ScreenViewModel<LoginOtpStore.Intent, LoginOtpStore.State, LoginOtpStore.Label>(
    store = storeFactory.create(),
)
