package com.simplr.mykitta2.data.net

import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.darwin.Darwin

actual fun httpEngineFactory(): HttpClientEngineFactory<*> = Darwin
