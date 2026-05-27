package com.simplr.mykitta2.data.repo

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.simplr.mykitta2.shared.db.MyKittaDatabase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * JVM-side coverage that the prod wiper actually clears every user-scoped
 * SQLDelight table. Add new assertions here when new `.sq` tables land.
 */
class MyKittaDatabaseWiperTest {

    private fun freshDb(): MyKittaDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        MyKittaDatabase.Schema.create(driver)
        return MyKittaDatabase(driver)
    }

    @Test fun wipeAll_emptiesPrincipalTable() = runTest {
        val db = freshDb()
        db.principalQueries.upsert(
            principalId = "p1",
            principalName = "Acme",
            principalImg = "",
            isActive = 1,
            sortOrder = 0,
        )
        db.principalQueries.upsert(
            principalId = "p2",
            principalName = "Beta",
            principalImg = "",
            isActive = 1,
            sortOrder = 1,
        )
        assertTrue(db.principalQueries.selectAll().executeAsList().isNotEmpty())

        MyKittaDatabaseWiper(db).wipeAll()

        assertEquals(emptyList(), db.principalQueries.selectAll().executeAsList())
    }

    @Test fun wipeAll_isIdempotentOnEmptyDb() = runTest {
        val db = freshDb()
        // No throw, no rows.
        MyKittaDatabaseWiper(db).wipeAll()
        assertEquals(emptyList(), db.principalQueries.selectAll().executeAsList())
    }

    @Test fun wipeAll_emptiesNotificationTable() = runTest {
        val db = freshDb()
        db.notificationQueries.upsert(
            id = "n1", title = "T1", description = "D", type = "Order",
            payload = "{}", isRead = 0, createdAt = "2026-05-26T00:00:00Z",
        )
        db.notificationQueries.upsert(
            id = "n2", title = "T2", description = "D", type = "Principal",
            payload = "{}", isRead = 1, createdAt = "2026-05-26T00:00:01Z",
        )
        assertEquals(1L, db.notificationQueries.countUnread().executeAsOne())

        MyKittaDatabaseWiper(db).wipeAll()

        assertEquals(0L, db.notificationQueries.countUnread().executeAsOne())
        assertEquals(emptyList(), db.notificationQueries.selectFirstPage(20).executeAsList())
    }

    @Test fun wipeAll_clearsPrincipalsAndNotificationsTogether() = runTest {
        val db = freshDb()
        db.principalQueries.upsert(
            principalId = "p1", principalName = "Acme", principalImg = "",
            isActive = 1, sortOrder = 0,
        )
        db.notificationQueries.upsert(
            id = "n1", title = "T", description = "D", type = "Order",
            payload = "{}", isRead = 0, createdAt = "2026-05-26T00:00:00Z",
        )

        MyKittaDatabaseWiper(db).wipeAll()

        assertEquals(emptyList(), db.principalQueries.selectAll().executeAsList())
        assertEquals(emptyList(), db.notificationQueries.selectFirstPage(20).executeAsList())
    }

    @Test fun wipeAll_emptiesHistoryTable() = runTest {
        val db = freshDb()
        db.historyQueries.upsert(
            invNo = "INV-1",
            invDate = "2026-05-20",
            status = "Waiting",
            principalName = "COLUMBIA",
            total = 1.0,
            currency = "PHP",
            itemCount = 1,
            firstProductName = "",
            firstProductImageUrl = "",
            firstProductQty = 0,
            fetchedAt = 0,
        )
        assertTrue(db.historyQueries.countByStatus("Waiting").executeAsOne() > 0)

        MyKittaDatabaseWiper(db).wipeAll()

        assertEquals(0L, db.historyQueries.countByStatus("Waiting").executeAsOne())
    }

    @Test fun wipeAll_emptiesAddressTable() = runTest {
        val db = freshDb()
        db.addressQueries.upsert(
            customerAddressId = "A-1",
            name = "Home",
            address1 = "1 Main St",
            address2 = "",
            zipcode = "1000",
            city = "Manila",
            phone = "0900",
            contact = "Juan",
            barangay = "",
            province = "",
            subdivision = "",
            isSelected = 1,
            fetchedAt = 0,
        )
        assertEquals(1L, db.addressQueries.countAll().executeAsOne())

        MyKittaDatabaseWiper(db).wipeAll()

        assertEquals(0L, db.addressQueries.countAll().executeAsOne())
    }
}
