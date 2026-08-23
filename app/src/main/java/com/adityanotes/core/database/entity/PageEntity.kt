package com.adityanotes.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "pages",
    foreignKeys = [
        ForeignKey(
            entity = NotebookEntity::class,
            parentColumns = ["id"],
            childColumns = ["notebookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["notebookId"])
    ]
)
data class PageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val notebookId: Long,

    val name: String,

    val content: String = "",

    /** BLANK, RULED, or GRID. Stored as text so templates remain migration-friendly. */
    val paperTemplate: String = "RULED",

    val isDarkPaper: Boolean = false,

    val createdAt: Long,

    val updatedAt: Long
)
