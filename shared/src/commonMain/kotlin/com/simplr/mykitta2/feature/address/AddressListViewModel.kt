package com.simplr.mykitta2.feature.address

import com.simplr.mykitta2.core.mvi.ScreenViewModel

class AddressListViewModel(
    storeFactory: AddressListStoreFactory,
) : ScreenViewModel<AddressListStore.Intent, AddressListStore.State, AddressListStore.Label>(
    store = storeFactory.create(),
)
