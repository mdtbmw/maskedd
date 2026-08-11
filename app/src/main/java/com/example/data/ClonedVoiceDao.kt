package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ClonedVoiceDao {
    @Query("SELECT * FROM cloned_voices ORDER BY isFavorite DESC, createdAt DESC")
    fun getAllClonedVoices(): Flow<List<ClonedVoiceEntity>>

    @Query("UPDATE cloned_voices SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavoriteStatus(id: String, isFavorite: Boolean)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClonedVoice(voice: ClonedVoiceEntity)

    @Delete
    suspend fun deleteClonedVoice(voice: ClonedVoiceEntity)
}
