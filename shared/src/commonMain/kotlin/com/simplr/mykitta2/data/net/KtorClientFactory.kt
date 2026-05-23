package com.simplr.mykitta2.data.net

import com.simplr.mykitta2.core.env.BuildEnv
import com.simplr.mykitta2.core.logging.AppLogger
import com.simplr.mykitta2.data.prefs.TokenStore
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

object KtorClientFactory {
    fun create(
        tokenStore: TokenStore,
        appLogger: AppLogger,
        engineFactory: HttpClientEngineFactory<*> = httpEngineFactory(),
        baseUrl: String = BuildEnv.baseUrl,
        isDebug: Boolean = BuildEnv.isDebug,
    ): HttpClient = HttpClient(engineFactory) {
        expectSuccess = true
        installContentNegotiation()
        installLogging(appLogger, isDebug)
        installTimeouts()
        installAuth(tokenStore)
        installDefaults(baseUrl)
    }

    private fun HttpClientConfig<*>.installContentNegotiation() {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = false
                encodeDefaults = true
            })
        }
    }

    private fun HttpClientConfig<*>.installLogging(appLogger: AppLogger, isDebug: Boolean) {
        install(Logging) {
            level = if (isDebug) LogLevel.HEADERS else LogLevel.INFO
            logger = object : Logger {
                override fun log(message: String) {
                    appLogger.d("Ktor") { redact(message) }
                }
            }
            sanitizeHeader { header -> header.equals(HttpHeaders.Authorization, ignoreCase = true) }
        }
    }

    private fun HttpClientConfig<*>.installTimeouts() {
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 30_000
            socketTimeoutMillis = 30_000
        }
    }

    private fun HttpClientConfig<*>.installAuth(tokenStore: TokenStore) {
        install(Auth) {
            bearer {
                loadTokens {
                    tokenStore.read()?.let { BearerTokens(it.access, it.refresh) }
                }
                refreshTokens {
                    // Refresh endpoint wiring lands with the OTP-verify sub-project.
                    tokenStore.read()?.let { BearerTokens(it.access, it.refresh) }
                }
            }
        }
    }

    private fun HttpClientConfig<*>.installDefaults(baseUrl: String) {
        install(UserAgent) { agent = "MyKitta/${BuildEnv.versionName} (${BuildEnv.flavor.name})" }
        defaultRequest {
            if (baseUrl.isNotEmpty()) url(baseUrl)
            headers.append(HttpHeaders.Accept, ContentType.Application.Json.toString())
        }
    }

    private fun redact(message: String): String =
        message.replace(Regex("(?i)(authorization:\\s*)[^\\r\\n]+"), "$1<redacted>")
}
