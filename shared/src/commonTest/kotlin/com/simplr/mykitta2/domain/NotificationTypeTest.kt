package com.simplr.mykitta2.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class NotificationTypeTest {

    @Test fun fromWire_mapsPrincipalAndOrder() {
        assertEquals(NotificationType.PRINCIPAL, NotificationType.fromWire("Principal"))
        assertEquals(NotificationType.PRINCIPAL, NotificationType.fromWire("PRINCIPAL"))
        assertEquals(NotificationType.ORDER, NotificationType.fromWire("order"))
    }

    @Test fun fromWire_unknownAndNull_returnUNKNOWN() {
        assertEquals(NotificationType.UNKNOWN, NotificationType.fromWire(null))
        assertEquals(NotificationType.UNKNOWN, NotificationType.fromWire(""))
        assertEquals(NotificationType.UNKNOWN, NotificationType.fromWire("Promo"))
    }
}
