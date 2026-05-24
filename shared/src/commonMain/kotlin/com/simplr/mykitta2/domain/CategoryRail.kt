package com.simplr.mykitta2.domain

/**
 * Server-driven horizontal rail on the home screen. The `functionName` is the
 * `User/GetObject` endpoint discriminator (e.g. `GetItem`, `GetLastOrder`); the
 * `title` is the user-visible label the backend ships in `ConfigList.Description`.
 *
 * `loading == true` means the rail has been declared (config arrived) but its
 * items haven't fanned-in yet. UI renders a shimmer in that state.
 */
data class CategoryRail(
    val functionName: String,
    val title: String,
    val displayOrder: Int,
    val items: List<Item> = emptyList(),
    val loading: Boolean = true,
)
