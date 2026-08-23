package com.adityanotes.feature.page.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adityanotes.core.database.entity.PageEntity
import com.adityanotes.feature.page.data.PageRepository
import com.adityanotes.feature.page.data.StrokeEntity
import com.adityanotes.feature.page.data.StrokePoint
import com.adityanotes.feature.page.data.StrokePointCodec
import com.adityanotes.feature.page.data.StrokeRepository
import com.adityanotes.feature.page.data.StrokeTool
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@HiltViewModel
class PageViewModel @Inject constructor(
    private val pageRepository: PageRepository,
    private val strokeRepository: StrokeRepository
) : ViewModel() {

    private val _pages = MutableStateFlow<List<PageEntity>>(emptyList())
    val pages: StateFlow<List<PageEntity>> = _pages.asStateFlow()

    private var currentNotebookId: Long? = null

    fun loadPages(notebookId: Long) {
        if (currentNotebookId == notebookId) {
            return
        }

        currentNotebookId = notebookId
        viewModelScope.launch {
            pageRepository.getPagesForNotebook(notebookId).collect { pageList ->
                _pages.value = pageList
            }
        }
    }

    fun createPage(notebookId: Long, name: String) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            pageRepository.createPage(
                PageEntity(
                    notebookId = notebookId,
                    name = name,
                    createdAt = now,
                    updatedAt = now
                )
            )
        }
    }

    fun renamePage(page: PageEntity, newName: String) {
        if (newName.isBlank()) {
            return
        }

        viewModelScope.launch {
            pageRepository.updatePage(
                page.copy(
                    name = newName.trim(),
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun deletePage(page: PageEntity) {
        viewModelScope.launch {
            pageRepository.deletePage(page)
        }
    }

    /* Room Flow is the single source of truth for completed, visible strokes. */
    private val _strokes = MutableStateFlow<List<StrokeEntity>>(emptyList())
    val strokes: StateFlow<List<StrokeEntity>> = _strokes.asStateFlow()

    private val _currentPage = MutableStateFlow<PageEntity?>(null)
    val currentPage: StateFlow<PageEntity?> = _currentPage.asStateFlow()

    private var currentPageId: Long? = null
    private var strokesJob: Job? = null
    private var strokeSessionId = 0L
    private val strokeMutex = Mutex()

    fun loadStrokes(pageId: Long) {
        if (currentPageId == pageId && strokesJob?.isActive == true) {
            return
        }

        currentPageId = pageId
        val sessionId = ++strokeSessionId
        strokesJob?.cancel()
        _strokes.value = emptyList()
        _currentPage.value = null

        strokesJob = viewModelScope.launch {
            val page = pageRepository.getPageById(pageId)
            if (currentPageId == pageId && strokeSessionId == sessionId) {
                _currentPage.value = page
            }

            strokeRepository.observeStrokes(pageId).collect { databaseStrokes ->
                if (currentPageId == pageId && strokeSessionId == sessionId) {
                    _strokes.value = databaseStrokes
                }
            }
        }
    }

    fun addStroke(
        pageId: Long,
        points: List<StrokePoint>,
        color: Long,
        strokeWidth: Float,
        tool: StrokeTool
    ) {
        if (points.isEmpty()) {
            return
        }

        val stroke = StrokeEntity(
            pageId = pageId,
            pointData = StrokePointCodec.encode(points),
            color = color,
            strokeWidth = strokeWidth,
            tool = tool.name
        )

        viewModelScope.launch {
            strokeMutex.withLock {
                strokeRepository.addStroke(stroke)
            }
        }
    }

    fun eraseStrokes(
        pageId: Long,
        eraserPoints: List<StrokePoint>,
        eraserWidth: Float
    ) {
        if (eraserPoints.isEmpty()) {
            return
        }

        viewModelScope.launch {
            strokeMutex.withLock {
                strokeRepository.eraseStrokes(
                    pageId = pageId,
                    eraserPoints = eraserPoints,
                    eraserWidth = eraserWidth
                )
            }
        }
    }

    fun undo() {
        val pageId = currentPageId ?: return

        viewModelScope.launch {
            strokeMutex.withLock {
                strokeRepository.undo(pageId)
            }
        }
    }

    fun redo() {
        val pageId = currentPageId ?: return

        viewModelScope.launch {
            strokeMutex.withLock {
                strokeRepository.redo(pageId)
            }
        }
    }

    fun updatePaper(
        pageId: Long,
        paperTemplate: String,
        isDarkPaper: Boolean
    ) {
        viewModelScope.launch {
            val page = pageRepository.getPageById(pageId) ?: return@launch
            val updatedPage = page.copy(
                paperTemplate = paperTemplate,
                isDarkPaper = isDarkPaper,
                updatedAt = System.currentTimeMillis()
            )
            pageRepository.updatePage(updatedPage)

            if (currentPageId == pageId) {
                _currentPage.value = updatedPage
            }
        }
    }

    override fun onCleared() {
        strokesJob?.cancel()
        super.onCleared()
    }
}
