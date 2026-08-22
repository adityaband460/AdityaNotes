package com.adityanotes.feature.page.presentation

import android.content.Context
import android.graphics.Matrix
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.ink.authoring.InProgressStrokesView
import androidx.ink.brush.Brush
import androidx.ink.brush.StockBrushes
import androidx.input.motionprediction.MotionEventPredictor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PageEditorScreen(
    pageId: Long,
    viewModel: PageViewModel,
    onBack: () -> Unit
) {
    /*
     * Handwriting brush.
     *
     * Keep this object stable so we don't create a new
     * Brush during recomposition.
     */
    val brush = remember {
        Brush.createWithColorIntArgb(
            family = StockBrushes.pressurePen(),
            colorIntArgb = android.graphics.Color.BLACK,
            size = 4f,
            epsilon = 0.1f
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Page")
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack
                    ) {
                        Text("←")
                    }
                }
            )
        }
    ) { innerPadding ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.White)
        ) {

            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    LowLatencyInkView(
                        context = context,
                        brush = brush
                    )
                },
                update = { view ->
                    view.setBrush(brush)
                }
            )
        }
    }
}


/**
 * Container for Jetpack Ink's InProgressStrokesView.
 *
 * IMPORTANT:
 *
 * InProgressStrokesView is FINAL.
 * We therefore DO NOT subclass it.
 *
 * Instead:
 *
 * LowLatencyInkView
 *       |
 *       +-- InProgressStrokesView
 *
 * MotionEvents are forwarded directly to Ink.
 */
private class LowLatencyInkView(
    context: Context,
    private var brush: Brush
) : FrameLayout(context) {

    private val inkView: InProgressStrokesView =
        InProgressStrokesView(context)

    private val predictor: MotionEventPredictor =
        MotionEventPredictor.newInstance(this)

    private val identityMatrix = Matrix()

    init {

        setBackgroundColor(
            android.graphics.Color.WHITE
        )

        /*
         * Put Ink renderer directly into the hierarchy.
         */
        addView(
            inkView,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
            )
        )

        /*
         * Tell Ink to initialize its renderer now rather
         * than on the first pen stroke.
         */
        inkView.eagerInit()

        /*
         * We need MotionEvents ourselves so that we can
         * forward them to Jetpack Ink and use prediction.
         */
        isClickable = true
        isFocusable = true
    }

    fun setBrush(newBrush: Brush) {
        brush = newBrush
    }

    override fun onTouchEvent(
        event: MotionEvent
    ): Boolean {

        /*
         * Only stylus/eraser creates ink.
         *
         * Finger can later be used for:
         * - pan
         * - zoom
         * - selection
         * - gestures
         */
        val actionIndex = event.actionIndex

        if (
            actionIndex < 0 ||
            actionIndex >= event.pointerCount
        ) {
            return false
        }

        val toolType =
            event.getToolType(actionIndex)

        if (
            toolType != MotionEvent.TOOL_TYPE_STYLUS &&
            toolType != MotionEvent.TOOL_TYPE_ERASER
        ) {
            return false
        }

        when (event.actionMasked) {

            MotionEvent.ACTION_DOWN -> {

                /*
                 * CRITICAL FOR LOW LATENCY.
                 *
                 * Ask Android to stop batching these events.
                 */
                requestUnbufferedDispatch(event)

                /*
                 * Feed the event into the predictor.
                 */
                predictor.record(event)

                /*
                 * The pointer ID must remain the same
                 * for the complete stroke.
                 */
                val pointerId =
                    event.getPointerId(
                        event.actionIndex
                    )

                /*
                 * Start the Ink stroke.
                 *
                 * Ink will render the stroke directly.
                 * We don't create:
                 *
                 * Bitmap
                 * Canvas
                 * Path
                 * HandlerThread
                 * FloatArray
                 *
                 * for every move.
                 */
                inkView.startStroke(
                    event = event,
                    pointerId = pointerId,
                    brush = brush,
                    motionEventToWorldTransform =
                        identityMatrix,
                    strokeToWorldTransform =
                        identityMatrix
                )

                return true
            }

            MotionEvent.ACTION_MOVE -> {

                predictor.record(event)

                /*
                 * Predict where the pen will be on the
                 * next display frame.
                 */
                val prediction =
                    predictor.predict()

                /*
                 * Process the real MotionEvent plus the
                 * predicted MotionEvent.
                 */
                val pointerId =
                    findActiveStylusPointer(event)

                if (pointerId != null) {

                    inkView.addToStroke(
                        event = event,
                        pointerId = pointerId,
                        prediction = prediction
                    )
                }

                /*
                 * Prediction object is owned by us after
                 * predict(), so release it.
                 */
                prediction?.recycle()

                return true
            }

            MotionEvent.ACTION_UP -> {

                predictor.record(event)

                val pointerId =
                    event.getPointerId(
                        event.actionIndex
                    )

                inkView.finishStroke(
                    event = event,
                    pointerId = pointerId
                )

                return true
            }

            MotionEvent.ACTION_CANCEL -> {

                inkView.cancelUnfinishedStrokes()

                return true
            }

            MotionEvent.ACTION_POINTER_UP -> {

                val pointerId =
                    event.getPointerId(
                        event.actionIndex
                    )

                inkView.finishStroke(
                    event = event,
                    pointerId = pointerId
                )

                return true
            }

            else -> {
                return true
            }
        }
    }

    /**
     * Find the active stylus pointer.
     *
     * Normally there is only one stylus pointer.
     */
    private fun findActiveStylusPointer(
        event: MotionEvent
    ): Int? {

        for (index in 0 until event.pointerCount) {

            if (
                event.getToolType(index) ==
                MotionEvent.TOOL_TYPE_STYLUS
            ) {
                return event.getPointerId(index)
            }
        }

        return null
    }

    override fun onDetachedFromWindow() {

        /*
         * Prevent an unfinished stroke from remaining
         * alive after leaving the screen.
         */
        inkView.cancelUnfinishedStrokes()
        super.onDetachedFromWindow()
    }
}