package com.simplr.mykitta2

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import com.simplr.mykitta2.data.prefs.ThemeStore
import com.simplr.mykitta2.di.IMAGE_HTTP_CLIENT
import com.simplr.mykitta2.feature.splash.SplashStore
import com.simplr.mykitta2.ui.nav.AppNavHost
import com.simplr.mykitta2.ui.splash.SplashScreen
import com.simplr.mykitta2.ui.theme.MyKittaTheme
import io.ktor.client.HttpClient
import org.koin.compose.koinInject
import org.koin.core.qualifier.named

@Composable
fun App() {
    // Coil gets its own thin HttpClient — see KtorClientFactory.createForImages.
    // Sharing the API client breaks image fetching because Ktor's ContentNegotiation
    // plugin auto-adds Accept: application/json and IIS responds 406 for static
    // image files. Idempotent: setSingletonImageLoaderFactory holds the latest
    // factory and the loader is built lazily on first AsyncImage.
    val imageHttpClient: HttpClient = koinInject(qualifier = named(IMAGE_HTTP_CLIENT))
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .components { add(KtorNetworkFetcherFactory(imageHttpClient)) }
            .crossfade(true)
            .build()
    }

    val themeStore: ThemeStore = koinInject()
    val themeMode by themeStore.mode.collectAsStateWithLifecycle()

    MyKittaTheme(themeMode = themeMode) {
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
