package com.simplr.mykitta2.data.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.simplr.mykitta2.shared.db.MyKittaDatabase

actual class SqlDriverFactory {
    actual fun create(): SqlDriver =
        NativeSqliteDriver(
            schema = MyKittaDatabase.Schema,
            name = "mykitta.db",
        )
}
