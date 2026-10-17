package com.max97k.pddf.feature.viewer

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.max97k.pddf.domain.repository.PdfRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

class PdfTextToSpeechController(
    private val context: Context,
    private val repository: PdfRepository,
    private val coroutineScope: CoroutineScope
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPage = MutableStateFlow(1)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    private val _playbackRate = MutableStateFlow(1.0f)
    val playbackRate: StateFlow<Float> = _playbackRate.asStateFlow()

    private val _totalPages = MutableStateFlow(0)
    val totalPages: StateFlow<Int> = _totalPages.asStateFlow()

    private var currentUri: String? = null

    init {
        tts = TextToSpeech(context, this)
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _isPlaying.value = true
            }

            override fun onDone(utteranceId: String?) {
                if (_isPlaying.value) {
                    coroutineScope.launch {
                        nextPage()
                    }
                }
            }

            override fun onError(utteranceId: String?) {
                _isPlaying.value = false
            }
        })
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.getDefault())
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("PdfTTSController", "Language not supported")
            } else {
                isInitialized = true
            }
        } else {
            Log.e("PdfTTSController", "TTS Initialization failed")
        }
    }

    fun play(uri: String, startPage: Int = 1) {
        currentUri = uri
        _currentPage.value = startPage
        coroutineScope.launch {
            _totalPages.value = repository.getPageCount(uri)
            readCurrentPage()
        }
    }

    private fun readCurrentPage() {
        val uri = currentUri ?: return
        val page = _currentPage.value
        if (page > _totalPages.value && _totalPages.value > 0) {
            _isPlaying.value = false
            return
        }

        coroutineScope.launch {
            _isPlaying.value = true
            val text = repository.extractPageText(uri, page)
            if (!text.isNullOrBlank()) {
                val utteranceId = "page_$page"
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            } else {
                // If page is empty, auto skip to next
                nextPage()
            }
        }
    }

    fun pause() {
        _isPlaying.value = false
        tts?.stop()
    }

    fun stop() {
        _isPlaying.value = false
        _currentPage.value = 1
        tts?.stop()
    }

    fun nextPage() {
        if (_currentPage.value < _totalPages.value) {
            _currentPage.value += 1
            if (_isPlaying.value) {
                readCurrentPage()
            }
        } else {
            _isPlaying.value = false
        }
    }

    fun previousPage() {
        if (_currentPage.value > 1) {
            _currentPage.value -= 1
            if (_isPlaying.value) {
                readCurrentPage()
            }
        }
    }

    fun setRate(rate: Float) {
        _playbackRate.value = rate
        tts?.setSpeechRate(rate)
        if (_isPlaying.value) {
            // Restart current page with new rate
            readCurrentPage()
        }
    }

    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}
