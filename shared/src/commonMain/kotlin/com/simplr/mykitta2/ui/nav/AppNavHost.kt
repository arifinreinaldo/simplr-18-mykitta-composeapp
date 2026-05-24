package com.simplr.mykitta2.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.simplr.mykitta2.domain.Country
import com.simplr.mykitta2.feature.auth.LoginOtpScreen
import com.simplr.mykitta2.feature.auth.OtpVerifyScreen
import com.simplr.mykitta2.feature.auth.SignedInPlaceholderScreen
import com.simplr.mykitta2.feature.home.HomeScreen

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Destination.LoginOtp) {
        composable<Destination.LoginOtp> {
            LoginOtpScreen(
                onOtpSent = { phoneE164, userIdDigits, country ->
                    navController.navigate(
                        Destination.OtpVerify(
                            phoneE164 = phoneE164,
                            userIdDigits = userIdDigits,
                            countryIso = country.iso,
                        )
                    )
                },
            )
        }
        composable<Destination.OtpVerify> { backStackEntry ->
            val route = backStackEntry.toRoute<Destination.OtpVerify>()
            // Route arg is a string for Compose Navigation type-safety; recover the
            // enum here. fromIso should never miss for values we put in, but fall
            // back to PH if a future migration introduces an unknown ISO.
            val country = Country.fromIso(route.countryIso) ?: Country.PH
            OtpVerifyScreen(
                phoneE164 = route.phoneE164,
                userIdDigits = route.userIdDigits,
                country = country,
                onVerified = {
                    // Post-OTP lands on Home directly; the auth back-stack is dropped
                    // so the system Back button on Home exits the app instead of
                    // bouncing the user back to the login flow.
                    navController.navigate(Destination.Home) {
                        popUpTo(Destination.LoginOtp) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable<Destination.Home> {
            HomeScreen()
        }
        // Retained for tests / debug routing; no longer reachable from the OTP
        // verify path. Safe to remove once nothing references it.
        composable<Destination.SignedIn> {
            SignedInPlaceholderScreen(
                onBackToLogin = {
                    navController.navigate(Destination.LoginOtp) {
                        popUpTo(Destination.SignedIn) { inclusive = true }
                    }
                },
            )
        }
    }
}
