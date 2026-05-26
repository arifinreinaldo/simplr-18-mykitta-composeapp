package com.simplr.mykitta2.data.repo

import com.simplr.mykitta2.shared.db.MyKittaDatabase

/**
 * Thin seam over the SQLDelight tables that hold user-scoped rows. Lives as an
 * interface so [AuthRepository] can be unit-tested across all platforms without
 * pulling in a JVM-only SQLite driver — common tests pass a fake; the real DB
 * wipe is verified via [MyKittaDatabaseWiper] in `androidHostTest`.
 *
 * Add a new `deleteAll()` call here as features land new `.sq` tables; that
 * keeps `AuthRepository.logout()` correct without further changes.
 */
fun interface LocalDataWiper {
    suspend fun wipeAll()
}

class MyKittaDatabaseWiper(
    private val database: MyKittaDatabase,
) : LocalDataWiper {
    // Meta is a startup warm-up cache (intentionally left); Principal is the
    // only user-scoped table today. Wrap multi-table wipes in a transaction
    // when a second table lands.
    override suspend fun wipeAll() {
        database.principalQueries.deleteAll()
    }
}
