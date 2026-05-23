package com.simplr.mykitta2.data.net.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LoginOtpRequest(
    val phone: String,
    val country: String,
)

@Serializable
data class LoginOtpResponse(
    val success: Boolean = true,
    val message: String? = null,
)

@Serializable
data class ErrorEnvelope(
    @SerialName("error") val error: String? = null,
    @SerialName("message") val message: String? = null,
    @SerialName("code") val code: Int? = null,
)
