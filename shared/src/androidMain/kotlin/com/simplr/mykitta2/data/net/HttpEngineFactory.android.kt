package com.simplr.mykitta2.data.net

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.Interceptor

/**
 * Host app contributes OkHttp interceptors here BEFORE the HttpClient is built —
 * typically in Application.onCreate. Chucker is the canonical use case.
 *
 * Encapsulated as add-only — the internal list can't be cleared by callers,
 * removing a footgun where a late `clear()` would silently strip Chucker.
 */
object AndroidNetworkConfig {
    private val _interceptors = mutableListOf<Interceptor>()
    internal val interceptors: List<Interceptor> get() = _interceptors

    fun addInterceptor(interceptor: Interceptor) {
        _interceptors += interceptor
    }
}

actual fun createPlatformHttpClient(
    config: HttpClientConfig<*>.() -> Unit,
): HttpClient = HttpClient(OkHttp) {
    config()
    engine {
        AndroidNetworkConfig.interceptors.forEach { addInterceptor(it) }
    }
}
