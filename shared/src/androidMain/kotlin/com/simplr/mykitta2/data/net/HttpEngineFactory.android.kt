package com.simplr.mykitta2.data.net

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.okhttp.OkHttp

actual fun httpEngineFactory(): HttpClientEngineFactory<*> = OkHttp
