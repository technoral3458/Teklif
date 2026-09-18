package com.teknoral.parametrik.ui.viewer

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import com.teknoral.parametrik.domain.model.Geometry
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Kamera durumu — ekran döndürmede korunur. */
data class ViewerState(
    val yaw: Float = -0.60f,
    val pitch: Float = 0.32f,
    val zoom: Float = 1f,
    val panX: Float = 0f,
    val panY: Float = 0f
)

enum class ViewPreset(val label: String, val yaw: Float, val pitch: Float) {
    PERSPECTIVE("Perspektif", -0.60f, 0.32f),
    FRONT("Ön", 0f, 0f),
    SIDE("Yan", (Math.PI / 2).toFloat(), 0f),
    TOP("Üst", 0f, (Math.PI / 2).toFloat())
}

/**
 * Ortografik izdüşüm + ressam algoritması ile panel yığınını çizer.
 * Compose Canvas binlerce path'te yavaş kaldığı için klasik View + Canvas.
 */
class Model3dView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private var fullSet: SurfaceSet? = null
    private var liteSet: SurfaceSet? = null
    private var currentGeometry: Geometry? = null

    private var yaw = ViewerState().yaw
    private var pitch = ViewerState().pitch
    private var zoom = 1f
    private var panX = 0f
    private var panY = 0f

    private var interacting = false

    var onStateChanged: ((ViewerState) -> Unit)? = null

    // döndürülmüş koordinatlar (nesne ayırma yok)
    private var rx = FloatArray(0)
    private var ry = FloatArray(0)
    private var rz = FloatArray(0)
    private var sx = FloatArray(0)
    private var sy = FloatArray(0)
    private var sortKeys = LongArray(0)
    private var faceDepth = FloatArray(0)

    private val facePath = Path()
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(51, 0, 0, 0)
        strokeWidth = 0.5f * resources.displayMetrics.density
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(150, 255, 255, 255)
        textSize = 14f * resources.displayMetrics.density
        textAlign = Paint.Align.CENTER
    }

    private var hint: String = "Model yükleniyor…"

    // dokunma
    private var lastX = 0f
    private var lastY = 0f
    private var lastDistance = 0f
    private var lastMidX = 0f
    private var lastMidY = 0f
    private var pointerMode = MODE_NONE

    private val doubleTapDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                reset()
                return true
            }
        }
    )

    fun setGeometry(geometry: Geometry) {
        if (currentGeometry === geometry) return
        currentGeometry = geometry
        if (geometry.isEmpty) {
            fullSet = null
            liteSet = null
            hint = "Bu iş henüz dilimlenmedi. Önce dilimleyin."
            invalidate()
            return
        }
        val full = SurfaceSet.build(geometry, step = 1)
        fullSet = full
        liteSet = if (full.faceCount > LITE_THRESHOLD) SurfaceSet.build(geometry, step = 2) else full
        ensureBuffers(max(full.vertexCount, liteSet?.vertexCount ?: 0), max(full.faceCount, liteSet?.faceCount ?: 0))
        invalidate()
    }

    fun setHint(text: String) {
        hint = text
        invalidate()
    }

    fun applyState(state: ViewerState) {
        yaw = state.yaw
        pitch = state.pitch
        zoom = state.zoom
        panX = state.panX
        panY = state.panY
        invalidate()
    }

    fun currentState(): ViewerState = ViewerState(yaw, pitch, zoom, panX, panY)

    fun applyPreset(preset: ViewPreset) {
        yaw = preset.yaw
        pitch = preset.pitch
        zoom = 1f
        panX = 0f
        panY = 0f
        publishState()
        invalidate()
    }

    fun reset() = applyPreset(ViewPreset.PERSPECTIVE)

    private fun ensureBuffers(vertexCount: Int, faceCount: Int) {
        if (rx.size < vertexCount) {
            rx = FloatArray(vertexCount)
            ry = FloatArray(vertexCount)
            rz = FloatArray(vertexCount)
            sx = FloatArray(vertexCount)
            sy = FloatArray(vertexCount)
        }
        if (sortKeys.size < faceCount) {
            sortKeys = LongArray(faceCount)
            faceDepth = FloatArray(faceCount)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val set = activeSet()
        if (set == null || set.faceCount == 0) {
            canvas.drawText(hint, width / 2f, height / 2f, hintPaint)
            return
        }

        val bb = set.bounds
        val cx = (bb[0] + bb[3]) * 0.5f
        val cy = (bb[1] + bb[4]) * 0.5f
        val cz = (bb[2] + bb[5]) * 0.5f
        val radius = max(max(bb[3] - bb[0], bb[4] - bb[1]), bb[5] - bb[2]) * 0.5f
        val safeRadius = if (radius <= 0.0001f) 1f else radius
        val scale = min(width, height) / (safeRadius * 2.35f) * zoom
        val originX = width / 2f + panX
        val originY = height / 2f + panY

        val cosYaw = cos(yaw)
        val sinYaw = sin(yaw)
        val cosPitch = cos(pitch)
        val sinPitch = sin(pitch)

        val count = set.vertexCount
        for (i in 0 until count) {
            val x = set.vx[i] - cx
            val y = set.vy[i] - cy
            val z = set.vz[i] - cz
            val vxr = x * cosYaw - y * sinYaw
            val vyr = x * sinYaw + y * cosYaw
            val depth = vyr * cosPitch - z * sinPitch
            val up = vyr * sinPitch + z * cosPitch
            rx[i] = vxr
            ry[i] = depth
            rz[i] = up
            sx[i] = originX + vxr * scale
            sy[i] = originY - up * scale
        }

        // arka yüz ayıklama + derinlik sıralaması
        var visible = 0
        var minDepth = Float.MAX_VALUE
        var maxDepth = -Float.MAX_VALUE
        for (face in 0 until set.faceCount) {
            val start = set.faceStart[face]
            val len = set.faceLen[face]
            var area = 0f
            var depthSum = 0f
            for (k in 0 until len) {
                val a = set.faceVerts[start + k]
                val b = set.faceVerts[start + (k + 1) % len]
                area += sx[a] * sy[b] - sx[b] * sy[a]
                depthSum += ry[a]
            }
            if (area >= 0f) continue
            val depth = depthSum / len
            if (depth < minDepth) minDepth = depth
            if (depth > maxDepth) maxDepth = depth
            faceDepth[face] = depth
            sortKeys[visible++] = (sortableKey(depth).toLong() shl 32) or (face.toLong() and 0xFFFFFFFFL)
        }
        if (visible == 0) return
        java.util.Arrays.sort(sortKeys, 0, visible)

        val depthRange = (maxDepth - minDepth).takeIf { it > 0.0001f } ?: 1f
        val drawEdges = !interacting

        // uzaktan yakına
        for (index in visible - 1 downTo 0) {
            val face = (sortKeys[index] and 0xFFFFFFFFL).toInt()
            val start = set.faceStart[face]
            val len = set.faceLen[face]

            facePath.rewind()
            val first = set.faceVerts[start]
            facePath.moveTo(sx[first], sy[first])
            for (k in 1 until len) {
                val v = set.faceVerts[start + k]
                facePath.lineTo(sx[v], sy[v])
            }
            facePath.close()

            val shade = shadeOf(set, start, len)
            val fog = 1f - 0.26f * ((faceDepth[face] - minDepth) / depthRange)
            val base = if (set.facePanel[face]) PANEL_COLOR else FRAME_COLOR
            fillPaint.color = Color.rgb(
                clamp255(base[0] * shade * fog),
                clamp255(base[1] * shade * fog),
                clamp255(base[2] * shade * fog)
            )
            canvas.drawPath(facePath, fillPaint)
            if (drawEdges) canvas.drawPath(facePath, edgePaint)
        }
    }

    /** Döndürülmüş ilk üç köşeden normal → Lambert + hafif ön aydınlatma. */
    private fun shadeOf(set: SurfaceSet, start: Int, len: Int): Float {
        if (len < 3) return 0.8f
        val a = set.faceVerts[start]
        val b = set.faceVerts[start + 1]
        val c = set.faceVerts[start + 2]
        val ux = rx[b] - rx[a]; val uy = ry[b] - ry[a]; val uz = rz[b] - rz[a]
        val wx = rx[c] - rx[a]; val wy = ry[c] - ry[a]; val wz = rz[c] - rz[a]
        var nx = uy * wz - uz * wy
        var ny = uz * wx - ux * wz
        var nz = ux * wy - uy * wx
        val length = kotlin.math.sqrt(nx * nx + ny * ny + nz * nz)
        if (length < 1e-6f) return 0.8f
        nx /= length; ny /= length; nz /= length
        if (ny > 0f) { nx = -nx; ny = -ny; nz = -nz }
        var lambert = nx * -0.34f + ny * -0.86f + nz * 0.38f
        if (lambert < 0f) lambert = 0f
        return 0.46f + 0.52f * lambert + 0.12f * max(0f, nz)
    }

    private fun activeSet(): SurfaceSet? =
        if (interacting) (liteSet ?: fullSet) else fullSet

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        doubleTapDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                interacting = true
                pointerMode = MODE_ROTATE
                lastX = event.x
                lastY = event.y
                invalidate()
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount >= 2) {
                    pointerMode = MODE_ZOOM_PAN
                    lastDistance = distanceOf(event)
                    lastMidX = (event.getX(0) + event.getX(1)) * 0.5f
                    lastMidY = (event.getY(0) + event.getY(1)) * 0.5f
                }
            }

            MotionEvent.ACTION_MOVE -> {
                if (pointerMode == MODE_ZOOM_PAN && event.pointerCount >= 2) {
                    val distance = distanceOf(event)
                    if (lastDistance > 1f && distance > 1f) {
                        zoom = (zoom * (distance / lastDistance)).coerceIn(MIN_ZOOM, MAX_ZOOM)
                    }
                    lastDistance = distance
                    val midX = (event.getX(0) + event.getX(1)) * 0.5f
                    val midY = (event.getY(0) + event.getY(1)) * 0.5f
                    panX += midX - lastMidX
                    panY += midY - lastMidY
                    lastMidX = midX
                    lastMidY = midY
                    invalidate()
                } else if (pointerMode == MODE_ROTATE) {
                    val dx = event.x - lastX
                    val dy = event.y - lastY
                    if (abs(dx) > 0.01f || abs(dy) > 0.01f) {
                        yaw += dx * ROTATE_SPEED
                        pitch = (pitch + dy * ROTATE_SPEED).coerceIn(-PITCH_LIMIT, PITCH_LIMIT)
                        lastX = event.x
                        lastY = event.y
                        invalidate()
                    }
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                pointerMode = MODE_ROTATE
                val remaining = if (event.actionIndex == 0) 1 else 0
                lastX = event.getX(remaining)
                lastY = event.getY(remaining)
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                interacting = false
                pointerMode = MODE_NONE
                publishState()
                invalidate()
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun distanceOf(event: MotionEvent): Float =
        hypot(event.getX(0) - event.getX(1), event.getY(0) - event.getY(1))

    private fun publishState() {
        onStateChanged?.invoke(currentState())
    }

    private companion object {
        const val MODE_NONE = 0
        const val MODE_ROTATE = 1
        const val MODE_ZOOM_PAN = 2

        const val ROTATE_SPEED = 0.011f
        val PITCH_LIMIT = (Math.PI / 2 * 1.35).toFloat()
        const val MIN_ZOOM = 0.15f
        const val MAX_ZOOM = 12f
        const val LITE_THRESHOLD = 6000

        val PANEL_COLOR = floatArrayOf(208f, 170f, 112f)
        val FRAME_COLOR = floatArrayOf(150f, 112f, 64f)

        fun clamp255(value: Float): Int = value.toInt().coerceIn(0, 255)

        /** float → sıralanabilir int (LongArray içinde derinlik anahtarı olarak kullanılır). */
        fun sortableKey(value: Float): Int {
            val bits = java.lang.Float.floatToIntBits(value)
            return bits xor ((bits shr 31) and 0x7FFFFFFF)
        }
    }
}
