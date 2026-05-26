package com.simplr.mykitta2.feature.address

import com.simplr.mykitta2.core.mvi.ScreenViewModel

class AddressFormViewModel(
    storeFactory: AddressFormStoreFactory,
) : ScreenViewModel<AddressFormStore.Intent, AddressFormStore.State, AddressFormStore.Label>(
    store = storeFactory.create(),
)
