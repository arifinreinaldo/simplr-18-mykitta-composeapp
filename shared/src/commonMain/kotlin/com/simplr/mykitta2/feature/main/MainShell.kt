package com.simplr.mykitta2.feature.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.simplr.mykitta2.feature.home.HomeScreen
import com.simplr.mykitta2.feature.principal.PrincipalScreen
import com.simplr.mykitta2.feature.profile.ProfileScreen
import com.simplr.mykitta2.ui.common.PlatformBackButton
import com.simplr.mykitta2.ui.nav.MainTab

/**
 * Post-auth shell. Owns its own NavController so tab switches don't pollute the
 * top-level NavController (which still holds LoginOtp / OtpVerify / MainShell).
 * Each tab is a standalone composable; they're free to nest their own Scaffolds
 * for per-screen TopAppBars (HomeScreen does this).
 *
 * Cart and Chat aren't tabs — they're top-bar shortcuts on Home (matching the
 * legacy product-catalog layout) and will become parent-NavController
 * destinations when their screens land.
 */
@Composable
fun MainShell(
    onOpenSearch: () -> Unit = {},
    onOpenProfileDetail: () -> Unit = {},
    onLogout: () -> Unit = {},
) {
    val tabNavController = rememberNavController()
    val currentDest = tabNavController.currentBackStackEntryAsState().value?.destination

    Scaffold(
        bottomBar = { MainBottomBar(currentDest, tabNavController) },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        NavHost(
            navController = tabNavController,
            startDestination = MainTab.Home,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            composable<MainTab.Home> {
                HomeScreen(
                    // Cart, Chat, Notifications screens land in later phases —
                    // keep callbacks as no-ops for now so the icons still react.
                    onOpenCart = { /* Cart destination lands in a later phase. */ },
                    onOpenChat = { /* Chat destination lands in a later phase. */ },
                    onOpenNotifications = { /* Notification destination lands in a later phase. */ },
                    onOpenRewards = { tabNavController.switchTab(MainTab.Rewards) },
                    onOpenSearch = onOpenSearch,
                )
            }
            composable<MainTab.Principal> {
                PrincipalScreen(
                    onOpenCatalog = { principal ->
                        tabNavController.navigate(
                            MainTab.PrincipalCatalog(
                                principalId = principal.principalId,
                                principalName = principal.principalName,
                            )
                        )
                    },
                )
            }
            composable<MainTab.PrincipalCatalog> { backStackEntry ->
                val route = backStackEntry.toRoute<MainTab.PrincipalCatalog>()
                PrincipalCatalogStub(
                    principalName = route.principalName,
                    onBack = { tabNavController.popBackStack() },
                )
            }
            composable<MainTab.Rewards> { TabStub("Rewards") }
            composable<MainTab.Profile> {
                ProfileScreen(
                    onMenuClick = { id ->
                        // Only "profile" has a destination wired today; the
                        // rest of the menu rows (stores, shipment, history,
                        // principal, faq, tutorial, about) remain stubs until
                        // their feature surfaces land.
                        if (id == "profile") onOpenProfileDetail()
                    },
                    onLogout = onLogout,
                )
            }
        }
    }
}

/** Placeholder for the principal-scoped catalog. Real screen lands when the
 *  catalog phase begins; for now it just acknowledges the tap and offers Back. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrincipalCatalogStub(principalName: String, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(principalName) },
                navigationIcon = { PlatformBackButton(onClick = onBack) },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "$principalName catalog — coming soon",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MainBottomBar(currentDest: NavDestination?, navController: NavController) {
    NavigationBar {
        // 4 explicit items rather than a list-driven loop so `hasRoute<T>()` keeps
        // its reified type — no reflection, no string-matching on class names.
        NavigationBarItem(
            selected = currentDest?.hasRoute<MainTab.Home>() == true,
            onClick = { navController.switchTab(MainTab.Home) },
            icon = { Text("🏠", fontSize = 18.sp) },
            label = { Text("Home") },
        )
        NavigationBarItem(
            selected = currentDest?.hasRoute<MainTab.Principal>() == true,
            onClick = { navController.switchTab(MainTab.Principal) },
            icon = { Text("🏷️", fontSize = 18.sp) },
            label = { Text("Principal") },
        )
        NavigationBarItem(
            selected = currentDest?.hasRoute<MainTab.Rewards>() == true,
            onClick = { navController.switchTab(MainTab.Rewards) },
            icon = { Text("🎁", fontSize = 18.sp) },
            label = { Text("Rewards") },
        )
        NavigationBarItem(
            selected = currentDest?.hasRoute<MainTab.Profile>() == true,
            onClick = { navController.switchTab(MainTab.Profile) },
            icon = { Text("👤", fontSize = 18.sp) },
            label = { Text("My Profile") },
        )
    }
}

/**
 * Tab switch with single-top + saveState semantics — same options pattern the
 * Compose Navigation docs recommend for bottom-nav tabs. Each tab's back-stack
 * and scroll state survive a switch away and back.
 */
private fun NavController.switchTab(tab: MainTab) {
    navigate(tab) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Placeholder content for tabs whose real screen hasn't landed yet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TabStub(title: String) {
    Scaffold(topBar = { TopAppBar(title = { Text(title) }) }) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "$title — coming soon",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}