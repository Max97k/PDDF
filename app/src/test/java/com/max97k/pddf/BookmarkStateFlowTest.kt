package com.max97k.pddf

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.max97k.pddf.data.BookmarkDao
import com.max97k.pddf.data.BookmarkEntity
import com.max97k.pddf.feature.viewer.PdfViewerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

class FakeBookmarkDao : BookmarkDao {
    private val data = mutableMapOf<String, MutableStateFlow<List<BookmarkEntity>>>()

    override fun getBookmarksForFile(fileUriHash: String): kotlinx.coroutines.flow.Flow<List<BookmarkEntity>> {
        return data.getOrPut(fileUriHash) { MutableStateFlow(emptyList()) }
    }

    override suspend fun insertBookmark(bookmark: BookmarkEntity) {
        val list = data[bookmark.fileUriHash]?.value?.toMutableList() ?: mutableListOf()
        list.add(bookmark)
        data[bookmark.fileUriHash]?.value = list
    }

    override suspend fun deleteBookmark(bookmark: BookmarkEntity) {
        val list = data[bookmark.fileUriHash]?.value?.toMutableList() ?: mutableListOf()
        list.remove(bookmark)
        data[bookmark.fileUriHash]?.value = list
    }

    override suspend fun deleteBookmarkByPage(fileUriHash: String, pageIndex: Int) {
        val list = data[fileUriHash]?.value?.toMutableList() ?: mutableListOf()
        list.removeAll { it.pageIndex == pageIndex }
        data[fileUriHash]?.value = list
    }
}

@RunWith(AndroidJUnit4::class)
class BookmarkStateFlowTest {

    @OptIn(ExperimentalCoroutinesApi::class)
    private val testDispatcher = StandardTestDispatcher()

    @OptIn(ExperimentalCoroutinesApi::class)
    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testViewModelStateFlow() = runTest {
        val fakeDao = FakeBookmarkDao()
        val viewModel = PdfViewerViewModel(fakeDao)
        val uri = Uri.parse("file://test.pdf")

        viewModel.loadBookmarks(uri)
        testDispatcher.scheduler.advanceUntilIdle()

        var bookmarks = viewModel.bookmarks.first()
        assertEquals(0, bookmarks.size)

        viewModel.addBookmark(0, "Test 1")
        testDispatcher.scheduler.advanceUntilIdle()

        bookmarks = viewModel.bookmarks.first()
        assertEquals(1, bookmarks.size)
        assertEquals(0, bookmarks[0].pageIndex)

        viewModel.deleteBookmark(0)
        testDispatcher.scheduler.advanceUntilIdle()

        bookmarks = viewModel.bookmarks.first()
        assertEquals(0, bookmarks.size)
    }
}
