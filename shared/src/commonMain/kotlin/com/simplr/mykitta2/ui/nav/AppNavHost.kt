package com.simplr.mykitta2.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.simplr.mykitta2.data.repo.AuthRepository
import com.simplr.mykitta2.domain.Country
import com.simplr.mykitta2.feature.address.AddressFormScreen
import com.simplr.mykitta2.feature.address.AddressListScreen
import com.simplr.mykitta2.feature.address.KEY_ADDRESS_SAVED
import com.simplr.mykitta2.feature.auth.LoginOtpScreen
import com.simplr.mykitta2.feature.auth.OtpVerifyScreen
import com.simplr.mykitta2.feature.auth.SignedInPlaceholderScreen
import com.simplr.mykitta2.feature.main.MainShell
import com.simplr.mykitta2.feature.notification.NotificationScreen
import com.simplr.mykitta2.feature.profile.ProfileDetailScreen
import com.simplr.mykitta2.feature.search.SearchScreen
import com.simplr.mykitta2.feature.splash.SplashStore
import com.simplr.mykitta2.ui.nav.PendingNavStore
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun AppNavHost(startDestination: SplashStore.Destination) {
    val navController = rememberNavController()
    val authRepository: AuthRepository = koinInject()
    val logoutScope = rememberCoroutineScope()
    val initialRoute: Destination = when (startDestination) {
        SplashStore.Destination.Home -> Destination.Home
        SplashStore.Destination.Login -> Destination.LoginOtp
    }
    NavHost(navController = navController, startDestination = initialRoute) {
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
            // `Home` is the signed-in entry point; MainShell owns the bottom-nav
            // and the per-tab child NavController.
            MainShell(
                onOpenSearch = { navController.navigate(Destination.Search) },
                onOpenProfileDetail = { navController.navigate(Destination.ProfileDetail) },
                onOpenNotifications = { navController.navigate(Destination.Notifications) },
                onOpenAddressList = { navController.navigate(Destination.AddressList) },
                onLogout = {
                    // Wipe local session, then drop the whole signed-in graph so
                    // VMs die and the system Back button on Login exits the app
                    // instead of bouncing the user back into Home. We don't
                    // surface logout failures — the user asked to leave; trapping
                    // them on Profile is worse than a partially-cleared DB.
                    logoutScope.launch {
                        authRepository.logout()
                        navController.navigate(Destination.LoginOtp) {
                            popUpTo(Destination.Home) { inclusive = true }
                        }
                    }
                },
            )
        }
        composable<Destination.Search> {
            SearchScreen(onBack = { navController.popBackStack() })
        }
        composable<Destination.ProfileDetail> {
            ProfileDetailScreen(onBack = { navController.popBackStack() })
        }
        composable<Destination.AddressList> { backStackEntry ->
            AddressListScreen(
                onBack = { navController.popBackStack() },
                onOpenForm = { id ->
                    navController.navigate(Destination.AddressForm(customerAddressId = id))
                },
                navEntry = backStackEntry,
            )
        }
        composable<Destination.AddressForm> { backStackEntry ->
            val route = backStackEntry.toRoute<Destination.AddressForm>()
            AddressFormScreen(
                customerAddressId = route.customerAddressId,
                onBack = { navController.popBackStack() },
                onSaved = {
                    // Signal the list (via its saved-state handle) that an
                    // address was just persisted, then pop. The list reads
                    // the flag in its LaunchedEffect and shows a snackbar.
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set(KEY_ADDRESS_SAVED, true)
                    navController.popBackStack()
                },
            )
        }
        composable<Destination.Notifications> {
            // Tapping a PRINCIPAL notification needs to deep-link into a
            // sibling NavController (MainShell's tab graph). PendingNavStore
            // carries that intent across — write here, pop back, MainShell
            // observes and navigates on its side.
            val pendingNavStore: PendingNavStore = koinInject()
            NotificationScreen(
                onBack = { navController.popBackStack() },
                onOpenPrincipal = { principalId, principalName ->
                    pendingNavStore.requestPrincipalCatalog(principalId, principalName)
                    navController.popBackStack()
                },
            )
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
