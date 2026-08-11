package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AudioCacheDao {
    @Query("SELECT * FROM audio_cache WHERE textHash = :textHash LIMIT 1")
    suspend fun getCacheEntry(textHash: String): AudioCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCacheEntry(entry: AudioCacheEntity)

    @Query("UPDATE audio_cache SET lastAccessedTime = :accessTime WHERE textHash = :textHash")
    suspend fun updateAccessTime(textHash: String, accessTime: Long = System.currentTimeMillis())

    @Query("SELECT * FROM audio_cache ORDER BY lastAccessedTime ASC")
    suspend fun getAllEntriesLRU(): List<AudioCacheEntity>

    @Query("SELECT SUM(fileSizeByte) FROM audio_cache")
    suspend fun getTotalCacheSizeBytes(): Long?

    @Delete
    suspend fun deleteCacheEntry(entry: AudioCacheEntity)

    @Query("DELETE FROM audio_cache WHERE textHash IN (SELECT textHash FROM audio_cache ORDER BY lastAccessedTime ASC LIMIT :limitCount)")
    suspend fun deleteOldestEntries(limitCount: Int)
}
