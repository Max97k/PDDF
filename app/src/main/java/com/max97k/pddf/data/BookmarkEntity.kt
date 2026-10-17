package com.max97k.pddf.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val fileUriHash: String,
    val pageIndex: Int,
    val note: String?,
    val timestamp: Long
)
