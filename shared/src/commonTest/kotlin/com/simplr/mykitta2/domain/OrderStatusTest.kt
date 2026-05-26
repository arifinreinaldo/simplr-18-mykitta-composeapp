package com.simplr.mykitta2.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OrderStatusTest {

    @Test fun fromWire_recognizesAllFourStatuses() {
        assertEquals(OrderStatus.WAITING, OrderStatus.fromWire("Waiting"))
        assertEquals(OrderStatus.PROCESSED, OrderStatus.fromWire("Processed"))
        assertEquals(OrderStatus.ON_DELIVERY, OrderStatus.fromWire("On-Delivery"))
        assertEquals(OrderStatus.FINISHED, OrderStatus.fromWire("Finished"))
    }

    @Test fun fromWire_isCaseInsensitive() {
        assertEquals(OrderStatus.WAITING, OrderStatus.fromWire("waiting"))
        assertEquals(OrderStatus.FINISHED, OrderStatus.fromWire("FINISHED"))
        assertEquals(OrderStatus.ON_DELIVERY, OrderStatus.fromWire("on-delivery"))
    }

    @Test fun fromWire_unknownStringReturnsNull() {
        // Defensive: a future backend status ("Refunded", "PartialShip", typos)
        // returns null so the repository can drop the row from the list rather
        // than crashing or misrouting it under a wrong tab.
        assertNull(OrderStatus.fromWire("Refunded"))
        assertNull(OrderStatus.fromWire(""))
        assertNull(OrderStatus.fromWire("OnDelivery")) // missing hyphen
    }
}
