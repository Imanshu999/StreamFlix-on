package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY addedTimestamp DESC")
    fun getAllBookmarks(): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE mediaId = :mediaId")
    suspend fun deleteByMediaId(mediaId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE mediaId = :mediaId)")
    suspend fun isBookmarked(mediaId: String): Boolean

    @Query("DELETE FROM bookmarks")
    suspend fun clearAll()
}
