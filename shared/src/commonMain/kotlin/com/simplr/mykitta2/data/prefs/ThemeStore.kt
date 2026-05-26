package com.simplr.mykitta2.data.prefs

import com.russhwolf.settings.Settings
import com.simplr.mykitta2.domain.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persists the user's selected theme mode. Exposes a hot [StateFlow] so
 * `App.kt` re-themes immediately on selection without a manual recomposition
 * trigger. Defaults to [ThemeMode.SYSTEM] when nothing is stored.
 */
interface ThemeStore {
    val mode: StateFlow<ThemeMode>
    fun set(mode: ThemeMode)
}

class SettingsThemeStore(private val settings: Settings) : ThemeStore {
    private val _mode = MutableStateFlow(readMode())
    override val mode: StateFlow<ThemeMode> = _mode.asStateFlow()

    override fun set(mode: ThemeMode) {
        settings.putString(KEY, mode.name)
        _mode.value = mode
    }

    private fun readMode(): ThemeMode {
        val raw = settings.getStringOrNull(KEY) ?: return ThemeMode.SYSTEM
        return runCatching { ThemeMode.valueOf(raw) }.getOrDefault(ThemeMode.SYSTEM)
    }

    private companion object {
        const val KEY = "user.theme_mode"
    }
}
