package com.adityanotes.feature.page.presentation

import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.Matrix
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
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
import com.adityanotes.feature.page.data.StrokeEntity
import com.adityanotes.feature.page.data.StrokePoint
import com.adityanotes.feature.page.data.StrokePointCodec
import com.adityanotes.feature.page.data.StrokeTool
import kotlin.math.sqrt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageEditorScreen(
    pageId: Long,
    viewModel: PageViewModel,
    onBack: () -> Unit
) {
    LaunchedEffect(pageId) {
        viewModel.loadStrokes(pageId)
    }

    val strokes by viewModel.strokes.collectAsStateWithLifecycle()
    val page by viewModel.currentPage.collectAsStateWithLifecycle()

    var selectedToolName by remember { mutableStateOf(EditorTool.PEN.name) }
    var selectedColor by remember { mutableStateOf(COLOR_PRESETS.first().argb) }
    var strokeWidth by remember { mutableStateOf(4f) }
    var zoom by remember { mutableStateOf(1f) }
    var panX by remember { mutableStateOf(0f) }
    var panY by remember { mutableStateOf(0f) }

    val selectedTool = remember(selectedToolName) {
        EditorTool.entries.firstOrNull { it.name == selectedToolName } ?: EditorTool.PEN
    }
    val paperTemplate = remember(page?.paperTemplate) {
        PaperTemplate.entries.firstOrNull { it.name == page?.paperTemplate } ?: PaperTemplate.RULED
    }
    val isDarkPaper = page?.isDarkPaper ?: false
    val effectiveColor = remember(selectedColor, selectedTool) {
        selectedColor.withToolAlpha(selectedTool)
    }

    val effectiveStrokeWidth = remember(selectedTool, strokeWidth) {
        if (selectedTool == EditorTool.HIGHLIGHTER) strokeWidth * 2.5f else strokeWidth
    }

    val penBrushFamily = remember { StockBrushes.pressurePen() }
    val highlighterBrushFamily = remember { StockBrushes.highlighter() }
    val canvasStrokeRenderer = remember { CanvasStrokeRenderer.create() }
    val strokeCache = remember { mutableMapOf<Long, InkStroke?>() }

    val brush = remember(selectedTool, effectiveColor, effectiveStrokeWidth) {
        val family = if (selectedTool == EditorTool.HIGHLIGHTER) highlighterBrushFamily else penBrushFamily
        Brush.createWithColorIntArgb(
            family = family,
            colorIntArgb = effectiveColor.toInt(),
            size = effectiveStrokeWidth,
            epsilon = 0.1f
        )
    }

    val onViewportTransform: (Float, Float, Float, Float, Float) -> Unit =
        { scaleFactor, panDeltaX, panDeltaY, focusX, focusY ->
            val newZoom = (zoom * scaleFactor).coerceIn(0.25f, 5.0f)
            val actualScale = newZoom / zoom
            panX = focusX - (focusX - panX) * actualScale + panDeltaX
            panY = focusY - (focusY - panY) * actualScale + panDeltaY
            zoom = newZoom
        }

    val pageModifier = Modifier
        .fillMaxSize()
        .graphicsLayer {
            transformOrigin = TransformOrigin(0f, 0f)
            scaleX = zoom
            scaleY = zoom
            translationX = panX
            translationY = panY
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(page?.name ?: "Page") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("←")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::undo) {
                        Text("↶")
                    }
                    IconButton(onClick = viewModel::redo) {
                        Text("↷")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            EditorToolbar(
                selectedTool = selectedTool,
                selectedColor = selectedColor,
                strokeWidth = strokeWidth,
                paperTemplate = paperTemplate,
                isDarkPaper = isDarkPaper,
                onToolSelected = { selectedToolName = it.name },
                onColorSelected = { selectedColor = it },
                onWidthSelected = { strokeWidth = it },
                onTemplateSelected = { template ->
                    viewModel.updatePaper(
                        pageId = pageId,
                        paperTemplate = template.name,
                        isDarkPaper = isDarkPaper
                    )
                },
                onDarkPaperToggled = {
                    viewModel.updatePaper(
                        pageId = pageId,
                        paperTemplate = paperTemplate.name,
                        isDarkPaper = !isDarkPaper
                    )
                }
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (isDarkPaper) Color(0xFF101214) else Color(0xFFE7E9EC))
            ) {
                Canvas(modifier = pageModifier) {
                    drawPaperTemplate(
                        template = paperTemplate,
                        isDarkPaper = isDarkPaper
                    )
                    drawIntoCanvas { composeCanvas ->
                        val nativeCanvas = composeCanvas.nativeCanvas
                        val strokeToScreenTransform = Matrix().apply {
                            setScale(zoom, zoom)
                        }
                        strokes.forEach { strokeEntity ->
                            val inkStroke = strokeCache.getOrPut(strokeEntity.id) {
                                buildInkStroke(strokeEntity, penBrushFamily, highlighterBrushFamily)
                            }
                            if (inkStroke != null) {
                                canvasStrokeRenderer.draw(nativeCanvas, inkStroke, strokeToScreenTransform)
                            } else {
                                drawStoredStrokeFallback(strokeEntity)
                            }
                        }
                    }
                }

                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        LowLatencyInkView(
                            context = context,
                            brush = brush,
                            strokeColor = effectiveColor,
                            strokeWidth = effectiveStrokeWidth,
                            initialTool = selectedTool,
                            pageId = pageId,
                            viewModel = viewModel,
                            zoom = zoom,
                            panX = panX,
                            panY = panY,
                            onViewportTransform = onViewportTransform
                        )
                    },
                    update = { view ->
                        view.updateConfiguration(
                            brush = brush,
                            tool = selectedTool,
                            strokeColor = effectiveColor,
                            strokeWidth = effectiveStrokeWidth,
                            zoom = zoom,
                            panX = panX,
                            panY = panY
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun EditorToolbar(
    selectedTool: EditorTool,
    selectedColor: Long,
    strokeWidth: Float,
    paperTemplate: PaperTemplate,
    isDarkPaper: Boolean,
    onToolSelected: (EditorTool) -> Unit,
    onColorSelected: (Long) -> Unit,
    onWidthSelected: (Float) -> Unit,
    onTemplateSelected: (PaperTemplate) -> Unit,
    onDarkPaperToggled: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        EditorTool.entries.forEach { tool ->
            FilterChip(
                selected = selectedTool == tool,
                onClick = { onToolSelected(tool) },
                label = { Text(tool.label) }
            )
        }

        STROKE_WIDTHS.forEach { width ->
            FilterChip(
                selected = strokeWidth == width,
                onClick = { onWidthSelected(width) },
                label = { Text("${width.toInt()}px") }
            )
        }

        COLOR_PRESETS.forEach { color ->
            FilterChip(
                selected = selectedColor == color.argb,
                onClick = { onColorSelected(color.argb) },
                label = { Text(color.label) }
            )
        }

        PaperTemplate.entries.forEach { template ->
            FilterChip(
                selected = paperTemplate == template,
                onClick = { onTemplateSelected(template) },
                label = { Text(template.label) }
            )
        }

        FilterChip(
            selected = isDarkPaper,
            onClick = onDarkPaperToggled,
            label = { Text("Dark paper") }
        )
    }
}

private fun DrawScope.drawPaperTemplate(
    template: PaperTemplate,
    isDarkPaper: Boolean
) {
    val paperColor = if (isDarkPaper) Color(0xFF1B1E22) else Color(0xFFFEFEFC)
    val lineColor = if (isDarkPaper) Color(0xFF3A4652) else Color(0xFFD8E2EE)
    val marginColor = if (isDarkPaper) Color(0xFF6E3F48) else Color(0xFFFFB5BC)

    drawRect(paperColor)
    when (template) {
        PaperTemplate.BLANK -> Unit
        PaperTemplate.RULED -> {
            var y = 52f
            while (y < size.height) {
                drawLine(lineColor, start = androidx.compose.ui.geometry.Offset(0f, y), end = androidx.compose.ui.geometry.Offset(size.width, y), strokeWidth = 1f)
                y += 36f
            }
            drawLine(marginColor, start = androidx.compose.ui.geometry.Offset(56f, 0f), end = androidx.compose.ui.geometry.Offset(56f, size.height), strokeWidth = 1.5f)
        }
        PaperTemplate.GRID -> {
            var x = 0f
            while (x < size.width) {
                drawLine(lineColor, start = androidx.compose.ui.geometry.Offset(x, 0f), end = androidx.compose.ui.geometry.Offset(x, size.height), strokeWidth = 1f)
                x += 32f
            }
            var y = 0f
            while (y < size.height) {
                drawLine(lineColor, start = androidx.compose.ui.geometry.Offset(0f, y), end = androidx.compose.ui.geometry.Offset(size.width, y), strokeWidth = 1f)
                y += 32f
            }
        }
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
        epsilon = 0.1f
    )

    val inputBatch = MutableStrokeInputBatch()
    var lastTime = -1L
    for (pt in points) {
        val time = pt.elapsedMillis.toLong().coerceAtLeast(lastTime + 1L)
        lastTime = time
        val pressure = if (pt.pressure in 0.01f..1.0f) pt.pressure else StrokeInput.NO_PRESSURE
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
            // Ignore duplicate or invalid points
        }
    }

    if (inputBatch.isEmpty()) return null
    return runCatching { InkStroke(brush, inputBatch) }.getOrNull()
}

private fun DrawScope.drawStoredStrokeFallback(stroke: StrokeEntity) {
    val points = StrokePointCodec.decode(stroke.pointData)
    if (points.isEmpty()) {
        return
    }

    val color = Color(stroke.color.toInt())

    if (points.size == 1) {
        drawCircle(
            color = color,
            radius = stroke.strokeWidth / 2f,
            center = androidx.compose.ui.geometry.Offset(points.first().x, points.first().y)
        )
        return
    }

    points.zipWithNext().forEach { (start, end) ->
        drawLine(
            color = color,
            start = androidx.compose.ui.geometry.Offset(start.x, start.y),
            end = androidx.compose.ui.geometry.Offset(end.x, end.y),
            strokeWidth = stroke.strokeWidth,
            cap = StrokeCap.Round
        )
    }
}

private class LowLatencyInkView(
    context: Context,
    private var brush: Brush,
    private var strokeColor: Long,
    private var strokeWidth: Float,
    initialTool: EditorTool,
    private val pageId: Long,
    private val viewModel: PageViewModel,
    private var zoom: Float,
    private var panX: Float,
    private var panY: Float,
    private val onViewportTransform: (Float, Float, Float, Float, Float) -> Unit
) : FrameLayout(context) {

    private val inkView = InProgressStrokesView(context)
    private val predictor = MotionEventPredictor.newInstance(this)
    private val identityMatrix = Matrix()
    private val currentStrokePoints = ArrayList<StrokePoint>()

    private var selectedTool = initialTool
    private var activeTool: EditorTool? = null
    private var activePointerId: Int? = null
    private var strokeStartEventTime = 0L
    private var isStylusActive = false
    private var lastPanX: Float? = null
    private var lastPanY: Float? = null
    private var lastPinchDistance: Float? = null

    private val finishedStrokesListener = object : InProgressStrokesFinishedListener {
        override fun onStrokesFinished(strokes: Map<InProgressStrokeId, InkStroke>) {
            /* The Room-backed Compose canvas owns all completed strokes. */
            inkView.removeFinishedStrokes(strokes.keys)
        }
    }

    init {
        setBackgroundColor(AndroidColor.TRANSPARENT)
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
        strokeColor: Long,
        strokeWidth: Float,
        zoom: Float,
        panX: Float,
        panY: Float
    ) {
        this.brush = brush
        selectedTool = tool
        this.strokeColor = strokeColor
        this.strokeWidth = strokeWidth
        this.zoom = zoom
        this.panX = panX
        this.panY = panY
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
                    // Single finger touch: start drawing if no stylus is active
                    if (!isStylusActive) {
                        beginStroke(event, actionIndex)
                    }
                }
                return true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                if (isStylusEvent && activePointerId == null) {
                    isStylusActive = true
                    beginStroke(event, actionIndex)
                } else if (!isStylusActive) {
                    // Two fingers touching: switch from drawing to pinch-zoom / pan
                    if (activePointerId != null) {
                        cancelActiveStroke()
                    }
                    initPinchGesture(event)
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (activePointerId != null) {
                    updateStroke(event)
                } else if (!isStylusActive && event.pointerCount >= 2) {
                    handlePinchGesture(event)
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
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (activePointerId != null) {
                    finishStroke(event)
                }
                isStylusActive = false
                resetViewportGesture()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                cancelActiveStroke()
                isStylusActive = false
                resetViewportGesture()
                return true
            }
        }

        return true
    }

    private fun beginStroke(event: MotionEvent, pointerIndex: Int) {
        requestUnbufferedDispatch(event)
        activePointerId = event.getPointerId(pointerIndex)
        activeTool = if (event.getToolType(pointerIndex) == MotionEvent.TOOL_TYPE_ERASER) {
            EditorTool.ERASER
        } else {
            selectedTool
        }
        strokeStartEventTime = event.eventTime
        currentStrokePoints.clear()
        addRealPoints(event)

        if (activeTool != EditorTool.ERASER) {
            val motionToWorld = Matrix().apply {
                postTranslate(-panX, -panY)
                postScale(1f / zoom, 1f / zoom)
            }
            predictor.record(event)
            inkView.startStroke(
                event = event,
                pointerId = activePointerId!!,
                brush = brush,
                strokeToWorldTransform = identityMatrix,
                motionEventToWorldTransform = motionToWorld
            )
        }
    }

    private fun updateStroke(event: MotionEvent) {
        val pointerId = activePointerId ?: return
        if (event.findPointerIndex(pointerId) < 0) {
            return
        }

        addRealPoints(event)
        if (activeTool == EditorTool.ERASER) {
            return
        }

        predictor.record(event)
        val prediction = predictor.predict()
        inkView.addToStroke(
            event = event,
            pointerId = pointerId,
            prediction = prediction
        )
        prediction?.recycle()
    }

    private fun finishStroke(event: MotionEvent) {
        val pointerId = activePointerId ?: return
        addRealPoints(event)

        when (activeTool) {
            EditorTool.ERASER -> viewModel.eraseStrokes(
                pageId = pageId,
                eraserPoints = currentStrokePoints.toList(),
                eraserWidth = ERASER_WIDTH / zoom
            )

            EditorTool.PEN,
            EditorTool.HIGHLIGHTER -> {
                predictor.record(event)
                inkView.finishStroke(event, pointerId)
                viewModel.addStroke(
                    pageId = pageId,
                    points = currentStrokePoints.toList(),
                    color = strokeColor,
                    strokeWidth = strokeWidth,
                    tool = activeTool!!.toStrokeTool()
                )
            }

            null -> Unit
        }

        activePointerId = null
        activeTool = null
        currentStrokePoints.clear()
    }

    private fun cancelActiveStroke() {
        if (activeTool != EditorTool.ERASER) {
            inkView.cancelUnfinishedStrokes()
        }
        activePointerId = null
        activeTool = null
        currentStrokePoints.clear()
    }

    private fun addRealPoints(event: MotionEvent) {
        val pointerId = activePointerId ?: return
        val pointerIndex = event.findPointerIndex(pointerId)
        if (pointerIndex < 0) {
            return
        }

        for (historyIndex in 0 until event.historySize) {
            val screenX = event.getHistoricalX(pointerIndex, historyIndex)
            val screenY = event.getHistoricalY(pointerIndex, historyIndex)
            val worldX = (screenX - panX) / zoom
            val worldY = (screenY - panY) / zoom
            currentStrokePoints += StrokePoint(
                x = worldX,
                y = worldY,
                pressure = event.getHistoricalPressure(pointerIndex, historyIndex),
                elapsedMillis = (event.getHistoricalEventTime(historyIndex) - strokeStartEventTime)
                    .coerceAtLeast(0L)
                    .coerceAtMost(Int.MAX_VALUE.toLong())
                    .toInt()
            )
        }

        val screenX = event.getX(pointerIndex)
        val screenY = event.getY(pointerIndex)
        val worldX = (screenX - panX) / zoom
        val worldY = (screenY - panY) / zoom
        currentStrokePoints += StrokePoint(
            x = worldX,
            y = worldY,
            pressure = event.getPressure(pointerIndex),
            elapsedMillis = (event.eventTime - strokeStartEventTime)
                .coerceAtLeast(0L)
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt()
        )
    }

    private fun initPinchGesture(event: MotionEvent) {
        if (event.pointerCount >= 2) {
            val focusX = (event.getX(0) + event.getX(1)) / 2f
            val focusY = (event.getY(0) + event.getY(1)) / 2f
            lastPanX = focusX
            lastPanY = focusY
            lastPinchDistance = distanceBetween(
                event.getX(0), event.getY(0), event.getX(1), event.getY(1)
            )
        }
    }

    private fun handlePinchGesture(event: MotionEvent) {
        if (event.pointerCount >= 2) {
            val focusX = (event.getX(0) + event.getX(1)) / 2f
            val focusY = (event.getY(0) + event.getY(1)) / 2f
            val distance = distanceBetween(
                event.getX(0), event.getY(0), event.getX(1), event.getY(1)
            )
            val prevDist = lastPinchDistance
            val prevPanX = lastPanX
            val prevPanY = lastPanY

            if (prevDist != null && prevDist > 0f && prevPanX != null && prevPanY != null) {
                val scaleFactor = distance / prevDist
                val panDeltaX = focusX - prevPanX
                val panDeltaY = focusY - prevPanY
                onViewportTransform(scaleFactor, panDeltaX, panDeltaY, focusX, focusY)
            }
            lastPinchDistance = distance
            lastPanX = focusX
            lastPanY = focusY
        }
    }

    private fun resetViewportGesture() {
        lastPanX = null
        lastPanY = null
        lastPinchDistance = null
    }

    override fun onDetachedFromWindow() {
        inkView.removeFinishedStrokesListener(finishedStrokesListener)
        cancelActiveStroke()
        super.onDetachedFromWindow()
    }
}

private enum class EditorTool(val label: String) {
    PEN("Pen"),
    HIGHLIGHTER("Highlight"),
    ERASER("Eraser");

    fun toStrokeTool(): StrokeTool = when (this) {
        PEN -> StrokeTool.PEN
        HIGHLIGHTER -> StrokeTool.HIGHLIGHTER
        ERASER -> error("Eraser strokes are not persisted")
    }
}

private enum class PaperTemplate(val label: String) {
    BLANK("Blank"),
    RULED("Ruled"),
    GRID("Grid")
}

private data class InkColorPreset(
    val label: String,
    val argb: Long
)

private fun Long.withToolAlpha(tool: EditorTool): Long =
    if (tool == EditorTool.HIGHLIGHTER) {
        (this and 0x00FFFFFFL) or 0x66000000L
    } else {
        (this and 0x00FFFFFFL) or 0xFF000000L
    }

private fun distanceBetween(
    firstX: Float,
    firstY: Float,
    secondX: Float,
    secondY: Float
): Float {
    val x = secondX - firstX
    val y = secondY - firstY
    return sqrt(x * x + y * y)
}

private val COLOR_PRESETS = listOf(
    InkColorPreset("Black", 0xFF1A1A1AL),
    InkColorPreset("Blue", 0xFF1F5FBFL),
    InkColorPreset("Red", 0xFFC9362BL),
    InkColorPreset("Green", 0xFF2C7A4BL),
    InkColorPreset("Yellow", 0xFFF0B429L)
)
private val STROKE_WIDTHS = listOf(2f, 4f, 8f)
private const val ERASER_WIDTH = 28f

