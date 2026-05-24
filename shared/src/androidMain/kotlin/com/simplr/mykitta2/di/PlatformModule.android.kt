package com.simplr.mykitta2.di

import com.simplr.mykitta2.data.db.SqlDriverFactory
import com.simplr.mykitta2.data.prefs.SettingsFactory
import com.simplr.mykitta2.feature.auth.AndroidCountryDetector
import com.simplr.mykitta2.feature.auth.CountryDetector
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    single { SettingsFactory(androidContext()) }
    single { SqlDriverFactory(androidContext()) }
    single<CountryDetector> { AndroidCountryDetector(androidContext()) }
}
