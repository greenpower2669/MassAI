package fr.massai.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class BodyModelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    enum class RenderMode { RAW, REPAIRED, CORRECTIONS }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private var reconstruction: BodyReconstruction? = null
    private var mode = RenderMode.REPAIRED
    private var yaw = 0.45f
    private var pitch = -0.08f
    private var zoom = 1.0f
    private var lastX = 0f
    private var lastY = 0f

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                zoom = (zoom * detector.scaleFactor).coerceIn(0.55f, 2.8f)
                invalidate()
                return true
            }
        }
    )

    fun setReconstruction(value: BodyReconstruction) {
        reconstruction = value
        mode = RenderMode.REPAIRED
        yaw = 0.45f
        pitch = -0.08f
        zoom = 1.0f
        invalidate()
    }

    fun clear() {
        reconstruction = null
        invalidate()
    }

    fun setMode(value: RenderMode) {
        mode = value
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val model = reconstruction ?: return

        val points = when (mode) {
            RenderMode.RAW -> model.rawSurface
            RenderMode.REPAIRED -> model.repairedSurface
            RenderMode.CORRECTIONS -> model.corrections
        }

        if (points.isEmpty()) return

        paint.color = when (mode) {
            RenderMode.RAW -> Color.rgb(77, 208, 225)
            RenderMode.REPAIRED -> Color.rgb(139, 195, 74)
            RenderMode.CORRECTIONS -> Color.rgb(255, 193, 7)
        }

        val cx = width * 0.5f
        val cy = height * 0.53f
        val fitX = width / (model.halfExtentM.toFloat() * 2.6f)
        val fitY = height / (model.heightM.toFloat() * 1.20f)
        val scale = min(fitX, fitY) * zoom
        val pointRadius = (1.7f * zoom).coerceIn(1.2f, 3.4f)

        val cyaw = cos(yaw)
        val syaw = sin(yaw)
        val cpitch = cos(pitch)
        val spitch = sin(pitch)
        val halfHeight = model.heightM.toFloat() * 0.5f
        val perspectiveBase = model.halfExtentM.toFloat().coerceAtLeast(0.25f)

        points.forEach { p ->
            val x1 = p.x * cyaw - p.z * syaw
            val z1 = p.x * syaw + p.z * cyaw
            val y0 = p.y - halfHeight

            val y1 = y0 * cpitch - z1 * spitch
            val z2 = y0 * spitch + z1 * cpitch

            val perspective = (1.0f / (1.0f + 0.20f * z2 / perspectiveBase))
                .coerceIn(0.72f, 1.35f)

            canvas.drawCircle(
                cx + x1 * scale * perspective,
                cy - y1 * scale * perspective,
                pointRadius,
                paint
            )
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!scaleDetector.isInProgress && event.pointerCount == 1) {
                    val dx = event.x - lastX
                    val dy = event.y - lastY
                    yaw += dx * 0.010f
                    pitch = (pitch + dy * 0.008f).coerceIn(-0.85f, 0.85f)
                    lastX = event.x
                    lastY = event.y
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                performClick()
                return true
            }
        }

        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
