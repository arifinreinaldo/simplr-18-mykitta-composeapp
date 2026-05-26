package com.simplr.mykitta2.test

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.simplr.mykitta2.shared.db.MyKittaDatabase

fun makeInMemoryDatabase(): MyKittaDatabase {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
    MyKittaDatabase.Schema.create(driver)
    return MyKittaDatabase(driver)
}
