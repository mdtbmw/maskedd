package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cloned_voices")
data class ClonedVoiceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val tag: String,
    val avatarEmoji: String,
    val audioFilePath: String,
    val elevenLabsVoiceId: String,
    val isFavorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
