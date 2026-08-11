package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents ORDER BY lastReadTime DESC")
    fun getAllDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :id")
    fun getDocumentByIdFlow(id: Long): Flow<DocumentEntity?>

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun getDocumentById(id: Long): DocumentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: DocumentEntity): Long

    @Update
    suspend fun updateDocument(document: DocumentEntity)

    @Query("UPDATE documents SET lastReadWordIndex = :wordIndex, lastReadSentenceIndex = :sentenceIndex, lastReadTime = :time WHERE id = :id")
    suspend fun updateReadingProgress(id: Long, wordIndex: Int, sentenceIndex: Int, time: Long = System.currentTimeMillis())

    @Query("UPDATE documents SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun toggleFavorite(id: Long, isFavorite: Boolean)

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteDocumentById(id: Long)

    @Query("SELECT COUNT(*) FROM documents")
    suspend fun getDocumentCount(): Int
}
