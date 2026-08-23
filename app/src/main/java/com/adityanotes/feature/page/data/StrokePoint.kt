package com.adityanotes.feature.page.data

/** A real stylus sample in page coordinates. */
data class StrokePoint(
    val x: Float,
    val y: Float,
    val pressure: Float,
    /** Milliseconds since the stroke began; preserves writing timing without a full timestamp. */
    val elapsedMillis: Int
)
