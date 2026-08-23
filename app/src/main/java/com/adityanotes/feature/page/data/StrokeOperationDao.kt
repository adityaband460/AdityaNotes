package com.adityanotes.feature.page.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface StrokeOperationDao {

    @Insert
    suspend fun insertOperation(
        operation: StrokeOperationEntity
    ): Long

    @Query(
        """
        SELECT * FROM stroke_operations
        WHERE pageId = :pageId AND isUndone = 0
        ORDER BY id DESC
        LIMIT 1
        """
    )
    suspend fun getLatestAppliedOperation(
        pageId: Long
    ): StrokeOperationEntity?

    @Query(
        """
        SELECT * FROM stroke_operations
        WHERE pageId = :pageId AND isUndone = 1
        ORDER BY id ASC
        LIMIT 1
        """
    )
    suspend fun getNextRedoOperation(
        pageId: Long
    ): StrokeOperationEntity?

    @Query(
        """
        UPDATE stroke_operations
        SET payload = :payload, isUndone = :isUndone
        WHERE id = :operationId
        """
    )
    suspend fun updateOperationState(
        operationId: Long,
        payload: ByteArray,
        isUndone: Boolean
    )

    @Query(
        """
        DELETE FROM stroke_operations
        WHERE pageId = :pageId AND isUndone = 1
        """
    )
    suspend fun deleteRedoOperations(
        pageId: Long
    )
}
