package com.max97k.pddf.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.max97k.pddf.data.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class ReadingProgressDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: ReadingProgressDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.readingProgressDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun testSaveAndGetProgress() = runBlocking {
        val hash = 12345
        val progress = ReadingProgressEntity(hash, 5, 10)
        dao.saveProgress(progress)

        val retrieved = dao.getProgress(hash)
        assertEquals(5, retrieved?.lastViewedPage)
        assertEquals(10, retrieved?.totalPages)
    }

    @Test
    fun testClearProgress() = runBlocking {
        val hash = 12345
        val progress = ReadingProgressEntity(hash, 5, 10)
        dao.saveProgress(progress)

        dao.clearProgress(hash)
        val retrieved = dao.getProgress(hash)
        assertNull(retrieved)
    }
}
