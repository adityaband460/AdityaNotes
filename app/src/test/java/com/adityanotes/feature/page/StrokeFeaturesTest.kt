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
}
