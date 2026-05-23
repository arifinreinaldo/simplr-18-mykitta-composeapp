package com.simplr.mykitta2.core.mvi

import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.logging.store.LoggingStoreFactory
import com.arkivanov.mvikotlin.main.store.DefaultStoreFactory
import com.arkivanov.mvikotlin.timetravel.store.TimeTravelStoreFactory
import com.simplr.mykitta2.core.logging.AppLogger
import com.simplr.mykitta2.core.logging.KermitMVILogger

class BaseStoreFactory(
    private val isDebug: Boolean,
    private val logger: AppLogger,
) {
    fun create(): StoreFactory {
        val base: StoreFactory = if (isDebug) TimeTravelStoreFactory() else DefaultStoreFactory()
        return LoggingStoreFactory(base, logger = KermitMVILogger(logger))
    }
}
