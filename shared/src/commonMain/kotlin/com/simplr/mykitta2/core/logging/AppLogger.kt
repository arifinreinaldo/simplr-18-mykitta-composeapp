package com.simplr.mykitta2.core.logging

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity

class AppLogger(private val delegate: Logger = Logger) {
    fun v(tag: String, message: () -> String) = delegate.v(tag = tag, throwable = null, messageString = message())
    fun d(tag: String, message: () -> String) = delegate.d(tag = tag, throwable = null, messageString = message())
    fun i(tag: String, message: () -> String) = delegate.i(tag = tag, throwable = null, messageString = message())
    fun w(tag: String, throwable: Throwable? = null, message: () -> String) =
        delegate.w(tag = tag, throwable = throwable, messageString = message())
    fun e(tag: String, throwable: Throwable? = null, message: () -> String) =
        delegate.e(tag = tag, throwable = throwable, messageString = message())

    fun log(severity: Severity, tag: String, throwable: Throwable?, message: String) {
        delegate.log(severity, tag, throwable, message)
    }
}
