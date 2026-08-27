package com.adityanotes.feature.page.data

import androidx.room.withTransaction
import com.adityanotes.core.database.AdityaNotesDatabase
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlin.math.max

/**
 * Applies page edits as durable database operations. The operation record and
 * the visible stroke rows are changed in one Room transaction, so undo/redo
 * remains available after process death or an app restart.
 */
@Singleton
class StrokeRepository @Inject constructor(
    private val database: AdityaNotesDatabase
) {

    private val strokeDao = database.strokeDao()
    private val operationDao = database.strokeOperationDao()

    fun observeStrokes(pageId: Long): Flow<List<StrokeEntity>> =
        strokeDao.observeStrokes(pageId)

    fun observeStrokesForPages(pageIds: List<Long>): Flow<List<StrokeEntity>> =
        if (pageIds.isEmpty()) kotlinx.coroutines.flow.flowOf(emptyList())
        else strokeDao.observeStrokesForPages(pageIds)

    suspend fun addStroke(stroke: StrokeEntity): Long = database.withTransaction {
        operationDao.deleteRedoOperations(stroke.pageId)

        val strokeId = strokeDao.insertStroke(stroke)
        val savedStroke = stroke.copy(id = strokeId)

        operationDao.insertOperation(
            StrokeOperationEntity(
                pageId = stroke.pageId,
                type = StrokeOperationType.ADD.name,
                payload = StrokeSnapshotCodec.encode(listOf(savedStroke))
            )
        )

        strokeId
    }

    suspend fun eraseStrokes(
        pageId: Long,
        eraserPoints: List<StrokePoint>,
        eraserWidth: Float
    ): Int = database.withTransaction {
        if (eraserPoints.isEmpty()) {
            return@withTransaction 0
        }

        val erasedStrokes = strokeDao
            .getStrokesForPage(pageId)
            .filter { stroke ->
                strokeTouchesEraser(
                    stroke = stroke,
                    eraserPoints = eraserPoints,
                    eraserRadius = eraserWidth / 2f + stroke.strokeWidth / 2f
                )
            }

        if (erasedStrokes.isEmpty()) {
            return@withTransaction 0
        }

        operationDao.deleteRedoOperations(pageId)
        strokeDao.deleteStrokesByIds(erasedStrokes.map(StrokeEntity::id))
        operationDao.insertOperation(
            StrokeOperationEntity(
                pageId = pageId,
                type = StrokeOperationType.ERASE.name,
                payload = StrokeSnapshotCodec.encode(erasedStrokes)
            )
        )

        erasedStrokes.size
    }

    suspend fun recordEraseOperation(
        pageId: Long,
        erasedStrokes: List<StrokeEntity>
    ) = database.withTransaction {
        if (erasedStrokes.isEmpty()) return@withTransaction
        operationDao.deleteRedoOperations(pageId)
        strokeDao.deleteStrokesByIds(erasedStrokes.map(StrokeEntity::id))
        operationDao.insertOperation(
            StrokeOperationEntity(
                pageId = pageId,
                type = StrokeOperationType.ERASE.name,
                payload = StrokeSnapshotCodec.encode(erasedStrokes)
            )
        )
    }

    suspend fun deleteStrokesDirect(strokeIds: List<Long>) {
        if (strokeIds.isEmpty()) return
        strokeDao.deleteStrokesByIds(strokeIds)
    }

    suspend fun transformStrokes(
        pageId: Long,
        beforeStrokes: List<StrokeEntity>,
        afterStrokes: List<StrokeEntity>
    ) = database.withTransaction {
        if (beforeStrokes.isEmpty() || afterStrokes.isEmpty()) return@withTransaction
        operationDao.deleteRedoOperations(pageId)
        strokeDao.updateStrokes(afterStrokes)
        operationDao.insertOperation(
            StrokeOperationEntity(
                pageId = pageId,
                type = StrokeOperationType.TRANSFORM.name,
                payload = StrokeSnapshotCodec.encode(beforeStrokes)
            )
        )
    }

    suspend fun duplicateStrokes(
        pageId: Long,
        strokes: List<StrokeEntity>
    ): List<StrokeEntity> = database.withTransaction {
        if (strokes.isEmpty()) return@withTransaction emptyList()
        operationDao.deleteRedoOperations(pageId)
        val created = strokes.map { stroke ->
            val id = strokeDao.insertStroke(stroke.copy(id = 0))
            stroke.copy(id = id)
        }
        operationDao.insertOperation(
            StrokeOperationEntity(
                pageId = pageId,
                type = StrokeOperationType.ADD.name,
                payload = StrokeSnapshotCodec.encode(created)
            )
        )
        created
    }

    suspend fun undo(pageId: Long): Boolean = database.withTransaction {
        val operation = operationDao.getLatestAppliedOperation(pageId)
            ?: return@withTransaction false
        val strokes = StrokeSnapshotCodec.decode(operation.payload)
        if (strokes.isEmpty()) {
            return@withTransaction false
        }

        when (operation.type.toOperationType()) {
            StrokeOperationType.ADD -> {
                strokeDao.deleteStrokesByIds(strokes.map(StrokeEntity::id))
                operationDao.updateOperationState(
                    operationId = operation.id,
                    payload = operation.payload,
                    isUndone = true
                )
            }

            StrokeOperationType.ERASE -> {
                val restoredStrokes = restoreStrokes(strokes)
                operationDao.updateOperationState(
                    operationId = operation.id,
                    payload = StrokeSnapshotCodec.encode(restoredStrokes),
                    isUndone = true
                )
            }

            StrokeOperationType.TRANSFORM -> {
                val currentStrokes = strokeDao.getStrokesByIds(strokes.map(StrokeEntity::id))
                strokeDao.updateStrokes(strokes)
                operationDao.updateOperationState(
                    operationId = operation.id,
                    payload = StrokeSnapshotCodec.encode(currentStrokes),
                    isUndone = true
                )
            }

            null -> return@withTransaction false
        }

        true
    }

    suspend fun redo(pageId: Long): Boolean = database.withTransaction {
        val operation = operationDao.getNextRedoOperation(pageId)
            ?: return@withTransaction false
        val strokes = StrokeSnapshotCodec.decode(operation.payload)
        if (strokes.isEmpty()) {
            return@withTransaction false
        }

        when (operation.type.toOperationType()) {
            StrokeOperationType.ADD -> {
                val restoredStrokes = restoreStrokes(strokes)
                operationDao.updateOperationState(
                    operationId = operation.id,
                    payload = StrokeSnapshotCodec.encode(restoredStrokes),
                    isUndone = false
                )
            }

            StrokeOperationType.ERASE -> {
                strokeDao.deleteStrokesByIds(strokes.map(StrokeEntity::id))
                operationDao.updateOperationState(
                    operationId = operation.id,
                    payload = operation.payload,
                    isUndone = false
                )
            }

            StrokeOperationType.TRANSFORM -> {
                val currentStrokes = strokeDao.getStrokesByIds(strokes.map(StrokeEntity::id))
                strokeDao.updateStrokes(strokes)
                operationDao.updateOperationState(
                    operationId = operation.id,
                    payload = StrokeSnapshotCodec.encode(currentStrokes),
                    isUndone = false
                )
            }

            null -> return@withTransaction false
        }

        true
    }

    private suspend fun restoreStrokes(
        strokes: List<StrokeEntity>
    ): List<StrokeEntity> = strokes.map { stroke ->
        stroke.copy(id = strokeDao.insertStroke(stroke.copy(id = 0)))
    }

    companion object {
        fun strokeTouchesEraser(
            stroke: StrokeEntity,
            eraserPoints: List<StrokePoint>,
            eraserRadius: Float
        ): Boolean {
            val strokePoints = StrokePointCodec.decode(stroke.pointData)
            if (strokePoints.isEmpty()) {
                return false
            }

            return pathsTouch(
                first = strokePoints,
                second = eraserPoints,
                radius = eraserRadius
            )
        }

        fun pathsTouch(
            first: List<StrokePoint>,
            second: List<StrokePoint>,
            radius: Float
        ): Boolean {
            val squaredRadius = radius * radius
            val firstSegments = first.zipWithNext().ifEmpty { first.zip(first) }
            val secondSegments = second.zipWithNext().ifEmpty { second.zip(second) }

            return firstSegments.any { (start, end) ->
                secondSegments.any { (eraserStart, eraserEnd) ->
                    pointToSegmentDistanceSquared(start, eraserStart, eraserEnd) <= squaredRadius ||
                        pointToSegmentDistanceSquared(end, eraserStart, eraserEnd) <= squaredRadius ||
                        pointToSegmentDistanceSquared(eraserStart, start, end) <= squaredRadius ||
                        pointToSegmentDistanceSquared(eraserEnd, start, end) <= squaredRadius
                }
            }
        }

        fun pointToSegmentDistanceSquared(
            point: StrokePoint,
            start: StrokePoint,
            end: StrokePoint
        ): Float {
            val dx = end.x - start.x
            val dy = end.y - start.y
            val segmentLengthSquared = dx * dx + dy * dy

            if (segmentLengthSquared == 0f) {
                val distanceX = point.x - start.x
                val distanceY = point.y - start.y
                return distanceX * distanceX + distanceY * distanceY
            }

            val projection = (
                (point.x - start.x) * dx + (point.y - start.y) * dy
                ) / segmentLengthSquared
            val t = max(0f, minOf(1f, projection))
            val closestX = start.x + t * dx
            val closestY = start.y + t * dy
            val distanceX = point.x - closestX
            val distanceY = point.y - closestY

            return distanceX * distanceX + distanceY * distanceY
        }
        fun precisionEraseStroke(
            stroke: StrokeEntity,
            eraserPoints: List<StrokePoint>,
            eraserRadius: Float
        ): List<StrokeEntity>? {
            val strokePoints = StrokePointCodec.decode(stroke.pointData)
            if (strokePoints.isEmpty()) return null

            val squaredRadius = eraserRadius * eraserRadius
            val eraserSegments = eraserPoints.zipWithNext().ifEmpty { eraserPoints.zip(eraserPoints) }

            fun isPointErased(pt: StrokePoint): Boolean {
                return eraserSegments.any { (start, end) ->
                    pointToSegmentDistanceSquared(pt, start, end) <= squaredRadius
                }
            }

            var hasAnyErased = false
            val remainingSegments = mutableListOf<MutableList<StrokePoint>>()
            var currentSegment = mutableListOf<StrokePoint>()

            for (pt in strokePoints) {
                if (isPointErased(pt)) {
                    hasAnyErased = true
                    if (currentSegment.isNotEmpty()) {
                        remainingSegments.add(currentSegment)
                        currentSegment = mutableListOf()
                    }
                } else {
                    currentSegment.add(pt)
                }
            }
            if (currentSegment.isNotEmpty()) {
                remainingSegments.add(currentSegment)
            }

            if (!hasAnyErased) return null

            return remainingSegments.map { points ->
                stroke.copy(
                    id = 0,
                    pointData = StrokePointCodec.encode(points)
                )
            }
        }
    }

    private fun String.toOperationType(): StrokeOperationType? =
        runCatching { StrokeOperationType.valueOf(this) }.getOrNull()
}
