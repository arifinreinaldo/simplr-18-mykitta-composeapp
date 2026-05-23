---
title: UIState pattern
slug: uistate-pattern
type: deep
verified: 2026-05-23
sources:
  - app/src/main/java/com/b2b/online/domain/UIState.kt
  - app/src/main/java/com/b2b/online/domain/Loading.kt
  - app/src/main/java/com/b2b/online/base/BaseFragment.kt
  - app/src/main/java/com/b2b/online/base/BaseActivity.kt
  - app/src/main/java/com/b2b/online/feature/cart/CartViewModel.kt
  - app/src/main/java/com/b2b/online/feature/cart/CartFragment.kt
  - app/src/main/java/com/b2b/online/feature/auth/AuthViewModel.kt
  - app/src/main/java/com/b2b/online/util/LiveOnce.kt
  - app/src/main/java/com/b2b/online/util/UITextState.kt
---

# UIState pattern

**UIState<T> sealed class plus the Fragment / ViewModel convention every feature uses.**

## What it does

`UIState<T>` is the project-wide envelope every repository-to-UI call rides in.
A Flow / LiveData typed as `UIState<T>` walks through `Loading` -> (`SuccessFromRemote(data)` | `Error(uiText)` | `Expired(msg)`) -> `Finish`, with `Init` as the seed value before any call has fired and `HasMore(Boolean)` reserved for paginated lists. ViewModels expose the stream as `StateFlow` (mainline) or `LiveData<LiveOnce<UIState<T>>>` (one-shot events), and Fragments collect it inside `viewLifecycleOwner.lifecycleScope` + `repeatOnLifecycle(STARTED)`, branching with a `when (state)` to toggle a shimmer / show data / surface an error. A parallel `UIRoom<T>` sealed class exists for Room-only streams that never hit the network and therefore never need `Expired`.

## Where it lives

- `app/src/main/java/com/b2b/online/domain/UIState.kt:6` — `sealed class UIState<out T>`
  - `:7` `Init` (seed)
  - `:8` `SuccessFromRemote<out T>(val data: T)`
  - `:9` `Error(val errorResponse: UITextState)`
  - `:10` `Loading`
  - `:11` `Finish`
  - `:12` `Expired(val errorMessage: UITextState.ServerMessage)` — 401 / token-expired channel
  - `:13` `HasMore(val boolean: Boolean)` — pagination signal
  - `:15-16` `equals`/`hashCode` overridden to always-different so `StateFlow.emit` of the same logical state still fires downstream
- `app/src/main/java/com/b2b/online/domain/UIState.kt:19` — `sealed class UIRoom<out T>` (Room-only twin, no `Expired` / `HasMore`)
- `app/src/main/java/com/b2b/online/util/LiveOnce.kt:4` — `LiveOnce<T>` wrapper; `showOnce()` returns content once, `showAgain()` always returns it
- `app/src/main/java/com/b2b/online/util/UITextState.kt:9` — `UITextState` sealed class carried inside `UIState.Error`
- `app/src/main/java/com/b2b/online/domain/Loading.kt:3` — `LoadingData(isLoading)` for the global activity-level spinner, separate from per-call `UIState.Loading`

Canonical ViewModel (StateFlow style):

- `app/src/main/java/com/b2b/online/feature/auth/AuthViewModel.kt:50` — `MutableStateFlow<UIState<LoginServerResponse>>(UIState.Init)`
- `AuthViewModel.kt:142` — `repository.get().doLogin(...).collect { _userState.emit(it) }`

Canonical ViewModel (LiveOnce style for one-shot results):

- `app/src/main/java/com/b2b/online/feature/cart/CartViewModel.kt:28` — `MutableLiveData<LiveOnce<UIState<List<CartActiveUpdate>>>>`
- `CartViewModel.kt:93` — `repo.doCheckCart(this).collect { _checkPromotion.postValue(LiveOnce(it)) }`

Canonical Fragment consumer:

- `app/src/main/java/com/b2b/online/feature/cart/CartFragment.kt:164` — `setupObserver()` opens a `viewLifecycleOwner.lifecycleScope.launch { repeatOnLifecycle(STARTED) { ... } }` block
- `CartFragment.kt:168-194` — `vm.checkPromotion.observe(...) { it.showOnce()?.let { state -> when (state) { is UIState.Loading -> ... is UIState.Finish -> ... else -> {} } } }`

## Depends on

- [[domain]]
- [[util]]
- [[base]]
- [[repository]]

## Depended on by
- [[address-management]]
- [[authentication]]
- [[chat]]
- [[feature]]
- [[orders]]
- [[product-catalog]]
- [[promotions]]
- [[repository]]
- [[shopping-cart]]

## Gotchas

- **`equals`/`hashCode` are intentionally broken.** `UIState.kt:15-16` returns `false` / `Random.nextInt()`. This is on purpose: `StateFlow` de-duplicates by `equals`, so re-emitting `UIState.Loading` after a previous `Loading` would otherwise be swallowed. Never "fix" this without auditing every `collect` site.
- **`SuccessFromRemote`, not `Success`.** The name calls out that the payload came from the network. Room-only streams use `UIRoom.Success` (`UIState.kt:21`) — don't mix the two; they are not assignable to each other.
- **`Finish` is not `Success`.** Repository flows commonly emit `Loading -> SuccessFromRemote(...) -> Finish` (or `Loading -> Error(...) -> Finish`). Fragments toggle the shimmer off on `Finish`, not on `SuccessFromRemote`. See `CartFragment.kt:180-188`.
- **`LiveOnce` is single-shot per observer dispatch.** `showOnce()` flips `hasBeenHandled = true` the first time it is read (`LiveOnce.kt:12-19`); subsequent observers in the same lifecycle (e.g. after config change) get `null`. Use `showAgain()` if you genuinely need replay. The original gist is Jose Alcerreca's `Event.kt` — same semantics.
- **`Expired` is a separate branch from `Error`.** It carries a `UITextState.ServerMessage` and is the hook for token-refresh / forced-logout flows; `BaseFragment.showExpiredToken` (`BaseFragment.kt:13`) exists to surface it through `MainViewModel.showAlertDialog`. Treating `Expired` as a generic `Error` silently breaks session handling.
- **Errors carry `UITextState`, not raw `String`.** This lets the repository decide between server text, a string resource, or the `QtyInsufficient` structured payload. Render via `UITextState.display(context)` (`UITextState.kt:21`); do not `.toString()` the sealed class.
- **`HasMore` is co-emitted, not terminal.** Paginated repository flows emit `HasMore(true|false)` alongside `SuccessFromRemote`; UIs treat it as a side-channel flag, not a state to render.
- **`LoadingData` is unrelated.** `domain/Loading.kt` is the global blocking-spinner data class wired through `MainViewModel`; do not conflate it with `UIState.Loading`.

## Open questions

- Is the always-unequal `equals`/`hashCode` override actually load-bearing for every consumer, or only for the `StateFlow` paths? Some `LiveData<LiveOnce<UIState>>` sites might not need it.
- `HasMore` only appears in pagination-aware features — is there a canonical list, or is each feature ad-hoc?
- `UIRoom` is defined but few call sites are obvious from this slice; how widely is it actually used vs. just reusing `UIState`?
