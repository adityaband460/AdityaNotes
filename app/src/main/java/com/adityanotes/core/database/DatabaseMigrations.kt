package com.adityanotes.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            ALTER TABLE pages
            ADD COLUMN content TEXT NOT NULL DEFAULT ''
            """.trimIndent()
        )
    }
}