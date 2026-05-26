package com.simplr.mykitta2.domain

import kotlinx.serialization.Serializable

/**
 * Fetched-on-demand user profile detail. Returned by the legacy `GetProfile`
 * call (`POST User/GetObject` with `functionName="GetProfile"`) and cached for
 * 24h in plain prefs so profile screens paint instantly on re-open.
 *
 * Distinct from [Session]: `Session` is the small set of fields persisted at
 * login (used to populate request envelopes — `userName` there is actually
 * the phone number); `Profile.custName` is the human-readable outlet name
 * that drives the "Welcome back, …" greeting. Fields default to empty strings
 * rather than null so the UI can apply uniform "show row when non-blank" logic.
 *
 * `email` is parsed (kept in the DTO) but currently not displayed anywhere.
 * `@Serializable` exists so [com.simplr.mykitta2.data.prefs.ProfileCacheStore]
 * can JSON-encode the whole object into a single pref slot.
 */
@Serializable
data class Profile(
    val custName: String = "",
    val phone: String = "",
    val email: String = "",
    val icPartner: String = "",
    val gstNo: String = "",
)
