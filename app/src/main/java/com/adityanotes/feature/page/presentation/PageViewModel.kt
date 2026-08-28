package com.adityanotes.feature.page.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adityanotes.core.database.entity.NotebookEntity
import com.adityanotes.core.database.entity.PageEntity
import com.adityanotes.feature.notebook.data.NotebookRepository
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
    private val strokeRepository: StrokeRepository,
    private val notebookRepository: NotebookRepository
) : ViewModel() {

    private val _pages = MutableStateFlow<List<PageEntity>>(emptyList())
    val pages: StateFlow<List<PageEntity>> = _pages.asStateFlow()

    private val _currentNotebook = MutableStateFlow<NotebookEntity?>(null)
    val currentNotebook: StateFlow<NotebookEntity?> = _currentNotebook.asStateFlow()

    // Map of pageId to list of visible strokes for that page
    private val _pageStrokes = MutableStateFlow<Map<Long, List<StrokeEntity>>>(emptyMap())
    val pageStrokes: StateFlow<Map<Long, List<StrokeEntity>>> = _pageStrokes.asStateFlow()

    private var currentNotebookId: Long? = null
    private var notebookJob: Job? = null

    /* Room Flow for completed, visible strokes of current active page. */
    private val _strokes = MutableStateFlow<List<StrokeEntity>>(emptyList())
    val strokes: StateFlow<List<StrokeEntity>> = _strokes.asStateFlow()

    private val _currentPage = MutableStateFlow<PageEntity?>(null)
    val currentPage: StateFlow<PageEntity?> = _currentPage.asStateFlow()

    private var currentPageId: Long? = null
    private var strokesJob: Job? = null
    private var strokeSessionId = 0L
    private val strokeMutex = Mutex()

    fun loadNotebook(notebookId: Long, initialPageId: Long? = null) {
        if (currentNotebookId == notebookId && notebookJob?.isActive == true) {
            if (initialPageId != null && currentPageId != initialPageId) {
                setActivePage(initialPageId)
            }
            return
        }

        currentNotebookId = notebookId
        notebookJob?.cancel()
        notebookJob = viewModelScope.launch {
            _currentNotebook.value = notebookRepository.getNotebookById(notebookId)
            pageRepository.getPagesForNotebook(notebookId).collect { pageList ->
                if (pageList.isEmpty()) {
                    val nb = notebookRepository.getNotebookById(notebookId)
                    if (nb == null) {
                        // Notebook was deleted: clean up state and stop observing
                        _pages.value = emptyList()
                        _currentNotebook.value = null
                        _pageStrokes.value = emptyMap()
                        _strokes.value = emptyList()
                        _currentPage.value = null
                        currentPageId = null
                        strokesJob?.cancel()
                        return@collect
                    }
                    // Create first page only if notebook still exists in DB
                    val now = System.currentTimeMillis()
                    runCatching {
                        pageRepository.createPage(
                            PageEntity(
                                notebookId = notebookId,
                                name = "Page 1",
                                paperTemplate = "RULED",
                                isDarkPaper = false,
                                createdAt = now,
                                updatedAt = now
                            )
                        )
                    }
                    return@collect
                }

                _pages.value = pageList

                // Select active page
                val targetPage = if (initialPageId != null) {
                    pageList.firstOrNull { it.id == initialPageId } ?: pageList.first()
                } else if (currentPageId != null) {
                    pageList.firstOrNull { it.id == currentPageId } ?: pageList.first()
                } else {
                    pageList.first()
                }

                setActivePage(targetPage.id)

                // Observe strokes for all pages in this notebook
                val pageIds = pageList.map { it.id }
                strokesJob?.cancel()
                strokesJob = launch {
                    strokeRepository.observeStrokesForPages(pageIds).collect { allStrokes ->
                        val grouped = allStrokes.groupBy { it.pageId }
                        _pageStrokes.value = grouped
                        val activeId = currentPageId
                        if (activeId != null) {
                            _strokes.value = grouped[activeId] ?: emptyList()
                        }
                    }
                }
            }
        }
    }

    fun loadPages(notebookId: Long) {
        loadNotebook(notebookId)
    }

    fun setActivePage(pageId: Long) {
        if (currentPageId == pageId && _currentPage.value?.id == pageId) return
        currentPageId = pageId
        val page = _pages.value.firstOrNull { it.id == pageId }
        _currentPage.value = page
        _strokes.value = _pageStrokes.value[pageId] ?: emptyList()
    }

    fun addNewPage(
        notebookId: Long,
        template: String? = null,
        isDarkPaper: Boolean? = null,
        onCreated: (Long) -> Unit = {}
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val existingPages = _pages.value
            val existingCount = existingPages.size
            val finalName = "Page ${existingCount + 1}"
            val activePage = existingPages.firstOrNull { it.id == currentPageId } ?: existingPages.lastOrNull()
            val finalTemplate = template ?: activePage?.paperTemplate ?: "RULED"
            val finalIsDark = isDarkPaper ?: activePage?.isDarkPaper ?: false
            val newId = pageRepository.createPage(
                PageEntity(
                    notebookId = notebookId,
                    name = finalName,
                    paperTemplate = finalTemplate,
                    isDarkPaper = finalIsDark,
                    createdAt = now,
                    updatedAt = now
                )
            )
            setActivePage(newId)
            onCreated(newId)
        }
    }

    fun duplicatePage(page: PageEntity, onCreated: (Long) -> Unit = {}) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val existingCount = _pages.value.size
            val newId = pageRepository.createPage(
                page.copy(
                    id = 0,
                    name = "Page ${existingCount + 1}",
                    createdAt = now,
                    updatedAt = now
                )
            )
            // Duplicate strokes
            val currentStrokes = _pageStrokes.value[page.id] ?: emptyList()
            if (currentStrokes.isNotEmpty()) {
                strokeRepository.duplicateStrokes(newId, currentStrokes)
            }
            setActivePage(newId)
            onCreated(newId)
        }
    }

    fun deletePage(page: PageEntity) {
        viewModelScope.launch {
            pageRepository.deletePage(page)
            val remaining = _pages.value.filterNot { it.id == page.id }
            if (remaining.isNotEmpty()) {
                setActivePage(remaining.first().id)
            }
        }
    }

    fun loadStrokes(pageId: Long) {
        setActivePage(pageId)
        if (_pages.value.isEmpty()) {
            viewModelScope.launch {
                val page = pageRepository.getPageById(pageId)
                if (page != null) {
                    loadNotebook(page.notebookId, pageId)
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

    /**
     * Instantly removes strokes from in-memory state and triggers Room deletion.
     */
    fun eraseStrokesInstantly(pageId: Long, strokeIds: Set<Long>) {
        if (strokeIds.isEmpty()) return
        _strokes.value = _strokes.value.filterNot { it.id in strokeIds }
        viewModelScope.launch {
            strokeMutex.withLock {
                strokeRepository.deleteStrokesDirect(strokeIds.toList())
            }
        }
    }

    /**
     * Commits a single undoable ERASE operation for all strokes erased in one continuous drag.
     */
    fun commitEraseSession(pageId: Long, erasedStrokes: List<StrokeEntity>) {
        if (erasedStrokes.isEmpty()) return
        viewModelScope.launch {
            strokeMutex.withLock {
                strokeRepository.recordEraseOperation(pageId, erasedStrokes)
            }
        }
    }

    /**
     * Transforms (moves, scales, recolors) selected strokes with full Undo/Redo.
     */
    fun transformStrokes(
        pageId: Long,
        beforeStrokes: List<StrokeEntity>,
        afterStrokes: List<StrokeEntity>
    ) {
        if (beforeStrokes.isEmpty() || afterStrokes.isEmpty()) return
        val afterMap = afterStrokes.associateBy { it.id }
        _strokes.value = _strokes.value.map { stroke ->
            afterMap[stroke.id] ?: stroke
        }
        val currentGroup = _pageStrokes.value.toMutableMap()
        val pageStrokesList = (currentGroup[pageId] ?: emptyList()).map { stroke ->
            afterMap[stroke.id] ?: stroke
        }
        currentGroup[pageId] = pageStrokesList
        _pageStrokes.value = currentGroup

        viewModelScope.launch {
            strokeMutex.withLock {
                strokeRepository.transformStrokes(pageId, beforeStrokes, afterStrokes)
            }
        }
    }

    /**
     * Transforms strokes across one or more pages atomically with full Undo/Redo.
     */
    fun transformStrokesMultiPage(
        beforeStrokes: List<StrokeEntity>,
        afterStrokes: List<StrokeEntity>
    ) {
        if (beforeStrokes.isEmpty() || afterStrokes.isEmpty()) return
        val affectedIds = beforeStrokes.map { it.id }.toSet()
        val afterMap = afterStrokes.associateBy { it.id }

        _strokes.value = _strokes.value.mapNotNull { stroke ->
            if (stroke.id in affectedIds) afterMap[stroke.id] else stroke
        }

        val currentGroup = _pageStrokes.value.toMutableMap()
        for ((pId, strokes) in currentGroup) {
            currentGroup[pId] = strokes.filterNot { it.id in affectedIds }
        }
        val afterGrouped = afterStrokes.groupBy { it.pageId }
        for ((pId, strokes) in afterGrouped) {
            currentGroup[pId] = (currentGroup[pId] ?: emptyList()) + strokes
        }
        _pageStrokes.value = currentGroup

        val activeId = currentPageId
        if (activeId != null) {
            _strokes.value = currentGroup[activeId] ?: emptyList()
        }

        viewModelScope.launch {
            strokeMutex.withLock {
                strokeRepository.transformStrokesMultiPage(beforeStrokes, afterStrokes)
            }
        }
    }

    /**
     * Updates in-memory state during precision erasing with temporary negative IDs.
     */
    fun updateLivePrecisionStrokes(pageId: Long, deletedIds: Set<Long>, newStrokes: List<StrokeEntity>) {
        if (deletedIds.isEmpty() && newStrokes.isEmpty()) return
        _strokes.value = _strokes.value.filterNot { it.id in deletedIds } + newStrokes
        val currentGroup = _pageStrokes.value.toMutableMap()
        val pageStrokesList = (currentGroup[pageId] ?: emptyList()).filterNot { it.id in deletedIds } + newStrokes
        currentGroup[pageId] = pageStrokesList
        _pageStrokes.value = currentGroup
    }

    /**
     * Commits a precision erase session to Room DB once the gesture completes.
     */
    fun commitPrecisionEraseSession(
        pageId: Long,
        deletedIds: Set<Long>,
        originalStrokes: List<StrokeEntity>,
        finalStrokes: List<StrokeEntity>
    ) {
        if (deletedIds.isEmpty() && finalStrokes.isEmpty()) return
        viewModelScope.launch {
            strokeMutex.withLock {
                strokeRepository.commitPrecisionErase(pageId, deletedIds, originalStrokes, finalStrokes)
            }
        }
    }

    /**
     * Clones selected strokes and adds them to the page with full Undo/Redo.
     */
    fun duplicateStrokes(
        pageId: Long,
        strokes: List<StrokeEntity>,
        onCreated: (List<StrokeEntity>) -> Unit = {}
    ) {
        if (strokes.isEmpty()) return
        val offsetStrokes = strokes.map { stroke ->
            val points = StrokePointCodec.decode(stroke.pointData)
            val shifted = points.map { pt -> pt.copy(x = pt.x + 30f, y = pt.y + 30f) }
            stroke.copy(pointData = StrokePointCodec.encode(shifted))
        }
        viewModelScope.launch {
            strokeMutex.withLock {
                val created = strokeRepository.duplicateStrokes(pageId, offsetStrokes)
                _strokes.value = _strokes.value + created
                val currentGroup = _pageStrokes.value.toMutableMap()
                currentGroup[pageId] = (currentGroup[pageId] ?: emptyList()) + created
                _pageStrokes.value = currentGroup
                onCreated(created)
            }
        }
    }

    /**
     * Deletes a batch of strokes (e.g. from Lasso delete) in one undoable step.
     */
    fun deleteStrokes(pageId: Long, strokesToDelete: List<StrokeEntity>) {
        if (strokesToDelete.isEmpty()) return
        val idsToDelete = strokesToDelete.map { it.id }.toSet()
        _strokes.value = _strokes.value.filterNot { it.id in idsToDelete }
        val currentGroup = _pageStrokes.value.toMutableMap()
        currentGroup[pageId] = (currentGroup[pageId] ?: emptyList()).filterNot { it.id in idsToDelete }
        _pageStrokes.value = currentGroup

        viewModelScope.launch {
            strokeMutex.withLock {
                strokeRepository.recordEraseOperation(pageId, strokesToDelete)
            }
        }
    }

    /**
     * Deletes a batch of strokes across multiple pages in one undoable step.
     */
    fun deleteStrokesMultiPage(strokesToDelete: List<StrokeEntity>) {
        if (strokesToDelete.isEmpty()) return
        val idsToDelete = strokesToDelete.map { it.id }.toSet()
        _strokes.value = _strokes.value.filterNot { it.id in idsToDelete }
        val currentGroup = _pageStrokes.value.toMutableMap()
        for ((pId, strokes) in currentGroup) {
            currentGroup[pId] = strokes.filterNot { it.id in idsToDelete }
        }
        _pageStrokes.value = currentGroup

        val activeId = currentPageId
        if (activeId != null) {
            _strokes.value = currentGroup[activeId] ?: emptyList()
        }

        val grouped = strokesToDelete.groupBy { it.pageId }
        viewModelScope.launch {
            strokeMutex.withLock {
                for ((pageId, list) in grouped) {
                    strokeRepository.recordEraseOperation(pageId, list)
                }
            }
        }
    }

    /**
     * Exports current notebook or specific page to a PDF document and opens the system share chooser.
     */
    fun exportToPdf(
        context: android.content.Context,
        targetPageId: Long? = null,
        onComplete: (Boolean, String?) -> Unit
    ) {
        val notebook = _currentNotebook.value ?: return onComplete(false, "Notebook not found")
        val pageList = _pages.value
        if (pageList.isEmpty()) return onComplete(false, "No pages to export")

        viewModelScope.launch {
            val result = com.adityanotes.feature.page.data.PdfExporter.exportPdf(
                context = context,
                notebook = notebook,
                pages = pageList,
                pageStrokesProvider = { pageId ->
                    if (pageId == currentPageId) {
                        _strokes.value
                    } else {
                        strokeRepository.observeStrokes(pageId).let { flow ->
                            // Read current snapshot
                            var snapshot = emptyList<StrokeEntity>()
                            val job = launch {
                                flow.collect { snapshot = it }
                            }
                            kotlinx.coroutines.delay(50)
                            job.cancel()
                            snapshot
                        }
                    }
                },
                targetPageId = targetPageId
            )

            result.fold(
                onSuccess = { file ->
                    com.adityanotes.feature.page.data.PdfExporter.sharePdf(
                        context = context,
                        pdfFile = file,
                        title = "${notebook.name}.pdf"
                    )
                    onComplete(true, null)
                },
                onFailure = { error ->
                    onComplete(false, error.message ?: "Failed to generate PDF")
                }
            )
        }
    }

    fun undo() {
        val pageIds = _pages.value.map { it.id }
        if (pageIds.isEmpty()) return

        viewModelScope.launch {
            strokeMutex.withLock {
                val activeId = currentPageId
                val undone = if (activeId != null) strokeRepository.undo(activeId) else false
                if (!undone) {
                    strokeRepository.undo(pageIds)
                }
            }
        }
    }

    fun redo() {
        val pageIds = _pages.value.map { it.id }
        if (pageIds.isEmpty()) return

        viewModelScope.launch {
            strokeMutex.withLock {
                val activeId = currentPageId
                val redone = if (activeId != null) strokeRepository.redo(activeId) else false
                if (!redone) {
                    strokeRepository.redo(pageIds)
                }
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
        notebookJob?.cancel()
        super.onCleared()
    }
}
