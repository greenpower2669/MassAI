package fr.massai.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class SpiralGuideView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    companion object {
        const val LEVELS = 3
        const val BINS = 72
        const val REQUIRED_BINS = 66
    }

    private val covered = Array(LEVELS) { BooleanArray(BINS) }
    private var activeLevel = 0
    private var pendingLevel: Int? = null
    private var currentYawRad = 0.0
    private var sensorAvailable = true

    private val faintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = 0x66FFFFFF
    }

    private val coveredPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 7f
        strokeCap = Paint.Cap.ROUND
        color = 0xFF75F0C1.toInt()
    }

    private val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
        strokeCap = Paint.Cap.ROUND
        color = 0xFFFFFFFF.toInt()
    }

    private val pendingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
        color = 0xFFFFC857.toInt()
    }

    private val connectorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = 0xAA75F0C1.toInt()
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFFFFFFFF.toInt()
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFFFFFFFF.toInt()
        textAlign = Paint.Align.CENTER
        textSize = 32f
    }

    fun reset(sensorAvailable: Boolean) {
        this.sensorAvailable = sensorAvailable
        for (level in 0 until LEVELS) covered[level].fill(false)
        activeLevel = 0
        pendingLevel = null
        currentYawRad = 0.0
        invalidate()
    }

    fun markAngle(level: Int, yawRad: Double) {
        if (level !in 0 until LEVELS) return
        currentYawRad = yawRad
        covered[level][binFor(yawRad)] = true
        activeLevel = level
        pendingLevel = null
        invalidate()
    }

    fun showPendingLevel(level: Int) {
        pendingLevel = level.takeIf { it in 0 until LEVELS }
        invalidate()
    }

    fun updateYaw(yawRad: Double) {
        currentYawRad = yawRad
        invalidate()
    }

    fun setActiveLevel(level: Int) {
        if (level !in 0 until LEVELS) return
        activeLevel = level
        pendingLevel = null
        invalidate()
    }

    fun coveredBinCount(level: Int): Int {
        if (level !in 0 until LEVELS) return 0
        return covered[level].count { it }
    }

    fun coverageDegrees(level: Int): Double {
        return coveredBinCount(level) * (360.0 / BINS.toDouble())
    }

    fun isLevelComplete(level: Int): Boolean = coveredBinCount(level) >= REQUIRED_BINS

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (!sensorAvailable) {
            canvas.drawText(
                "Guide 3D indisponible sans capteur d’orientation",
                width * 0.5f,
                height * 0.5f,
                labelPaint
            )
            return
        }

        val cx = width * 0.5f
        val ringWidth = width * 0.68f
        val ringHeight = height * 0.12f
        val topY = height * 0.28f
        val spacing = height * 0.17f

        var previousCompletedPointX: Float? = null
        var previousCompletedPointY: Float? = null

        for (level in 0 until LEVELS) {
            val cy = topY + (LEVELS - 1 - level) * spacing
            val oval = RectF(
                cx - ringWidth * 0.5f,
                cy - ringHeight * 0.5f,
                cx + ringWidth * 0.5f,
                cy + ringHeight * 0.5f
            )

            canvas.drawOval(oval, faintPaint)

            val step = 360f / BINS.toFloat()
            for (bin in 0 until BINS) {
                if (!covered[level][bin]) continue
                val start = bin * step - 90f
                canvas.drawArc(oval, start, step * 0.88f, false, coveredPaint)
            }

            if (level == activeLevel) {
                canvas.drawOval(oval, activePaint)
            } else if (level == pendingLevel) {
                canvas.drawOval(oval, pendingPaint)
            }

            if (isLevelComplete(level)) {
                val angle = -PI / 2.0
                val px = (cx + cos(angle) * ringWidth * 0.5).toFloat()
                val py = (cy + sin(angle) * ringHeight * 0.5).toFloat()
                if (previousCompletedPointX != null && previousCompletedPointY != null) {
                    canvas.drawLine(
                        previousCompletedPointX!!,
                        previousCompletedPointY!!,
                        px,
                        py,
                        connectorPaint
                    )
                }
                previousCompletedPointX = px
                previousCompletedPointY = py
            }
        }

        val currentCy = topY + (LEVELS - 1 - activeLevel) * spacing
        val displayAngle = currentYawRad - PI / 2.0
        val dotX = (cx + cos(displayAngle) * ringWidth * 0.5).toFloat()
        val dotY = (currentCy + sin(displayAngle) * ringHeight * 0.5).toFloat()
        canvas.drawCircle(dotX, dotY, 11f, dotPaint)

        val label = pendingLevel?.let {
            "Montez vers le niveau ${it + 1}"
        } ?: "Niveau ${activeLevel + 1}/$LEVELS"
        canvas.drawText(label, cx, height * 0.92f, labelPaint)
    }

    private fun binFor(yawRad: Double): Int {
        var wrapped = yawRad % (2.0 * PI)
        if (wrapped < 0.0) wrapped += 2.0 * PI
        return ((wrapped / (2.0 * PI)) * BINS)
            .toInt()
            .coerceIn(0, BINS - 1)
    }
}
