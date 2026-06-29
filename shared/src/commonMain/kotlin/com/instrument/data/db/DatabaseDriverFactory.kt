package com.instrument.data.db

import app.cash.sqldelight.db.SqlDriver

// プラットフォーム固有の SQLite ドライバを生成する expect クラス
expect class DatabaseDriverFactory {
    fun createDriver(): SqlDriver
}
