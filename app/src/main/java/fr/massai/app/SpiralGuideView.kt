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
        const val MAX_LEVELS = 5
        const val BINS = 72
        const val REQUIRED_BINS = 66
    }

    private val covered = Array(MAX_LEVELS) { BooleanArray(BINS) }
    private var targetLevels = 3
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

    fun configure(levels: Int, sensorAvailable: Boolean) {
        targetLevels = levels.coerceIn(1, MAX_LEVELS)
        reset(sensorAvailable)
    }

    fun levelCount(): Int = targetLevels

    fun reset(sensorAvailable: Boolean) {
        this.sensorAvailable = sensorAvailable
        for (level in 0 until MAX_LEVELS) covered[level].fill(false)
        activeLevel = 0
        pendingLevel = null
        currentYawRad = 0.0
        invalidate()
    }

    fun markAngle(level: Int, yawRad: Double) {
        if (level !in 0 until targetLevels) return
        currentYawRad = yawRad
        covered[level][binFor(yawRad)] = true
        activeLevel = level
        pendingLevel = null
        invalidate()
    }

    fun showPendingLevel(level: Int) {
        pendingLevel = level.takeIf { it in 0 until targetLevels }
        invalidate()
    }

    fun updateYaw(yawRad: Double) {
        currentYawRad = yawRad
        invalidate()
    }

    fun setActiveLevel(level: Int) {
        if (level !in 0 until targetLevels) return
        activeLevel = level
        pendingLevel = null
        invalidate()
    }

    fun coveredBinCount(level: Int): Int {
        if (level !in 0 until targetLevels) return 0
        return covered[level].count { it }
    }

    fun coverageDegrees(level: Int): Double {
        return coveredBinCount(level) * (360.0 / BINS.toDouble())
    }

    fun isLevelComplete(level: Int): Boolean =
        level in 0 until targetLevels && coveredBinCount(level) >= REQUIRED_BINS

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (!sensorAvailable) {
            canvas.drawText(
                "Guide angulaire indisponible sans capteur",
                width * 0.5f,
                height * 0.5f,
                labelPaint
            )
            return
        }

        val cx = width * 0.5f
        val ringWidth = width * 0.68f
        val ringHeight = (height * 0.10f).coerceAtLeast(34f)
        val bottomY = height * 0.70f
        val topY = height * 0.27f
        val spacing = if (targetLevels <= 1) {
            0f
        } else {
            (bottomY - topY) / (targetLevels - 1).toFloat()
        }

        fun centerY(level: Int): Float =
            if (targetLevels <= 1) {
                height * 0.50f
            } else {
                bottomY - level * spacing
            }

        var previousCompletedPointX: Float? = null
        var previousCompletedPointY: Float? = null

        for (level in 0 until targetLevels) {
            val cy = centerY(level)
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

            if (isLevelComplete(level)) {
                canvas.drawOval(oval, coveredPaint)
            } else if (level == activeLevel) {
                val currentBin = binFor(currentYawRad)
                canvas.drawArc(
                    oval,
                    currentBin * step - 90f,
                    step * 0.9f,
                    false,
                    activePaint
                )
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

        pendingLevel?.let { next ->
            val previous = (next - 1).coerceAtLeast(0)
            if (isLevelComplete(previous)) {
                val fromCy = centerY(previous)
                val toCy = centerY(next)
                val x = cx + ringWidth * 0.5f
                canvas.drawLine(x, fromCy, x, toCy, connectorPaint)
            }
        }

        val currentCy = centerY(activeLevel)
        val displayAngle = currentYawRad - PI / 2.0
        val dotX = (cx + cos(displayAngle) * ringWidth * 0.5).toFloat()
        val dotY = (currentCy + sin(displayAngle) * ringHeight * 0.5).toFloat()
        canvas.drawCircle(dotX, dotY, 11f, dotPaint)

        val label = pendingLevel?.let {
            "Montez vers le niveau ${it + 1}"
        } ?: "Niveau ${activeLevel + 1}/$targetLevels"
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
