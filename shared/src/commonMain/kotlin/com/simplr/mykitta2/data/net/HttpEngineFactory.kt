package com.simplr.mykitta2.data.net

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig

/**
 * Build a platform-configured Ktor HttpClient.
 *
 * On Android this wires the OkHttp engine and applies any interceptors registered
 * in `AndroidNetworkConfig.interceptors` (populated by the host app before Koin
 * builds the client — see MyKittaApplication for Chucker). On iOS it wires the
 * Darwin engine.
 */
expect fun createPlatformHttpClient(
    config: HttpClientConfig<*>.() -> Unit,
): HttpClient
