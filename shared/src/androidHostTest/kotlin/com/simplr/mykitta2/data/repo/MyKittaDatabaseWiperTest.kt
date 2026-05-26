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
}
