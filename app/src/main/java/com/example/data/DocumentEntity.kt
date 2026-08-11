package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val subtitle: String,
    val format: String, // "MARKDOWN", "PDF", "WORD", "TEXT"
    val content: String,
    val wordCount: Int,
    val category: String, // "Story", "Markdown", "Office", "Classic", "User Import"
    val coverGradientStart: Long = 0xFF7C3AED, // Default Purple
    val coverGradientEnd: Long = 0xFF4338CA,   // Default Indigo
    val lastReadWordIndex: Int = 0,
    val lastReadSentenceIndex: Int = 0,
    val lastReadTime: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val defaultReadingMode: String = "STORYTELLER"
)
