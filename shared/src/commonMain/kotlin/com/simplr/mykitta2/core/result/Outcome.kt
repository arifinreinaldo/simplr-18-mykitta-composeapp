package com.simplr.mykitta2.core.result

import com.simplr.mykitta2.core.error.AppError

sealed interface Outcome<out T> {
    data object Idle : Outcome<Nothing>
    data object Loading : Outcome<Nothing>
    data class Success<T>(val value: T) : Outcome<T>
    data class Failure(val error: AppError) : Outcome<Nothing>
}

inline fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> = when (this) {
    is Outcome.Success -> Outcome.Success(transform(value))
    is Outcome.Failure -> this
    Outcome.Idle -> Outcome.Idle
    Outcome.Loading -> Outcome.Loading
}
