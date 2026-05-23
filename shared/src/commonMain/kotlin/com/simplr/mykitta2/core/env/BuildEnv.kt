package com.simplr.mykitta2.core.env

expect object BuildEnv {
    val flavor: Flavor
    val baseUrl: String
    val versionName: String
    val isDebug: Boolean
    val appName: String
}
