package com.max97k.pddf.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PasswordDao {
    @Query("SELECT * FROM passwords ORDER BY useCount DESC, lastUsedAt DESC, id DESC")
    fun getAllPasswords(): Flow<List<PasswordEntity>>

    @Query("SELECT * FROM passwords ORDER BY useCount DESC, lastUsedAt DESC, id DESC")
    suspend fun getAllPasswordsList(): List<PasswordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPassword(password: PasswordEntity)

    @Query("UPDATE passwords SET useCount = useCount + 1, lastUsedAt = :timestamp WHERE id = :id")
    suspend fun incrementUsage(id: Int, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM passwords")
    suspend fun clearAllPasswords()

    @Query("DELETE FROM passwords WHERE id = :id")
    suspend fun deletePasswordById(id: Int)

    @Delete
    suspend fun deletePassword(password: PasswordEntity)
}
