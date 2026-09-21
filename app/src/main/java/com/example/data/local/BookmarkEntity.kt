package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey val mediaId: String,
    val addedTimestamp: Long = System.currentTimeMillis()
)
