package com.example.data.repository

import com.example.data.local.WatchHistoryDao
import com.example.data.local.WatchHistoryEntity
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class WatchHistoryRepository(private val watchHistoryDao: WatchHistoryDao) {

    private val firestore: FirebaseFirestore? = try {
        FirebaseFirestore.getInstance()
    } catch (_: Exception) {
        null
    }

    val allHistory: Flow<List<WatchHistoryEntity>> = watchHistoryDao.getAllWatchHistory()

    suspend fun savePlaybackPosition(
        mediaId: String,
        episodeId: String? = null,
        title: String,
        episodeTitle: String? = null,
        posterUrl: String,
        currentPositionMs: Long,
        durationMs: Long,
        userId: String? = null
    ) {
        val id = if (episodeId != null) "${mediaId}_$episodeId" else mediaId
        val progress = if (durationMs > 0) (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
        val isCompleted = progress >= 0.92f
        val timestamp = System.currentTimeMillis()

        val entity = WatchHistoryEntity(
            id = id,
            mediaId = mediaId,
            episodeId = episodeId,
            title = title,
            episodeTitle = episodeTitle,
            posterUrl = posterUrl,
            currentPositionMs = currentPositionMs,
            durationMs = durationMs,
            progressPercentage = progress,
            lastWatchedTimestamp = timestamp,
            isCompleted = isCompleted
        )
        watchHistoryDao.upsert(entity)

        // Real Firestore Watch History cloud sync
        if (userId != null && !userId.startsWith("guest")) {
            withContext(Dispatchers.IO) {
                try {
                    val data = hashMapOf(
                        "id" to id,
                        "mediaId" to mediaId,
                        "episodeId" to (episodeId ?: ""),
                        "title" to title,
                        "episodeTitle" to (episodeTitle ?: ""),
                        "posterUrl" to posterUrl,
                        "currentPositionMs" to currentPositionMs,
                        "durationMs" to durationMs,
                        "progressPercentage" to progress.toDouble(),
                        "lastWatchedTimestamp" to timestamp,
                        "isCompleted" to isCompleted
                    )
                    firestore?.collection("users")
                        ?.document(userId)
                        ?.collection("watch_history")
                        ?.document(id)
                        ?.set(data)
                } catch (_: Exception) {}
            }
        }
    }

    suspend fun getByMediaId(mediaId: String): WatchHistoryEntity? {
        return watchHistoryDao.getByMediaId(mediaId)
    }

    suspend fun removeHistoryItem(id: String, userId: String? = null) {
        watchHistoryDao.deleteById(id)
        if (userId != null && !userId.startsWith("guest")) {
            withContext(Dispatchers.IO) {
                try {
                    firestore?.collection("users")
                        ?.document(userId)
                        ?.collection("watch_history")
                        ?.document(id)
                        ?.delete()
                } catch (_: Exception) {}
            }
        }
    }

    suspend fun clearHistory(userId: String? = null) {
        watchHistoryDao.clearAll()
    }
}
