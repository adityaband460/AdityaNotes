package com.adityanotes.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.adityanotes.core.database.entity.NotebookEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NotebookDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotebook(notebook: NotebookEntity): Long

    @Query("SELECT * FROM notebooks ORDER BY updatedAt DESC")
    fun getAllNotebooks(): Flow<List<NotebookEntity>>

    @Query("SELECT * FROM notebooks WHERE folderId IS NULL ORDER BY updatedAt DESC")
    fun getRootNotebooks(): Flow<List<NotebookEntity>>

    @Query("SELECT * FROM notebooks WHERE folderId = :folderId ORDER BY updatedAt DESC")
    fun getNotebooksInFolder(folderId: Long): Flow<List<NotebookEntity>>

    @Query("SELECT * FROM notebooks WHERE id = :notebookId LIMIT 1")
    suspend fun getNotebookById(notebookId: Long): NotebookEntity?

    @Query("SELECT * FROM notebooks WHERE name LIKE '%' || :query || '%' ORDER BY updatedAt DESC")
    fun searchNotebooks(query: String): Flow<List<NotebookEntity>>

    @Query("SELECT COUNT(*) FROM pages WHERE notebookId = :notebookId")
    fun getPageCountForNotebook(notebookId: Long): Flow<Int>

    @Update
    suspend fun updateNotebook(notebook: NotebookEntity)

    @Delete
    suspend fun deleteNotebook(notebook: NotebookEntity)
}
