package com.simplr.mykitta2.data.net.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * POST Account/LoginOTP — confirmed via B2B.postman_collection.json.
 *
 * userId  — local digits only, no country code, no leading zero (e.g. "9171234567")
 * country — dial code without the leading "+" (e.g. "63", "65")
 */
@Serializable
data class LoginOtpRequest(
    val userId: String,
    val country: String,
)

/**
 * POST Account/VerifyLoginOTP — confirmed via B2B.postman_collection.json.
 *
 * firebaseToken — FCM push token; backend may register the device for push on
 * verify success. Empty string is accepted for now since FCM isn't wired yet.
 */
@Serializable
data class VerifyLoginOtpRequest(
    val userId: String,
    val otp: String,
    @SerialName("firebase_token") val firebaseToken: String = "",
)

/**
 * POST Account/VerifyLoginOTP response envelope (legacy `LoginServerResponse`).
 *
 * - `validaty` field name is misspelled in the backend response — preserved verbatim
 *   via `@SerialName` (DO NOT correct; backend contract is frozen).
 * - `isSupervisor` arrives as the string `"True"` / `"False"`, not a JSON bool.
 *   Caller converts via `parseIsSupervisor()`.
 * - `refreshToken` is nullable.
 */
@Serializable
data class VerifyLoginOtpResponse(
    val token: String,
    val refreshToken: String? = null,
    val expiredTime: String,
    @SerialName("validaty") val validaty: String,
    val guidId: String,
    val id: String,
    val userName: String,
    val isSupervisor: String,
    val supervisorCode: String,
) {
    fun parseIsSupervisor(): Boolean = isSupervisor.equals("True", ignoreCase = true)
}
