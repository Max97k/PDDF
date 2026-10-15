package com.max97k.pddf

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.max97k.pddf.data.AppDatabase
import com.max97k.pddf.data.BookmarkDao
import com.max97k.pddf.data.BookmarkEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BookmarkDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var bookmarkDao: BookmarkDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        bookmarkDao = database.bookmarkDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun insertAndGetBookmarks() = runBlocking {
        val hash = "testhash123"
        val bookmark1 = BookmarkEntity(fileUriHash = hash, pageIndex = 1, note = "Test Note", timestamp = 1000L)
        val bookmark2 = BookmarkEntity(fileUriHash = hash, pageIndex = 5, note = null, timestamp = 2000L)

        bookmarkDao.insertBookmark(bookmark1)
        bookmarkDao.insertBookmark(bookmark2)

        val bookmarks = bookmarkDao.getBookmarksForFile(hash).first()
        assertEquals(2, bookmarks.size)
        assertEquals(1, bookmarks[0].pageIndex)
        assertEquals(5, bookmarks[1].pageIndex)
    }

    @Test
    fun deleteBookmarkByPage() = runBlocking {
        val hash = "testhash123"
        val bookmark1 = BookmarkEntity(fileUriHash = hash, pageIndex = 1, note = "Test Note", timestamp = 1000L)
        bookmarkDao.insertBookmark(bookmark1)

        bookmarkDao.deleteBookmarkByPage(hash, 1)

        val bookmarks = bookmarkDao.getBookmarksForFile(hash).first()
        assertEquals(0, bookmarks.size)
    }
}
