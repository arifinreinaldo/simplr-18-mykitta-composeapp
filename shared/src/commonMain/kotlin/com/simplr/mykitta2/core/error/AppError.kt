package com.simplr.mykitta2.core.error

sealed interface AppError {
    data object Network : AppError
    data class Http(val status: Int, val body: String?) : AppError
    data object Unauthorized : AppError
    data class Parse(val cause: Throwable) : AppError
    data class Unknown(val cause: Throwable) : AppError
}
