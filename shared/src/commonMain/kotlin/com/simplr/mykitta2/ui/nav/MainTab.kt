package com.simplr.mykitta2.ui.nav

import kotlinx.serialization.Serializable

/**
 * Tab routes inside the signed-in bottom-nav shell.
 *
 * Kept separate from [Destination] because these are nested in
 * [com.simplr.mykitta2.feature.main.MainShell]'s child NavController, while
 * [Destination] entries live in the top-level NavHost that also hosts the
 * unauthenticated auth flow.
 *
 * Cart / Chat / Profile / Directory are stubs today — the real screens land
 * in their respective phases. Adding the route up-front means the bottom nav
 * already navigates and per-tab state is preserved across switches.
 */
sealed interface MainTab {
    @Serializable data object Home : MainTab
    @Serializable data object Directory : MainTab
    @Serializable data object Cart : MainTab
    @Serializable data object Chat : MainTab
    @Serializable data object Profile : MainTab
}
