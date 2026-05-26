package com.simplr.mykitta2.di

import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.simplr.mykitta2.core.env.BuildEnv
import com.simplr.mykitta2.core.logging.AppLogger
import com.simplr.mykitta2.core.mvi.BaseStoreFactory
import com.simplr.mykitta2.data.db.DatabaseFactory
import com.simplr.mykitta2.data.net.KtorClientFactory
import com.simplr.mykitta2.data.net.api.AuthApi
import com.simplr.mykitta2.data.net.api.CatalogApi
import com.simplr.mykitta2.data.net.api.KtorAuthApi
import com.simplr.mykitta2.data.net.api.KtorCatalogApi
import com.simplr.mykitta2.data.prefs.CountryStore
import com.simplr.mykitta2.data.prefs.ProfileCacheStore
import com.simplr.mykitta2.data.prefs.SessionStore
import com.simplr.mykitta2.data.prefs.SettingsCountryStore
import com.simplr.mykitta2.data.prefs.SettingsFactory
import com.simplr.mykitta2.data.prefs.SettingsProfileCacheStore
import com.simplr.mykitta2.data.prefs.SettingsSessionStore
import com.simplr.mykitta2.data.prefs.SettingsThemeStore
import com.simplr.mykitta2.data.prefs.SettingsTokenStore
import com.simplr.mykitta2.data.prefs.ThemeStore
import com.simplr.mykitta2.data.prefs.TokenStore
import com.simplr.mykitta2.data.repo.AuthRepository
import com.simplr.mykitta2.data.repo.DefaultAuthRepository
import com.simplr.mykitta2.data.repo.DefaultHomeRepository
import com.simplr.mykitta2.data.repo.DefaultNotificationRepository
import com.simplr.mykitta2.data.repo.DefaultPrincipalRepository
import com.simplr.mykitta2.data.repo.DefaultProfileRepository
import com.simplr.mykitta2.data.repo.HomeRepository
import com.simplr.mykitta2.data.repo.LocalDataWiper
import com.simplr.mykitta2.data.repo.MyKittaDatabaseWiper
import com.simplr.mykitta2.data.repo.NotificationRepository
import com.simplr.mykitta2.data.repo.PrincipalRepository
import com.simplr.mykitta2.data.repo.ProfileRepository
import com.simplr.mykitta2.feature.auth.LoginOtpStoreFactory
import com.simplr.mykitta2.feature.auth.LoginOtpViewModel
import com.simplr.mykitta2.feature.auth.OtpVerifyArgs
import com.simplr.mykitta2.feature.auth.OtpVerifyStoreFactory
import com.simplr.mykitta2.feature.auth.OtpVerifyViewModel
import com.simplr.mykitta2.feature.home.HomeStoreFactory
import com.simplr.mykitta2.feature.home.HomeViewModel
import com.simplr.mykitta2.feature.principal.PrincipalStoreFactory
import com.simplr.mykitta2.feature.principal.PrincipalViewModel
import com.simplr.mykitta2.feature.profile.ProfileStoreFactory
import com.simplr.mykitta2.feature.profile.ProfileViewModel
import com.simplr.mykitta2.feature.splash.SplashStoreFactory
import com.simplr.mykitta2.feature.splash.SplashViewModel
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

val coreModule = module {
    single { AppLogger() }
    single { BaseStoreFactory(isDebug = BuildEnv.isDebug, logger = get()) }
    single<StoreFactory> { get<BaseStoreFactory>().create() }
}

val prefsModule = module {
    single<TokenStore> {
        SettingsTokenStore(get<SettingsFactory>().secureSettings("mykitta.tokens"))
    }
    single<CountryStore> {
        SettingsCountryStore(get<SettingsFactory>().plainSettings("mykitta.prefs"))
    }
    single<SessionStore> {
        SettingsSessionStore(get<SettingsFactory>().plainSettings("mykitta.session"))
    }
    single<ThemeStore> {
        SettingsThemeStore(get<SettingsFactory>().plainSettings("mykitta.prefs"))
    }
    single<ProfileCacheStore> {
        SettingsProfileCacheStore(get<SettingsFactory>().plainSettings("mykitta.profile"))
    }
}

val databaseModule = module {
    single { DatabaseFactory(get()) }
    single { get<DatabaseFactory>().create() }
}

val networkModule = module {
    single {
        KtorClientFactory.create(
            tokenStore = get(),
            appLogger = get(),
        )
    }
    // Separate client for Coil image fetching — see KtorClientFactory.createForImages
    // for why we can't share the API client (Accept: application/json → 406 from IIS).
    single(named(IMAGE_HTTP_CLIENT)) { KtorClientFactory.createForImages() }
    single<AuthApi> { KtorAuthApi(get()) }
    single<CatalogApi> { KtorCatalogApi(get()) }
}

const val IMAGE_HTTP_CLIENT = "imageHttpClient"

val repositoryModule = module {
    single<LocalDataWiper> { MyKittaDatabaseWiper(database = get()) }
    single<AuthRepository> {
        DefaultAuthRepository(
            api = get(),
            tokenStore = get(),
            sessionStore = get(),
            countryStore = get(),
            profileCacheStore = get(),
            localDataWiper = get(),
        )
    }
    single<HomeRepository> {
        DefaultHomeRepository(catalogApi = get(), sessionStore = get(), countryStore = get())
    }
    single<NotificationRepository> {
        DefaultNotificationRepository(
            catalogApi = get(),
            sessionStore = get(),
            countryStore = get(),
            db = get(),
        )
    }
    single<PrincipalRepository> {
        DefaultPrincipalRepository(
            catalogApi = get(),
            database = get(),
            sessionStore = get(),
            countryStore = get(),
        )
    }
    single<ProfileRepository> {
        DefaultProfileRepository(
            catalogApi = get(),
            cacheStore = get(),
            sessionStore = get(),
            countryStore = get(),
        )
    }
}

val featureAuthModule = module {
    factory {
        LoginOtpStoreFactory(
            storeFactory = get(),
            countryStore = get(),
            countryDetector = get(),
            authRepository = get(),
        )
    }
    viewModelOf(::LoginOtpViewModel)

    // OtpVerifyStoreFactory needs per-screen args (userIdDigits, country) that
    // come from the navigation route — passed via parametersOf at koinViewModel()
    // call time. ViewModel forwards those to the factory.
    factory { (args: OtpVerifyArgs) ->
        OtpVerifyStoreFactory(
            storeFactory = get(),
            authRepository = get(),
            args = args,
        )
    }
    factory { (args: OtpVerifyArgs) -> OtpVerifyViewModel(storeFactory = get { parametersOf(args) }) }
}

val featureHomeModule = module {
    factory { HomeStoreFactory(storeFactory = get(), homeRepository = get()) }
    viewModelOf(::HomeViewModel)
}

val featurePrincipalModule = module {
    factory { PrincipalStoreFactory(storeFactory = get(), principalRepository = get()) }
    viewModelOf(::PrincipalViewModel)
}

val featureProfileModule = module {
    factory {
        ProfileStoreFactory(
            storeFactory = get(),
            profileRepository = get(),
        )
    }
    viewModelOf(::ProfileViewModel)
}

val featureSplashModule = module {
    factory {
        // Warm-up runs the cheapest possible query against the empty Meta table
        // — this triggers the SQLite driver open + PRAGMA work that would
        // otherwise happen on first feature query (Main thread). Future startup
        // touches can be appended here.
        val database: com.simplr.mykitta2.shared.db.MyKittaDatabase = get()
        SplashStoreFactory(
            storeFactory = get(),
            tokenStore = get(),
            sessionStore = get(),
            warmup = { database.schemaQueries.selectAll().executeAsList() },
            appLogger = get(),
        )
    }
    viewModelOf(::SplashViewModel)
}

fun commonModules(): List<Module> = listOf(
    coreModule,
    prefsModule,
    databaseModule,
    networkModule,
    repositoryModule,
    featureAuthModule,
    featureHomeModule,
    featurePrincipalModule,
    featureProfileModule,
    featureSplashModule,
)

expect val platformModule: Module

fun initKoin(extra: KoinAppDeclaration? = null) {
    startKoin {
        extra?.invoke(this)
        modules(commonModules() + platformModule)
    }
}

/**
 * Swift-friendly entry point. Kotlin Native doesn't propagate Kotlin's default
 * parameter values to Swift, so the parameter-less form here lets Swift call
 * `AppModuleKt.doInitKoinIos()` without having to pass `extra: nil`.
 *
 * Note: Kotlin renames any function starting with `init` to `doInit...` when
 * exposed to Swift (the `init` keyword conflicts with Swift's initializer
 * syntax). So this is callable as `AppModuleKt.doInitKoinIos()` from Swift.
 */
fun initKoinIos() = initKoin()
