package com.adityanotes.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.adityanotes.core.database.dao.NotebookDao
import com.adityanotes.core.database.dao.PageDao
import com.adityanotes.core.database.entity.NotebookEntity
import com.adityanotes.core.database.entity.PageEntity

@Database(
    entities = [
        NotebookEntity::class,
        PageEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class AdityaNotesDatabase : RoomDatabase() {

    abstract fun notebookDao(): NotebookDao

    abstract fun pageDao(): PageDao
}