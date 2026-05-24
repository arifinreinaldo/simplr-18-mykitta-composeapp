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
