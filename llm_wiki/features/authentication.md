---
title: Authentication
slug: authentication
type: feature
verified: 2026-05-23
sources:
  - app/src/main/java/com/b2b/online/feature/auth/AuthActivity.kt
  - app/src/main/java/com/b2b/online/feature/auth/AuthViewModel.kt
  - app/src/main/java/com/b2b/online/feature/auth/login/LoginFragment.kt
  - app/src/main/java/com/b2b/online/feature/auth/login/LoginViewModel.kt
  - app/src/main/java/com/b2b/online/feature/auth/login/otp/LoginOTPFragment.kt
  - app/src/main/java/com/b2b/online/feature/auth/login/otp/LoginOTPViewModel.kt
  - app/src/main/java/com/b2b/online/feature/auth/login/otp/VerifyOTPFragment.kt
  - app/src/main/java/com/b2b/online/feature/auth/register/RegisterPickerFragment.kt
  - app/src/main/java/com/b2b/online/feature/auth/register/VerificationDialogFragment.kt
  - app/src/main/java/com/b2b/online/feature/auth/register/VerificationDialogViewModel.kt
  - app/src/main/java/com/b2b/online/feature/auth/register/partner/RegisterPartnerFragment.kt
  - app/src/main/java/com/b2b/online/feature/auth/register/partner/RegisterPartnerViewModel.kt
  - app/src/main/java/com/b2b/online/feature/auth/register/ph/RegisterPHFragment.kt
  - app/src/main/java/com/b2b/online/feature/auth/register/ph/RegisterPHViewModel.kt
  - app/src/main/java/com/b2b/online/feature/auth/register/sg/RegisterFragment.kt
  - app/src/main/java/com/b2b/online/feature/auth/register/sg/RegisterViewModel.kt
  - app/src/main/java/com/b2b/online/feature/auth/register/principalpicker/PrincipalPickerFragment.kt
  - app/src/main/java/com/b2b/online/feature/auth/register/principalpicker/PrincipalPickerViewModel.kt
  - app/src/main/java/com/b2b/online/feature/auth/country/CountryListFragment.kt
  - app/src/main/java/com/b2b/online/feature/auth/waiting/WaitingFragment.kt
  - app/src/main/java/com/b2b/online/domain/User.kt
  - app/src/main/java/com/b2b/online/util/OTPView.kt
  - app/src/main/java/com/b2b/online/util/SmsBroadcastReceiver.kt
---

# Authentication

**Login, register, OTP waiting, country picker; feature/auth/.**

## What it does

`AuthActivity` is the unauthenticated entry point. On boot, `AuthViewModel.setDefaultScreen()`
reads SharedPreferences and routes the user through one of three states (`AuthState.COUNTRY`,
`AuthState.LOGIN`, `AuthState.MAIN`): no `Country` set → country picker; `Country` set but no
`Token` → login OTP; both set → bounce straight into `MainActivity`.

The happy path is **country pick → phone entry → request OTP → verify OTP → MainActivity**.
Country selection writes `C.COUNTRY` (`"PHILIPPINE"` / `"SINGAPORE"`) and forwards to the
phone-number screen, which uses `CountryHelper` to format and validate against country prefix
(+63 / +65). Submitting calls `AuthViewModel.doLoginOTP()` → `Repository.doLoginOTP()`; on
success the user is navigated to `VerifyOTPFragment`. Registration is a branch from
`LoginOTPFragment` → `RegisterPickerFragment`, which picks an owner flow (SG → `RegisterFragment`,
PH → `RegisterPHFragment`) or a partner/referral flow (`RegisterPartnerFragment`).
PH registration and partner registration each embed `VerificationDialogFragment` (bottom sheet)
to verify the partner phone number before submission.

SMS auto-read is wired via Google Play Services' `SmsRetriever` API. `VerifyOTPFragment.setupSMSReceiver()`
calls `SmsRetriever.getClient(...).startSmsRetriever()` and registers a `SmsBroadcastReceiver`
listening for `SmsRetriever.SMS_RETRIEVED_ACTION`. When a matching SMS arrives the receiver fires
`SmsListener.sendSMS`, which runs `String.extractOTP()` and auto-submits via `vm.verifyOtp()`.
Manual entry uses the 4-cell `OTPView` custom widget — typing the 4th digit auto-advances and
calls `OTPListener.onSuccess` to submit. On a successful verify, `Repository` writes `C.TOKEN`
(plus user profile, country code, Firebase/Huawei push token) to SharedPreferences and
`AuthViewModel.goMain()` posts `AuthState.MAIN`, which `AuthActivity` consumes to start `MainActivity`
and `finish()` itself. After fresh registration, `setTutorialAfterRegis()` clears the tutorial
flag (`C.TUTORIAL = false`) before bouncing to main. `WaitingFragment` is a separate post-login
gate used for users whose principal assignment is pending — it polls `mainVM.repeatCheck()` and
auto-navigates home once at least one active principal arrives.

## Where it lives

- `app/src/main/java/com/b2b/online/feature/auth/AuthActivity.kt:38` — `onCreate` installs the
  splash screen (held until `vm.isReady`), inflates `auth_nav`, and observes `vm.currentScreen`
  at line 140 to flip nav graph start destination or jump to `MainActivity`.
- `app/src/main/java/com/b2b/online/feature/auth/AuthViewModel.kt:93` — `init` triggers
  `setDayNightTheme`, `setDefaultScreen` (line 119, the routing logic), and `loadPHData` (PH
  CSV seed via `RepositoryPHLocation`).
- `AuthViewModel.kt:148` — `doLoginOTP`; `:156` — `verifyOtp`; `:140` — `doLogin` (legacy
  password); `:225` — `startTimer` (180 s countdown); `:260` — `getHuaweiToken`.
- `app/src/main/java/com/b2b/online/feature/auth/country/CountryListFragment.kt:59` — reads
  `TelephonyManager.networkCountryIso` and offers a "we recognise your country" confirm dialog.
- `app/src/main/java/com/b2b/online/feature/auth/login/otp/LoginOTPFragment.kt:129` — `requestPhone`
  invokes the Google `Credentials` hint-picker to pre-fill the phone number.
- `app/src/main/java/com/b2b/online/feature/auth/login/otp/VerifyOTPFragment.kt:70` —
  `setupSMSReceiver`; `:54`/`:64` — register/unregister the receiver per `onResume`/`onPause`.
- `app/src/main/java/com/b2b/online/feature/auth/register/RegisterPickerFragment.kt:19` —
  branches owner registration by `vm.getCountry()` (SG vs PH).
- `app/src/main/java/com/b2b/online/feature/auth/register/VerificationDialogFragment.kt:49` —
  bottom-sheet OTP dialog reused by PH and partner registration.
- `app/src/main/java/com/b2b/online/feature/auth/waiting/WaitingFragment.kt:26` — polls
  principals and jumps home once any are active.

## Depends on

- [[feature]]
- [[repository]]
- [[uistate-pattern]]
- [[domain]]
- [[util]]
- [[di]]
- [[base]]
- [[push-notifications]]
- [[localization]]
- [[orders]]

## Depended on by
- [[localization]]
- [[repository]]

## Gotchas

- **Token persistence is the only "logged-in" check.** `setDefaultScreen` only inspects
  `C.TOKEN` in SharedPreferences — there is no refresh/expiry handshake here. Token writes happen
  inside `Repository` during login/OTP-verify, not in `AuthViewModel`, so any bug that swallows
  that write leaves the user in a perpetual login loop.
- **OTP timer is hard-coded at `C.OTP_TIMER = 180` seconds** (`base/C.kt:16`). Both
  `AuthViewModel.startTimer` and `VerificationDialogViewModel.startTimer` re-implement the same
  countdown — they are duplicated, not shared. "Resend" is gated purely by the UI: the button
  only becomes visible after the timer hits 0; no server-side resend throttling is visible here,
  and tapping resend just re-calls `doLoginOTP`/`callOtp`, which restarts the timer on the next
  `SuccessFromRemote`.
- **Country switch mid-flow has no explicit reset.** The country picker writes `C.COUNTRY` and
  navigates onward, but it never clears `C.TOKEN` or other per-country caches. If a user manually
  re-enters the picker after login (not currently possible via UI, but reachable through dev
  routing), they will end up on a Login OTP screen formatted for the new country while the old
  token is still valid for the previous one. `CountryHelper` is instantiated lazily per
  ViewModel from the *current* `C.COUNTRY` value, so any in-flight ViewModel keeps the old
  country until process death.
- **SMS auto-read needs no runtime permission.** `SmsRetriever` works via Play Services and a
  hashed app signature embedded in the OTP SMS — no `RECEIVE_SMS` permission required. But
  `POST_NOTIFICATIONS` (Android 13+) is requested up-front in `AuthActivity.onCreate` and its
  result is silently ignored.
- **Huawei devices without Google Play Services partially break.** `LoginOTPFragment.requestPhone`
  catches the Huawei-throws-no-GPS case with a comment-only `catch (e: Exception)` and silently
  no-ops the phone-hint picker. The SMS retriever (`SmsRetriever.getClient`) likewise requires
  GMS — on a true Huawei-only device the auto-read never fires and the user must type the OTP
  manually into `OTPView`. Push tokens are dual-sourced: `getHuaweiToken` always runs
  (`HmsInstanceId`) and writes `C.HUAWEI_TOKEN`; the Firebase token (`C.FIRE_TOKEN`) is fetched
  elsewhere (see [[push-notifications]]).
- **`onActivityResult` is deprecated.** `LoginOTPFragment` still uses the legacy
  `startIntentSenderForResult(..., 555, ...)` for the credentials hint, not the modern
  `ActivityResultContracts` flow.
- **`LoginFragment` is dead-ish.** The legacy username/password login fragment is still wired to
  `doLogin` but `binding.register` is a commented-out no-op; the actual nav graph starts at
  `loginOTPFragment`, not `loginFragment`.

## Open questions

- Where exactly is `C.TOKEN` written after a successful OTP verify? `AuthViewModel.verifyOtp`
  only emits a `UIState` — the persistence side-effect must live inside `Repository.doVerifyLoginOTP`.
  Worth tracing in [[repository]].
- `domain/User.kt` is a 2-field DTO (`name`, `token`) but is not referenced in the auth flow
  read here. Is it dead code, or used downstream by [[chat]] / [[orders]]?
- `WaitingFragment` references `mainVM` from a base class — which base fragment, and is the
  polling interval / cancellation contract documented?
- `OTPView` hard-codes 4 digits via `etOTP1..4` IDs; if the server ever returns a 6-digit OTP the
  widget needs a rewrite, not a config change.
