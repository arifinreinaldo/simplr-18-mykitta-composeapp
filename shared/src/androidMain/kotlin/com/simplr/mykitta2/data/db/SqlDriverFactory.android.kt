package com.simplr.mykitta2.data.db

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.simplr.mykitta2.shared.db.MyKittaDatabase

actual class SqlDriverFactory(private val context: Context) {
    actual fun create(): SqlDriver =
        AndroidSqliteDriver(
            schema = MyKittaDatabase.Schema,
            context = context,
            name = "mykitta.db",
        )
}
