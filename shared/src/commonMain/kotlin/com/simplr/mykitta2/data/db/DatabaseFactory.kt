package com.simplr.mykitta2.data.db

import com.simplr.mykitta2.shared.db.MyKittaDatabase

class DatabaseFactory(private val driverFactory: SqlDriverFactory) {
    fun create(): MyKittaDatabase = MyKittaDatabase(driverFactory.create())
}
