package com.adityanotes.feature.page.data

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Serializes the exact strokes affected by one durable document operation. */
object StrokeSnapshotCodec {

    private const val MAGIC = 0x41534F50 // ASOP
    private const val HEADER_BYTES = 8

    fun encode(strokes: List<StrokeEntity>): ByteArray {
        val records = strokes.map { stroke ->
            val tool = stroke.tool.toByteArray(Charsets.UTF_8)
            SnapshotRecord(stroke, tool)
        }
        val totalBytes = HEADER_BYTES + records.sumOf { record ->
            44 + record.tool.size + record.stroke.pointData.size
        }
        val buffer = ByteBuffer.allocate(totalBytes).order(ByteOrder.LITTLE_ENDIAN)

        buffer.putInt(MAGIC)
        buffer.putInt(records.size)

        records.forEach { record ->
            val stroke = record.stroke
            buffer.putLong(stroke.id)
            buffer.putLong(stroke.pageId)
            buffer.putLong(stroke.color)
            buffer.putFloat(stroke.strokeWidth)
            buffer.putLong(stroke.createdAt)
            buffer.putInt(record.tool.size)
            buffer.put(record.tool)
            buffer.putInt(stroke.pointData.size)
            buffer.put(stroke.pointData)
        }

        return buffer.array()
    }

    fun decode(payload: ByteArray): List<StrokeEntity> {
        if (payload.size < HEADER_BYTES) {
            return emptyList()
        }

        val buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        if (buffer.int != MAGIC) {
            return emptyList()
        }

        val count = buffer.int
        if (count < 0) {
            return emptyList()
        }

        return buildList {
            repeat(count) {
                if (buffer.remaining() < 40) {
                    return@buildList
                }

                val id = buffer.long
                val pageId = buffer.long
                val color = buffer.long
                val strokeWidth = buffer.float
                val createdAt = buffer.long
                val toolSize = buffer.int
                if (toolSize < 0 || toolSize > buffer.remaining()) {
                    return@buildList
                }
                val toolBytes = ByteArray(toolSize)
                buffer.get(toolBytes)
                if (buffer.remaining() < Int.SIZE_BYTES) {
                    return@buildList
                }
                val pointDataSize = buffer.int
                if (pointDataSize < 0 || pointDataSize > buffer.remaining()) {
                    return@buildList
                }
                val pointData = ByteArray(pointDataSize)
                buffer.get(pointData)

                add(
                    StrokeEntity(
                        id = id,
                        pageId = pageId,
                        pointData = pointData,
                        color = color,
                        strokeWidth = strokeWidth,
                        tool = toolBytes.toString(Charsets.UTF_8),
                        createdAt = createdAt
                    )
                )
            }
        }
    }

    private data class SnapshotRecord(
        val stroke: StrokeEntity,
        val tool: ByteArray
    )
}
