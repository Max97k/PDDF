package com.max97k.pddf.feature.viewer

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.max97k.pddf.data.BookmarkDao
import com.max97k.pddf.data.BookmarkEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PdfViewerViewModel @Inject constructor(
    private val bookmarkDao: BookmarkDao
) : ViewModel() {

    private val _bookmarks = MutableStateFlow<List<BookmarkEntity>>(emptyList())
    val bookmarks: StateFlow<List<BookmarkEntity>> = _bookmarks.asStateFlow()

    private var currentFileUriHash: String = ""
    private var bookmarksJob: Job? = null

    fun loadBookmarks(uri: Uri) {
        val newHash = uri.toString().hashCode().toString()
        if (newHash == currentFileUriHash && bookmarksJob?.isActive == true) return
        currentFileUriHash = newHash
        bookmarksJob?.cancel()
        bookmarksJob = viewModelScope.launch {
            bookmarkDao.getBookmarksForFile(currentFileUriHash).collect {
                _bookmarks.value = it
            }
        }
    }

    fun addBookmark(pageIndex: Int, note: String?) {
        viewModelScope.launch {
            bookmarkDao.insertBookmark(
                BookmarkEntity(
                    fileUriHash = currentFileUriHash,
                    pageIndex = pageIndex,
                    note = note,
                    timestamp = System.currentTimeMillis()
                )
            )
        }
    }

    fun deleteBookmark(pageIndex: Int) {
        viewModelScope.launch {
            bookmarkDao.deleteBookmarkByPage(currentFileUriHash, pageIndex)
        }
    }
}
