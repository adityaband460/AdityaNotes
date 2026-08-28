package com.adityanotes.feature.page.data

import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import androidx.ink.brush.Brush
import androidx.ink.brush.BrushFamily
import androidx.ink.brush.InputToolType
import androidx.ink.brush.StockBrushes
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.Stroke as InkStroke
import androidx.ink.strokes.StrokeInput
import com.adityanotes.core.database.entity.NotebookEntity
import com.adityanotes.core.database.entity.PageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object PdfExporter {

    private const val PAGE_WIDTH = 1200
    private const val PAGE_HEIGHT = 1600

    suspend fun exportPdf(
        context: Context,
        notebook: NotebookEntity,
        pages: List<PageEntity>,
        pageStrokesProvider: suspend (Long) -> List<StrokeEntity>,
        targetPageId: Long? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val pagesToExport = if (targetPageId != null) {
                pages.filter { it.id == targetPageId }
            } else {
                pages
            }

            if (pagesToExport.isEmpty()) {
                error("No pages found to export")
            }

            val pdfDocument = PdfDocument()
            val canvasStrokeRenderer = CanvasStrokeRenderer.create()
            val penBrushFamily = StockBrushes.marker()
            val highlighterBrushFamily = StockBrushes.highlighter()

            pagesToExport.forEachIndexed { index, pageEntity ->
                val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, index + 1).create()
                val pdfPage = pdfDocument.startPage(pageInfo)
                val canvas = pdfPage.canvas

                // 1. Draw Paper Background
                val isDark = pageEntity.isDarkPaper
                val bgColor = if (isDark) AndroidColor.rgb(0x1B, 0x1E, 0x22) else AndroidColor.rgb(0xFE, 0xFE, 0xFC)
                val lineCol = if (isDark) AndroidColor.rgb(0x32, 0x3B, 0x45) else AndroidColor.rgb(0xDC, 0xE4, 0xEE)
                val marginCol = if (isDark) AndroidColor.rgb(0x6B, 0x3C, 0x44) else AndroidColor.rgb(0xFF, 0xB5, 0xBC)

                canvas.drawColor(bgColor)

                val linePaint = Paint().apply {
                    color = lineCol
                    strokeWidth = 1.5f
                    style = Paint.Style.STROKE
                    isAntiAlias = true
                }
                val marginPaint = Paint().apply {
                    color = marginCol
                    strokeWidth = 2.0f
                    style = Paint.Style.STROKE
                    isAntiAlias = true
                }

                // 2. Draw Paper Template Lines
                when (pageEntity.paperTemplate.uppercase()) {
                    "RULED" -> {
                        var y = 60f
                        while (y < PAGE_HEIGHT - 20f) {
                            canvas.drawLine(0f, y, PAGE_WIDTH.toFloat(), y, linePaint)
                            y += 36f
                        }
                        canvas.drawLine(70f, 0f, 70f, PAGE_HEIGHT.toFloat(), marginPaint)
                    }
                    "COLLEGE_RULED" -> {
                        var y = 56f
                        while (y < PAGE_HEIGHT - 20f) {
                            canvas.drawLine(0f, y, PAGE_WIDTH.toFloat(), y, linePaint)
                            y += 26f
                        }
                        canvas.drawLine(70f, 0f, 70f, PAGE_HEIGHT.toFloat(), marginPaint)
                    }
                    "GRID" -> {
                        var x = 0f
                        while (x < PAGE_WIDTH) {
                            canvas.drawLine(x, 0f, x, PAGE_HEIGHT.toFloat(), linePaint)
                            x += 30f
                        }
                        var y = 0f
                        while (y < PAGE_HEIGHT) {
                            canvas.drawLine(0f, y, PAGE_WIDTH.toFloat(), y, linePaint)
                            y += 30f
                        }
                    }
                    "CORNELL_RULED" -> {
                        canvas.drawLine(0f, 80f, PAGE_WIDTH.toFloat(), 80f, marginPaint)
                        val summaryTopY = PAGE_HEIGHT - 180f
                        canvas.drawLine(0f, summaryTopY.toFloat(), PAGE_WIDTH.toFloat(), summaryTopY.toFloat(), marginPaint)
                        val cueX = 220f
                        canvas.drawLine(cueX, 80f, cueX, summaryTopY.toFloat(), marginPaint)

                        var y = 112f
                        while (y < summaryTopY) {
                            canvas.drawLine(cueX, y, PAGE_WIDTH.toFloat(), y, linePaint)
                            y += 32f
                        }
                        var sy = summaryTopY + 36f
                        while (sy < PAGE_HEIGHT - 20f) {
                            canvas.drawLine(0f, sy, PAGE_WIDTH.toFloat(), sy, linePaint)
                            sy += 32f
                        }
                    }
                    "CORNELL_GRID" -> {
                        canvas.drawLine(0f, 80f, PAGE_WIDTH.toFloat(), 80f, marginPaint)
                        val summaryTopY = PAGE_HEIGHT - 180f
                        canvas.drawLine(0f, summaryTopY.toFloat(), PAGE_WIDTH.toFloat(), summaryTopY.toFloat(), marginPaint)
                        val cueX = 220f
                        canvas.drawLine(cueX, 80f, cueX, summaryTopY.toFloat(), marginPaint)

                        var x = cueX + 28f
                        while (x < PAGE_WIDTH) {
                            canvas.drawLine(x, 80f, x, summaryTopY.toFloat(), linePaint)
                            x += 28f
                        }
                        var y = 80f + 28f
                        while (y < summaryTopY) {
                            canvas.drawLine(cueX, y, PAGE_WIDTH.toFloat(), y, linePaint)
                            y += 28f
                        }
                        var sy = summaryTopY + 36f
                        while (sy < PAGE_HEIGHT - 20f) {
                            canvas.drawLine(0f, sy, PAGE_WIDTH.toFloat(), sy, linePaint)
                            sy += 32f
                        }
                    }
                    "SINGLE_COLUMN" -> {
                        val leftMargin = 70f
                        val rightMargin = PAGE_WIDTH - 70f
                        canvas.drawLine(leftMargin, 0f, leftMargin, PAGE_HEIGHT.toFloat(), marginPaint)
                        canvas.drawLine(rightMargin, 0f, rightMargin, PAGE_HEIGHT.toFloat(), marginPaint)

                        var y = 60f
                        while (y < PAGE_HEIGHT - 20f) {
                            canvas.drawLine(leftMargin, y, rightMargin, y, linePaint)
                            y += 34f
                        }
                    }
                    "TWO_COLUMN" -> {
                        val midX = PAGE_WIDTH / 2f
                        val topY = 60f
                        canvas.drawLine(0f, topY, PAGE_WIDTH.toFloat(), topY, marginPaint)
                        canvas.drawLine(midX, topY, midX, PAGE_HEIGHT.toFloat(), marginPaint)

                        var y = topY + 34f
                        while (y < PAGE_HEIGHT - 20f) {
                            canvas.drawLine(0f, y, PAGE_WIDTH.toFloat(), y, linePaint)
                            y += 34f
                        }
                    }
                    "TWO_COLUMN_LEFT" -> {
                        val cueX = 180f
                        val topY = 60f
                        canvas.drawLine(0f, topY, PAGE_WIDTH.toFloat(), topY, marginPaint)
                        canvas.drawLine(cueX, topY, cueX, PAGE_HEIGHT.toFloat(), marginPaint)

                        var y = topY + 34f
                        while (y < PAGE_HEIGHT - 20f) {
                            canvas.drawLine(0f, y, PAGE_WIDTH.toFloat(), y, linePaint)
                            y += 34f
                        }
                    }
                    "THREE_COLUMN" -> {
                        val col1 = PAGE_WIDTH / 3f
                        val col2 = (PAGE_WIDTH / 3f) * 2f
                        val topY = 60f
                        canvas.drawLine(0f, topY, PAGE_WIDTH.toFloat(), topY, marginPaint)
                        canvas.drawLine(col1, topY, col1, PAGE_HEIGHT.toFloat(), marginPaint)
                        canvas.drawLine(col2, topY, col2, PAGE_HEIGHT.toFloat(), marginPaint)

                        var y = topY + 34f
                        while (y < PAGE_HEIGHT - 20f) {
                            canvas.drawLine(0f, y, PAGE_WIDTH.toFloat(), y, linePaint)
                            y += 34f
                        }
                    }
                    "DIARY" -> {
                        val topBoxBottom = 90f
                        canvas.drawLine(0f, topBoxBottom, PAGE_WIDTH.toFloat(), topBoxBottom, marginPaint)
                        canvas.drawLine(70f, 0f, 70f, PAGE_HEIGHT.toFloat(), marginPaint)

                        var y = topBoxBottom + 36f
                        val reflectionBoxTop = PAGE_HEIGHT - 130f
                        while (y < reflectionBoxTop) {
                            canvas.drawLine(70f, y, PAGE_WIDTH.toFloat(), y, linePaint)
                            y += 34f
                        }

                        canvas.drawLine(0f, reflectionBoxTop.toFloat(), PAGE_WIDTH.toFloat(), reflectionBoxTop.toFloat(), marginPaint)
                        var ry = reflectionBoxTop + 34f
                        while (ry < PAGE_HEIGHT - 20f) {
                            canvas.drawLine(0f, ry, PAGE_WIDTH.toFloat(), ry, linePaint)
                            ry += 34f
                        }
                    }
                    else -> {
                        // BLANK - no lines
                    }
                }

                // 3. Draw Strokes
                val strokes = pageStrokesProvider(pageEntity.id)
                val identityMatrix = Matrix()

                for (stroke in strokes) {
                    val inkStroke = buildInkStroke(stroke, penBrushFamily, highlighterBrushFamily)
                    if (inkStroke != null) {
                        canvasStrokeRenderer.draw(canvas, inkStroke, identityMatrix)
                    } else {
                        drawFallbackStroke(canvas, stroke)
                    }
                }

                pdfDocument.finishPage(pdfPage)
            }

            // Write to app cache
            val sanitizedTitle = notebook.name
                .replace("[^a-zA-Z0-9_.-]".toRegex(), "_")
                .ifBlank { "Notebook" }
            val fileName = if (targetPageId != null) {
                "${sanitizedTitle}_Page_${targetPageId}.pdf"
            } else {
                "${sanitizedTitle}.pdf"
            }

            val pdfDir = File(context.cacheDir, "exported_pdfs").apply { mkdirs() }
            val pdfFile = File(pdfDir, fileName)

            FileOutputStream(pdfFile).use { out ->
                pdfDocument.writeTo(out)
            }
            pdfDocument.close()

            pdfFile
        }
    }

    fun sharePdf(context: Context, pdfFile: File, title: String) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            pdfFile
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(intent, "Export & Share PDF")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
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
                // Ignore invalid input
            }
        }

        if (inputBatch.isEmpty()) return null
        return runCatching { InkStroke(brush, inputBatch) }.getOrNull()
    }

    private fun drawFallbackStroke(canvas: android.graphics.Canvas, stroke: StrokeEntity) {
        val points = StrokePointCodec.decode(stroke.pointData)
        if (points.isEmpty()) return

        val paint = Paint().apply {
            color = stroke.color.toInt()
            strokeWidth = stroke.strokeWidth
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            style = Paint.Style.STROKE
            isAntiAlias = true
        }

        if (points.size == 1) {
            canvas.drawCircle(points[0].x, points[0].y, stroke.strokeWidth / 2f, paint.apply { style = Paint.Style.FILL })
            return
        }

        for (i in 0 until points.size - 1) {
            val p1 = points[i]
            val p2 = points[i + 1]
            canvas.drawLine(p1.x, p1.y, p2.x, p2.y, paint)
        }
    }
}
