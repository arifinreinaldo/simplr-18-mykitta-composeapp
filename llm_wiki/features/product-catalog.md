---
title: Product catalog
slug: product-catalog
type: feature
verified: 2026-05-23
sources:
  - app/src/main/java/com/b2b/online/feature/home/HomeFragment.kt
  - app/src/main/java/com/b2b/online/feature/home/HomeViewModel.kt
  - app/src/main/java/com/b2b/online/feature/home/search/SearchFragment.kt
  - app/src/main/java/com/b2b/online/feature/home/search/SearchViewModel.kt
  - app/src/main/java/com/b2b/online/feature/brand/directory/DirectoryFragment.kt
  - app/src/main/java/com/b2b/online/feature/brand/directory/DirectoryViewModel.kt
  - app/src/main/java/com/b2b/online/feature/brand/detail/DetailBrandFragment.kt
  - app/src/main/java/com/b2b/online/feature/brand/detail/DetailBrandViewModel.kt
  - app/src/main/java/com/b2b/online/feature/item/detail/DetailItemFragment.kt
  - app/src/main/java/com/b2b/online/feature/item/detail/DetailItemViewModel.kt
  - app/src/main/java/com/b2b/online/feature/item/detail/PurchaseDialogFragment.kt
  - app/src/main/java/com/b2b/online/feature/item/detail/PurchaseDialogViewModel.kt
  - app/src/main/java/com/b2b/online/feature/item/list/ListItemFragment.kt
  - app/src/main/java/com/b2b/online/feature/item/list/ListItemViewModel.kt
  - app/src/main/java/com/b2b/online/domain/Item.kt
  - app/src/main/java/com/b2b/online/domain/ItemDisplay.kt
  - app/src/main/java/com/b2b/online/domain/Category.kt
  - app/src/main/java/com/b2b/online/domain/Banner.kt
  - app/src/main/java/com/b2b/online/domain/Variant.kt
---

# Product catalog

**Home, brand, item browsing and search; feature/home/, feature/brand/, feature/item/.**

## What it does

The catalog is the user-facing surface for discovering products. `HomeFragment`
is the landing tab and acts as a composite: a top banner slider (`Banner`),
server-driven horizontal carousels keyed by `ConfigList` (Most-Buy, Last-Buy,
category rails of `Item`), badged toolbar shortcuts for cart/notif/chat, and a
non-focusable search bar that opens `SearchFragment`. The brand directory
(`DirectoryFragment`) renders a 2-column grid of `Principal` brands; tapping
one opens `DetailBrandFragment`, which hosts a per-brand banner slider plus a
`ViewPager2` of {Items, Promo, History, About} powered by `ListItemFragment`,
`PromoFragment`, and `OrderFragment`. `DetailItemFragment` shows item images,
description, AI reason, suggestion rail, and a promotion variant rail; picking
a `Variant` opens `PurchaseDialogFragment` which builds a `Cart` (base +
optional bonus rows) before delegating to the [[shopping-cart]] insert path.
Search is a 1- or 2-column grid (toggleable) of `Item` results with sort
(`Highest/Lowest Price`, `Newest`, `Best Seller`) and price-range filter,
optionally scoped to a `principalId` passed via nav args.

## Where it lives

- `app/src/main/java/com/b2b/online/feature/home/HomeFragment.kt:66` — landing
  setup; banner slider, multi-list adapter, badges.
- `app/src/main/java/com/b2b/online/feature/home/HomeFragment.kt:352` —
  `categoryList.collectLatest` reconciles loading vs loaded rails by `Title`.
- `app/src/main/java/com/b2b/online/feature/home/HomeViewModel.kt:50` —
  `refreshList()` walks `getConfigListFlow()` and fans out `getCategoryData()`.
- `app/src/main/java/com/b2b/online/feature/home/search/SearchFragment.kt:175` —
  `onSearch` query commit; resets paging via `vm.query` setter.
- `app/src/main/java/com/b2b/online/feature/home/search/SearchViewModel.kt:83` —
  `_reset.debounce(500)` collapses rapid filter/sort changes into one fetch.
- `app/src/main/java/com/b2b/online/feature/brand/directory/DirectoryFragment.kt:64`
  — 2-col grid; tap routes to `DetailBrandFragment`.
- `app/src/main/java/com/b2b/online/feature/brand/detail/DetailBrandFragment.kt:108`
  — `PrincipalTabAdapter` wires the Items/Promo/History tabs.
- `app/src/main/java/com/b2b/online/feature/item/list/ListItemFragment.kt:83` —
  collects `getListItemByPrincipal` and tracks `vm.offset = it.size`.
- `app/src/main/java/com/b2b/online/feature/item/list/ListItemFragment.kt:139` —
  `scrollListener` triggers `vm.fetchData()` for pagination.
- `app/src/main/java/com/b2b/online/feature/item/detail/DetailItemFragment.kt:49`
  — kicks off image, promo, principal, suggestion fetches off `args.item`.
- `app/src/main/java/com/b2b/online/feature/item/detail/DetailItemFragment.kt:129`
  — variant rail; tapping a `Variant` opens `PurchaseDialogFragment`.

## Depends on

- [[repository]]
- [[room-database]]
- [[uistate-pattern]]
- [[domain]]
- [[feature]]
- [[shopping-cart]]
- [[promotions]]
- [[chat]]
- [[orders]]
- [[localization]]

## Depended on by
- [[promotions]]
- [[repository]]
- [[shopping-cart]]

## Gotchas

- **Out-of-stock guard is at the navigation site, not on the row.** Every
  `Item` click site checks `if (item.InvQty > 0)` before navigating — see
  `HomeFragment.kt:190`, `SearchFragment.kt:90`, `DetailItemFragment.kt:189`,
  `ListItemFragment.kt:133`. The row itself still renders and stays clickable,
  only `soldOut` overlay is toggled. New entry points must replicate the check
  or sold-out items become tappable.
- **Variant "retail price" is name-detected, not flagged.** Both the rail
  binder (`DetailItemFragment.kt:120`) and the purchase dialog
  (`PurchaseDialogFragment.kt:140`) call `startsWith(PREFIX_DEF_PROMO)` on
  `Variant.promotionDesc` to swap in the `R.string.retail_price` label. If the
  backend prefix changes, the default-promo affordance silently shows the raw
  promo code.
- **Banner caching is Room-backed and per-principal.** `getBannerFlow()` (home)
  and `getBannerFlow(id)` (brand detail) are Room observables; the slider
  shimmer hides only when the emitted list is non-empty
  (`HomeFragment.kt:327`). A cold cache shows the shimmer until the first
  network refresh writes through — there is no explicit timeout.
- **Search debounce lives on filter/sort changes, not on keystrokes.** The
  query field uses `onSearch` (IME action), so typing does not fire requests;
  only sort/low/high setters call `doSearch()` which emits to `_reset` and is
  debounced 500 ms in `SearchViewModel.init`. Initial fetch flag
  (`initialSearch = true`) deletes the prior cache; subsequent paginations
  pass `false`.
- **Pagination is offset-driven from list size, not page index.** `ListItem`
  and `Search` both do `vm.offset = it.size` after every collect, then
  `scrollListener { vm.fetchData() }` re-fetches with that offset. The server
  must return strict appended pages — duplicate IDs would be re-inserted.
- **`HomeViewModel.refreshList()` collects `getConfigListFlow()` indefinitely.**
  Each refresh launches a new `collectLatest` without cancelling prior
  collectors (only the inner `collectLatest` cancels). Spamming pull-to-refresh
  leaks coroutines bound to the VM scope.
- **`HomeFragment` saves recycler scroll state per category name into
  `SavedStateHandle`** (`HomeFragment.kt:206` / `HomeViewModel.kt:140`). If two
  categories share a `Title`, their state collides.

## Open questions

- Does the suggestion rail (`DetailItemViewModel.getItemSuggestion`) honour
  stock filtering server-side, or can it return out-of-stock suggestions that
  the user can still tap? The click guard is present, but the rail looks busy
  if many items are sold-out.
- `SearchViewModel` calls `repository.deleteSearch()` in `init`, which wipes
  cached results on every fragment recreation. Is the intent to never persist
  search results across rotations, or is this a workaround for stale
  pagination?
- `ListItemFragment` arg `IS_PROMO` is parsed but never read after assignment
  — is the promo-list path expected here, or is this dead state superseded by
  `PromoFragment`?
