package com.max97k.pddf.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ReadingProgressDao {
    @Query("SELECT * FROM reading_progress WHERE documentUriHash = :hash")
    suspend fun getProgress(hash: Int): ReadingProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProgress(progress: ReadingProgressEntity)

    @Query("DELETE FROM reading_progress WHERE documentUriHash = :hash")
    suspend fun clearProgress(hash: Int)
}
