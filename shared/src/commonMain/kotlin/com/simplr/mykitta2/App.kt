package com.simplr.mykitta2

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.simplr.mykitta2.ui.nav.AppNavHost
import com.simplr.mykitta2.ui.splash.SplashScreen
import com.simplr.mykitta2.ui.theme.MyKittaTheme

@Composable
fun App() {
    MyKittaTheme {
        // Splash sits outside the nav graph so it can't be navigated back to.
        // rememberSaveable survives rotation — once the splash plays it stays gone.
        var splashDone by rememberSaveable { mutableStateOf(false) }
        if (splashDone) {
            AppNavHost()
        } else {
            SplashScreen(onFinished = { splashDone = true })
        }
    }
}
