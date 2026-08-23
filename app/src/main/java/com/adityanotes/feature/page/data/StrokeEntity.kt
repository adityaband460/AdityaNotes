package com.adityanotes.feature.page.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "strokes",
    indices = [Index(value = ["pageId"])]
)
data class StrokeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val pageId: Long,
    /** Versioned binary [StrokePointCodec] payload; never a CSV string. */
    val pointData: ByteArray,
    val color: Long,
    val strokeWidth: Float,
    val tool: String = StrokeTool.PEN.name,
    val createdAt: Long = System.currentTimeMillis()
)

enum class StrokeTool {
    PEN,
    HIGHLIGHTER
}
