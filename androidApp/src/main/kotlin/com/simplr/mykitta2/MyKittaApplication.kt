package com.simplr.mykitta2

import android.app.Application
import android.content.pm.PackageManager
import com.chuckerteam.chucker.api.ChuckerInterceptor
import com.simplr.mykitta2.core.env.Flavor
import com.simplr.mykitta2.core.env.initBuildEnv
import com.simplr.mykitta2.data.net.AndroidNetworkConfig
import com.simplr.mykitta2.di.initKoin
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.logger.Level

class MyKittaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initBuildEnv(
            flavor = when (BuildConfig.FLAVOR_NAME) {
                "staging" -> Flavor.Staging
                "prod" -> Flavor.Prod
                else -> Flavor.Dev
            },
            versionName = readVersionName(),
            isDebug = BuildConfig.DEBUG,
        )

        // Chucker interceptor — debug variant captures + shows traffic in a notification
        // and an in-app activity; release variant is a no-op (see chucker-no-op dep).
        // Guarded on BuildConfig.DEBUG so release builds skip even the no-op overhead.
        // Must be added BEFORE initKoin so the HttpClient picks it up at construction.
        if (BuildConfig.DEBUG) {
            AndroidNetworkConfig.addInterceptor(ChuckerInterceptor.Builder(this).build())
        }

        initKoin {
            androidContext(this@MyKittaApplication)
            androidLogger(if (BuildConfig.DEBUG) Level.INFO else Level.ERROR)
        }
    }

    private fun readVersionName(): String =
        try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "0.0"
        } catch (_: PackageManager.NameNotFoundException) {
            "0.0"
        }
}
