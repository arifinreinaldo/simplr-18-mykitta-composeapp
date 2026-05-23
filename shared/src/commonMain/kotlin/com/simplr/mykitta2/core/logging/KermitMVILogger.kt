package com.simplr.mykitta2.core.logging

import com.arkivanov.mvikotlin.core.utils.JvmSerializable
import com.arkivanov.mvikotlin.logging.logger.Logger as MVILogger

class KermitMVILogger(private val appLogger: AppLogger) : MVILogger, JvmSerializable {
    override fun log(text: String) {
        appLogger.d("MVI") { text }
    }
}
