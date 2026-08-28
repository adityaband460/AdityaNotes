package com.adityanotes.feature.page.presentation

import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.SystemClock
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.ink.authoring.InProgressStrokeId
import androidx.ink.authoring.InProgressStrokesFinishedListener
import androidx.ink.authoring.InProgressStrokesView
import androidx.ink.brush.Brush
import androidx.ink.brush.BrushFamily
import androidx.ink.brush.InputToolType
import androidx.ink.brush.StockBrushes
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.Stroke as InkStroke
import androidx.ink.strokes.StrokeInput
import androidx.input.motionprediction.MotionEventPredictor
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.adityanotes.core.database.entity.PageEntity
import com.adityanotes.feature.page.data.StrokeEntity
import com.adityanotes.feature.page.data.StrokeOperationType
import com.adityanotes.feature.page.data.StrokePoint
import com.adityanotes.feature.page.data.StrokePointCodec
import com.adityanotes.feature.page.data.StrokeRepository
import com.adityanotes.feature.page.data.StrokeTool
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

// Document layout constants in virtual coordinate units
const val PAGE_WIDTH = 800f
const val PAGE_HEIGHT = 1130f
const val PAGE_GAP = 0f

fun pageTopY(index: Int): Float = index * (PAGE_HEIGHT + PAGE_GAP)

enum class EraserMode(val label: String, val icon: String, val description: String) {
    STROKE("Stroke Eraser", "🧹", "Erases whole strokes on touch"),
    PRECISION("Precision Eraser", "🎯", "Erases only within circle cursor")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageEditorScreen(
    notebookId: Long?,
    initialPageId: Long?,
    viewModel: PageViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val pages by viewModel.pages.collectAsStateWithLifecycle()
    val pageStrokes by viewModel.pageStrokes.collectAsStateWithLifecycle()
    val currentNotebook by viewModel.currentNotebook.collectAsStateWithLifecycle()
    val activePage by viewModel.currentPage.collectAsStateWithLifecycle()

    LaunchedEffect(notebookId, initialPageId) {
        if (notebookId != null) {
            viewModel.loadNotebook(notebookId, initialPageId)
        } else if (initialPageId != null) {
            viewModel.loadStrokes(initialPageId)
        }
    }

    var selectedTool by remember { mutableStateOf(EditorTool.PEN) }
    var eraserMode by remember { mutableStateOf(EraserMode.STROKE) }

    // Tool-specific size states (Defaults tuned to 5-preset standards)
    var penWidth by remember { mutableFloatStateOf(2.2f) }
    var highlighterWidth by remember { mutableFloatStateOf(24.0f) }
    var eraserWidth by remember { mutableFloatStateOf(80.0f) }
    var laserWidth by remember { mutableFloatStateOf(5.5f) }
    var laserDurationSec by remember { mutableFloatStateOf(2.5f) }
    var laserHasTail by remember { mutableStateOf(true) }

    val activeStrokeWidth = when (selectedTool) {
        EditorTool.PEN -> penWidth
        EditorTool.HIGHLIGHTER -> highlighterWidth
        EditorTool.ERASER -> eraserWidth
        EditorTool.LASER -> laserWidth
        EditorTool.LASSO -> 2f
    }

    var zoom by remember { mutableFloatStateOf(1f) }
    var baseFitZoom by remember { mutableFloatStateOf(1f) }
    var panX by remember { mutableFloatStateOf(0f) }
    var panY by remember { mutableFloatStateOf(24f) }
    var isInitializedFit by remember { mutableStateOf(false) }
    var fingerDrawingEnabled by remember { mutableStateOf(false) }
    var showThumbnailOverviewSheet by remember { mutableStateOf(false) }
    var showLaserSettingsDialog by remember { mutableStateOf(false) }

    // 5 Quick Color Slots
    val quickColors = remember {
        mutableStateListOf(
            0xFF1C1D1FL, // Onyx Black
            0xFF1A56DBL, // Sapphire Blue
            0xFFE02424L, // Crimson Red
            0xFF057A55L, // Emerald Green
            0xFF7E3AF2L  // Royal Violet
        )
    }
    var activeColorSlotIndex by remember { mutableIntStateOf(0) }
    val selectedColor = quickColors.getOrElse(activeColorSlotIndex) { quickColors.first() }

    // Lasso Selection States
    var selectedLassoCount by remember { mutableIntStateOf(0) }
    var lassoDraggingActive by remember { mutableStateOf(false) }
    var lassoSelectedStrokeIds by remember { mutableStateOf(emptySet<Long>()) }

    var onLassoDuplicateAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var onLassoRecolorAction by remember { mutableStateOf<((Long) -> Unit)?>(null) }
    var onLassoDeleteAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var onLassoDeselectAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    // Dialog & popover states
    var showColorPickerDialog by remember { mutableStateOf(false) }
    var showSizeCustomizerDialog by remember { mutableStateOf(false) }
    var showAddPageDialog by remember { mutableStateOf(false) }
    var isExportingPdf by remember { mutableStateOf(false) }

    // Determine currently visible/active page based on viewport center
    val currentVisiblePageIndex = remember(panY, zoom, pages.size, screenHeightPx) {
        if (pages.isEmpty()) 0
        else {
            val viewportCenterY = if (screenHeightPx > 0f) screenHeightPx / 2f else 600f
            val docCenterY = (-panY + viewportCenterY) / zoom
            val idx = (docCenterY / PAGE_HEIGHT).toInt()
            idx.coerceIn(0, pages.size - 1)
        }
    }

    val currentPage = pages.getOrNull(currentVisiblePageIndex) ?: activePage

    LaunchedEffect(currentPage?.id) {
        currentPage?.id?.let { viewModel.setActivePage(it) }
    }

    val activePaperTemplate = remember(currentPage?.paperTemplate) {
        PaperTemplate.entries.firstOrNull { it.name == currentPage?.paperTemplate } ?: PaperTemplate.RULED
    }
    val activeIsDarkPaper = currentPage?.isDarkPaper ?: false

    val effectiveColor = remember(selectedColor, selectedTool) {
        selectedColor.withToolAlpha(selectedTool)
    }

    val penBrushFamily = remember { StockBrushes.marker() }
    val highlighterBrushFamily = remember { StockBrushes.highlighter() }
    val canvasStrokeRenderer = remember { CanvasStrokeRenderer.create() }
    val strokeCache = remember { mutableMapOf<Long, InkStroke?>() }
    val recentFinishedStrokes = remember { mutableMapOf<String, InkStroke>() }
    val strokeToScreenMatrix = remember(zoom) {
        Matrix().apply {
            setScale(zoom, zoom)
        }
    }

    // Optimistic strokes for zero-flicker dry layer handoff
    val optimisticStrokes = remember { mutableStateListOf<StrokeEntity>() }
    var inkViewRef by remember { mutableStateOf<ContinuousInkView?>(null) }

    LaunchedEffect(pageStrokes) {
        if (optimisticStrokes.isNotEmpty()) {
            optimisticStrokes.clear()
        }
    }

    val brush = remember(selectedTool, effectiveColor, activeStrokeWidth) {
        val family = if (selectedTool == EditorTool.HIGHLIGHTER) highlighterBrushFamily else penBrushFamily
        Brush.createWithColorIntArgb(
            family = family,
            colorIntArgb = effectiveColor.toInt(),
            size = activeStrokeWidth,
            epsilon = 0.01f
        )
    }

    val onViewportTransform: (Float, Float, Float, Float, Float) -> Unit =
        { scaleFactor, panDeltaX, panDeltaY, focusX, focusY ->
            val minZoom = (baseFitZoom * 0.3f).coerceAtLeast(0.2f)
            val maxZoom = (baseFitZoom * 10.0f).coerceAtLeast(6.0f)
            val newZoom = (zoom * scaleFactor).coerceIn(minZoom, maxZoom)
            val actualScale = newZoom / zoom
            panX = focusX - (focusX - panX) * actualScale + panDeltaX
            panY = focusY - (focusY - panY) * actualScale + panDeltaY
            zoom = newZoom
        }

    val pageCount = pages.size.coerceAtLeast(1)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            FreeNotesTopBar(
                title = currentNotebook?.name ?: "Notebook",
                currentPageIndex = currentVisiblePageIndex + 1,
                totalPages = pageCount,
                zoomPercentage = if (baseFitZoom > 0f) ((zoom / baseFitZoom) * 100).toInt() else 100,
                paperTemplate = activePaperTemplate,
                isDarkPaper = activeIsDarkPaper,
                fingerDrawingEnabled = fingerDrawingEnabled,
                onBack = onBack,
                onUndo = {
                    strokeCache.clear()
                    viewModel.undo()
                },
                onRedo = {
                    strokeCache.clear()
                    viewModel.redo()
                },
                onResetZoom = {
                    zoom = baseFitZoom
                    panX = if (screenWidthPx > PAGE_WIDTH * baseFitZoom) (screenWidthPx - PAGE_WIDTH * baseFitZoom) / 2f else 0f
                    panY = 0f
                },
                onToggleFingerDrawing = {
                    fingerDrawingEnabled = !fingerDrawingEnabled
                },
                onOpenThumbnailOverview = {
                    showThumbnailOverviewSheet = true
                },
                onAddPageClick = {
                    val nbId = notebookId ?: pages.firstOrNull()?.notebookId
                    nbId?.let { id ->
                        optimisticStrokes.clear()
                        strokeCache.clear()
                        viewModel.addNewPage(
                            notebookId = id,
                            template = activePaperTemplate.name,
                            isDarkPaper = activeIsDarkPaper
                        )
                    }
                },
                onExportPdf = { exportAll ->
                    isExportingPdf = true
                    viewModel.exportToPdf(
                        context = context,
                        targetPageId = if (exportAll) null else currentPage?.id
                    ) { success, errorMsg ->
                        isExportingPdf = false
                        if (!success && errorMsg != null) {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("PDF Export Error: $errorMsg")
                            }
                        }
                    }
                },
                onTemplateSelected = { template ->
                    currentPage?.let { curr ->
                        viewModel.updatePaper(
                            pageId = curr.id,
                            paperTemplate = template.name,
                            isDarkPaper = curr.isDarkPaper
                        )
                    }
                },
                onDarkPaperToggled = {
                    currentPage?.let { curr ->
                        viewModel.updatePaper(
                            pageId = curr.id,
                            paperTemplate = curr.paperTemplate,
                            isDarkPaper = !curr.isDarkPaper
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val availableWidth = constraints.maxWidth.toFloat()
            val availableHeight = constraints.maxHeight.toFloat()

            // 100% zoom is consistently based on portrait screen width so landscape does not blow up the page
            LaunchedEffect(availableWidth, availableHeight) {
                if (availableWidth > 100f && availableHeight > 100f) {
                    val portraitWidth = min(availableWidth, availableHeight)
                    val fitZoom = portraitWidth / PAGE_WIDTH
                    baseFitZoom = fitZoom
                    if (!isInitializedFit) {
                        zoom = fitZoom
                        panX = if (availableWidth > PAGE_WIDTH * fitZoom) (availableWidth - PAGE_WIDTH * fitZoom) / 2f else 0f
                        panY = 0f
                        isInitializedFit = true
                    }
                }
            }

            Column(modifier = Modifier.fillMaxSize()) {
                // Segmented Toolbar with Clean Circle Dot Presets
                FreeNotesToolbar(
                    selectedTool = selectedTool,
                    eraserMode = eraserMode,
                    quickColors = quickColors,
                    activeColorSlotIndex = activeColorSlotIndex,
                    currentWidth = activeStrokeWidth,
                    onToolSelected = {
                        if (it == EditorTool.LASER && selectedTool == EditorTool.LASER) {
                            showLaserSettingsDialog = true
                        } else {
                            selectedTool = it
                            if (it != EditorTool.LASSO) {
                                onLassoDeselectAction?.invoke()
                            }
                            if (it != EditorTool.PEN && it != EditorTool.HIGHLIGHTER) {
                                optimisticStrokes.clear()
                            }
                        }
                    },
                    onEraserModeSelected = { eraserMode = it },
                    onSelectColorSlot = { activeColorSlotIndex = it },
                    onOpenColorPicker = { showColorPickerDialog = true },
                    onWidthSelected = { newWidth ->
                        when (selectedTool) {
                            EditorTool.PEN -> penWidth = newWidth
                            EditorTool.HIGHLIGHTER -> highlighterWidth = newWidth
                            EditorTool.ERASER -> eraserWidth = newWidth
                            EditorTool.LASER -> laserWidth = newWidth
                            EditorTool.LASSO -> Unit
                        }
                    },
                    onOpenSizeCustomizer = { showSizeCustomizerDialog = true }
                )

                // Multi-Page Continuous Workspace Desk
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clipToBounds()
                        .background(if (activeIsDarkPaper) Color(0xFF0F1115) else Color(0xFFE5EAF2))
                ) {
                    // Continuous multi-page canvas (rendered at full native screen resolution)
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val totalDocHeight = pages.size * PAGE_HEIGHT
                        withTransform({
                            translate(left = panX, top = panY)
                            scale(scaleX = zoom, scaleY = zoom, pivot = Offset.Zero)
                            clipRect(left = 0f, top = 0f, right = PAGE_WIDTH, bottom = totalDocHeight)
                        }) {
                            val hiddenIds = if (lassoDraggingActive) lassoSelectedStrokeIds else emptySet()

                            // Pass 1: Render all continuous paper sheets, templates & divider lines
                            pages.forEachIndexed { index, pageEntity ->
                                val topY = pageTopY(index)
                                withTransform({
                                    translate(left = 0f, top = topY)
                                }) {
                                    drawContinuousPaperPage(
                                        pageIndex = index + 1,
                                        template = PaperTemplate.entries.firstOrNull { it.name == pageEntity.paperTemplate } ?: PaperTemplate.RULED,
                                        isDarkPaper = pageEntity.isDarkPaper
                                    )
                                }
                            }

                            // Pass 2: Render all strokes on top of all paper sheets
                            pages.forEachIndexed { index, pageEntity ->
                                val topY = pageTopY(index)
                                withTransform({
                                    translate(left = 0f, top = topY)
                                }) {
                                    drawIntoCanvas { composeCanvas ->
                                        val nativeCanvas = composeCanvas.nativeCanvas
                                        val strokesForThisPage = pageStrokes[pageEntity.id] ?: emptyList()

                                        val persistedKeys = strokesForThisPage.map {
                                            "${it.pageId}_${it.pointData.contentHashCode()}_${it.color}"
                                        }.toSet()

                                        // Highlighters First (persisted from Room)
                                        for (strokeEntity in strokesForThisPage) {
                                            if (strokeEntity.id in hiddenIds) continue
                                            if (strokeEntity.tool == StrokeTool.HIGHLIGHTER.name) {
                                                val inkStroke = strokeCache.getOrPut(strokeEntity.id) {
                                                    val cacheKey = "${strokeEntity.pageId}_${strokeEntity.pointData.contentHashCode()}_${strokeEntity.color}"
                                                    recentFinishedStrokes[cacheKey] ?: buildInkStroke(strokeEntity, penBrushFamily, highlighterBrushFamily)
                                                }
                                                if (inkStroke != null) {
                                                    canvasStrokeRenderer.draw(nativeCanvas, inkStroke, strokeToScreenMatrix)
                                                } else {
                                                    drawStoredStrokeFallback(strokeEntity)
                                                }
                                            }
                                        }

                                        // Optimistic Highlighters (in-flight before Room emits, ignore duplicates)
                                        for (strokeEntity in optimisticStrokes) {
                                            if (strokeEntity.id in hiddenIds) continue
                                            if (strokeEntity.pageId == pageEntity.id && strokeEntity.tool == StrokeTool.HIGHLIGHTER.name) {
                                                val cacheKey = "${strokeEntity.pageId}_${strokeEntity.pointData.contentHashCode()}_${strokeEntity.color}"
                                                if (cacheKey in persistedKeys) continue // Prevent double-rendering / blinking!
                                                val inkStroke = strokeCache[strokeEntity.id] ?: recentFinishedStrokes[cacheKey] ?: buildInkStroke(strokeEntity, penBrushFamily, highlighterBrushFamily)
                                                if (inkStroke != null) {
                                                    canvasStrokeRenderer.draw(nativeCanvas, inkStroke, strokeToScreenMatrix)
                                                } else {
                                                    drawStoredStrokeFallback(strokeEntity)
                                                }
                                            }
                                        }

                                        // Pen Strokes on Top (persisted from Room)
                                        for (strokeEntity in strokesForThisPage) {
                                            if (strokeEntity.id in hiddenIds) continue
                                            if (strokeEntity.tool != StrokeTool.HIGHLIGHTER.name) {
                                                val inkStroke = strokeCache.getOrPut(strokeEntity.id) {
                                                    val cacheKey = "${strokeEntity.pageId}_${strokeEntity.pointData.contentHashCode()}_${strokeEntity.color}"
                                                    recentFinishedStrokes[cacheKey] ?: buildInkStroke(strokeEntity, penBrushFamily, highlighterBrushFamily)
                                                }
                                                if (inkStroke != null) {
                                                    canvasStrokeRenderer.draw(nativeCanvas, inkStroke, strokeToScreenMatrix)
                                                } else {
                                                    drawStoredStrokeFallback(strokeEntity)
                                                }
                                            }
                                        }

                                        // Optimistic Pen Strokes (in-flight before Room emits, ignore duplicates)
                                        for (strokeEntity in optimisticStrokes) {
                                            if (strokeEntity.id in hiddenIds) continue
                                            if (strokeEntity.pageId == pageEntity.id && strokeEntity.tool != StrokeTool.HIGHLIGHTER.name) {
                                                val cacheKey = "${strokeEntity.pageId}_${strokeEntity.pointData.contentHashCode()}_${strokeEntity.color}"
                                                if (cacheKey in persistedKeys) continue // Prevent double-rendering / blinking!
                                                val inkStroke = strokeCache[strokeEntity.id] ?: recentFinishedStrokes[cacheKey] ?: buildInkStroke(strokeEntity, penBrushFamily, highlighterBrushFamily)
                                                if (inkStroke != null) {
                                                    canvasStrokeRenderer.draw(nativeCanvas, inkStroke, strokeToScreenMatrix)
                                                } else {
                                                    drawStoredStrokeFallback(strokeEntity)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Add Page card at the end of document
                            if (pages.isNotEmpty()) {
                                val bottomY = pageTopY(pages.size)
                                drawAddPagePlaceholder(bottomY)
                            }
                        }
                    }

                    // Interactive Ink Surface with Front-Buffered Strokes & Freehand Lasso
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            ContinuousInkView(
                                context = ctx,
                                brush = brush,
                                strokeColor = effectiveColor,
                                strokeWidth = activeStrokeWidth,
                                initialTool = selectedTool,
                                eraserMode = eraserMode,
                                eraserRadius = eraserWidth,
                                laserDurationMs = (laserDurationSec * 1000).toLong(),
                                laserHasTail = laserHasTail,
                                pagesProvider = { viewModel.pages.value },
                                activePageIdProvider = { currentPage?.id ?: viewModel.currentPage.value?.id },
                                pageStrokesProvider = { viewModel.pageStrokes.value },
                                viewModel = viewModel,
                                zoom = zoom,
                                panX = panX,
                                panY = panY,
                                fingerDrawingEnabled = fingerDrawingEnabled,
                                onViewportTransform = onViewportTransform,
                                onOptimisticStroke = { optimisticStroke, prebuiltInkStroke ->
                                    val cacheKey = "${optimisticStroke.pageId}_${optimisticStroke.pointData.contentHashCode()}_${optimisticStroke.color}"
                                    if (prebuiltInkStroke != null) {
                                        strokeCache[optimisticStroke.id] = prebuiltInkStroke
                                        recentFinishedStrokes[cacheKey] = prebuiltInkStroke
                                    }
                                    optimisticStrokes.add(optimisticStroke)
                                },
                                onLassoSelectionChanged = { count, selectedIds, isDragging, duplicateFn, recolorFn, deleteFn, deselectFn ->
                                    selectedLassoCount = count
                                    lassoSelectedStrokeIds = selectedIds
                                    lassoDraggingActive = isDragging
                                    onLassoDuplicateAction = duplicateFn
                                    onLassoRecolorAction = recolorFn
                                    onLassoDeleteAction = deleteFn
                                    onLassoDeselectAction = deselectFn
                                },
                                onInvalidateCache = { ids ->
                                    if (ids == null) {
                                        strokeCache.clear()
                                        recentFinishedStrokes.clear()
                                        optimisticStrokes.clear()
                                    } else {
                                        ids.forEach { strokeCache.remove(it) }
                                        optimisticStrokes.removeAll { it.id in ids }
                                    }
                                },
                                onAddPageAtBottom = {
                                    val nbId = notebookId ?: pages.firstOrNull()?.notebookId
                                    nbId?.let { id ->
                                        optimisticStrokes.clear()
                                        strokeCache.clear()
                                        viewModel.addNewPage(
                                            notebookId = id,
                                            template = activePaperTemplate.name,
                                            isDarkPaper = activeIsDarkPaper
                                        )
                                    }
                                }
                            ).also { inkViewRef = it }
                        },
                        update = { view ->
                            inkViewRef = view
                            view.updateConfiguration(
                                brush = brush,
                                tool = selectedTool,
                                eraserMode = eraserMode,
                                strokeColor = effectiveColor,
                                strokeWidth = activeStrokeWidth,
                                eraserRadius = eraserWidth,
                                laserDurationMs = (laserDurationSec * 1000).toLong(),
                                laserHasTail = laserHasTail,
                                zoom = zoom,
                                panX = panX,
                                panY = panY,
                                fingerDrawingEnabled = fingerDrawingEnabled
                            )
                        }
                    )

                    // Lasso Contextual Floating Action Bar
                    if (selectedTool == EditorTool.LASSO && selectedLassoCount > 0) {
                        LassoContextActionBar(
                            count = selectedLassoCount,
                            activeColor = selectedColor,
                            onDuplicate = { onLassoDuplicateAction?.invoke() },
                            onRecolor = { onLassoRecolorAction?.invoke(selectedColor) },
                            onDelete = { onLassoDeleteAction?.invoke() },
                            onDeselect = { onLassoDeselectAction?.invoke() },
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 12.dp)
                        )
                    }

                    // Loading overlay for PDF export
                    if (isExportingPdf) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    shadowElevation = 8.dp,
                                    modifier = Modifier.padding(24.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(24.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                                        Column {
                                            Text("Generating PDF Document...", fontWeight = FontWeight.Bold)
                                            Text("Rendering high-quality notebook pages", style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Page Thumbnail Overview Bottom Sheet
    if (showThumbnailOverviewSheet) {
        ModalBottomSheet(
            onDismissRequest = { showThumbnailOverviewSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Pages Overview (${pages.size})",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Button(
                        onClick = {
                            val nbId = notebookId ?: pages.firstOrNull()?.notebookId
                            nbId?.let { id ->
                                optimisticStrokes.clear()
                                strokeCache.clear()
                                viewModel.addNewPage(
                                    notebookId = id,
                                    template = activePaperTemplate.name,
                                    isDarkPaper = activeIsDarkPaper
                                )
                            }
                            showThumbnailOverviewSheet = false
                        },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("➕ Add Page")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 130.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    itemsIndexed(pages, key = { _, p -> p.id }) { index, p ->
                        val isCurrent = index == currentVisiblePageIndex
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    panY = - (pageTopY(index) * zoom) + 24f
                                    viewModel.setActivePage(p.id)
                                    showThumbnailOverviewSheet = false
                                }
                        ) {
                            Card(
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (p.isDarkPaper) Color(0xFF1E2125) else Color(0xFFFCFCF9)
                                ),
                                border = androidx.compose.foundation.BorderStroke(
                                    width = if (isCurrent) 2.5.dp else 1.dp,
                                    color = if (isCurrent) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(0.72f)
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (p.isDarkPaper) Color.White else Color.Black
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Page ${index + 1}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    // Instant Color Selection Dialog (One-tap select & live slider updates)
    if (showColorPickerDialog) {
        FreeNotesInstantColorPickerDialog(
            initialColor = selectedColor,
            onDismiss = { showColorPickerDialog = false },
            onColorSelected = { newColor ->
                quickColors[activeColorSlotIndex] = newColor
            }
        )
    }

    // Instant Size Customizer Dialog with Clean Circle Dot Preview
    if (showSizeCustomizerDialog) {
        FreeNotesInstantSizeDialog(
            tool = selectedTool,
            currentSize = activeStrokeWidth,
            onDismiss = { showSizeCustomizerDialog = false },
            onSizeSelected = { newSize ->
                when (selectedTool) {
                    EditorTool.PEN -> penWidth = newSize
                    EditorTool.HIGHLIGHTER -> highlighterWidth = newSize
                    EditorTool.ERASER -> eraserWidth = newSize
                    EditorTool.LASER -> laserWidth = newSize
                    EditorTool.LASSO -> Unit
                }
            }
        )
    }

    // Laser Pen Settings Popup (Matching User Reference Image)
    if (showLaserSettingsDialog) {
        LaserPenSettingsDialog(
            currentColor = selectedColor,
            hasTail = laserHasTail,
            thickness = laserWidth,
            duration = laserDurationSec,
            onDismiss = { showLaserSettingsDialog = false },
            onTailToggle = { laserHasTail = it },
            onThicknessChange = { laserWidth = it },
            onDurationChange = { laserDurationSec = it },
            onColorSelect = { newColor ->
                quickColors[activeColorSlotIndex] = newColor
            }
        )
    }

    // Add Page Dialog
    if (showAddPageDialog) {
        AddPageDialog(
            defaultTemplate = activePaperTemplate,
            isDarkPaper = activeIsDarkPaper,
            onDismiss = { showAddPageDialog = false },
            onAddPage = { template, isDark ->
                val nbId = notebookId ?: pages.firstOrNull()?.notebookId
                nbId?.let { id ->
                    optimisticStrokes.clear()
                    strokeCache.clear()
                    viewModel.addNewPage(
                        notebookId = id,
                        template = template.name,
                        isDarkPaper = isDark
                    )
                }
                showAddPageDialog = false
            }
        )
    }
}

/**
 * Top Navigation Header
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FreeNotesTopBar(
    title: String,
    currentPageIndex: Int,
    totalPages: Int,
    zoomPercentage: Int,
    paperTemplate: PaperTemplate,
    isDarkPaper: Boolean,
    fingerDrawingEnabled: Boolean,
    onBack: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onResetZoom: () -> Unit,
    onToggleFingerDrawing: () -> Unit,
    onOpenThumbnailOverview: () -> Unit,
    onAddPageClick: () -> Unit,
    onExportPdf: (exportAll: Boolean) -> Unit,
    onTemplateSelected: (PaperTemplate) -> Unit,
    onDarkPaperToggled: () -> Unit
) {
    var templateMenuExpanded by remember { mutableStateOf(false) }
    var pdfMenuExpanded by remember { mutableStateOf(false) }

    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface
        ),
        title = {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Page $currentPageIndex of $totalPages",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Text("←", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        },
        actions = {
            // Page Thumbnail Grid Sheet Button (⊞)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                modifier = Modifier
                    .clickable(onClick = onOpenThumbnailOverview)
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("⊞", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = "$currentPageIndex / $totalPages",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Add Page Button
            IconButton(onClick = onAddPageClick, modifier = Modifier.size(36.dp)) {
                Text("➕", fontSize = 14.sp)
            }

            // Undo & Redo
            IconButton(onClick = onUndo, modifier = Modifier.size(36.dp)) {
                Text("↶", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            IconButton(onClick = onRedo, modifier = Modifier.size(36.dp)) {
                Text("↷", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }

            // PDF Export Menu
            Box {
                IconButton(onClick = { pdfMenuExpanded = true }, modifier = Modifier.size(36.dp)) {
                    Text("📄", fontSize = 16.sp)
                }

                DropdownMenu(
                    expanded = pdfMenuExpanded,
                    onDismissRequest = { pdfMenuExpanded = false }
                ) {
                    Text(
                        text = "Export Options",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    DropdownMenuItem(
                        text = { Text("📑 Export Current View as PDF") },
                        onClick = {
                            pdfMenuExpanded = false
                            onExportPdf(false)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("📚 Export Entire Notebook as PDF") },
                        onClick = {
                            pdfMenuExpanded = false
                            onExportPdf(true)
                        }
                    )
                }
            }

            // 1-Tap Zoom Reset Badge (fits to width automatically)
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.clickable(onClick = onResetZoom)
            ) {
                Text(
                    text = "$zoomPercentage%",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }

            // Stylus / Scroll Mode Toggle
            IconButton(onClick = onToggleFingerDrawing, modifier = Modifier.size(36.dp)) {
                Text(
                    text = if (fingerDrawingEnabled) "✍️" else "👆",
                    fontSize = 16.sp
                )
            }

            // Paper Template Menu
            Box {
                IconButton(onClick = { templateMenuExpanded = true }, modifier = Modifier.size(36.dp)) {
                    Text("⚙️", fontSize = 16.sp)
                }

                DropdownMenu(
                    expanded = templateMenuExpanded,
                    onDismissRequest = { templateMenuExpanded = false }
                ) {
                    Text(
                        text = "Paper Template",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    PaperTemplate.entries.forEach { template ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = if (template == paperTemplate) "✓ ${template.label}" else "    ${template.label}"
                                )
                            },
                            onClick = {
                                onTemplateSelected(template)
                                templateMenuExpanded = false
                            }
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    DropdownMenuItem(
                        text = {
                            Text(if (isDarkPaper) "☀️ Light Paper" else "🌙 Dark Paper")
                        },
                        onClick = {
                            onDarkPaperToggled()
                            templateMenuExpanded = false
                        }
                    )
                }
            }
        }
    )
}

/**
 * Floating Context Action Bar for Freehand Lasso Selection
 */
@Composable
private fun LassoContextActionBar(
    count: Int,
    activeColor: Long,
    onDuplicate: () -> Unit,
    onRecolor: () -> Unit,
    onDelete: () -> Unit,
    onDeselect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = 6.dp,
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                text = "➰ $count selected",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(horizontal = 6.dp)
            )

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.padding(horizontal = 2.dp)
            ) {
                Text(
                    text = "✋ Drag to move",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            // Duplicate
            IconButton(onClick = onDuplicate, modifier = Modifier.size(32.dp)) {
                Text("📋", fontSize = 14.sp)
            }

            // Recolor
            IconButton(onClick = onRecolor, modifier = Modifier.size(32.dp)) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(Color(activeColor.toInt()))
                        .border(1.dp, Color.White, CircleShape)
                )
            }

            // Delete
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Text("🗑️", fontSize = 14.sp)
            }

            // Deselect
            IconButton(onClick = onDeselect, modifier = Modifier.size(32.dp)) {
                Text("✕", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * FreeNotes Segmented Floating Toolbar with Clean Circle Dot Presets for all tools
 */
@Composable
private fun FreeNotesToolbar(
    selectedTool: EditorTool,
    eraserMode: EraserMode,
    quickColors: List<Long>,
    activeColorSlotIndex: Int,
    currentWidth: Float,
    onToolSelected: (EditorTool) -> Unit,
    onEraserModeSelected: (EraserMode) -> Unit,
    onSelectColorSlot: (Int) -> Unit,
    onOpenColorPicker: () -> Unit,
    onWidthSelected: (Float) -> Unit,
    onOpenSizeCustomizer: () -> Unit
) {
    var showEraserMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(1f),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Tool Segment Pills: Pen, Highlighter, Eraser, Lasso, Laser
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.padding(vertical = 2.dp)
            ) {
                Row(
                    modifier = Modifier.padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    EditorTool.entries.forEach { tool ->
                        val isSelected = selectedTool == tool
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            modifier = Modifier.clickable {
                                if (tool == EditorTool.ERASER && isSelected) {
                                    showEraserMenu = true
                                } else {
                                    onToolSelected(tool)
                                }
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    if (tool == EditorTool.ERASER) eraserMode.icon else tool.icon,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = if (tool == EditorTool.ERASER) eraserMode.label else tool.label,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Eraser Mode Selector Dropdown when Eraser is Active
            if (selectedTool == EditorTool.ERASER) {
                Box {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        modifier = Modifier.clickable { showEraserMenu = true }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "Mode: ${eraserMode.label} ▾",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showEraserMenu,
                        onDismissRequest = { showEraserMenu = false }
                    ) {
                        EraserMode.entries.forEach { mode ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            text = "${mode.icon} ${mode.label} ${if (mode == eraserMode) "✓" else ""}",
                                            fontWeight = if (mode == eraserMode) FontWeight.Bold else FontWeight.Normal
                                        )
                                        Text(
                                            text = mode.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                onClick = {
                                    onEraserModeSelected(mode)
                                    showEraserMenu = false
                                }
                            )
                        }
                    }
                }
            }

            // Quick Color Slots (Shown for Pen, Highlighter, Lasso, Laser)
            if (selectedTool == EditorTool.PEN || selectedTool == EditorTool.HIGHLIGHTER || selectedTool == EditorTool.LASSO || selectedTool == EditorTool.LASER) {
                ToolbarDivider()

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    quickColors.forEachIndexed { index, colorValue ->
                        val isSelected = activeColorSlotIndex == index
                        val color = Color(colorValue.toInt())

                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .then(
                                    if (isSelected) {
                                        Modifier.border(2.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                    } else {
                                        Modifier.border(1.dp, Color.LightGray.copy(alpha = 0.5f), CircleShape)
                                    }
                                )
                                .padding(3.dp)
                                .clip(CircleShape)
                                .background(color)
                                .clickable {
                                    if (isSelected) {
                                        onOpenColorPicker()
                                    } else {
                                        onSelectColorSlot(index)
                                    }
                                }
                        )
                    }

                    IconButton(
                        onClick = onOpenColorPicker,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Text("🎨", fontSize = 15.sp)
                    }
                }
            }

            // Quick Thickness Selector (Consistent Circle Dots for all tools)
            if (selectedTool != EditorTool.LASSO) {
                ToolbarDivider()

                val presets = when (selectedTool) {
                    EditorTool.PEN -> listOf(1.5f, 2.2f, 3.2f, 4.8f, 7.0f)
                    EditorTool.HIGHLIGHTER -> listOf(12.0f, 20.0f, 32.0f, 48.0f, 64.0f)
                    EditorTool.ERASER -> listOf(20.0f, 45.0f, 80.0f, 140.0f, 220.0f)
                    EditorTool.LASER -> listOf(2.0f, 3.5f, 5.5f, 8.5f, 12.0f)
                    EditorTool.LASSO -> emptyList()
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presets.forEach { width ->
                        val isSelected = (currentWidth - width).let { it in -0.6f..0.6f }
                        val dotScale = when (width) {
                            presets[0] -> 3.dp
                            presets[1] -> 5.dp
                            presets[2] -> 7.dp
                            presets[3] -> 10.dp
                            else -> 13.dp
                        }

                        Surface(
                            shape = CircleShape,
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .size(30.dp)
                                .clickable { onWidthSelected(width) }
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(dotScale)
                                        .clip(CircleShape)
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = onOpenSizeCustomizer,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Text("⚙️", fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolbarDivider() {
    Box(
        modifier = Modifier
            .height(22.dp)
            .width(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    )
}

/**
 * Continuous Paper Page Renderer (In local page space)
 */
private fun DrawScope.drawContinuousPaperPage(
    pageIndex: Int,
    template: PaperTemplate,
    isDarkPaper: Boolean
) {
    val paperColor = if (isDarkPaper) Color(0xFF1C1F25) else Color(0xFFFCFCFA)
    val lineColor = if (isDarkPaper) Color(0xFF2E3540) else Color(0xFFE2E7F0)
    val marginColor = if (isDarkPaper) Color(0xFF6B3C44) else Color(0xFFFFB5BC)
    val pageBorderColor = if (isDarkPaper) Color(0xFF475569) else Color(0xFF94A3B8)

    // Paper Surface
    drawRect(
        color = paperColor,
        topLeft = Offset(0f, 0f),
        size = Size(PAGE_WIDTH, PAGE_HEIGHT)
    )

    // Paper Template Lines
    when (template) {
        PaperTemplate.BLANK -> Unit
        PaperTemplate.RULED -> {
            var y = 60f
            while (y < PAGE_HEIGHT - 20f) {
                drawLine(lineColor, start = Offset(0f, y), end = Offset(PAGE_WIDTH, y), strokeWidth = 1f)
                y += 36f
            }
            drawLine(marginColor, start = Offset(70f, 0f), end = Offset(70f, PAGE_HEIGHT), strokeWidth = 1.5f)
        }
        PaperTemplate.COLLEGE_RULED -> {
            var y = 56f
            while (y < PAGE_HEIGHT - 20f) {
                drawLine(lineColor, start = Offset(0f, y), end = Offset(PAGE_WIDTH, y), strokeWidth = 1f)
                y += 26f
            }
            drawLine(marginColor, start = Offset(70f, 0f), end = Offset(70f, PAGE_HEIGHT), strokeWidth = 1.5f)
        }
        PaperTemplate.GRID -> {
            var x = 0f
            while (x < PAGE_WIDTH) {
                drawLine(lineColor, start = Offset(x, 0f), end = Offset(x, PAGE_HEIGHT), strokeWidth = 1f)
                x += 30f
            }
            var y = 0f
            while (y < PAGE_HEIGHT) {
                drawLine(lineColor, start = Offset(0f, y), end = Offset(PAGE_WIDTH, y), strokeWidth = 1f)
                y += 30f
            }
        }
        PaperTemplate.CORNELL_RULED -> {
            // Header block (Title & Date)
            drawLine(marginColor, start = Offset(0f, 80f), end = Offset(PAGE_WIDTH, 80f), strokeWidth = 1.5f)
            // Summary block (Bottom area)
            val summaryTopY = PAGE_HEIGHT - 180f
            drawLine(marginColor, start = Offset(0f, summaryTopY), end = Offset(PAGE_WIDTH, summaryTopY), strokeWidth = 1.5f)
            // Cue column dividing line
            val cueX = 220f
            drawLine(marginColor, start = Offset(cueX, 80f), end = Offset(cueX, summaryTopY), strokeWidth = 1.5f)

            // Main notes area rulings
            var y = 112f
            while (y < summaryTopY) {
                drawLine(lineColor, start = Offset(cueX, y), end = Offset(PAGE_WIDTH, y), strokeWidth = 1f)
                y += 32f
            }
            // Summary area rulings
            var sy = summaryTopY + 36f
            while (sy < PAGE_HEIGHT - 20f) {
                drawLine(lineColor, start = Offset(0f, sy), end = Offset(PAGE_WIDTH, sy), strokeWidth = 1f)
                sy += 32f
            }
        }
        PaperTemplate.CORNELL_GRID -> {
            // Header block
            drawLine(marginColor, start = Offset(0f, 80f), end = Offset(PAGE_WIDTH, 80f), strokeWidth = 1.5f)
            // Summary block
            val summaryTopY = PAGE_HEIGHT - 180f
            drawLine(marginColor, start = Offset(0f, summaryTopY), end = Offset(PAGE_WIDTH, summaryTopY), strokeWidth = 1.5f)
            // Cue column dividing line
            val cueX = 220f
            drawLine(marginColor, start = Offset(cueX, 80f), end = Offset(cueX, summaryTopY), strokeWidth = 1.5f)

            // Main notes area grid
            var x = cueX + 28f
            while (x < PAGE_WIDTH) {
                drawLine(lineColor, start = Offset(x, 80f), end = Offset(x, summaryTopY), strokeWidth = 1f)
                x += 28f
            }
            var y = 80f + 28f
            while (y < summaryTopY) {
                drawLine(lineColor, start = Offset(cueX, y), end = Offset(PAGE_WIDTH, y), strokeWidth = 1f)
                y += 28f
            }
            // Summary area rulings
            var sy = summaryTopY + 36f
            while (sy < PAGE_HEIGHT - 20f) {
                drawLine(lineColor, start = Offset(0f, sy), end = Offset(PAGE_WIDTH, sy), strokeWidth = 1f)
                sy += 32f
            }
        }
        PaperTemplate.SINGLE_COLUMN -> {
            val leftMargin = 70f
            val rightMargin = PAGE_WIDTH - 70f
            drawLine(marginColor, start = Offset(leftMargin, 0f), end = Offset(leftMargin, PAGE_HEIGHT), strokeWidth = 1.5f)
            drawLine(marginColor, start = Offset(rightMargin, 0f), end = Offset(rightMargin, PAGE_HEIGHT), strokeWidth = 1.5f)

            var y = 60f
            while (y < PAGE_HEIGHT - 20f) {
                drawLine(lineColor, start = Offset(leftMargin, y), end = Offset(rightMargin, y), strokeWidth = 1f)
                y += 34f
            }
        }
        PaperTemplate.TWO_COLUMN -> {
            val midX = PAGE_WIDTH / 2f
            val topY = 60f
            drawLine(marginColor, start = Offset(0f, topY), end = Offset(PAGE_WIDTH, topY), strokeWidth = 1.5f)
            drawLine(marginColor, start = Offset(midX, topY), end = Offset(midX, PAGE_HEIGHT), strokeWidth = 1.5f)

            var y = topY + 34f
            while (y < PAGE_HEIGHT - 20f) {
                drawLine(lineColor, start = Offset(0f, y), end = Offset(PAGE_WIDTH, y), strokeWidth = 1f)
                y += 34f
            }
        }
        PaperTemplate.TWO_COLUMN_LEFT -> {
            val cueX = 180f
            val topY = 60f
            drawLine(marginColor, start = Offset(0f, topY), end = Offset(PAGE_WIDTH, topY), strokeWidth = 1.5f)
            drawLine(marginColor, start = Offset(cueX, topY), end = Offset(cueX, PAGE_HEIGHT), strokeWidth = 1.5f)

            var y = topY + 34f
            while (y < PAGE_HEIGHT - 20f) {
                drawLine(lineColor, start = Offset(0f, y), end = Offset(PAGE_WIDTH, y), strokeWidth = 1f)
                y += 34f
            }
        }
        PaperTemplate.THREE_COLUMN -> {
            val col1 = PAGE_WIDTH / 3f
            val col2 = (PAGE_WIDTH / 3f) * 2f
            val topY = 60f
            drawLine(marginColor, start = Offset(0f, topY), end = Offset(PAGE_WIDTH, topY), strokeWidth = 1.5f)
            drawLine(marginColor, start = Offset(col1, topY), end = Offset(col1, PAGE_HEIGHT), strokeWidth = 1.5f)
            drawLine(marginColor, start = Offset(col2, topY), end = Offset(col2, PAGE_HEIGHT), strokeWidth = 1.5f)

            var y = topY + 34f
            while (y < PAGE_HEIGHT - 20f) {
                drawLine(lineColor, start = Offset(0f, y), end = Offset(PAGE_WIDTH, y), strokeWidth = 1f)
                y += 34f
            }
        }
        PaperTemplate.DIARY -> {
            val topBoxBottom = 90f
            // Date / header banner
            drawLine(marginColor, start = Offset(0f, topBoxBottom), end = Offset(PAGE_WIDTH, topBoxBottom), strokeWidth = 1.5f)
            drawLine(marginColor, start = Offset(70f, 0f), end = Offset(70f, PAGE_HEIGHT), strokeWidth = 1.5f)
            drawLine(marginColor.copy(alpha = 0.5f), start = Offset(76f, 0f), end = Offset(76f, PAGE_HEIGHT), strokeWidth = 1.0f)

            // Diary ruled lines
            var y = topBoxBottom + 36f
            val reflectionBoxTop = PAGE_HEIGHT - 130f
            while (y < reflectionBoxTop) {
                drawLine(lineColor, start = Offset(76f, y), end = Offset(PAGE_WIDTH, y), strokeWidth = 1f)
                y += 34f
            }

            // Bottom reflection banner
            drawLine(marginColor, start = Offset(0f, reflectionBoxTop), end = Offset(PAGE_WIDTH, reflectionBoxTop), strokeWidth = 1.5f)
            var ry = reflectionBoxTop + 34f
            while (ry < PAGE_HEIGHT - 20f) {
                drawLine(lineColor, start = Offset(0f, ry), end = Offset(PAGE_WIDTH, ry), strokeWidth = 1f)
                ry += 34f
            }
        }
    }

    // Left and Right Document Borders
    drawLine(pageBorderColor, start = Offset(0f, 0f), end = Offset(0f, PAGE_HEIGHT), strokeWidth = 1.5f)
    drawLine(pageBorderColor, start = Offset(PAGE_WIDTH, 0f), end = Offset(PAGE_WIDTH, PAGE_HEIGHT), strokeWidth = 1.5f)

    // Top border on first page
    if (pageIndex == 1) {
        drawLine(pageBorderColor, start = Offset(0f, 0f), end = Offset(PAGE_WIDTH, 0f), strokeWidth = 1.5f)
    }

    // Distinct Page Separation Line at bottom of page
    drawLine(
        color = pageBorderColor,
        start = Offset(0f, PAGE_HEIGHT),
        end = Offset(PAGE_WIDTH, PAGE_HEIGHT),
        strokeWidth = 1.5f
    )
}

/**
 * Add Page Card Placeholder at Bottom of Document
 */
private fun DrawScope.drawAddPagePlaceholder(bottomY: Float) {
    val boxColor = Color(0xFF1E212B)
    val borderColor = Color(0xFF333A48)

    drawRect(
        color = boxColor,
        topLeft = Offset(0f, bottomY),
        size = Size(PAGE_WIDTH, 140f)
    )

    drawRect(
        color = borderColor,
        topLeft = Offset(0f, bottomY),
        size = Size(PAGE_WIDTH, 140f),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f)
    )
}

private fun DrawScope.drawStoredStrokeFallback(stroke: StrokeEntity) {
    val points = StrokePointCodec.decode(stroke.pointData)
    if (points.isEmpty()) return

    val color = Color(stroke.color.toInt())

    if (points.size == 1) {
        drawCircle(
            color = color,
            radius = stroke.strokeWidth / 2f,
            center = Offset(points.first().x, points.first().y)
        )
        return
    }

    points.zipWithNext().forEach { (start, end) ->
        drawLine(
            color = color,
            start = Offset(start.x, start.y),
            end = Offset(end.x, end.y),
            strokeWidth = stroke.strokeWidth,
            cap = StrokeCap.Round
        )
    }
}

private fun buildInkStroke(
    stroke: StrokeEntity,
    penBrushFamily: BrushFamily,
    highlighterBrushFamily: BrushFamily
): InkStroke? {
    val points = StrokePointCodec.decode(stroke.pointData)
    if (points.isEmpty()) return null

    val isHighlighter = stroke.tool == StrokeTool.HIGHLIGHTER.name
    val brush = Brush.createWithColorIntArgb(
        family = if (isHighlighter) highlighterBrushFamily else penBrushFamily,
        colorIntArgb = stroke.color.toInt(),
        size = stroke.strokeWidth,
        epsilon = 0.01f
    )

    val inputBatch = MutableStrokeInputBatch()
    var lastTime = -1L
    for (pt in points) {
        val pressure = if (pt.pressure in 0.05f..1.0f) pt.pressure else StrokeInput.NO_PRESSURE
        var time = pt.elapsedMillis.toLong().coerceAtLeast(0L)
        if (time <= lastTime) {
            time = lastTime + 1L
        }
        lastTime = time
        try {
            inputBatch.add(
                type = InputToolType.STYLUS,
                x = pt.x,
                y = pt.y,
                elapsedTimeMillis = time,
                strokeUnitLengthCm = StrokeInput.NO_STROKE_UNIT_LENGTH,
                pressure = pressure,
                tiltRadians = StrokeInput.NO_TILT,
                orientationRadians = StrokeInput.NO_ORIENTATION
            )
        } catch (_: Exception) {
        }
    }

    if (inputBatch.isEmpty()) return null
    return runCatching { InkStroke(brush, inputBatch) }.getOrNull()
}

private data class LaserPoint(
    val screenX: Float,
    val screenY: Float,
    val timestamp: Long
)

/**
 * Continuous multi-page interactive drawing surface with front-buffer inking,
 * auto-page routing, zero-blink dry layer handoff, and smooth gesture panning/scrolling.
 */
private class ContinuousInkView(
    context: Context,
    private var brush: Brush,
    private var strokeColor: Long,
    private var strokeWidth: Float,
    initialTool: EditorTool,
    private var eraserMode: EraserMode,
    private var eraserRadius: Float,
    private var laserDurationMs: Long,
    private var laserHasTail: Boolean,
    private val pagesProvider: () -> List<PageEntity>,
    private val activePageIdProvider: () -> Long?,
    private val pageStrokesProvider: () -> Map<Long, List<StrokeEntity>>,
    private val viewModel: PageViewModel,
    private var zoom: Float,
    private var panX: Float,
    private var panY: Float,
    private var fingerDrawingEnabled: Boolean,
    private val onViewportTransform: (Float, Float, Float, Float, Float) -> Unit,
    private val onOptimisticStroke: (StrokeEntity, InkStroke?) -> Unit,
    private val onLassoSelectionChanged: (
        count: Int,
        selectedIds: Set<Long>,
        isDragging: Boolean,
        duplicateFn: () -> Unit,
        recolorFn: (Long) -> Unit,
        deleteFn: () -> Unit,
        deselectFn: () -> Unit
    ) -> Unit,
    private val onInvalidateCache: (Set<Long>?) -> Unit,
    private val onAddPageAtBottom: () -> Unit
) : FrameLayout(context) {

    private val inkView = InProgressStrokesView(context)
    private val predictor = MotionEventPredictor.newInstance(this)
    private val currentStrokePoints = ArrayList<StrokePoint>()

    private var selectedTool = initialTool
    private var activeTool: EditorTool? = null
    private var activePointerId: Int? = null
    private var isStylusActive = false

    // Active page index under current stroke
    private var activePageIndex = 0
    private var activePageId = 0L

    private var lastPanX: Float? = null
    private var lastPanY: Float? = null
    private var lastPinchDistance: Float? = null

    // Real-Time Eraser State
    private var eraserScreenX: Float? = null
    private var eraserScreenY: Float? = null
    private var showEraserCircle = false
    private val currentGestureErasedStrokes = mutableListOf<StrokeEntity>()
    private val currentGestureErasedIds = mutableSetOf<Long>()
    private var tempPrecisionStrokeId = -1L
    private val currentPrecisionOriginalStrokes = mutableMapOf<Long, StrokeEntity>()
    private val currentPrecisionDeletedOriginalIds = mutableSetOf<Long>()

    private val eraserFillPaint = Paint().apply {
        color = AndroidColor.argb(45, 239, 83, 80)
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val eraserStrokePaint = Paint().apply {
        color = AndroidColor.argb(220, 239, 83, 80)
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        isAntiAlias = true
    }

    // Laser Pointer State
    private val laserStrokes = mutableListOf<MutableList<LaserPoint>>()
    private var isDrawingLaser = false
    private var laserFadeStartTime = 0L

    private fun getVibrantNeonColor(rawColor: Long): Int {
        val r = (rawColor shr 16 and 0xFF).toInt()
        val g = (rawColor shr 8 and 0xFF).toInt()
        val b = (rawColor and 0xFF).toInt()
        val luminance = 0.299f * r + 0.587f * g + 0.114f * b
        // If color is too dark (e.g. black or dark gray), default to electric laser red
        if (luminance < 75f) {
            return AndroidColor.rgb(255, 30, 80) // Vibrant Electric Laser Red
        }
        return AndroidColor.rgb(r, g, b)
    }

    private val laserOuterGlowPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }
    private val laserMidGlowPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }
    private val laserCorePaint = Paint().apply {
        color = AndroidColor.WHITE
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }
    private val laserDotOuterPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val laserDotMidPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val laserDotCorePaint = Paint().apply {
        color = AndroidColor.WHITE
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    // Freehand Lasso Selection State
    private data class DecodedLassoStroke(
        val stroke: StrokeEntity,
        val pageTop: Float,
        val points: List<StrokePoint>
    )

    private val currentLassoPath = ArrayList<StrokePoint>()
    private val selectedStrokes = mutableListOf<StrokeEntity>()
    private val selectedStrokesDecoded = ArrayList<DecodedLassoStroke>()
    private var selectedStrokesBounds: StrokePointCodec.Bounds? = null
    private val selectedLassoClosedPath = ArrayList<StrokePoint>()
    private var selectedPageIndex = 0
    private var selectedPageId = 0L
    private var isDraggingLassoSelection = false
    private var lassoDragStartWorldX = 0f
    private var lassoDragStartWorldY = 0f
    private var lassoDragDeltaWorldX = 0f
    private var lassoDragDeltaWorldY = 0f

    private val lassoContourPaint = Paint().apply {
        color = AndroidColor.rgb(99, 102, 241)
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        pathEffect = DashPathEffect(floatArrayOf(12f, 8f), 0f)
        isAntiAlias = true
    }
    private val lassoFillPaint = Paint().apply {
        color = AndroidColor.argb(28, 99, 102, 241)
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val movingStrokePaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }

    private data class FinishedStrokeMeta(
        val pageId: Long,
        val encodedBytes: ByteArray,
        val color: Long,
        val strokeWidth: Float,
        val tool: StrokeTool
    )

    private val pendingFinishedStrokes = java.util.concurrent.ConcurrentHashMap<InProgressStrokeId, FinishedStrokeMeta>()
    private var activeInProgressStrokeId: InProgressStrokeId? = null
    private val finishedStrokesListener = object : InProgressStrokesFinishedListener {
        override fun onStrokesFinished(strokes: Map<InProgressStrokeId, InkStroke>) {
            for ((strokeId, inkStroke) in strokes) {
                val meta = pendingFinishedStrokes.remove(strokeId)
                if (meta != null) {
                    val optimisticEntity = StrokeEntity(
                        id = -System.nanoTime(),
                        pageId = meta.pageId,
                        pointData = meta.encodedBytes,
                        color = meta.color,
                        strokeWidth = meta.strokeWidth,
                        tool = meta.tool.name
                    )
                    onOptimisticStroke(optimisticEntity, inkStroke)
                }
            }
            if (strokes.isNotEmpty()) {
                inkView.removeFinishedStrokes(strokes.keys)
            }
        }
    }

    init {
        setBackgroundColor(AndroidColor.TRANSPARENT)
        setWillNotDraw(false)
        addView(
            inkView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
        inkView.addFinishedStrokesListener(finishedStrokesListener)
        inkView.eagerInit()
        isClickable = true
        isFocusable = true
    }

    fun updateConfiguration(
        brush: Brush,
        tool: EditorTool,
        eraserMode: EraserMode,
        strokeColor: Long,
        strokeWidth: Float,
        eraserRadius: Float,
        laserDurationMs: Long,
        laserHasTail: Boolean,
        zoom: Float,
        panX: Float,
        panY: Float,
        fingerDrawingEnabled: Boolean
    ) {
        this.brush = brush
        selectedTool = tool
        this.eraserMode = eraserMode
        this.strokeColor = strokeColor
        this.strokeWidth = strokeWidth
        this.eraserRadius = eraserRadius
        this.laserDurationMs = laserDurationMs
        this.laserHasTail = laserHasTail
        this.zoom = zoom
        this.panX = panX
        this.panY = panY
        this.fingerDrawingEnabled = fingerDrawingEnabled
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)

        // 1. Eraser Circle Cursor
        val ex = eraserScreenX
        val ey = eraserScreenY
        if (showEraserCircle && ex != null && ey != null) {
            val radius = (eraserRadius * zoom) / 2f
            canvas.drawCircle(ex, ey, radius, eraserFillPaint)
            canvas.drawCircle(ex, ey, radius, eraserStrokePaint)
        }

        // 2. Freehand Lasso In-Progress Drawing Contour (Document Space)
        if (currentLassoPath.size >= 2) {
            val path = Path()
            val first = currentLassoPath[0]
            path.moveTo(first.x * zoom + panX, first.y * zoom + panY)
            for (i in 1 until currentLassoPath.size) {
                val pt = currentLassoPath[i]
                path.lineTo(pt.x * zoom + panX, pt.y * zoom + panY)
            }
            canvas.drawPath(path, lassoContourPaint)
        }

        // 3. Freehand Lasso Closed Selection Outline (Around Selected Items in Document Space)
        if (selectedStrokes.isNotEmpty() && selectedLassoClosedPath.size >= 3) {
            val (dx, dy) = if (isDraggingLassoSelection) {
                getClampedLassoDragDelta(lassoDragDeltaWorldX, lassoDragDeltaWorldY)
            } else {
                Pair(0f, 0f)
            }

            val path = Path()
            val first = selectedLassoClosedPath[0]
            path.moveTo((first.x + dx) * zoom + panX, (first.y + dy) * zoom + panY)
            for (i in 1 until selectedLassoClosedPath.size) {
                val pt = selectedLassoClosedPath[i]
                path.lineTo((pt.x + dx) * zoom + panX, (pt.y + dy) * zoom + panY)
            }
            path.close()
            canvas.drawPath(path, lassoFillPaint)
            canvas.drawPath(path, lassoContourPaint)
        }

        // 4. Moving Selected Strokes in Real-Time During Drag (Zero-Allocation Rendering)
        if (isDraggingLassoSelection && selectedStrokesDecoded.isNotEmpty()) {
            val (dx, dy) = getClampedLassoDragDelta(lassoDragDeltaWorldX, lassoDragDeltaWorldY)

            for (decoded in selectedStrokesDecoded) {
                val points = decoded.points
                if (points.isEmpty()) continue
                val pageTop = decoded.pageTop

                movingStrokePaint.color = decoded.stroke.color.toInt()
                movingStrokePaint.strokeWidth = decoded.stroke.strokeWidth * zoom

                if (points.size == 1) {
                    val p = points[0]
                    val sx = (p.x + dx) * zoom + panX
                    val sy = (p.y + pageTop + dy) * zoom + panY
                    canvas.drawCircle(sx, sy, (decoded.stroke.strokeWidth * zoom) / 2f, movingStrokePaint)
                } else {
                    val path = Path()
                    val first = points[0]
                    path.moveTo((first.x + dx) * zoom + panX, (first.y + pageTop + dy) * zoom + panY)
                    for (i in 1 until points.size) {
                        val p = points[i]
                        path.lineTo((p.x + dx) * zoom + panX, (p.y + pageTop + dy) * zoom + panY)
                    }
                    canvas.drawPath(path, movingStrokePaint)
                }
            }
        }

        // 5. Glowing Neon Laser Pointer Beam & Dot (Pure white center + selected vibrant neon glow surrounding)
        if (laserStrokes.isNotEmpty()) {
            val now = SystemClock.uptimeMillis()
            val globalLife = if (isDrawingLaser) {
                1.0f
            } else {
                if (laserFadeStartTime == 0L) {
                    laserFadeStartTime = now
                }
                val elapsed = now - laserFadeStartTime
                (1f - (elapsed.toFloat() / laserDurationMs)).coerceIn(0f, 1f)
            }

            if (globalLife <= 0.005f) {
                laserStrokes.clear()
                laserFadeStartTime = 0L
            } else {
                val laserBaseWidth = (strokeWidth * zoom).coerceAtLeast(2.5f)
                val neonColor = getVibrantNeonColor(strokeColor)

                laserOuterGlowPaint.color = neonColor
                laserMidGlowPaint.color = neonColor
                laserDotOuterPaint.color = neonColor
                laserDotMidPaint.color = neonColor

                if (laserHasTail) {
                    for (stroke in laserStrokes) {
                        if (stroke.size >= 2) {
                            val path = Path()
                            path.moveTo(stroke[0].screenX, stroke[0].screenY)
                            for (i in 1 until stroke.size) {
                                val prev = stroke[i - 1]
                                val curr = stroke[i]
                                val midX = (prev.screenX + curr.screenX) / 2f
                                val midY = (prev.screenY + curr.screenY) / 2f
                                if (i == 1) {
                                    path.lineTo(midX, midY)
                                } else {
                                    path.quadTo(prev.screenX, prev.screenY, midX, midY)
                                }
                            }
                            val last = stroke.last()
                            path.lineTo(last.screenX, last.screenY)

                            // Layer 1: Wide Outer Neon Glow Aura
                            laserOuterGlowPaint.strokeWidth = laserBaseWidth * 2.2f
                            laserOuterGlowPaint.alpha = (globalLife * 60).toInt()
                            canvas.drawPath(path, laserOuterGlowPaint)

                            // Layer 2: Medium Luminous Neon Corona
                            laserMidGlowPaint.strokeWidth = laserBaseWidth * 1.3f
                            laserMidGlowPaint.alpha = (globalLife * 160).toInt()
                            canvas.drawPath(path, laserMidGlowPaint)

                            // Layer 3: Vibrant Surrounding Neon Sheen
                            laserMidGlowPaint.strokeWidth = laserBaseWidth * 0.8f
                            laserMidGlowPaint.alpha = (globalLife * 240).toInt()
                            canvas.drawPath(path, laserMidGlowPaint)

                            // Layer 4: Pure Brilliant White Core Beam
                            laserCorePaint.strokeWidth = (laserBaseWidth * 0.35f).coerceAtLeast(1.5f)
                            laserCorePaint.alpha = (globalLife * 255).toInt()
                            canvas.drawPath(path, laserCorePaint)
                        } else if (stroke.size == 1) {
                            val pt = stroke[0]
                            laserDotOuterPaint.alpha = (globalLife * 75).toInt()
                            canvas.drawCircle(pt.screenX, pt.screenY, laserBaseWidth * 1.8f, laserDotOuterPaint)
                            laserDotMidPaint.alpha = (globalLife * 220).toInt()
                            canvas.drawCircle(pt.screenX, pt.screenY, laserBaseWidth * 1.1f, laserDotMidPaint)
                            laserDotCorePaint.alpha = (globalLife * 255).toInt()
                            canvas.drawCircle(pt.screenX, pt.screenY, (laserBaseWidth * 0.45f).coerceAtLeast(1.8f), laserDotCorePaint)
                        }
                    }
                }

                // Laser Head Pointer Dot (Tip of the active drawing stroke)
                val activeStroke = laserStrokes.lastOrNull()
                val head = activeStroke?.lastOrNull()
                if (head != null) {
                    // Outer neon pulse
                    laserDotOuterPaint.alpha = (globalLife * 75).toInt()
                    canvas.drawCircle(head.screenX, head.screenY, laserBaseWidth * 1.8f, laserDotOuterPaint)
                    // Mid neon halo
                    laserDotMidPaint.alpha = (globalLife * 220).toInt()
                    canvas.drawCircle(head.screenX, head.screenY, laserBaseWidth * 1.1f, laserDotMidPaint)
                    // Pure white intense core
                    laserDotCorePaint.alpha = (globalLife * 255).toInt()
                    canvas.drawCircle(head.screenX, head.screenY, (laserBaseWidth * 0.45f).coerceAtLeast(1.8f), laserDotCorePaint)
                }

                if (!isDrawingLaser) {
                    postInvalidateOnAnimation()
                }
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val actionIndex = event.actionIndex
        val toolType = event.getToolType(actionIndex)
        val isStylusEvent = toolType == MotionEvent.TOOL_TYPE_STYLUS || toolType == MotionEvent.TOOL_TYPE_ERASER

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (isStylusEvent) {
                    isStylusActive = true
                    beginStroke(event, actionIndex)
                } else {
                    if (!isStylusActive) {
                        if (fingerDrawingEnabled) {
                            beginStroke(event, actionIndex)
                        } else {
                            initSingleFingerPan(event)
                        }
                    }
                }
                return true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                if (isStylusEvent && activePointerId == null) {
                    isStylusActive = true
                    beginStroke(event, actionIndex)
                } else if (!isStylusActive) {
                    if (activePointerId == null) {
                        initPinchGesture(event)
                    }
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (activePointerId != null) {
                    updateStroke(event)
                } else if (!isStylusActive) {
                    if (event.pointerCount >= 2) {
                        handlePinchGesture(event)
                    } else if (!fingerDrawingEnabled && event.pointerCount == 1) {
                        handleSingleFingerPan(event)
                    }
                }
                return true
            }

            MotionEvent.ACTION_POINTER_UP -> {
                val pointerId = event.getPointerId(actionIndex)
                if (activePointerId == pointerId) {
                    finishStroke(event)
                    if (isStylusEvent) {
                        isStylusActive = false
                    }
                } else if (!isStylusActive) {
                    resetViewportGesture()
                }
                hideEraserCursor()
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (activePointerId != null) {
                    finishStroke(event)
                }
                isStylusActive = false
                resetViewportGesture()
                hideEraserCursor()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                cancelActiveStroke()
                isStylusActive = false
                resetViewportGesture()
                hideEraserCursor()
                return true
            }
        }

        return true
    }

    private fun hideEraserCursor() {
        showEraserCircle = false
        eraserScreenX = null
        eraserScreenY = null
        invalidate()
    }

    private fun beginStroke(event: MotionEvent, pointerIndex: Int) {
        val pages = pagesProvider()
        if (pages.isEmpty()) return

        requestUnbufferedDispatch(event)
        activePointerId = event.getPointerId(pointerIndex)
        activeTool = if (event.getToolType(pointerIndex) == MotionEvent.TOOL_TYPE_ERASER) {
            EditorTool.ERASER
        } else {
            selectedTool
        }

        val screenX = event.getX(pointerIndex)
        val screenY = event.getY(pointerIndex)
        val docX = (screenX - panX) / zoom
        val docY = (screenY - panY) / zoom

        // Route to the touched page
        val targetIdx = (docY / (PAGE_HEIGHT + PAGE_GAP)).toInt().coerceIn(0, pages.size - 1)
        activePageIndex = targetIdx
        val targetPage = pages[targetIdx]
        activePageId = targetPage.id
        viewModel.setActivePage(targetPage.id)

        currentStrokePoints.clear()
        addRealPoints(event)

        when (activeTool) {
            EditorTool.ERASER -> {
                onInvalidateCache(null)
                eraserScreenX = screenX
                eraserScreenY = screenY
                showEraserCircle = true
                currentGestureErasedStrokes.clear()
                currentGestureErasedIds.clear()
                tempPrecisionStrokeId = -1L
                currentPrecisionOriginalStrokes.clear()
                currentPrecisionDeletedOriginalIds.clear()
                handleEraserMotion(currentStrokePoints)
                invalidate()
            }

            EditorTool.LASSO -> {
                val isInsideSelection = isTouchInsideSelection(docX, docY)

                if (selectedStrokes.isNotEmpty() && isInsideSelection) {
                    isDraggingLassoSelection = true
                    lassoDragStartWorldX = docX
                    lassoDragStartWorldY = docY
                    lassoDragDeltaWorldX = 0f
                    lassoDragDeltaWorldY = 0f
                    notifyLassoSelection(isDragging = true)
                } else {
                    onInvalidateCache(null)
                    clearLassoSelection()
                    currentLassoPath.clear()
                    currentLassoPath.add(StrokePoint(docX, docY, 0.5f, 0))
                }
                invalidate()
            }

            EditorTool.LASER -> {
                isDrawingLaser = true
                laserFadeStartTime = 0L
                val newStroke = mutableListOf(LaserPoint(screenX, screenY, SystemClock.uptimeMillis()))
                laserStrokes.add(newStroke)
                invalidate()
            }

            EditorTool.PEN -> {
                val pageTop = pageTopY(targetIdx)
                val motionToWorld = Matrix().apply {
                    postTranslate(-panX, -panY)
                    postScale(1f / zoom, 1f / zoom)
                    postTranslate(0f, -pageTop)
                }
                predictor.record(event)
                activeInProgressStrokeId = inkView.startStroke(
                    event = event,
                    pointerId = activePointerId!!,
                    brush = brush,
                    motionEventToWorldTransform = motionToWorld
                )
            }

            EditorTool.HIGHLIGHTER -> {
                val pageTop = pageTopY(targetIdx)
                val motionToWorld = Matrix().apply {
                    postTranslate(-panX, -panY)
                    postScale(1f / zoom, 1f / zoom)
                    postTranslate(0f, -pageTop)
                }
                activeInProgressStrokeId = inkView.startStroke(
                    event = event,
                    pointerId = activePointerId!!,
                    brush = brush,
                    motionEventToWorldTransform = motionToWorld
                )
            }

            null -> Unit
        }
    }

    private fun updateStroke(event: MotionEvent) {
        val pointerId = activePointerId ?: return
        val pointerIndex = event.findPointerIndex(pointerId)
        if (pointerIndex < 0) return

        val prevPointCount = currentStrokePoints.size
        addRealPoints(event)
        val newPoints = currentStrokePoints.subList(prevPointCount, currentStrokePoints.size)

        val screenX = event.getX(pointerIndex)
        val screenY = event.getY(pointerIndex)
        val docX = (screenX - panX) / zoom
        val docY = (screenY - panY) / zoom

        when (activeTool) {
            EditorTool.ERASER -> {
                eraserScreenX = screenX
                eraserScreenY = screenY
                showEraserCircle = true
                handleEraserMotion(if (newPoints.isNotEmpty()) newPoints else currentStrokePoints)
                invalidate()
            }

            EditorTool.LASSO -> {
                if (isDraggingLassoSelection) {
                    val rawDx = docX - lassoDragStartWorldX
                    val rawDy = docY - lassoDragStartWorldY
                    val (clampedDx, clampedDy) = getClampedLassoDragDelta(rawDx, rawDy)
                    lassoDragDeltaWorldX = clampedDx
                    lassoDragDeltaWorldY = clampedDy
                } else {
                    for (h in 0 until event.historySize) {
                        val hx = (event.getHistoricalX(pointerIndex, h) - panX) / zoom
                        val hy = (event.getHistoricalY(pointerIndex, h) - panY) / zoom
                        val last = currentLassoPath.lastOrNull()
                        if (last == null || ((hx - last.x) * (hx - last.x) + (hy - last.y) * (hy - last.y) >= 36f)) {
                            currentLassoPath.add(StrokePoint(hx, hy, 0.5f, 0))
                        }
                    }
                    val last = currentLassoPath.lastOrNull()
                    if (last == null || ((docX - last.x) * (docX - last.x) + (docY - last.y) * (docY - last.y) >= 36f)) {
                        currentLassoPath.add(StrokePoint(docX, docY, 0.5f, 0))
                    }
                }
                invalidate()
            }

            EditorTool.LASER -> {
                isDrawingLaser = true
                laserFadeStartTime = 0L
                val now = SystemClock.uptimeMillis()
                val currentStroke = laserStrokes.lastOrNull() ?: mutableListOf<LaserPoint>().also { laserStrokes.add(it) }
                currentStroke.add(LaserPoint(screenX, screenY, now))
                invalidate()
            }

            EditorTool.HIGHLIGHTER -> {
                inkView.addToStroke(
                    event = event,
                    pointerId = pointerId,
                    prediction = null
                )
            }

            EditorTool.PEN -> {
                predictor.record(event)
                val prediction = predictor.predict()
                inkView.addToStroke(
                    event = event,
                    pointerId = pointerId,
                    prediction = prediction
                )
                prediction?.recycle()
            }

            null -> Unit
        }
    }

    private fun finishStroke(event: MotionEvent) {
        val pointerId = activePointerId ?: return
        addRealPoints(event)

        when (activeTool) {
            EditorTool.ERASER -> {
                handleEraserMotion(currentStrokePoints)
                if (eraserMode == EraserMode.STROKE) {
                    if (currentGestureErasedStrokes.isNotEmpty()) {
                        viewModel.commitEraseSession(activePageId, currentGestureErasedStrokes.toList())
                        currentGestureErasedStrokes.clear()
                        currentGestureErasedIds.clear()
                    }
                } else {
                    if (currentPrecisionDeletedOriginalIds.isNotEmpty()) {
                        val allStrokesMap = pageStrokesProvider()
                        val currentStrokesOnPage = allStrokesMap[activePageId] ?: emptyList()
                        val finalStrokesToPersist = currentStrokesOnPage.filter { it.id < 0 }
                        viewModel.commitPrecisionEraseSession(
                            pageId = activePageId,
                            deletedIds = currentPrecisionDeletedOriginalIds.toSet(),
                            originalStrokes = currentPrecisionOriginalStrokes.values.toList(),
                            finalStrokes = finalStrokesToPersist
                        )
                        currentPrecisionOriginalStrokes.clear()
                        currentPrecisionDeletedOriginalIds.clear()
                    }
                }
            }

            EditorTool.LASSO -> {
                if (isDraggingLassoSelection) {
                    val (dx, dy) = getClampedLassoDragDelta(lassoDragDeltaWorldX, lassoDragDeltaWorldY)
                    if (dx != 0f || dy != 0f) {
                        applyLassoTranslation(dx, dy)
                    }
                    isDraggingLassoSelection = false
                    lassoDragDeltaWorldX = 0f
                    lassoDragDeltaWorldY = 0f
                    notifyLassoSelection(isDragging = false)
                } else {
                    val pointerIndex = event.findPointerIndex(pointerId)
                    if (pointerIndex >= 0) {
                        val sx = event.getX(pointerIndex)
                        val sy = event.getY(pointerIndex)
                        val docX = (sx - panX) / zoom
                        val docY = (sy - panY) / zoom
                        currentLassoPath.add(StrokePoint(docX, docY, 0.5f, 0))
                    }
                    if (currentLassoPath.size >= 3) {
                        val simplified = simplifyPath(currentLassoPath, 10f)
                        selectedLassoClosedPath.clear()
                        selectedLassoClosedPath.addAll(simplified)
                        computeLassoSelection(selectedLassoClosedPath)
                        currentLassoPath.clear()
                    }
                }
                invalidate()
            }

            EditorTool.LASER -> {
                isDrawingLaser = false
                laserFadeStartTime = SystemClock.uptimeMillis()
                postInvalidateOnAnimation()
            }

            EditorTool.PEN,
            EditorTool.HIGHLIGHTER -> {
                if (activeTool == EditorTool.PEN) {
                    predictor.record(event)
                }
                val finishedId = activeInProgressStrokeId
                inkView.finishStroke(event, pointerId)
                activeInProgressStrokeId = null

                val pages = pagesProvider()
                if (pages.isNotEmpty() && currentStrokePoints.isNotEmpty()) {
                    val pointsCopy = currentStrokePoints.toList()
                    val toolEnum = activeTool!!.toStrokeTool()

                    // Assign stroke to the page where it originates
                    val startDocY = pointsCopy.first().y
                    val pageIdx = (startDocY / PAGE_HEIGHT).toInt().coerceIn(0, pages.size - 1)
                    val targetPage = pages[pageIdx]
                    val pageTop = pageTopY(pageIdx)

                    val localPoints = if (pointsCopy.size == 1) {
                        val p = pointsCopy[0]
                        listOf(
                            StrokePoint(p.x, p.y - pageTop, p.pressure, p.elapsedMillis),
                            StrokePoint(p.x + 0.05f, (p.y - pageTop) + 0.05f, p.pressure, p.elapsedMillis + 1)
                        )
                    } else {
                        pointsCopy.map { StrokePoint(it.x, it.y - pageTop, it.pressure, it.elapsedMillis) }
                    }

                    val encodedBytes = StrokePointCodec.encode(localPoints)

                    if (finishedId != null) {
                        pendingFinishedStrokes[finishedId] = FinishedStrokeMeta(
                            pageId = targetPage.id,
                            encodedBytes = encodedBytes,
                            color = strokeColor,
                            strokeWidth = strokeWidth,
                            tool = toolEnum
                        )
                    }

                    viewModel.addStroke(
                        pageId = targetPage.id,
                        points = localPoints,
                        color = strokeColor,
                        strokeWidth = strokeWidth,
                        tool = toolEnum
                    )
                }
            }

            null -> Unit
        }

        activePointerId = null
        activeTool = null
        currentStrokePoints.clear()
        hideEraserCursor()
    }

    private fun cancelActiveStroke() {
        if (activeTool == EditorTool.PEN || activeTool == EditorTool.HIGHLIGHTER) {
            inkView.cancelUnfinishedStrokes()
            activeInProgressStrokeId = null
        }
        if (activeTool == EditorTool.LASER) {
            isDrawingLaser = false
            laserFadeStartTime = SystemClock.uptimeMillis()
            postInvalidateOnAnimation()
        }
        if (activeTool == EditorTool.LASSO) {
            if (isDraggingLassoSelection) {
                val dx = lassoDragDeltaWorldX
                val dy = lassoDragDeltaWorldY
                if (dx != 0f || dy != 0f) {
                    applyLassoTranslation(dx, dy)
                }
                isDraggingLassoSelection = false
                lassoDragDeltaWorldX = 0f
                lassoDragDeltaWorldY = 0f
                notifyLassoSelection(isDragging = false)
            } else if (currentLassoPath.size >= 3) {
                val simplified = simplifyPath(currentLassoPath, 10f)
                selectedLassoClosedPath.clear()
                selectedLassoClosedPath.addAll(simplified)
                computeLassoSelection(selectedLassoClosedPath)
                currentLassoPath.clear()
            }
        }
        activePointerId = null
        activeTool = null
        currentStrokePoints.clear()
        currentLassoPath.clear()
        isDraggingLassoSelection = false
        hideEraserCursor()
        invalidate()
    }

    private fun handleEraserMotion(pointsToTest: List<StrokePoint>) {
        if (pointsToTest.isEmpty()) return
        val allStrokesMap = pageStrokesProvider()
        val pages = pagesProvider()
        val effectiveRadius = eraserRadius / 2f

        // Fast Document-Space Bounding Box of the eraser sweep
        var eMinY = Float.MAX_VALUE
        var eMaxY = -Float.MAX_VALUE
        for (p in pointsToTest) {
            if (p.y < eMinY) eMinY = p.y
            if (p.y > eMaxY) eMaxY = p.y
        }
        val paddedMinY = eMinY - effectiveRadius - 10f
        val paddedMaxY = eMaxY + effectiveRadius + 10f

        for ((idx, page) in pages.withIndex()) {
            val pageTop = pageTopY(idx)
            val pageBottom = pageTop + PAGE_HEIGHT

            // Skip pages outside eraser vertical reach
            if (paddedMaxY < pageTop || paddedMinY > pageBottom) {
                continue
            }

            val currentStrokes = allStrokesMap[page.id] ?: emptyList()
            if (currentStrokes.isEmpty()) continue

            val localEraserPoints = pointsToTest.map { StrokePoint(it.x, it.y - pageTop, it.pressure, it.elapsedMillis) }

            if (eraserMode == EraserMode.STROKE) {
                val newlyErased = mutableListOf<StrokeEntity>()
                val newlyErasedIds = mutableSetOf<Long>()

                for (stroke in currentStrokes) {
                    if (stroke.id !in currentGestureErasedIds) {
                        val touches = StrokeRepository.strokeTouchesEraser(
                            stroke = stroke,
                            eraserPoints = localEraserPoints,
                            eraserRadius = effectiveRadius + stroke.strokeWidth / 2f
                        )
                        if (touches) {
                            newlyErased.add(stroke)
                            newlyErasedIds.add(stroke.id)
                            currentGestureErasedIds.add(stroke.id)
                        }
                    }
                }

                if (newlyErased.isNotEmpty()) {
                    currentGestureErasedStrokes.addAll(newlyErased)
                    onInvalidateCache(newlyErasedIds)
                    viewModel.eraseStrokesInstantly(page.id, newlyErasedIds)
                }
            } else {
                // Precision Erase
                for (stroke in currentStrokes) {
                    val splitStrokes = StrokeRepository.precisionEraseStroke(
                        stroke = stroke,
                        eraserPoints = localEraserPoints,
                        eraserRadius = effectiveRadius + stroke.strokeWidth / 2f
                    )
                    if (splitStrokes != null) {
                        if (stroke.id > 0 && stroke.id !in currentPrecisionOriginalStrokes) {
                            currentPrecisionOriginalStrokes[stroke.id] = stroke
                        }
                        if (stroke.id > 0) {
                            currentPrecisionDeletedOriginalIds.add(stroke.id)
                        }

                        val piecesWithUniqueIds = splitStrokes.map {
                            it.copy(id = tempPrecisionStrokeId--)
                        }

                        onInvalidateCache(setOf(stroke.id))
                        viewModel.updateLivePrecisionStrokes(page.id, setOf(stroke.id), piecesWithUniqueIds)
                    }
                }
            }
        }
    }

    private fun getPageTopY(pageId: Long): Float {
        val pages = pagesProvider()
        val idx = pages.indexOfFirst { it.id == pageId }
        return if (idx >= 0) pageTopY(idx) else 0f
    }

    private fun simplifyPath(points: List<StrokePoint>, tolerance: Float = 10f): List<StrokePoint> {
        if (points.size <= 3) return points
        val result = ArrayList<StrokePoint>(points.size / 2)
        result.add(points.first())
        var lastPt = points.first()
        val tolSq = tolerance * tolerance

        for (i in 1 until points.size - 1) {
            val pt = points[i]
            val dx = pt.x - lastPt.x
            val dy = pt.y - lastPt.y
            if (dx * dx + dy * dy >= tolSq) {
                result.add(pt)
                lastPt = pt
            }
        }
        result.add(points.last())
        return result
    }

    private fun computeLassoSelection(rawPolygon: List<StrokePoint>) {
        if (rawPolygon.size < 3) return
        val lassoPolygon = simplifyPath(rawPolygon, 10f)
        if (lassoPolygon.size < 3) return

        val allStrokesMap = pageStrokesProvider()
        val pages = pagesProvider()
        val selected = mutableListOf<StrokeEntity>()
        val decodedList = ArrayList<DecodedLassoStroke>()

        var polyMinX = Float.MAX_VALUE
        var polyMinY = Float.MAX_VALUE
        var polyMaxX = -Float.MAX_VALUE
        var polyMaxY = -Float.MAX_VALUE
        for (p in lassoPolygon) {
            polyMinX = min(polyMinX, p.x)
            polyMinY = min(polyMinY, p.y)
            polyMaxX = max(polyMaxX, p.x)
            polyMaxY = max(polyMaxY, p.y)
        }

        var overallMinX = Float.MAX_VALUE
        var overallMinY = Float.MAX_VALUE
        var overallMaxX = -Float.MAX_VALUE
        var overallMaxY = -Float.MAX_VALUE

        for ((idx, page) in pages.withIndex()) {
            val pageTop = pageTopY(idx)
            val strokes = allStrokesMap[page.id] ?: continue

            for (stroke in strokes) {
                val bounds = StrokePointCodec.computeBounds(stroke.pointData, pageTop) ?: continue

                // Fast AABB check against lasso bounding box
                if (bounds.maxX < polyMinX || bounds.minX > polyMaxX || bounds.maxY < polyMinY || bounds.minY > polyMaxY) {
                    continue
                }

                val centroid = StrokePoint(
                    x = (bounds.minX + bounds.maxX) / 2f,
                    y = (bounds.minY + bounds.maxY) / 2f,
                    pressure = 0.5f,
                    elapsedMillis = 0
                )

                // If centroid is inside lasso polygon, select stroke immediately
                var isSelected = isPointInPolygon(centroid, lassoPolygon)

                var strokePoints: List<StrokePoint>? = null
                if (!isSelected) {
                    strokePoints = StrokePointCodec.decode(stroke.pointData)
                    for (pt in strokePoints) {
                        if (isPointInPolygon(StrokePoint(pt.x, pt.y + pageTop, pt.pressure, pt.elapsedMillis), lassoPolygon)) {
                            isSelected = true
                            break
                        }
                    }

                    if (!isSelected && strokePoints.size >= 2) {
                        val lassoSegments = lassoPolygon.zipWithNext().ifEmpty { lassoPolygon.zip(lassoPolygon) }
                        val strokeSegments = strokePoints.zipWithNext()
                        for ((sStart, sEnd) in strokeSegments) {
                            val s1 = StrokePoint(sStart.x, sStart.y + pageTop, sStart.pressure, sStart.elapsedMillis)
                            val s2 = StrokePoint(sEnd.x, sEnd.y + pageTop, sEnd.pressure, sEnd.elapsedMillis)
                            for ((lStart, lEnd) in lassoSegments) {
                                if (segmentsIntersect(s1, s2, lStart, lEnd)) {
                                    isSelected = true
                                    break
                                }
                            }
                            if (isSelected) break
                        }
                    }
                }

                if (isSelected) {
                    selected.add(stroke)
                    val pts = strokePoints ?: StrokePointCodec.decode(stroke.pointData)
                    decodedList.add(DecodedLassoStroke(stroke, pageTop, pts))
                    overallMinX = min(overallMinX, bounds.minX)
                    overallMinY = min(overallMinY, bounds.minY)
                    overallMaxX = max(overallMaxX, bounds.maxX)
                    overallMaxY = max(overallMaxY, bounds.maxY)
                }
            }
        }

        selectedStrokes.clear()
        selectedStrokes.addAll(selected)
        selectedStrokesDecoded.clear()
        selectedStrokesDecoded.addAll(decodedList)

        if (selectedStrokes.isEmpty()) {
            selectedLassoClosedPath.clear()
            selectedStrokesBounds = null
        } else {
            selectedStrokesBounds = StrokePointCodec.Bounds(overallMinX, overallMinY, overallMaxX, overallMaxY)
        }
        notifyLassoSelection(isDragging = false)
    }

    private fun isTouchInsideSelection(docX: Float, docY: Float): Boolean {
        if (selectedStrokes.isEmpty()) return false

        // 1. Check inside closed lasso polygon (in document space)
        if (selectedLassoClosedPath.size >= 3 && isPointInPolygon(StrokePoint(docX, docY, 0.5f, 0), selectedLassoClosedPath)) {
            return true
        }

        // 2. Check inside pre-computed bounding box with padding (zero allocations)
        val bounds = selectedStrokesBounds
        if (bounds != null) {
            val padding = 40f / zoom
            if (docX in (bounds.minX - padding)..(bounds.maxX + padding) && docY in (bounds.minY - padding)..(bounds.maxY + padding)) {
                return true
            }
        }

        return false
    }

    private fun isPointInPolygon(point: StrokePoint, polygon: List<StrokePoint>): Boolean {
        if (polygon.size < 3) return false
        var inside = false
        var j = polygon.size - 1
        for (i in polygon.indices) {
            val xi = polygon[i].x
            val yi = polygon[i].y
            val xj = polygon[j].x
            val yj = polygon[j].y
            val intersect = ((yi > point.y) != (yj > point.y)) &&
                (point.x < (xj - xi) * (point.y - yi) / (yj - yi) + xi)
            if (intersect) inside = !inside
            j = i
        }
        return inside
    }

    private fun segmentsIntersect(p1: StrokePoint, p2: StrokePoint, p3: StrokePoint, p4: StrokePoint): Boolean {
        fun ccw(a: StrokePoint, b: StrokePoint, c: StrokePoint): Float =
            (c.y - a.y) * (b.x - a.x) - (b.y - a.y) * (c.x - a.x)
        val d1 = ccw(p3, p4, p1)
        val d2 = ccw(p3, p4, p2)
        val d3 = ccw(p1, p2, p3)
        val d4 = ccw(p1, p2, p4)
        return ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) &&
               ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))
    }

    private fun getClampedLassoDragDelta(rawDx: Float, rawDy: Float): Pair<Float, Float> {
        val bounds = selectedStrokesBounds ?: return Pair(rawDx, rawDy)
        val pages = pagesProvider()
        val totalDocHeight = (pages.size * PAGE_HEIGHT) + max(0, pages.size - 1) * PAGE_GAP

        val minDx = -bounds.minX
        val maxDx = max(minDx, PAGE_WIDTH - bounds.maxX)
        val clampedDx = if (minDx <= maxDx) rawDx.coerceIn(minDx, maxDx) else rawDx

        val minDy = -bounds.minY
        val maxDy = max(minDy, totalDocHeight - bounds.maxY)
        val clampedDy = if (minDy <= maxDy) rawDy.coerceIn(minDy, maxDy) else rawDy

        return Pair(clampedDx, clampedDy)
    }

    private fun applyLassoTranslation(dx: Float, dy: Float) {
        if (selectedStrokesDecoded.isEmpty()) return
        val pages = pagesProvider()
        if (pages.isEmpty()) return

        val affectedIds = selectedStrokesDecoded.map { it.stroke.id }.toSet()
        onInvalidateCache(affectedIds)

        val updatedSelectedStrokes = mutableListOf<StrokeEntity>()
        val updatedDecodedList = mutableListOf<DecodedLassoStroke>()

        val beforeStrokesList = mutableListOf<StrokeEntity>()
        val afterStrokesList = mutableListOf<StrokeEntity>()

        for (decoded in selectedStrokesDecoded) {
            val originalStroke = decoded.stroke
            beforeStrokesList.add(originalStroke)

            // Convert points to document coordinates to find vertical center
            var minDocY = Float.MAX_VALUE
            var maxDocY = -Float.MAX_VALUE
            for (pt in decoded.points) {
                val docY = pt.y + decoded.pageTop + dy
                if (docY < minDocY) minDocY = docY
                if (docY > maxDocY) maxDocY = docY
            }

            val strokeCenterDocY = if (minDocY <= maxDocY) (minDocY + maxDocY) / 2f else (decoded.points.firstOrNull()?.let { it.y + decoded.pageTop + dy } ?: 0f)
            val newPageIdx = (strokeCenterDocY / PAGE_HEIGHT).toInt().coerceIn(0, pages.size - 1)
            val targetPage = pages[newPageIdx]
            val newPageTop = pageTopY(newPageIdx)

            val shiftedPoints = decoded.points.map { pt ->
                StrokePoint(
                    x = (pt.x + dx).coerceIn(0f, PAGE_WIDTH),
                    y = (pt.y + decoded.pageTop + dy) - newPageTop,
                    pressure = pt.pressure,
                    elapsedMillis = pt.elapsedMillis
                )
            }

            val newBytes = StrokePointCodec.encode(shiftedPoints)
            val updatedStroke = originalStroke.copy(
                pageId = targetPage.id,
                pointData = newBytes
            )

            updatedSelectedStrokes.add(updatedStroke)
            updatedDecodedList.add(DecodedLassoStroke(updatedStroke, newPageTop, shiftedPoints))
            afterStrokesList.add(updatedStroke)
        }

        viewModel.transformStrokesMultiPage(beforeStrokesList, afterStrokesList)

        selectedStrokes.clear()
        selectedStrokes.addAll(updatedSelectedStrokes)
        selectedStrokesDecoded.clear()
        selectedStrokesDecoded.addAll(updatedDecodedList)

        // Update cached bounds in document space
        val b = selectedStrokesBounds
        if (b != null) {
            selectedStrokesBounds = StrokePointCodec.Bounds(b.minX + dx, b.minY + dy, b.maxX + dx, b.maxY + dy)
        }

        // Translate the lasso closed outline path in document space as well
        val shiftedLasso = selectedLassoClosedPath.map { it.copy(x = it.x + dx, y = it.y + dy) }
        selectedLassoClosedPath.clear()
        selectedLassoClosedPath.addAll(shiftedLasso)
    }

    private fun clearLassoSelection() {
        selectedStrokes.clear()
        selectedStrokesDecoded.clear()
        selectedStrokesBounds = null
        selectedLassoClosedPath.clear()
        notifyLassoSelection(isDragging = false)
    }

    private fun notifyLassoSelection(isDragging: Boolean) {
        val ids: Set<Long> = selectedStrokes.map { it.id }.toSet()
        val allStrokesGrouped = selectedStrokes.groupBy { it.pageId }
        onLassoSelectionChanged(
            selectedStrokes.size,
            ids,
            isDragging,
            {
                // Duplicate
                for ((pageId, strokesOnPage) in allStrokesGrouped) {
                    viewModel.duplicateStrokes(pageId, strokesOnPage)
                }
                clearLassoSelection()
            },
            { newColor: Long ->
                // Recolor
                val affectedIds = selectedStrokes.map { it.id }.toSet()
                onInvalidateCache(affectedIds)
                val beforeList = selectedStrokes.toList()
                val afterList = beforeList.map { it.copy(color = newColor) }
                viewModel.transformStrokesMultiPage(beforeList, afterList)
                clearLassoSelection()
            },
            {
                // Delete
                val affectedIds = selectedStrokes.map { it.id }.toSet()
                onInvalidateCache(affectedIds)
                viewModel.deleteStrokesMultiPage(selectedStrokes.toList())
                clearLassoSelection()
            },
            {
                // Deselect
                clearLassoSelection()
            }
        )
    }

    private fun addRealPoints(event: MotionEvent) {
        val pointerId = activePointerId ?: return
        val pointerIndex = event.findPointerIndex(pointerId)
        if (pointerIndex < 0) return

        val isStylus = event.getToolType(pointerIndex) == MotionEvent.TOOL_TYPE_STYLUS

        val historySize = event.historySize
        for (h in 0 until historySize) {
            val hx = event.getHistoricalX(pointerIndex, h)
            val hy = event.getHistoricalY(pointerIndex, h)
            val worldX = (hx - panX) / zoom
            val worldY = (hy - panY) / zoom
            val rawPressure = event.getHistoricalPressure(pointerIndex, h)
            val pressure = if (isStylus) rawPressure.coerceIn(0.05f, 1.0f) else 0.5f
            val time = (event.getHistoricalEventTime(h) - event.downTime).toInt().coerceAtLeast(0)
            currentStrokePoints.add(StrokePoint(worldX, worldY, pressure, time))
        }

        val sx = event.getX(pointerIndex)
        val sy = event.getY(pointerIndex)
        val worldX = (sx - panX) / zoom
        val worldY = (sy - panY) / zoom
        val rawPressure = event.getPressure(pointerIndex)
        val pressure = if (isStylus) rawPressure.coerceIn(0.05f, 1.0f) else 0.5f
        val time = (event.eventTime - event.downTime).toInt().coerceAtLeast(0)
        currentStrokePoints.add(StrokePoint(worldX, worldY, pressure, time))
    }

    // Pinch / Pan Handlers
    private fun initSingleFingerPan(event: MotionEvent) {
        lastPanX = event.x
        lastPanY = event.y
    }

    private fun handleSingleFingerPan(event: MotionEvent) {
        val lx = lastPanX ?: return
        val ly = lastPanY ?: return
        val dx = event.x - lx
        val dy = event.y - ly
        lastPanX = event.x
        lastPanY = event.y
        onViewportTransform(1f, dx, dy, event.x, event.y)
    }

    private fun initPinchGesture(event: MotionEvent) {
        if (event.pointerCount >= 2) {
            val x0 = event.getX(0)
            val y0 = event.getY(0)
            val x1 = event.getX(1)
            val y1 = event.getY(1)
            val dist = kotlin.math.hypot((x1 - x0).toDouble(), (y1 - y0).toDouble()).toFloat()
            lastPinchDistance = dist
            lastPanX = (x0 + x1) / 2f
            lastPanY = (y0 + y1) / 2f
        }
    }

    private fun handlePinchGesture(event: MotionEvent) {
        if (event.pointerCount >= 2) {
            val x0 = event.getX(0)
            val y0 = event.getY(0)
            val x1 = event.getX(1)
            val y1 = event.getY(1)
            val dist = kotlin.math.hypot((x1 - x0).toDouble(), (y1 - y0).toDouble()).toFloat()
            val focalX = (x0 + x1) / 2f
            val focalY = (y0 + y1) / 2f

            val ld = lastPinchDistance
            val lx = lastPanX
            val ly = lastPanY

            if (ld != null && ld > 10f && lx != null && ly != null) {
                val scale = dist / ld
                val dx = focalX - lx
                val dy = focalY - ly
                onViewportTransform(scale, dx, dy, focalX, focalY)
            }

            lastPinchDistance = dist
            lastPanX = focalX
            lastPanY = focalY
        }
    }

    private fun resetViewportGesture() {
        lastPanX = null
        lastPanY = null
        lastPinchDistance = null
    }
}

/**
 * FreeNotes Instant Color Selection Dialog (One-tap selection & live RGB sliders)
 */
@Composable
private fun FreeNotesInstantColorPickerDialog(
    initialColor: Long,
    onDismiss: () -> Unit,
    onColorSelected: (Long) -> Unit
) {
    var selectedColor by remember { mutableStateOf(initialColor) }
    var selectedPaletteTab by remember { mutableIntStateOf(0) }

    val redVal = ((selectedColor shr 16) and 0xFF).toInt()
    val greenVal = ((selectedColor shr 8) and 0xFF).toInt()
    val blueVal = (selectedColor and 0xFF).toInt()

    var r by remember { mutableFloatStateOf(redVal.toFloat()) }
    var g by remember { mutableFloatStateOf(greenVal.toFloat()) }
    var b by remember { mutableFloatStateOf(blueVal.toFloat()) }

    val paletteList = when (selectedPaletteTab) {
        0 -> CLASSIC_PALETTE
        1 -> PASTEL_PALETTE
        2 -> VIBRANT_PALETTE
        else -> EARTHY_PALETTE
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(selectedColor.toInt()))
                            .border(1.5.dp, Color.Gray.copy(alpha = 0.4f), CircleShape)
                    )
                    Text("Color Palette", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Text("✕", fontSize = 14.sp)
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("Classic", "Pastel", "Vibrant", "Earthy").forEachIndexed { index, tabName ->
                        val isSelected = selectedPaletteTab == index
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedPaletteTab = index }
                        ) {
                            Text(
                                text = tabName,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.padding(vertical = 6.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    paletteList.forEach { colorPreset ->
                        val isPresetActive = selectedColor == colorPreset.argb
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(colorPreset.argb.toInt()))
                                .border(
                                    if (isPresetActive) 3.dp else 1.dp,
                                    if (isPresetActive) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.3f),
                                    CircleShape
                                )
                                .clickable {
                                    selectedColor = colorPreset.argb
                                    onColorSelected(colorPreset.argb)
                                    onDismiss()
                                }
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Text("Custom RGB Sliders (Live Update)", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("R", modifier = Modifier.width(20.dp), fontWeight = FontWeight.Bold, color = Color(0xFFE02424))
                    Slider(
                        value = r,
                        onValueChange = {
                            r = it
                            val cr = r.toInt().coerceIn(0, 255)
                            val cg = g.toInt().coerceIn(0, 255)
                            val cb = b.toInt().coerceIn(0, 255)
                            val col = 0xFF000000L or ((cr.toLong() and 0xFF) shl 16) or ((cg.toLong() and 0xFF) shl 8) or (cb.toLong() and 0xFF)
                            selectedColor = col
                            onColorSelected(col)
                        },
                        valueRange = 0f..255f,
                        modifier = Modifier.weight(1f)
                    )
                    Text("${r.toInt()}", modifier = Modifier.width(36.dp), style = MaterialTheme.typography.bodySmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("G", modifier = Modifier.width(20.dp), fontWeight = FontWeight.Bold, color = Color(0xFF0E9F6E))
                    Slider(
                        value = g,
                        onValueChange = {
                            g = it
                            val cr = r.toInt().coerceIn(0, 255)
                            val cg = g.toInt().coerceIn(0, 255)
                            val cb = b.toInt().coerceIn(0, 255)
                            val col = 0xFF000000L or ((cr.toLong() and 0xFF) shl 16) or ((cg.toLong() and 0xFF) shl 8) or (cb.toLong() and 0xFF)
                            selectedColor = col
                            onColorSelected(col)
                        },
                        valueRange = 0f..255f,
                        modifier = Modifier.weight(1f)
                    )
                    Text("${g.toInt()}", modifier = Modifier.width(36.dp), style = MaterialTheme.typography.bodySmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("B", modifier = Modifier.width(20.dp), fontWeight = FontWeight.Bold, color = Color(0xFF1A56DB))
                    Slider(
                        value = b,
                        onValueChange = {
                            b = it
                            val cr = r.toInt().coerceIn(0, 255)
                            val cg = g.toInt().coerceIn(0, 255)
                            val cb = b.toInt().coerceIn(0, 255)
                            val col = 0xFF000000L or ((cr.toLong() and 0xFF) shl 16) or ((cg.toLong() and 0xFF) shl 8) or (cb.toLong() and 0xFF)
                            selectedColor = col
                            onColorSelected(col)
                        },
                        valueRange = 0f..255f,
                        modifier = Modifier.weight(1f)
                    )
                    Text("${b.toInt()}", modifier = Modifier.width(36.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

/**
 * FreeNotes Instant Size Dialog with Clean Circle Dot Preview
 */
@Composable
private fun FreeNotesInstantSizeDialog(
    tool: EditorTool,
    currentSize: Float,
    onDismiss: () -> Unit,
    onSizeSelected: (Float) -> Unit
) {
    val (minVal, maxVal) = when (tool) {
        EditorTool.PEN -> 1f to 30f
        EditorTool.HIGHLIGHTER -> 6f to 60f
        EditorTool.ERASER -> 20f to 300f
        EditorTool.LASER -> 1f to 20f
        EditorTool.LASSO -> 1f to 10f
    }

    var sizeValue by remember { mutableFloatStateOf(currentSize.coerceIn(minVal, maxVal)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("${tool.label} Size", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Text("✕", fontSize = 14.sp)
                }
            }
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    val previewDp = (sizeValue / 3.2f).coerceIn(4f, 75f)
                    Box(
                        modifier = Modifier
                            .size(previewDp.dp)
                            .clip(CircleShape)
                            .background(
                                if (tool == EditorTool.ERASER) Color(0xFFEF5350) else MaterialTheme.colorScheme.primary
                            )
                    )
                }

                Slider(
                    value = sizeValue,
                    onValueChange = {
                        sizeValue = it
                        onSizeSelected(it)
                    },
                    valueRange = minVal..maxVal
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

/**
 * Laser Pen Settings Dialog (Matching User Reference Screenshot)
 */
@Composable
private fun LaserPenSettingsDialog(
    currentColor: Long,
    hasTail: Boolean,
    thickness: Float,
    duration: Float,
    onDismiss: () -> Unit,
    onTailToggle: (Boolean) -> Unit,
    onThicknessChange: (Float) -> Unit,
    onDurationChange: (Float) -> Unit,
    onColorSelect: (Long) -> Unit
) {
    val laserColors = listOf(
        0xFFFF2255L to "Electric Red",
        0xFF00E5FFL to "Neon Cyan",
        0xFF00E676L to "Laser Green",
        0xFFD500F9L to "Vivid Purple",
        0xFFFFD600L to "Neon Yellow",
        0xFFFF6E40L to "Neon Coral",
        0xFF2979FFL to "Sapphire Blue",
        0xFFFFFFFFL to "Bright White"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text("Laser Pen", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Segment: No Tail / Tail
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (!hasTail) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onTailToggle(false) }
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(vertical = 10.dp)
                        ) {
                            Text("•", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            Text("No Tail", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold))
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (hasTail) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onTailToggle(true) }
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(vertical = 10.dp)
                        ) {
                            Text("〰", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text("Tail", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold))
                        }
                    }
                }

                // Thickness row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Thickness", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        IconButton(
                            onClick = { onThicknessChange((thickness - 1f).coerceAtLeast(1f)) },
                            modifier = Modifier.size(26.dp)
                        ) {
                            Text("−", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        Text("${String.format("%.1f", thickness)}", style = MaterialTheme.typography.bodyMedium)
                        IconButton(
                            onClick = { onThicknessChange((thickness + 1f).coerceAtMost(20f)) },
                            modifier = Modifier.size(26.dp)
                        ) {
                            Text("+", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
                Slider(
                    value = thickness,
                    onValueChange = { onThicknessChange(it) },
                    valueRange = 1f..20f
                )

                // Duration row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Duration", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        IconButton(
                            onClick = { onDurationChange((duration - 0.5f).coerceAtLeast(0.5f)) },
                            modifier = Modifier.size(26.dp)
                        ) {
                            Text("−", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        Text("${String.format("%.1f", duration)}s", style = MaterialTheme.typography.bodyMedium)
                        IconButton(
                            onClick = { onDurationChange((duration + 0.5f).coerceAtMost(6.0f)) },
                            modifier = Modifier.size(26.dp)
                        ) {
                            Text("+", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
                Slider(
                    value = duration,
                    onValueChange = { onDurationChange(it) },
                    valueRange = 0.5f..6.0f
                )

                // Color Palette Grid
                Text("Color", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.height(88.dp)
                ) {
                    itemsIndexed(laserColors) { _, (colorLong, _) ->
                        val isSelected = currentColor == colorLong
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(colorLong))
                                .border(
                                    if (isSelected) 3.dp else 1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray.copy(alpha = 0.4f),
                                    CircleShape
                                )
                                .clickable {
                                    onColorSelect(colorLong)
                                }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

/**
 * Add Page Dialog
 */
@Composable
private fun AddPageDialog(
    defaultTemplate: PaperTemplate,
    isDarkPaper: Boolean,
    onDismiss: () -> Unit,
    onAddPage: (PaperTemplate, Boolean) -> Unit
) {
    var selectedTemplate by remember { mutableStateOf(defaultTemplate) }
    var darkPaper by remember { mutableStateOf(isDarkPaper) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Next Page", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Select Paper Template:", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PaperTemplate.entries.forEach { template ->
                        val isSelected = selectedTemplate == template
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedTemplate = template }
                        ) {
                            Text(
                                text = template.label,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                ),
                                modifier = Modifier.padding(vertical = 12.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { darkPaper = !darkPaper }
                        .padding(vertical = 4.dp)
                ) {
                    Text("Dark Paper Mode", style = MaterialTheme.typography.bodyMedium)
                    Text(if (darkPaper) "🌙 ON" else "☀️ OFF", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
            }
        },
        confirmButton = {
            Button(onClick = { onAddPage(selectedTemplate, darkPaper) }) {
                Text("➕ Add Page")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

enum class EditorTool(val label: String, val icon: String) {
    PEN("Pen", "✍️"),
    HIGHLIGHTER("Highlighter", "🖍️"),
    ERASER("Eraser", "🧹"),
    LASSO("Lasso", "➰"),
    LASER("Laser", "🔴");

    fun toStrokeTool(): StrokeTool = when (this) {
        HIGHLIGHTER -> StrokeTool.HIGHLIGHTER
        else -> StrokeTool.PEN
    }
}

enum class PaperTemplate(val label: String) {
    RULED("Standard Ruled"),
    COLLEGE_RULED("College Ruled"),
    GRID("Grid"),
    CORNELL_RULED("Cornell Ruled"),
    CORNELL_GRID("Cornell Grid"),
    SINGLE_COLUMN("Single Column"),
    TWO_COLUMN("Two Column"),
    TWO_COLUMN_LEFT("Two Column (Left)"),
    THREE_COLUMN("Three Column"),
    DIARY("Diary"),
    BLANK("Blank")
}

private data class PaletteColor(val name: String, val argb: Long)

private val CLASSIC_PALETTE = listOf(
    PaletteColor("Onyx Black", 0xFF1C1D1FL),
    PaletteColor("Sapphire Blue", 0xFF1A56DBL),
    PaletteColor("Crimson Red", 0xFFE02424L),
    PaletteColor("Emerald Green", 0xFF0E9F6EL),
    PaletteColor("Amber Orange", 0xFFD97706L),
    PaletteColor("Royal Purple", 0xFF7E3AF2L)
)

private val PASTEL_PALETTE = listOf(
    PaletteColor("Pastel Mint", 0xFF86EFACL),
    PaletteColor("Pastel Sky", 0xFF93C5FDL),
    PaletteColor("Pastel Rose", 0xFFFCA5A5L),
    PaletteColor("Pastel Peach", 0xFFFDBA74L),
    PaletteColor("Pastel Lavender", 0xFFD8B4FEL),
    PaletteColor("Pastel Lemon", 0xFFFDE047L)
)

private val VIBRANT_PALETTE = listOf(
    PaletteColor("Vibrant Cyan", 0xFF06B6D4L),
    PaletteColor("Vibrant Pink", 0xFFEC4899L),
    PaletteColor("Vibrant Lime", 0xFF84CC16L),
    PaletteColor("Vibrant Violet", 0xFF8B5CF6L),
    PaletteColor("Vibrant Amber", 0xFFF59E0BL),
    PaletteColor("Vibrant Blue", 0xFF3B82F6L)
)

private val EARTHY_PALETTE = listOf(
    PaletteColor("Deep Walnut", 0xFF451A03L),
    PaletteColor("Warm Mocha", 0xFF78350FL),
    PaletteColor("Forest Pine", 0xFF14532DL),
    PaletteColor("Deep Navy", 0xFF1E3A8AL),
    PaletteColor("Burgundy", 0xFF831843L),
    PaletteColor("Charcoal", 0xFF374151L)
)

private fun Long.withToolAlpha(tool: EditorTool): Long {
    val alpha = if (tool == EditorTool.HIGHLIGHTER) 0x66 else 0xFF
    return (this and 0x00FFFFFFL) or (alpha.toLong() shl 24)
}
