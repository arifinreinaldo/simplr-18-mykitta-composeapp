---
title: Push notifications (Firebase + HMS)
slug: push-notifications
type: deep
verified: 2026-05-23
sources:
  - app/src/main/java/com/b2b/online/util/FirebaseMessagingReceiver.kt
  - app/src/main/java/com/b2b/online/util/HuaweiMessagingReceiver.kt
  - app/src/main/java/com/b2b/online/base/BaseApplication.kt
  - app/src/main/java/com/b2b/online/domain/Notif.kt
  - app/src/main/AndroidManifest.xml
  - app/src/main/java/com/b2b/online/feature/notif/NotifFragment.kt
---

# Push notifications (Firebase + HMS)

**Dual messaging stack: Firebase Cloud Messaging and Huawei HMS Push, with shared dispatch logic.**

## What it does

The app supports two parallel push backends so it can run on both Google-Mobile-Services (Play) and Huawei-Mobile-Services devices. `FirebaseMessagingReceiver` extends `FirebaseMessagingService`; `HuaweiMessagingReceiver` extends `HmsMessageService`. Both are `@AndroidEntryPoint` services declared in the manifest and registered against their respective platform's MESSAGING_EVENT intent action.

On message receipt each receiver reads `remoteMessage.notification?.body` and funnels it into an in-class `sendNotification(messageBody)` that builds an O+ `NotificationChannel`, wraps a `PendingIntent` to `MainActivity` with the body as the `"message"` extra, and posts via `NotificationManager.notify(0, …)`. Token refresh writes to `SharedPreferences` under separate keys: `C.FIRE_TOKEN` and `C.HUAWEI_TOKEN` — those tokens are later read by the [[repository]] when registering the device server-side.

The `Notif` domain model (id, title, description, type, payload, payload_data, is_read, created_at) is **not** populated from the push payload itself — push delivery only wakes the tray. The actual `Notif` list is fetched from the API and rendered by `NotifFragment`, which switches on `type` (`"Order"` → `NotifOrderPayload` → order-detail nav; `"Principal"` → `NotifPrincipalPayload` → principal lookup then brand-detail nav). Push and the in-app notif list are effectively decoupled: the push only nudges the user to open the app; routing happens via the typed list, not via push payload deep-linking.

## Where it lives

- Firebase service: `app/src/main/java/com/b2b/online/util/FirebaseMessagingReceiver.kt:39` (`onMessageReceived`), `:97` (`onNewToken` → writes `C.FIRE_TOKEN`), `:107` (`sendNotification`).
- HMS service: `app/src/main/java/com/b2b/online/util/HuaweiMessagingReceiver.kt:39` (`onMessageReceived`), `:97` (`onNewToken` → writes `C.HUAWEI_TOKEN`), `:107` (`sendNotification`).
- Manifest service entries: `app/src/main/AndroidManifest.xml:81-87` (Firebase, `com.google.firebase.MESSAGING_EVENT`) and `:88-95` (HMS, `com.huawei.push.action.MESSAGING_EVENT`, with `android:directBootAware="true"`).
- Default Firebase channel meta: `app/src/main/AndroidManifest.xml:77-79` (`default_notification_channel_id`).
- `Notif` model: `app/src/main/java/com/b2b/online/domain/Notif.kt`.
- Consumer / dispatch from list tap: `app/src/main/java/com/b2b/online/feature/notif/NotifFragment.kt:74-118` (type-switched binding + onClick navigation).
- App init (Hilt entry point for the injected `SharedPreferences`): `app/src/main/java/com/b2b/online/base/BaseApplication.kt:21-26`.

## Depends on

- [[base]]
- [[util]]
- [[domain]]
- [[di]]
- [[di-graph]]
- [[repository]]
- [[feature]]

## Depended on by
- [[authentication]]
- [[build-variants]]
- [[chat]]
- [[orders]]
- [[repository]]

## Gotchas

- **Parallel-receiver maintenance burden.** `FirebaseMessagingReceiver` and `HuaweiMessagingReceiver` are near-line-identical copies (channel build, `PendingIntent` flags, `sendNotification`, `sendNotification2` dead helper, even the same `TAG`-referenced log line at `:41`). Any fix — payload schema change, deep-link routing, channel-importance tweak — must be mirrored to both files or the two platforms will silently diverge. There is no shared base class extracted; the duplication is structural.
- **`TAG` is unresolved.** Both `Log.d(TAG, …)` calls at line 41 reference an identifier that isn't declared in either file or imported from `C`. This currently compiles only if a `TAG` symbol exists somewhere on the import-resolution path (e.g., a generated `BuildConfig` field or top-level constant) — worth verifying before treating the log line as functional. See open questions.
- **Payload schema drift.** Only `remoteMessage.notification?.body` is consumed; `remoteMessage.data` is read in commented-out `sendNotification2` paths that reference a hard-coded `"Nick"` key. If the backend ever switches to data-only messages (required for background delivery on some OEM ROMs), neither receiver will display anything. The Firebase vs HMS `RemoteMessage` types are distinct classes — any future data-payload handling must be implemented twice.
- **Token refresh** writes to `SharedPreferences` only; there's no in-receiver call back into [[repository]] to re-register the new token with the server. The next server registration depends on whichever caller next reads `C.FIRE_TOKEN` / `C.HUAWEI_TOKEN`. A token rotated while the app is killed will not be propagated until the user next opens the app.
- **Deep-link routing from notif tap** is shallow: the `PendingIntent` always targets `MainActivity` with `FLAG_ACTIVITY_CLEAR_TOP` and a `"message"` string extra. There is no parsing of `payload` to jump to order detail or chat. Type-aware navigation (Order → order-detail, Principal → brand-detail) lives in `NotifFragment.kt:94-118`, reached only after the user manually opens the notif list.
- **Notification ID is hard-coded to `0`.** Every push replaces the previous tray entry. Multiple concurrent pushes collapse into one visible notification.
- **HMS service is `directBootAware="true"`** while the Firebase one is not — a deliberate asymmetry, presumably to support Huawei delivery before user unlock, but it means the HMS receiver must not touch credential-locked storage on early boot. The current `SharedPreferences` write at `onNewToken` may be affected if it fires pre-unlock.

## Open questions

- Where is the `TAG` symbol referenced in `Log.d(TAG, …)` actually declared? It is not in either receiver, not in `C`, and not imported.
- Is the server told about both tokens, or only the one matching the device's distribution channel (Play vs AppGallery)? `Repository` reads of `C.FIRE_TOKEN` / `C.HUAWEI_TOKEN` need to be traced.
- Why is `sendNotification2` (the `data`-map variant) kept in both files as dead code? Was a data-payload migration started and shelved?
- Should the receivers parse `remoteMessage.data` to set the `PendingIntent` target dynamically (e.g., straight to `OrderDetailFragment`) instead of always landing on `MainActivity`?
- `directBootAware="true"` on HMS but not Firebase — intentional, or copy-paste accident from the HMS sample?
