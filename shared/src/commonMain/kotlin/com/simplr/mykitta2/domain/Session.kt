package com.simplr.mykitta2.domain

/**
 * Authenticated session derived from [VerifyLoginOTP][com.simplr.mykitta2.data.net.dto.VerifyLoginOtpResponse].
 *
 * Fields mirror the legacy SharedPreferences contract (`SUPERVISOR`, `USER`,
 * `ISSUPERVISOR`) so downstream `User/GetObject` requests can populate
 * `user`/`supervisor` parameters identically.
 */
data class Session(
    val userName: String,
    val supervisorCode: String,
    val isSupervisor: Boolean,
)
