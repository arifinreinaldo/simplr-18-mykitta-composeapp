package com.simplr.mykitta2.di

import com.simplr.mykitta2.data.db.SqlDriverFactory
import com.simplr.mykitta2.data.prefs.SettingsFactory
import com.simplr.mykitta2.feature.auth.CountryDetector
import com.simplr.mykitta2.feature.auth.IosCountryDetector
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module = module {
    single { SettingsFactory() }
    single { SqlDriverFactory() }
    single<CountryDetector> { IosCountryDetector() }
}
