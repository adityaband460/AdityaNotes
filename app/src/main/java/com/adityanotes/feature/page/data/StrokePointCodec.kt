package com.adityanotes.feature.page.data

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Compact, versioned storage for actual stylus points. */
object StrokePointCodec {

    private const val MAGIC = 0x41535452 // ASTR
    private const val HEADER_BYTES = 8
    private const val POINT_BYTES = 16

    fun encode(points: List<StrokePoint>): ByteArray {
        val buffer = ByteBuffer
            .allocate(HEADER_BYTES + points.size * POINT_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)

        buffer.putInt(MAGIC)
        buffer.putInt(points.size)

        points.forEach { point ->
            buffer.putFloat(point.x)
            buffer.putFloat(point.y)
            buffer.putFloat(point.pressure)
            buffer.putInt(point.elapsedMillis)
        }

        return buffer.array()
    }

    fun decode(data: ByteArray): List<StrokePoint> {
        if (data.size >= HEADER_BYTES) {
            val buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            val magic = buffer.int
            val count = buffer.int

            if (magic == MAGIC && count >= 0 && count <= (data.size - HEADER_BYTES) / POINT_BYTES) {
                return List(count) {
                    StrokePoint(
                        x = buffer.float,
                        y = buffer.float,
                        pressure = buffer.float,
                        elapsedMillis = buffer.int
                    )
                }
            }
        }

        /* Migration fallback for strokes stored by the pre-binary editor. */
        return data
            .toString(Charsets.UTF_8)
            .split(",")
            .mapNotNull(String::toFloatOrNull)
            .chunked(2)
            .mapIndexedNotNull { index, values ->
                if (values.size == 2) {
                    StrokePoint(
                        x = values[0],
                        y = values[1],
                        pressure = 1f,
                        elapsedMillis = index
                    )
                } else {
                    null
                }
            }
    }
}
