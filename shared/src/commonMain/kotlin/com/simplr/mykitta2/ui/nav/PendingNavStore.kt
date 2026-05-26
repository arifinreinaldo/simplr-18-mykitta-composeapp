package com.simplr.mykitta2.ui.nav

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * One-shot channel for cross-NavController deep-links. `NotificationScreen`
 * lives on the outer NavController; `MainTab.PrincipalCatalog` lives on the
 * inner one (owned by MainShell). The notification screen writes here,
 * `MainShell` observes + [consume]s once. Reusable for Phase 2 push deep-links.
 */
class PendingNavStore {
    private val _pendingPrincipal = MutableStateFlow<PendingPrincipal?>(null)
    val pendingPrincipal: StateFlow<PendingPrincipal?> = _pendingPrincipal.asStateFlow()

    fun requestPrincipalCatalog(id: String, name: String) {
        _pendingPrincipal.value = PendingPrincipal(id, name)
    }

    fun consume() {
        _pendingPrincipal.value = null
    }

    data class PendingPrincipal(val principalId: String, val principalName: String)
}
