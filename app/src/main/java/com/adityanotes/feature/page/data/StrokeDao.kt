package com.adityanotes.feature.page.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface StrokeDao {

    /*
     * Room continuously observes this query.
     *
     * Whenever a stroke is inserted or deleted,
     * Room emits the latest list automatically.
     */
    @Query(
        """
        SELECT * FROM strokes
        WHERE pageId = :pageId
        ORDER BY createdAt ASC, id ASC
        """
    )
    fun observeStrokes(
        pageId: Long
    ): Flow<List<StrokeEntity>>

    @Query(
        """
        SELECT * FROM strokes
        WHERE pageId = :pageId
        ORDER BY createdAt ASC, id ASC
        """
    )
    suspend fun getStrokesForPage(
        pageId: Long
    ): List<StrokeEntity>

    /**
     * Reads one persisted stroke for undo before its row is deleted.
     *
     * This deliberately does not depend on the UI-facing Flow: Room may not
     * have delivered the latest emission when a user immediately taps Undo.
     */
    @Query(
        """
        SELECT * FROM strokes
        WHERE id = :strokeId
        LIMIT 1
        """
    )
    suspend fun getStrokeById(
        strokeId: Long
    ): StrokeEntity?


    /*
     * Insert a new stroke.
     *
     * Returns the Room-generated primary key.
     */
    @Insert
    suspend fun insertStroke(
        stroke: StrokeEntity
    ): Long


    /*
     * Delete exactly one stroke by its primary key.
     *
     * This is safer for Undo than using @Delete with
     * an entire entity.
     */
    @Query(
        """
        DELETE FROM strokes
        WHERE id = :strokeId
        """
    )
    suspend fun deleteStrokeById(
        strokeId: Long
    )

    @Query(
        """
        DELETE FROM strokes
        WHERE id IN (:strokeIds)
        """
    )
    suspend fun deleteStrokesByIds(
        strokeIds: List<Long>
    )


    /*
     * Delete all strokes belonging to a page.
     */
    @Query(
        """
        DELETE FROM strokes
        WHERE pageId = :pageId
        """
    )
    suspend fun deleteAllForPage(
        pageId: Long
    )
}
