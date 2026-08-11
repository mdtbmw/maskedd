package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "audio_cache")
data class AudioCacheEntity(
    @PrimaryKey val textHash: String,
    val filePath: String,
    val fileSizeByte: Long,
    val voiceId: String,
    var lastAccessedTime: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
)
