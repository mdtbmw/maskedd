package com.example.tts

import android.content.Context
import com.example.data.AppDatabase
import com.example.data.AudioCacheEntity
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LruAudioCacheManager(private val context: Context) {

    private val audioCacheDao = AppDatabase.getDatabase(context).audioCacheDao()
    private val maxCacheSizeBytes = 100 * 1024 * 1024L // 100 MB LRU limit

    private fun computeHash(voiceId: String, text: String): String {
        val input = "$voiceId:$text"
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    suspend fun getCachedAudioFile(voiceId: String, text: String): File? = withContext(Dispatchers.IO) {
        val hash = computeHash(voiceId, text)
        val entry = audioCacheDao.getCacheEntry(hash) ?: return@withContext null

        val file = File(entry.filePath)
        if (file.exists() && file.length() > 0) {
            audioCacheDao.updateAccessTime(hash, System.currentTimeMillis())
            return@withContext file
        } else {
            // Stale file removed from disk, clean up DB entry
            audioCacheDao.deleteCacheEntry(entry)
            return@withContext null
        }
    }

    suspend fun saveToCache(voiceId: String, text: String, sourceFile: File): File = withContext(Dispatchers.IO) {
        val hash = computeHash(voiceId, text)
        val cacheDir = File(context.cacheDir, "neural_tts_lru").apply { mkdirs() }
        val targetFile = File(cacheDir, "tts_$hash.mp3")

        if (sourceFile.absolutePath != targetFile.absolutePath) {
            sourceFile.copyTo(targetFile, overwrite = true)
        }

        val entry = AudioCacheEntity(
            textHash = hash,
            filePath = targetFile.absolutePath,
            fileSizeByte = targetFile.length(),
            voiceId = voiceId,
            lastAccessedTime = System.currentTimeMillis()
        )

        audioCacheDao.insertCacheEntry(entry)
        enforceLruQuota()

        return@withContext targetFile
    }

    private suspend fun enforceLruQuota() {
        val totalSize = audioCacheDao.getTotalCacheSizeBytes() ?: 0L
        if (totalSize > maxCacheSizeBytes) {
            val allEntries = audioCacheDao.getAllEntriesLRU()
            var currentSize = totalSize
            for (entry in allEntries) {
                if (currentSize <= maxCacheSizeBytes * 0.75) break
                val file = File(entry.filePath)
                if (file.exists()) {
                    file.delete()
                }
                audioCacheDao.deleteCacheEntry(entry)
                currentSize -= entry.fileSizeByte
            }
        }
    }
}
