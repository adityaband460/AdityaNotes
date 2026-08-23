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

val MIGRATION_2_3 = object : Migration(2, 3) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `strokes` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `pageId` INTEGER NOT NULL,
                `points` TEXT NOT NULL,
                `color` INTEGER NOT NULL,
                `strokeWidth` REAL NOT NULL,
                `createdAt` INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}

/* Converts the first handwriting implementation's CSV points into BLOBs. */
val MIGRATION_3_4 = object : Migration(3, 4) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE `strokes_new` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `pageId` INTEGER NOT NULL,
                `pointData` BLOB NOT NULL,
                `color` INTEGER NOT NULL,
                `strokeWidth` REAL NOT NULL,
                `tool` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO `strokes_new` (`id`, `pageId`, `pointData`, `color`, `strokeWidth`, `tool`, `createdAt`)
            SELECT `id`, `pageId`, CAST(`points` AS BLOB), `color`, `strokeWidth`, 'PEN', `createdAt`
            FROM `strokes`
            """.trimIndent()
        )
        db.execSQL("DROP TABLE `strokes`")
        db.execSQL("ALTER TABLE `strokes_new` RENAME TO `strokes`")
        db.execSQL("CREATE INDEX `index_strokes_pageId` ON `strokes` (`pageId`)")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            ALTER TABLE `pages`
            ADD COLUMN `paperTemplate` TEXT NOT NULL DEFAULT 'RULED'
            """.trimIndent()
        )
        db.execSQL(
            """
            ALTER TABLE `pages`
            ADD COLUMN `isDarkPaper` INTEGER NOT NULL DEFAULT 0
            """.trimIndent()
        )
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `stroke_operations` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `pageId` INTEGER NOT NULL,
                `type` TEXT NOT NULL,
                `payload` BLOB NOT NULL,
                `isUndone` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS `index_stroke_operations_pageId_isUndone_id`
            ON `stroke_operations` (`pageId`, `isUndone`, `id`)
            """.trimIndent()
        )
    }
}
