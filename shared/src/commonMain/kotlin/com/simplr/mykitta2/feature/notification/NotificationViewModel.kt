package com.simplr.mykitta2.feature.notification

import com.simplr.mykitta2.core.mvi.ScreenViewModel

class NotificationViewModel(
    storeFactory: NotificationStoreFactory,
) : ScreenViewModel<NotificationStore.Intent, NotificationStore.State, NotificationStore.Label>(
    store = storeFactory.create(),
)
