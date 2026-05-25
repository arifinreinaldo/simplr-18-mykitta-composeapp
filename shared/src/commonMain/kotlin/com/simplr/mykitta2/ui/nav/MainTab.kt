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
 * Principal / Rewards / Profile are stubs today — the real screens land in
 * their respective phases. Adding the route up-front means the bottom nav
 * already navigates and per-tab state is preserved across switches. Cart and
 * Chat are intentionally NOT tabs: they live in the Home top bar as shortcuts
 * (matching the legacy product-catalog screen) and will become top-level
 * destinations on the parent NavController when their screens land.
 */
sealed interface MainTab {
    @Serializable data object Home : MainTab
    @Serializable data object Principal : MainTab
    @Serializable data object Rewards : MainTab
    @Serializable data object Profile : MainTab

    /** Catalog scoped to a single principal. Lives on the tab NavController so
     *  back from the catalog returns to the Principal grid; switching tabs
     *  away and back preserves whichever principal was open (saveState). */
    @Serializable data class PrincipalCatalog(
        val principalId: String,
        val principalName: String,
    ) : MainTab
}
