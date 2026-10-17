package com.max97k.pddf.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE fileUriHash = :fileUriHash ORDER BY pageIndex ASC")
    fun getBookmarksForFile(fileUriHash: String): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntity)

    @Delete
    suspend fun deleteBookmark(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE fileUriHash = :fileUriHash AND pageIndex = :pageIndex")
    suspend fun deleteBookmarkByPage(fileUriHash: String, pageIndex: Int)
}
