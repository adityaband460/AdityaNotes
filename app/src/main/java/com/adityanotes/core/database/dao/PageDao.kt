package com.adityanotes.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.adityanotes.core.database.entity.PageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPage(page: PageEntity): Long

    @Query(
        "SELECT * FROM pages " +
                "WHERE notebookId = :notebookId " +
                "ORDER BY createdAt ASC, id ASC"
    )
    fun getPagesForNotebook(
        notebookId: Long
    ): Flow<List<PageEntity>>

    @Query(
        "SELECT * FROM pages " +
                "WHERE id = :pageId " +
                "LIMIT 1"
    )
    suspend fun getPageById(
        pageId: Long
    ): PageEntity?

    @Query("SELECT COUNT(*) FROM pages WHERE notebookId = :notebookId")
    suspend fun getPageCount(notebookId: Long): Int

    @Update
    suspend fun updatePage(page: PageEntity)

    @Delete
    suspend fun deletePage(page: PageEntity)

    @Query("DELETE FROM pages WHERE notebookId = :notebookId")
    suspend fun deletePagesForNotebook(notebookId: Long)
}
