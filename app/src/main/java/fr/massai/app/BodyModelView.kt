package fr.massai.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class BodyModelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    enum class RenderMode {
        RAW,
        REPAIRED,
        MESH,
        WIREFRAME,
        CORRECTIONS
    }

    private data class ProjectedTriangle(
        val a: Int,
        val b: Int,
        val c: Int,
        val depth: Float
    )

    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val meshPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.rgb(118, 196, 126)
        alpha = 205
    }

    private val wirePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f
        color = Color.rgb(205, 245, 210)
        alpha = 220
    }

    private val path = Path()
    private var reconstruction: BodyReconstruction? = null
    private var mode = RenderMode.MESH
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
        mode = if (value.repairedMesh.triangleCount > 0) {
            RenderMode.MESH
        } else {
            RenderMode.REPAIRED
        }
        yaw = 0.45f
        pitch = -0.08f
        zoom = 1.0f
        invalidate()
    }

    fun getReconstruction(): BodyReconstruction? = reconstruction

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

        when (mode) {
            RenderMode.MESH -> drawMesh(canvas, model, wireframe = false)
            RenderMode.WIREFRAME -> drawMesh(canvas, model, wireframe = true)
            RenderMode.RAW -> drawPoints(canvas, model, model.rawSurface, Color.rgb(77, 208, 225))
            RenderMode.REPAIRED ->
                drawPoints(canvas, model, model.repairedSurface, Color.rgb(139, 195, 74))
            RenderMode.CORRECTIONS ->
                drawPoints(canvas, model, model.corrections, Color.rgb(255, 193, 7))
        }
    }

    private fun drawPoints(
        canvas: Canvas,
        model: BodyReconstruction,
        points: List<Point3>,
        color: Int
    ) {
        if (points.isEmpty()) return
        pointPaint.color = color

        val transform = transformInfo(model)
        val pointRadius = (1.7f * zoom).coerceIn(1.2f, 3.4f)

        points.forEach { p ->
            val projected = project(
                p.x,
                p.y,
                p.z,
                model,
                transform
            )
            canvas.drawCircle(
                projected[0],
                projected[1],
                pointRadius,
                pointPaint
            )
        }
    }

    private fun drawMesh(
        canvas: Canvas,
        model: BodyReconstruction,
        wireframe: Boolean
    ) {
        val mesh = model.repairedMesh
        if (mesh.triangleCount == 0 || mesh.vertexCount == 0) {
            drawPoints(
                canvas,
                model,
                model.repairedSurface,
                Color.rgb(139, 195, 74)
            )
            return
        }

        val transform = transformInfo(model)
        val projected = FloatArray(mesh.vertexCount * 3)

        var v = 0
        while (v < mesh.vertexCount) {
            val src = v * 3
            val p = project(
                mesh.vertices[src],
                mesh.vertices[src + 1],
                mesh.vertices[src + 2],
                model,
                transform
            )
            projected[src] = p[0]
            projected[src + 1] = p[1]
            projected[src + 2] = p[2]
            v++
        }

        val triangleCount = mesh.triangleCount
        val stride = ceil(triangleCount / MAX_RENDER_TRIANGLES.toDouble())
            .toInt()
            .coerceAtLeast(1)

        val triangles = ArrayList<ProjectedTriangle>(
            (triangleCount / stride).coerceAtLeast(1)
        )

        var t = 0
        while (t < triangleCount) {
            val base = t * 3
            val a = mesh.indices[base]
            val b = mesh.indices[base + 1]
            val c = mesh.indices[base + 2]

            val za = projected[a * 3 + 2]
            val zb = projected[b * 3 + 2]
            val zc = projected[c * 3 + 2]
            triangles += ProjectedTriangle(a, b, c, (za + zb + zc) / 3f)

            t += stride
        }

        triangles.sortByDescending { it.depth }
        val paint = if (wireframe) wirePaint else meshPaint

        triangles.forEach { triangle ->
            val ai = triangle.a * 3
            val bi = triangle.b * 3
            val ci = triangle.c * 3

            path.reset()
            path.moveTo(projected[ai], projected[ai + 1])
            path.lineTo(projected[bi], projected[bi + 1])
            path.lineTo(projected[ci], projected[ci + 1])
            path.close()
            canvas.drawPath(path, paint)
        }
    }

    private data class TransformInfo(
        val cx: Float,
        val cy: Float,
        val scale: Float,
        val cyaw: Float,
        val syaw: Float,
        val cpitch: Float,
        val spitch: Float,
        val halfHeight: Float,
        val perspectiveBase: Float
    )

    private fun transformInfo(model: BodyReconstruction): TransformInfo {
        val cx = width * 0.5f
        val cy = height * 0.53f
        val fitX = width / (model.halfExtentM.toFloat() * 2.6f)
        val fitY = height / (model.heightM.toFloat() * 1.20f)
        val scale = min(fitX, fitY) * zoom

        return TransformInfo(
            cx = cx,
            cy = cy,
            scale = scale,
            cyaw = cos(yaw.toDouble()).toFloat(),
            syaw = sin(yaw.toDouble()).toFloat(),
            cpitch = cos(pitch.toDouble()).toFloat(),
            spitch = sin(pitch.toDouble()).toFloat(),
            halfHeight = model.heightM.toFloat() * 0.5f,
            perspectiveBase = model.halfExtentM.toFloat().coerceAtLeast(0.25f)
        )
    }

    private fun project(
        x: Float,
        y: Float,
        z: Float,
        model: BodyReconstruction,
        tr: TransformInfo
    ): FloatArray {
        val x1 = x * tr.cyaw - z * tr.syaw
        val z1 = x * tr.syaw + z * tr.cyaw
        val y0 = y - tr.halfHeight

        val y1 = y0 * tr.cpitch - z1 * tr.spitch
        val z2 = y0 * tr.spitch + z1 * tr.cpitch

        val perspective = (
            1.0f /
                (1.0f + 0.20f * z2 / tr.perspectiveBase)
            ).coerceIn(0.72f, 1.35f)

        return floatArrayOf(
            tr.cx + x1 * tr.scale * perspective,
            tr.cy - y1 * tr.scale * perspective,
            z2
        )
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

    companion object {
        private const val MAX_RENDER_TRIANGLES = 18_000
    }
}
