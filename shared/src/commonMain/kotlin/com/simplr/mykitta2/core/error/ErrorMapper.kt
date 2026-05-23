package com.simplr.mykitta2.core.error

import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.serialization.JsonConvertException
import io.ktor.utils.io.errors.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException

object ErrorMapper {
    fun from(throwable: Throwable): AppError {
        if (throwable is CancellationException) throw throwable
        return when (throwable) {
            is HttpRequestTimeoutException,
            is ConnectTimeoutException,
            is SocketTimeoutException,
            is IOException -> AppError.Network

            is ClientRequestException -> {
                if (throwable.response.status.value == 401) AppError.Unauthorized
                else AppError.Http(throwable.response.status.value, throwable.message)
            }

            is ServerResponseException ->
                AppError.Http(throwable.response.status.value, throwable.message)

            is ResponseException ->
                AppError.Http(throwable.response.status.value, throwable.message)

            is JsonConvertException,
            is SerializationException -> AppError.Parse(throwable)

            else -> AppError.Unknown(throwable)
        }
    }

    fun message(error: AppError): String = when (error) {
        AppError.Network -> "Network unavailable. Check your connection and try again."
        AppError.Unauthorized -> "Your session expired. Please sign in again."
        is AppError.Http -> when (error.status) {
            in 400..499 -> "Request rejected (${error.status})."
            in 500..599 -> "Server error (${error.status}). Try again shortly."
            else -> "Unexpected response (${error.status})."
        }
        is AppError.Parse -> "Couldn't read the server response."
        is AppError.Unknown -> "Something went wrong. Please try again."
    }
}
