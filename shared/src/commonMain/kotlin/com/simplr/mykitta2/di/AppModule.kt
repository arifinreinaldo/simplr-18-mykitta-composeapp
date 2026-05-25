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
import com.simplr.mykitta2.data.prefs.SessionStore
import com.simplr.mykitta2.data.prefs.SettingsCountryStore
import com.simplr.mykitta2.data.prefs.SettingsFactory
import com.simplr.mykitta2.data.prefs.SettingsSessionStore
import com.simplr.mykitta2.data.prefs.SettingsTokenStore
import com.simplr.mykitta2.data.prefs.TokenStore
import com.simplr.mykitta2.data.repo.AuthRepository
import com.simplr.mykitta2.data.repo.DefaultAuthRepository
import com.simplr.mykitta2.data.repo.DefaultHomeRepository
import com.simplr.mykitta2.data.repo.HomeRepository
import com.simplr.mykitta2.feature.auth.LoginOtpStoreFactory
import com.simplr.mykitta2.feature.auth.LoginOtpViewModel
import com.simplr.mykitta2.feature.auth.OtpVerifyArgs
import com.simplr.mykitta2.feature.auth.OtpVerifyStoreFactory
import com.simplr.mykitta2.feature.auth.OtpVerifyViewModel
import com.simplr.mykitta2.feature.home.HomeStoreFactory
import com.simplr.mykitta2.feature.home.HomeViewModel
import com.simplr.mykitta2.feature.splash.SplashStoreFactory
import com.simplr.mykitta2.feature.splash.SplashViewModel
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.parameter.parametersOf
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
    single<AuthApi> { KtorAuthApi(get()) }
    single<CatalogApi> { KtorCatalogApi(get()) }
}

val repositoryModule = module {
    single<AuthRepository> {
        DefaultAuthRepository(api = get(), tokenStore = get(), sessionStore = get())
    }
    single<HomeRepository> {
        DefaultHomeRepository(catalogApi = get(), sessionStore = get(), countryStore = get())
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
