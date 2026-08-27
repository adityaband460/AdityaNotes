package com.adityanotes.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.adityanotes.core.database.dao.FolderDao
import com.adityanotes.core.database.dao.NotebookDao
import com.adityanotes.core.database.dao.PageDao
import com.adityanotes.core.database.entity.FolderEntity
import com.adityanotes.core.database.entity.NotebookEntity
import com.adityanotes.core.database.entity.PageEntity
import com.adityanotes.feature.page.data.StrokeDao
import com.adityanotes.feature.page.data.StrokeEntity
import com.adityanotes.feature.page.data.StrokeOperationDao
import com.adityanotes.feature.page.data.StrokeOperationEntity

@Database(
    entities = [
        FolderEntity::class,
        NotebookEntity::class,
        PageEntity::class,
        StrokeEntity::class,
        StrokeOperationEntity::class
    ],
    version = 7,
    exportSchema = false
)
abstract class AdityaNotesDatabase : RoomDatabase() {

    abstract fun folderDao(): FolderDao

    abstract fun notebookDao(): NotebookDao

    abstract fun pageDao(): PageDao

    abstract fun strokeDao(): StrokeDao

    abstract fun strokeOperationDao(): StrokeOperationDao
}
