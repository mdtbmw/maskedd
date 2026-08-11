package com.example.data

import kotlinx.coroutines.flow.Flow

class DocumentRepository(
    private val documentDao: DocumentDao,
    private val bookmarkDao: BookmarkDao
) {
    val allDocuments: Flow<List<DocumentEntity>> = documentDao.getAllDocuments()
    val allBookmarks: Flow<List<BookmarkEntity>> = bookmarkDao.getAllBookmarks()

    fun getDocumentFlow(id: Long): Flow<DocumentEntity?> = documentDao.getDocumentByIdFlow(id)

    suspend fun getDocumentById(id: Long): DocumentEntity? = documentDao.getDocumentById(id)

    suspend fun insertDocument(document: DocumentEntity): Long = documentDao.insertDocument(document)

    suspend fun updateDocument(document: DocumentEntity) = documentDao.updateDocument(document)

    suspend fun updateReadingProgress(id: Long, wordIndex: Int, sentenceIndex: Int) {
        documentDao.updateReadingProgress(id, wordIndex, sentenceIndex)
    }

    suspend fun toggleFavorite(id: Long, isFavorite: Boolean) {
        documentDao.toggleFavorite(id, isFavorite)
    }

    suspend fun deleteDocument(id: Long) = documentDao.deleteDocumentById(id)

    suspend fun getDocumentCount(): Int = documentDao.getDocumentCount()

    fun getBookmarksForDocument(documentId: Long): Flow<List<BookmarkEntity>> =
        bookmarkDao.getBookmarksForDocument(documentId)

    suspend fun addBookmark(bookmark: BookmarkEntity): Long = bookmarkDao.insertBookmark(bookmark)

    suspend fun deleteBookmark(id: Long) = bookmarkDao.deleteBookmark(id)
}
