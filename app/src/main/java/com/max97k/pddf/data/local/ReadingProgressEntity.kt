package com.max97k.pddf.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reading_progress")
data class ReadingProgressEntity(
    @PrimaryKey val documentUriHash: Int,
    val lastViewedPage: Int,
    val totalPages: Int,
    val timestamp: Long = System.currentTimeMillis()
)
