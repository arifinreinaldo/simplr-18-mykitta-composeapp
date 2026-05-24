package com.simplr.mykitta2.feature.auth

import com.simplr.mykitta2.core.mvi.ScreenViewModel

class OtpVerifyViewModel(
    storeFactory: OtpVerifyStoreFactory,
) : ScreenViewModel<OtpVerifyStore.Intent, OtpVerifyStore.State, OtpVerifyStore.Label>(
    store = storeFactory.create(),
)
