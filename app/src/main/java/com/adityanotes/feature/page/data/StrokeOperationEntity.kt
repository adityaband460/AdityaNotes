package com.adityanotes.feature.page.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "stroke_operations",
    indices = [Index(value = ["pageId", "isUndone", "id"])]
)
data class StrokeOperationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val pageId: Long,
    val type: String,
    /** Binary [StrokeSnapshotCodec] payload. */
    val payload: ByteArray,
    val isUndone: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

enum class StrokeOperationType {
    ADD,
    ERASE,
    TRANSFORM
}
