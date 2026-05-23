package com.simplr.mykitta2.data.net

import io.ktor.client.engine.HttpClientEngineFactory

expect fun httpEngineFactory(): HttpClientEngineFactory<*>
