package com.adityanotes.feature.page

import com.adityanotes.feature.page.data.StrokeEntity
import com.adityanotes.feature.page.data.StrokePoint
import com.adityanotes.feature.page.data.StrokePointCodec
import com.adityanotes.feature.page.data.StrokeRepository
import com.adityanotes.feature.page.data.StrokeSnapshotCodec
import com.adityanotes.feature.page.data.StrokeTool
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StrokeFeaturesTest {

    @Test
    fun testRealtimeEraserIntersection() {
        val strokePoints = listOf(
            StrokePoint(100f, 100f, 1f, 0),
            StrokePoint(200f, 100f, 1f, 10),
            StrokePoint(300f, 100f, 1f, 20)
        )
        val stroke = StrokeEntity(
            id = 1L,
            pageId = 1L,
            pointData = StrokePointCodec.encode(strokePoints),
            color = 0xFF000000L,
            strokeWidth = 4f,
            tool = StrokeTool.PEN.name
        )

        // Eraser intersecting across stroke
        val crossingEraserPoints = listOf(
            StrokePoint(200f, 50f, 1f, 0),
            StrokePoint(200f, 150f, 1f, 5)
        )
        assertTrue(
            StrokeRepository.strokeTouchesEraser(
                stroke = stroke,
                eraserPoints = crossingEraserPoints,
                eraserRadius = 10f
            )
        )

        // Eraser far away from stroke
        val farAwayEraserPoints = listOf(
            StrokePoint(500f, 500f, 1f, 0),
            StrokePoint(520f, 520f, 1f, 5)
        )
        assertFalse(
            StrokeRepository.strokeTouchesEraser(
                stroke = stroke,
                eraserPoints = farAwayEraserPoints,
                eraserRadius = 10f
            )
        )
    }

    @Test
    fun testStrokeSnapshotCodecWithMultipleStrokes() {
        val stroke1 = StrokeEntity(
            id = 101L,
            pageId = 5L,
            pointData = StrokePointCodec.encode(listOf(StrokePoint(10f, 20f, 0.5f, 0))),
            color = 0xFF123456L,
            strokeWidth = 4f,
            tool = StrokeTool.PEN.name
        )
        val stroke2 = StrokeEntity(
            id = 102L,
            pageId = 5L,
            pointData = StrokePointCodec.encode(listOf(StrokePoint(50f, 60f, 0.9f, 5))),
            color = 0xFF654321L,
            strokeWidth = 20f,
            tool = StrokeTool.HIGHLIGHTER.name
        )

        val originalList = listOf(stroke1, stroke2)
        val encoded = StrokeSnapshotCodec.encode(originalList)
        val decoded = StrokeSnapshotCodec.decode(encoded)

        assertEquals(2, decoded.size)
        assertEquals(stroke1.id, decoded[0].id)
        assertEquals(stroke1.tool, decoded[0].tool)
        assertEquals(stroke1.color, decoded[0].color)
        assertEquals(stroke2.id, decoded[1].id)
        assertEquals(stroke2.tool, decoded[1].tool)
        assertEquals(stroke2.strokeWidth, decoded[1].strokeWidth)
    }

    @Test
    fun testLassoPointInPolygon() {
        // Rectangle polygon from (10, 10) to (100, 100)
        val polygon = listOf(
            StrokePoint(10f, 10f, 1f, 0),
            StrokePoint(100f, 10f, 1f, 0),
            StrokePoint(100f, 100f, 1f, 0),
            StrokePoint(10f, 100f, 1f, 0)
        )

        fun isPointInPolygon(point: StrokePoint, poly: List<StrokePoint>): Boolean {
            var inside = false
            var j = poly.size - 1
            for (i in poly.indices) {
                val xi = poly[i].x
                val yi = poly[i].y
                val xj = poly[j].x
                val yj = poly[j].y
                val intersect = ((yi > point.y) != (yj > point.y)) &&
                    (point.x < (xj - xi) * (point.y - yi) / (yj - yi) + xi)
                if (intersect) inside = !inside
                j = i
            }
            return inside
        }

        assertTrue(isPointInPolygon(StrokePoint(50f, 50f, 1f, 0), polygon))
        assertFalse(isPointInPolygon(StrokePoint(150f, 150f, 1f, 0), polygon))
        assertFalse(isPointInPolygon(StrokePoint(5f, 50f, 1f, 0), polygon))
    }

    @Test
    fun testLassoCrossPageTranslationDownward() {
        val pageHeight = 1130f
        val pageWidth = 800f
        val page0Id = 1L
        val page1Id = 2L
        val page0Top = 0f
        val page1Top = 1130f

        // Stroke on Page 0 at y = 100..200
        val originalPoints = listOf(
            StrokePoint(50f, 100f, 1f, 0),
            StrokePoint(60f, 200f, 1f, 10)
        )
        val stroke = StrokeEntity(
            id = 10L,
            pageId = page0Id,
            pointData = StrokePointCodec.encode(originalPoints),
            color = 0xFF000000L,
            strokeWidth = 3f,
            tool = StrokeTool.PEN.name
        )

        // Drag down across boundary to Page 1 (dy = 1200)
        val dy = 1200f
        val dx = 20f

        var minDocY = Float.MAX_VALUE
        var maxDocY = -Float.MAX_VALUE
        for (pt in originalPoints) {
            val docY = pt.y + page0Top + dy
            if (docY < minDocY) minDocY = docY
            if (docY > maxDocY) maxDocY = docY
        }

        val strokeCenterDocY = (minDocY + maxDocY) / 2f
        val newPageIdx = (strokeCenterDocY / pageHeight).toInt().coerceIn(0, 1)
        assertEquals(1, newPageIdx)

        val targetPageId = if (newPageIdx == 0) page0Id else page1Id
        val targetPageTop = if (newPageIdx == 0) page0Top else page1Top

        val shiftedPoints = originalPoints.map { pt ->
            StrokePoint(
                x = (pt.x + dx).coerceIn(0f, pageWidth),
                y = (pt.y + page0Top + dy) - targetPageTop,
                pressure = pt.pressure,
                elapsedMillis = pt.elapsedMillis
            )
        }

        assertEquals(page1Id, targetPageId)
        // 100 + 0 + 1200 - 1130 = 170
        assertEquals(170f, shiftedPoints[0].y, 0.01f)
        assertEquals(70f, shiftedPoints[0].x, 0.01f)
        // 200 + 0 + 1200 - 1130 = 270
        assertEquals(270f, shiftedPoints[1].y, 0.01f)
        assertEquals(80f, shiftedPoints[1].x, 0.01f)

        val updatedStroke = stroke.copy(
            pageId = targetPageId,
            pointData = StrokePointCodec.encode(shiftedPoints)
        )
        assertEquals(page1Id, updatedStroke.pageId)

        val decoded = StrokePointCodec.decode(updatedStroke.pointData)
        assertEquals(2, decoded.size)
        assertEquals(170f, decoded[0].y, 0.01f)
        assertEquals(270f, decoded[1].y, 0.01f)
    }

    @Test
    fun testLassoCrossPageTranslationUpward() {
        val pageHeight = 1130f
        val pageWidth = 800f
        val page0Id = 1L
        val page1Id = 2L
        val page0Top = 0f
        val page1Top = 1130f

        // Stroke on Page 1 at local y = 150..250 (docY = 1280..1380)
        val originalPoints = listOf(
            StrokePoint(100f, 150f, 1f, 0),
            StrokePoint(100f, 250f, 1f, 10)
        )
        val stroke = StrokeEntity(
            id = 20L,
            pageId = page1Id,
            pointData = StrokePointCodec.encode(originalPoints),
            color = 0xFF000000L,
            strokeWidth = 3f,
            tool = StrokeTool.PEN.name
        )

        // Drag up across boundary to Page 0 (dy = -1000)
        val dy = -1000f
        val dx = 0f

        var minDocY = Float.MAX_VALUE
        var maxDocY = -Float.MAX_VALUE
        for (pt in originalPoints) {
            val docY = pt.y + page1Top + dy
            if (docY < minDocY) minDocY = docY
            if (docY > maxDocY) maxDocY = docY
        }

        val strokeCenterDocY = (minDocY + maxDocY) / 2f
        val newPageIdx = (strokeCenterDocY / pageHeight).toInt().coerceIn(0, 1)
        assertEquals(0, newPageIdx)

        val targetPageId = if (newPageIdx == 0) page0Id else page1Id
        val targetPageTop = if (newPageIdx == 0) page0Top else page1Top

        val shiftedPoints = originalPoints.map { pt ->
            StrokePoint(
                x = (pt.x + dx).coerceIn(0f, pageWidth),
                y = (pt.y + page1Top + dy) - targetPageTop,
                pressure = pt.pressure,
                elapsedMillis = pt.elapsedMillis
            )
        }

        assertEquals(page0Id, targetPageId)
        // 150 + 1130 - 1000 - 0 = 280
        assertEquals(280f, shiftedPoints[0].y, 0.01f)
        // 250 + 1130 - 1000 - 0 = 380
        assertEquals(380f, shiftedPoints[1].y, 0.01f)

        val updatedStroke = stroke.copy(
            pageId = targetPageId,
            pointData = StrokePointCodec.encode(shiftedPoints)
        )
        assertEquals(page0Id, updatedStroke.pageId)
    }

    @Test
    fun testSegmentsIntersect() {
        fun segmentsIntersect(p1: StrokePoint, p2: StrokePoint, p3: StrokePoint, p4: StrokePoint): Boolean {
            fun ccw(a: StrokePoint, b: StrokePoint, c: StrokePoint): Float =
                (c.y - a.y) * (b.x - a.x) - (b.y - a.y) * (c.x - a.x)
            val d1 = ccw(p3, p4, p1)
            val d2 = ccw(p3, p4, p2)
            val d3 = ccw(p1, p2, p3)
            val d4 = ccw(p1, p2, p4)
            return ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) &&
                   ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))
        }

        // Horizontal line (0, 50) -> (100, 50) and vertical line (50, 0) -> (50, 100)
        val s1 = StrokePoint(0f, 50f, 1f, 0)
        val s2 = StrokePoint(100f, 50f, 1f, 0)
        val l1 = StrokePoint(50f, 0f, 1f, 0)
        val l2 = StrokePoint(50f, 100f, 1f, 0)
        assertTrue(segmentsIntersect(s1, s2, l1, l2))

        // Parallel lines
        val l3 = StrokePoint(0f, 60f, 1f, 0)
        val l4 = StrokePoint(100f, 60f, 1f, 0)
        assertFalse(segmentsIntersect(s1, s2, l3, l4))
    }

    @Test
    fun testMultipleHighlighterStrokesLassoSelection() {
        // Create 5 highlighter strokes
        val strokes = (0 until 5).map { idx ->
            val yOffset = 50f + idx * 30f
            val pts = listOf(
                StrokePoint(100f, yOffset, 1f, 0),
                StrokePoint(200f, yOffset, 1f, 5),
                StrokePoint(300f, yOffset, 1f, 10)
            )
            StrokeEntity(
                id = 100L + idx,
                pageId = 1L,
                pointData = StrokePointCodec.encode(pts),
                color = 0x40FFFF00L,
                strokeWidth = 24f,
                tool = StrokeTool.HIGHLIGHTER.name
            )
        }

        // Lasso polygon encircling all 5 strokes
        val lassoPolygon = listOf(
            StrokePoint(50f, 20f, 1f, 0),
            StrokePoint(350f, 20f, 1f, 0),
            StrokePoint(350f, 220f, 1f, 0),
            StrokePoint(50f, 220f, 1f, 0)
        )

        fun isPointInPolygon(point: StrokePoint, poly: List<StrokePoint>): Boolean {
            var inside = false
            var j = poly.size - 1
            for (i in poly.indices) {
                val xi = poly[i].x
                val yi = poly[i].y
                val xj = poly[j].x
                val yj = poly[j].y
                val intersect = ((yi > point.y) != (yj > point.y)) &&
                    (point.x < (xj - xi) * (point.y - yi) / (yj - yi) + xi)
                if (intersect) inside = !inside
                j = i
            }
            return inside
        }

        val selectedStrokes = mutableListOf<StrokeEntity>()
        for (stroke in strokes) {
            val strokePoints = StrokePointCodec.decode(stroke.pointData)
            val isSelected = strokePoints.any { isPointInPolygon(it, lassoPolygon) }
            if (isSelected) {
                selectedStrokes.add(stroke)
            }
        }

        // All 5 highlighter strokes must be selected without any stroke getting missed
        assertEquals(5, selectedStrokes.size)
    }
}
