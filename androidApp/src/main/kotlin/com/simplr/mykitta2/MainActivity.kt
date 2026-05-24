package com.simplr.mykitta2

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Install the AndroidX splash compat — applies on API 23+. We make the
        // system splash visually invisible (transparent icon, white background
        // via Theme.MyKitta.Splash) and kill the exit animation so the OS-level
        // splash hands off instantly to the custom Compose SplashScreen.
        installSplashScreen().setOnExitAnimationListener { it.remove() }

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { App() }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
