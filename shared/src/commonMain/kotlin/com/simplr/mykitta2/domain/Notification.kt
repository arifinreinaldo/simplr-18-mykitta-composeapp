package com.simplr.mykitta2.domain

/**
 * In-app notification record. Mirrors the legacy `Notif` payload but drops the
 * pre-parsed `payload_data` field — parsing is done on-demand at tap-time.
 * `createdAt` is assumed lexically sortable (ISO8601-like).
 */
data class Notification(
    val id: String,
    val title: String,
    val description: String,
    val type: NotificationType,
    val payload: String,
    val isRead: Boolean,
    val createdAt: String,
)

enum class NotificationType {
    PRINCIPAL,
    ORDER,
    UNKNOWN;

    companion object {
        fun fromWire(raw: String?): NotificationType = when (raw?.uppercase()) {
            "PRINCIPAL" -> PRINCIPAL
            "ORDER" -> ORDER
            else -> UNKNOWN
        }
    }
}
