package com.simplr.mykitta2.domain

/**
 * The four order statuses the History screen renders as tabs.
 *
 * - [wire] is the case-insensitive value backend emits in `InvStatus`. Used
 *   when building the `search="status=<wire>"` request parameter and when
 *   parsing rows back via [fromWire].
 * - [label] is the user-facing tab title (English).
 *
 * Any status string the server returns that doesn't map here is dropped at the
 * repository boundary (`fromWire` returns null). This is intentional: it lets
 * the backend grow new statuses without crashing this build.
 */
enum class OrderStatus(val wire: String, val label: String) {
    WAITING("Waiting", "Waiting"),
    PROCESSED("Processed", "Processed"),
    ON_DELIVERY("On-Delivery", "On Delivery"),
    FINISHED("Finished", "Finished");

    companion object {
        fun fromWire(s: String): OrderStatus? =
            entries.firstOrNull { it.wire.equals(s, ignoreCase = true) }
    }
}
