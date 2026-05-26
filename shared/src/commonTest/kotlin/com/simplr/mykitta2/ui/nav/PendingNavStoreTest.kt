package com.simplr.mykitta2.ui.nav

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PendingNavStoreTest {

    @Test fun requestPrincipalCatalog_thenConsume_clearsValue() {
        val store = PendingNavStore()
        assertNull(store.pendingPrincipal.value)

        store.requestPrincipalCatalog(id = "P-1", name = "Acme")
        assertEquals(PendingNavStore.PendingPrincipal("P-1", "Acme"), store.pendingPrincipal.value)

        store.consume()
        assertNull(store.pendingPrincipal.value)
    }

    @Test fun secondRequest_overwritesFirst_ifNotConsumed() {
        val store = PendingNavStore()
        store.requestPrincipalCatalog("P-1", "A")
        store.requestPrincipalCatalog("P-2", "B")
        assertEquals(PendingNavStore.PendingPrincipal("P-2", "B"), store.pendingPrincipal.value)
    }
}
