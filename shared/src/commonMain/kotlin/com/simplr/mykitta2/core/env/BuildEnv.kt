package com.simplr.mykitta2.core.env

import com.simplr.mykitta2.domain.Country

expect object BuildEnv {
    val flavor: Flavor
    val versionName: String
    val isDebug: Boolean
    val appName: String

    /** Per-country backend root, e.g. `http://ph.example/api/`. Each Country gets a separate server. */
    fun baseUrlFor(country: Country): String
}
