package com.adityanotes.core.di

import android.content.Context
import androidx.room.Room
import com.adityanotes.core.database.AdityaNotesDatabase
import com.adityanotes.core.database.MIGRATION_1_2
import com.adityanotes.core.database.MIGRATION_2_3
import com.adityanotes.core.database.MIGRATION_3_4
import com.adityanotes.core.database.MIGRATION_4_5
import com.adityanotes.core.database.MIGRATION_5_6
import com.adityanotes.core.database.dao.NotebookDao
import com.adityanotes.core.database.dao.PageDao
import com.adityanotes.feature.page.data.StrokeDao
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
        )
            .addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6
            )
            .build()
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

    @Provides
    fun provideStrokeDao(
        database: AdityaNotesDatabase
    ): StrokeDao {
        return database.strokeDao()
    }
}
