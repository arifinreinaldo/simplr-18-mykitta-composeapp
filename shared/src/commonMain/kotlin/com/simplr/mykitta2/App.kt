package com.simplr.mykitta2

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import com.simplr.mykitta2.feature.splash.SplashStore
import com.simplr.mykitta2.ui.nav.AppNavHost
import com.simplr.mykitta2.ui.splash.SplashScreen
import com.simplr.mykitta2.ui.theme.MyKittaTheme
import io.ktor.client.HttpClient
import org.koin.compose.koinInject

@Composable
fun App() {
    // Wire Coil 3's singleton ImageLoader to reuse our Ktor HttpClient (auth,
    // logging, Chucker on debug) instead of standing up a parallel network
    // stack. Idempotent — Coil retains the latest factory, so this is safe to
    // run on every recomposition; the actual loader is built lazily on first
    // AsyncImage and reused thereafter.
    val httpClient: HttpClient = koinInject()
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .components { add(KtorNetworkFetcherFactory(httpClient)) }
            .crossfade(true)
            .build()
    }

    MyKittaTheme {
        // Splash sits outside the nav graph so it can't be navigated back to.
        // We persist the resolved destination name across rotation — splashing
        // again after a config change would be jarring. The enum is round-tripped
        // as its String name; primitives are universally Saveable on both
        // Android (Bundle) and iOS (Compose Multiplatform state holder).
        var savedDestination by rememberSaveable { mutableStateOf<String?>(null) }
        val destination = savedDestination?.let(SplashStore.Destination::valueOf)

        if (destination == null) {
            SplashScreen(onDestination = { savedDestination = it.name })
        } else {
            AppNavHost(startDestination = destination)
        }
    }
}
