package com.adityanotes.core.di

import android.content.Context
import androidx.room.Room
import com.adityanotes.core.database.AdityaNotesDatabase
import com.adityanotes.core.database.dao.NotebookDao
import com.adityanotes.core.database.dao.PageDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): AdityaNotesDatabase {
        return Room.databaseBuilder(
            context,
            AdityaNotesDatabase::class.java,
            "aditya_notes.db"
        ).build()
    }

    @Provides
    fun provideNotebookDao(
        database: AdityaNotesDatabase
    ): NotebookDao {
        return database.notebookDao()
    }

    @Provides
    fun providePageDao(
        database: AdityaNotesDatabase
    ): PageDao {
        return database.pageDao()
    }
}