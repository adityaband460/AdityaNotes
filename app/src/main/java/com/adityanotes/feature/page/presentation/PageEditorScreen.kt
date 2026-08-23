package com.adityanotes.feature.page.presentation

import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.Matrix
import android.graphics.Paint
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
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

    var selectedTool by remember { mutableStateOf(EditorTool.PEN) }
    var strokeWidth by remember { mutableStateOf(4f) }
    var zoom by remember { mutableStateOf(1f) }
    var panX by remember { mutableStateOf(0f) }
    var panY by remember { mutableStateOf(0f) }
    var fingerDrawingEnabled by remember { mutableStateOf(true) }

    // 3 Quick Color Slots (GoodNotes style)
    val quickColors = remember {
        mutableStateListOf(
            COLOR_PRESETS[0].argb, // Black
            COLOR_PRESETS[1].argb, // Blue
            COLOR_PRESETS[2].argb  // Red
        )
    }
    var activeColorSlotIndex by remember { mutableStateOf(0) }
    val selectedColor = quickColors.getOrElse(activeColorSlotIndex) { quickColors.first() }

    val paperTemplate = remember(page?.paperTemplate) {
        PaperTemplate.entries.firstOrNull { it.name == page?.paperTemplate } ?: PaperTemplate.RULED
    }
    val isDarkPaper = page?.isDarkPaper ?: false

    val effectiveColor = remember(selectedColor, selectedTool) {
        selectedColor.withToolAlpha(selectedTool)
    }

    val effectiveStrokeWidth = remember(selectedTool, strokeWidth) {
        if (selectedTool == EditorTool.HIGHLIGHTER) strokeWidth * 3.5f else strokeWidth
    }

    val penBrushFamily = remember { StockBrushes.pressurePen() }
    val highlighterBrushFamily = remember { StockBrushes.highlighter() }
    val canvasStrokeRenderer = remember { CanvasStrokeRenderer.create() }
    val strokeCache = remember { mutableMapOf<Long, InkStroke?>() }

    // Optimistic strokes for instantaneous, zero-flicker dry layer handoff
    val optimisticStrokes = remember { mutableStateListOf<StrokeEntity>() }

    // Reconcile optimistic strokes when DB strokes update
    LaunchedEffect(strokes) {
        if (optimisticStrokes.isNotEmpty()) {
            optimisticStrokes.clear()
        }
    }

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
            GoodNotesTopBar(
                title = page?.name ?: "Page",
                zoom = zoom,
                paperTemplate = paperTemplate,
                isDarkPaper = isDarkPaper,
                fingerDrawingEnabled = fingerDrawingEnabled,
                onBack = onBack,
                onUndo = viewModel::undo,
                onRedo = viewModel::redo,
                onResetZoom = {
                    zoom = 1f
                    panX = 0f
                    panY = 0f
                },
                onToggleFingerDrawing = {
                    fingerDrawingEnabled = !fingerDrawingEnabled
                },
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
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // GoodNotes Docked/Floating Segmented Tool Bar
            GoodNotesToolbar(
                selectedTool = selectedTool,
                quickColors = quickColors,
                activeColorSlotIndex = activeColorSlotIndex,
                strokeWidth = strokeWidth,
                onToolSelected = { selectedTool = it },
                onSelectColorSlot = { activeColorSlotIndex = it },
                onColorChangedForSlot = { slotIdx, newColor ->
                    quickColors[slotIdx] = newColor
                    activeColorSlotIndex = slotIdx
                },
                onWidthSelected = { strokeWidth = it }
            )

            // Drawing Area with clipped bounds so zoom/pan cannot overflow over the toolbar
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .background(if (isDarkPaper) Color(0xFF121417) else Color(0xFFE8ECF2))
            ) {
                Canvas(modifier = pageModifier) {
                    drawPaperSheet(
                        template = paperTemplate,
                        isDarkPaper = isDarkPaper
                    )
                    drawIntoCanvas { composeCanvas ->
                        val nativeCanvas = composeCanvas.nativeCanvas
                        val strokeToScreenTransform = Matrix().apply {
                            setScale(zoom, zoom)
                        }

                        // Draw committed DB strokes
                        for (strokeEntity in strokes) {
                            val inkStroke = strokeCache.getOrPut(strokeEntity.id) {
                                buildInkStroke(strokeEntity, penBrushFamily, highlighterBrushFamily)
                            }
                            if (inkStroke != null) {
                                canvasStrokeRenderer.draw(nativeCanvas, inkStroke, strokeToScreenTransform)
                            } else {
                                drawStoredStrokeFallback(strokeEntity)
                            }
                        }

                        // Draw optimistic pending strokes
                        for (strokeEntity in optimisticStrokes) {
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
                            fingerDrawingEnabled = fingerDrawingEnabled,
                            onViewportTransform = onViewportTransform,
                            onOptimisticStroke = { optimisticStroke, prebuiltInkStroke ->
                                strokeCache[optimisticStroke.id] = prebuiltInkStroke
                                optimisticStrokes.add(optimisticStroke)
                            }
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
                            panY = panY,
                            fingerDrawingEnabled = fingerDrawingEnabled
                        )
                    }
                )
            }
        }
    }
}

/**
 * GoodNotes 6-style Top Navigation Header
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoodNotesTopBar(
    title: String,
    zoom: Float,
    paperTemplate: PaperTemplate,
    isDarkPaper: Boolean,
    fingerDrawingEnabled: Boolean,
    onBack: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onResetZoom: () -> Unit,
    onToggleFingerDrawing: () -> Unit,
    onTemplateSelected: (PaperTemplate) -> Unit,
    onDarkPaperToggled: () -> Unit
) {
    var templateMenuExpanded by remember { mutableStateOf(false) }

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
                        fontSize = 17.sp
                    )
                )
                Text(
                    text = "AdityaNotes",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
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
            // Undo Button
            IconButton(onClick = onUndo) {
                Text("↶", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }

            // Redo Button
            IconButton(onClick = onRedo) {
                Text("↷", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Zoom Indicator Badge (Tap to reset to 100%)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.clickable(onClick = onResetZoom)
            ) {
                Text(
                    text = "${(zoom * 100).toInt()}%",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Stylus Only vs Finger Mode Toggle
            IconButton(onClick = onToggleFingerDrawing) {
                Text(
                    text = if (fingerDrawingEnabled) "👆" else "✍️",
                    fontSize = 18.sp
                )
            }

            // Paper Template Menu Button
            Box {
                IconButton(onClick = { templateMenuExpanded = true }) {
                    Text("📄", fontSize = 18.sp)
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
 * GoodNotes 6 Floating Segmented Toolbar
 */
@Composable
private fun GoodNotesToolbar(
    selectedTool: EditorTool,
    quickColors: List<Long>,
    activeColorSlotIndex: Int,
    strokeWidth: Float,
    onToolSelected: (EditorTool) -> Unit,
    onSelectColorSlot: (Int) -> Unit,
    onColorChangedForSlot: (Int, Long) -> Unit,
    onWidthSelected: (Float) -> Unit
) {
    var paletteExpanded by remember { mutableStateOf(false) }

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
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Tool Pills: Pen, Highlighter, Eraser
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
                            modifier = Modifier.clickable { onToolSelected(tool) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(tool.icon, fontSize = 14.sp)
                                Text(
                                    text = tool.label,
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

            ToolbarDivider()

            // 3 Quick Color Slots (GoodNotes style)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                quickColors.forEachIndexed { index, colorValue ->
                    val isSelected = activeColorSlotIndex == index && selectedTool != EditorTool.ERASER
                    val color = Color(colorValue.toInt())

                    Box(
                        modifier = Modifier
                            .size(32.dp)
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
                                onSelectColorSlot(index)
                            }
                    )
                }

                // Palette Popover Button
                Box {
                    IconButton(
                        onClick = { paletteExpanded = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Text("🎨", fontSize = 16.sp)
                    }

                    DropdownMenu(
                        expanded = paletteExpanded,
                        onDismissRequest = { paletteExpanded = false }
                    ) {
                        Text(
                            text = "Preset Colors",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                        COLOR_PRESETS.forEach { preset ->
                            DropdownMenuItem(
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(Color(preset.argb.toInt()))
                                            .border(1.dp, Color.Gray.copy(alpha = 0.4f), CircleShape)
                                    )
                                },
                                text = { Text(preset.label) },
                                onClick = {
                                    onColorChangedForSlot(activeColorSlotIndex, preset.argb)
                                    paletteExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            ToolbarDivider()

            // 3 Quick Stroke Thickness Slots (Fine, Medium, Bold) with visual dot previews
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                STROKE_WIDTH_PRESETS.forEach { preset ->
                    val isSelected = strokeWidth == preset.width
                    Surface(
                        shape = CircleShape,
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier
                            .size(32.dp)
                            .clickable { onWidthSelected(preset.width) }
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(preset.dotSize.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                            )
                        }
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
            .height(24.dp)
            .width(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    )
}

/**
 * Realistic Notebook Paper Presentation
 */
private fun DrawScope.drawPaperSheet(
    template: PaperTemplate,
    isDarkPaper: Boolean
) {
    val paperColor = if (isDarkPaper) Color(0xFF1B1E22) else Color(0xFFFEFEFC)
    val lineColor = if (isDarkPaper) Color(0xFF323B45) else Color(0xFFDCE4EE)
    val marginColor = if (isDarkPaper) Color(0xFF6B3C44) else Color(0xFFFFB5BC)

    // Solid Page background
    drawRect(paperColor)

    // Paper template patterns
    when (template) {
        PaperTemplate.BLANK -> Unit
        PaperTemplate.RULED -> {
            var y = 56f
            while (y < size.height) {
                drawLine(lineColor, start = Offset(0f, y), end = Offset(size.width, y), strokeWidth = 1f)
                y += 36f
            }
            // Vertical margin guide
            drawLine(marginColor, start = Offset(60f, 0f), end = Offset(60f, size.height), strokeWidth = 1.5f)
        }
        PaperTemplate.GRID -> {
            var x = 0f
            while (x < size.width) {
                drawLine(lineColor, start = Offset(x, 0f), end = Offset(x, size.height), strokeWidth = 1f)
                x += 32f
            }
            var y = 0f
            while (y < size.height) {
                drawLine(lineColor, start = Offset(0f, y), end = Offset(size.width, y), strokeWidth = 1f)
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

/**
 * Low-latency drawing surface with hardware front-buffer inking, live eraser indicator, and smooth gesture routing.
 */
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
    private var fingerDrawingEnabled: Boolean,
    private val onViewportTransform: (Float, Float, Float, Float, Float) -> Unit,
    private val onOptimisticStroke: (StrokeEntity, InkStroke?) -> Unit
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

    // Live Eraser Circle Cursor Indicator
    private var eraserScreenX: Float? = null
    private var eraserScreenY: Float? = null
    private var showEraserCircle = false
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

    private val finishedStrokesListener = object : InProgressStrokesFinishedListener {
        override fun onStrokesFinished(strokes: Map<InProgressStrokeId, InkStroke>) {
            /* The Room-backed and optimistic Compose canvas owns completed strokes. */
            inkView.removeFinishedStrokes(strokes.keys)
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
        strokeColor: Long,
        strokeWidth: Float,
        zoom: Float,
        panX: Float,
        panY: Float,
        fingerDrawingEnabled: Boolean
    ) {
        this.brush = brush
        selectedTool = tool
        this.strokeColor = strokeColor
        this.strokeWidth = strokeWidth
        this.zoom = zoom
        this.panX = panX
        this.panY = panY
        this.fingerDrawingEnabled = fingerDrawingEnabled
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)
        // Draw live circular eraser cursor
        val ex = eraserScreenX
        val ey = eraserScreenY
        if (showEraserCircle && ex != null && ey != null) {
            val radius = ERASER_WIDTH / 2f
            canvas.drawCircle(ex, ey, radius, eraserFillPaint)
            canvas.drawCircle(ex, ey, radius, eraserStrokePaint)
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
                            // Stylus Only Mode: 1-finger drag pans the viewport
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
                    // Two fingers touching: cancel 1-finger stroke & switch to pan/zoom
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
                    if (activeTool == EditorTool.ERASER) {
                        val pointerIndex = event.findPointerIndex(activePointerId!!)
                        if (pointerIndex >= 0) {
                            eraserScreenX = event.getX(pointerIndex)
                            eraserScreenY = event.getY(pointerIndex)
                            showEraserCircle = true
                            invalidate()
                        }
                    }
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

        if (activeTool == EditorTool.ERASER) {
            eraserScreenX = event.getX(pointerIndex)
            eraserScreenY = event.getY(pointerIndex)
            showEraserCircle = true
            invalidate()
        } else {
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
            EditorTool.ERASER -> {
                viewModel.eraseStrokes(
                    pageId = pageId,
                    eraserPoints = currentStrokePoints.toList(),
                    eraserWidth = ERASER_WIDTH / zoom
                )
            }

            EditorTool.PEN,
            EditorTool.HIGHLIGHTER -> {
                predictor.record(event)
                inkView.finishStroke(event, pointerId)

                val pointsCopy = currentStrokePoints.toList()
                val toolEnum = activeTool!!.toStrokeTool()
                val tempId = -System.nanoTime()

                // Generate optimistic stroke entity for 0ms handoff
                val optimisticEntity = StrokeEntity(
                    id = tempId,
                    pageId = pageId,
                    pointData = StrokePointCodec.encode(pointsCopy),
                    color = strokeColor,
                    strokeWidth = strokeWidth,
                    tool = toolEnum.name
                )
                onOptimisticStroke(optimisticEntity, null)

                // Persist asynchronously in Room DB
                viewModel.addStroke(
                    pageId = pageId,
                    points = pointsCopy,
                    color = strokeColor,
                    strokeWidth = strokeWidth,
                    tool = toolEnum
                )
            }

            null -> Unit
        }

        activePointerId = null
        activeTool = null
        currentStrokePoints.clear()
        hideEraserCursor()
    }

    private fun cancelActiveStroke() {
        if (activeTool != EditorTool.ERASER) {
            inkView.cancelUnfinishedStrokes()
        }
        activePointerId = null
        activeTool = null
        currentStrokePoints.clear()
        hideEraserCursor()
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

    private fun initSingleFingerPan(event: MotionEvent) {
        lastPanX = event.x
        lastPanY = event.y
    }

    private fun handleSingleFingerPan(event: MotionEvent) {
        val prevX = lastPanX
        val prevY = lastPanY
        val currX = event.x
        val currY = event.y
        if (prevX != null && prevY != null) {
            val deltaX = currX - prevX
            val deltaY = currY - prevY
            onViewportTransform(1f, deltaX, deltaY, currX, currY)
        }
        lastPanX = currX
        lastPanY = currY
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

private enum class EditorTool(val label: String, val icon: String) {
    PEN("Pen", "🖊️"),
    HIGHLIGHTER("Highlighter", "🖍️"),
    ERASER("Eraser", "🧹");

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

private data class StrokeThicknessPreset(
    val width: Float,
    val dotSize: Float
)

private val STROKE_WIDTH_PRESETS = listOf(
    StrokeThicknessPreset(2f, 4f),
    StrokeThicknessPreset(4f, 7f),
    StrokeThicknessPreset(8f, 11f)
)

private data class InkColorPreset(
    val label: String,
    val argb: Long
)

private fun Long.withToolAlpha(tool: EditorTool): Long =
    if (tool == EditorTool.HIGHLIGHTER) {
        (this and 0x00FFFFFFL) or 0x55000000L
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

// GoodNotes 6 Curated Color Palette
private val COLOR_PRESETS = listOf(
    InkColorPreset("Onyx Black", 0xFF1C1D1FL),
    InkColorPreset("Sapphire Blue", 0xFF1A56DBL),
    InkColorPreset("Crimson Red", 0xFFE02424L),
    InkColorPreset("Emerald Green", 0xFF0E9F6EL),
    InkColorPreset("Amber Gold", 0xFFE3A008L),
    InkColorPreset("Royal Purple", 0xFF9061F9L),
    InkColorPreset("Coral Orange", 0xFFFF5A1FL),
    InkColorPreset("Slate Teal", 0xFF319795L)
)
private const val ERASER_WIDTH = 32f

