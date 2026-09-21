package com.example.data.repository

import android.util.Log
import com.example.data.local.BookmarkDao
import com.example.data.local.BookmarkEntity
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class BookmarkRepository(private val bookmarkDao: BookmarkDao) {

    private val firestore: FirebaseFirestore? = try {
        FirebaseFirestore.getInstance()
    } catch (e: Exception) {
        null
    }

    val allBookmarks: Flow<List<BookmarkEntity>> = bookmarkDao.getAllBookmarks()
    val bookmarkedMediaIds: Flow<Set<String>> = allBookmarks.map { list ->
        list.map { it.mediaId }.toSet()
    }

    suspend fun toggleBookmark(mediaId: String, userId: String? = null) {
        val exists = bookmarkDao.isBookmarked(mediaId)
        if (exists) {
            bookmarkDao.deleteByMediaId(mediaId)
            if (userId != null && !userId.startsWith("guest")) {
                withContext(Dispatchers.IO) {
                    try {
                        firestore?.collection("users")
                            ?.document(userId)
                            ?.collection("bookmarks")
                            ?.document(mediaId)
                            ?.delete()
                    } catch (e: Exception) {
                        Log.e("BookmarkRepo", "Failed to remove bookmark from Firestore", e)
                    }
                }
            }
        } else {
            val entity = BookmarkEntity(mediaId = mediaId, addedTimestamp = System.currentTimeMillis())
            bookmarkDao.insert(entity)
            if (userId != null && !userId.startsWith("guest")) {
                withContext(Dispatchers.IO) {
                    try {
                        val data = hashMapOf(
                            "mediaId" to mediaId,
                            "addedTimestamp" to entity.addedTimestamp
                        )
                        firestore?.collection("users")
                            ?.document(userId)
                            ?.collection("bookmarks")
                            ?.document(mediaId)
                            ?.set(data)
                    } catch (e: Exception) {
                        Log.e("BookmarkRepo", "Failed to sync bookmark to Firestore", e)
                    }
                }
            }
        }
    }

    suspend fun isBookmarked(mediaId: String): Boolean {
        return bookmarkDao.isBookmarked(mediaId)
    }

    suspend fun syncBookmarksFromFirestore(userId: String) {
        if (userId.startsWith("guest") || firestore == null) return
        withContext(Dispatchers.IO) {
            try {
                val snapshot = firestore.collection("users")
                    .document(userId)
                    .collection("bookmarks")
                    .get()
                for (doc in snapshot.result.documents) {
                    val mediaId = doc.getString("mediaId") ?: doc.id
                    val timestamp = doc.getLong("addedTimestamp") ?: System.currentTimeMillis()
                    bookmarkDao.insert(BookmarkEntity(mediaId, timestamp))
                }
            } catch (e: Exception) {
                Log.e("BookmarkRepo", "Failed to pull bookmarks from Firestore", e)
            }
        }
    }

    suspend fun clearAll(userId: String? = null) {
        bookmarkDao.clearAll()
    }
}
