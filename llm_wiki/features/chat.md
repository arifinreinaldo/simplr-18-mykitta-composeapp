---
title: Chat
slug: chat
type: feature
verified: 2026-05-23
sources:
  - app/src/main/java/com/b2b/online/feature/chat/ChatContactFragment.kt
  - app/src/main/java/com/b2b/online/feature/chat/ChatContactViewModel.kt
  - app/src/main/java/com/b2b/online/feature/chat/ChatFragment.kt
  - app/src/main/java/com/b2b/online/feature/chat/ChatViewModel.kt
  - app/src/main/java/com/b2b/online/feature/chat/ChatListFragment.kt
  - app/src/main/java/com/b2b/online/feature/chat/ChatListViewModel.kt
---

# Chat

**Contact list, chat threads, and messaging; feature/chat/.**

## What it does

Three screens compose the chat feature:

1. **Chat list** (`ChatListFragment`) — the user's existing conversations, one row
   per principal (supplier), with the last message preview, an unread dot, and a
   FAB to start a new conversation.
2. **Contact list** (`ChatContactFragment`) — a 2-column grid of all `Principal`
   entities the user can message; reached via the FAB; tapping a tile opens the
   thread for that principal.
3. **Chat thread** (`ChatFragment`) — the actual conversation, rendered as a
   reverse-layout `RecyclerView` with three row types: `ChatUser`,
   `ChatPrincipal`, and `ChatDate` separators. A text field + send button drives
   outbound messages.

New-message detection is **polling-based, not push**. `ChatViewModel.init` spawns
an `IO` coroutine that calls `getLastChat()` every 10 seconds for the lifetime
of the ViewModel (`while (true) { delay(10000); getLastChat() }`). There is no
WebSocket, no FCM-triggered refresh path, and no `ChatService` wired here.

Attachments are **not supported** — only plain text. Long-pressing a bubble
copies its text to the system clipboard. Emoji are handled via
`encodeEmoji`/`decodeEmoji` util wrappers on send and render.

## Where it lives

- `app/src/main/java/com/b2b/online/feature/chat/ChatListFragment.kt:30` —
  entry point; `setupListener` wires FAB → contact list; `setupDisplay:56` binds
  the `ChatData` rows.
- `app/src/main/java/com/b2b/online/feature/chat/ChatListViewModel.kt:18` — Room
  flow at `:25` (`repository.getChatData()`); remote refresh at `:32`
  (`getLastChatList("0", 0)`).
- `app/src/main/java/com/b2b/online/feature/chat/ChatContactFragment.kt:31` —
  grid of `Principal`s; `setupDisplay:49` builds the recycler; click navigates
  to `ChatFragment` with the principal as nav-arg.
- `app/src/main/java/com/b2b/online/feature/chat/ChatContactViewModel.kt:16` —
  Room flow at `:24` (`getPrincipalFlow`); remote refresh at `:32`
  (`getPrincipalList`).
- `app/src/main/java/com/b2b/online/feature/chat/ChatFragment.kt:40` — thread
  UI; `setupDisplay:92` builds the reversed recycler; `setupListener:59` wires
  the send button and IME action.
- `app/src/main/java/com/b2b/online/feature/chat/ChatViewModel.kt:25` — 10s
  polling loop at `:37`; `showChat:61` loads room-cached history and groups
  rows by date; `sendChat:113` posts a message and triggers `getLastChat()` on
  success.

## Depends on

- [[repository]]
- [[room-database]]
- [[uistate-pattern]]
- [[domain]]
- [[feature]]
- [[util]]
- [[push-notifications]]

## Depended on by
- [[product-catalog]]
- [[repository]]

## Gotchas

- **Polling, not real-time.** The 10s loop in `ChatViewModel.init` runs
  unconditionally for the ViewModel's lifetime. There is no backoff, no pause
  when the screen is backgrounded, and no cancellation aside from VM clearance.
  Two consequences: (a) opening a thread starts a fresh poll on top of any
  background activity, (b) battery / API cost scales linearly with time spent
  on a thread.
- **`ChatListFragment` is not polled.** Only the open thread polls. The list
  screen relies on its Room flow (`getChatData()`) for live updates, which
  means a new inbound message only surfaces on the list when *something else*
  writes it into Room — typically the thread's own poll, or a server push that
  the repository processes. If neither happens, the list is stale.
- **Unread badge source-of-truth is the row itself, not a counter.**
  `ChatListFragment.setupDisplay:62` shows the read-dot when
  `item.chat.Status == "0" && item.chat.MessageType == "Principal"`. There is
  no separate unread-count field; marking-read is implicit (status flip on the
  last `ChatEntity`), and no per-thread unread totals are exposed in the UI.
- **Push interaction is indirect.** See [[push-notifications]] — FCM/HMS
  payloads for new messages land in the messaging service and (best case)
  update Room, which then propagates via the list's flow. The chat screens
  themselves do not subscribe to push events; they only react to Room.
- **Disconnect behavior is silent.** `getLastChat()` failures are emitted into
  `_state` but `ChatFragment.setupObserver` only handles `Loading`/`Finish`
  for that flow — `Error` is dropped, so a flaky connection produces no UI
  feedback on the thread screen. The send flow does surface errors via
  `chatResult`.
- **Reverse-layout footgun.** The thread recycler uses
  `LinearLayoutManager.reverseLayout = true`, so the `ChatDate` separator added
  at `ChatViewModel.showChat:107` (`chats.add(ChatDate(date))` *after* the
  loop) is the **oldest** date, not the newest — this is intentional given the
  reversal but easy to mis-read.
- **Send button enabled-state is the only in-flight guard.** `sendChat` does
  not debounce; double-tapping in the IME-action path can post twice if the
  button hasn't disabled yet.

## Open questions

- Does the repository have a server-push code path that writes inbound
  `ChatEntity` rows into Room independently of the 10s poll? If yes, the list
  screen's freshness depends on it — worth tracing in [[repository]] and
  [[push-notifications]].
- What does `getLastChatList("0", 0)` paginate against? `ChatListViewModel` only
  ever calls it with offset `0`, so older threads may be invisible.
- Why is `ChatFragment.binding.refresh.setOnRefreshListener` a no-op that
  immediately sets `isRefreshing = false`? Either dead code or an intentional
  disable of pull-to-refresh on the thread screen.
- `ChatViewModel` has commented-out `_message`/`_chats` scaffolding at `:123` —
  remnant of a SharedFlow-based design? Worth confirming the current Room-backed
  approach is the only path.
