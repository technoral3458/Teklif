package com.teklif.yolcu.map

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import com.teklif.yolcu.geo.Geo
import com.teklif.yolcu.geo.LatLon
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sin

/** Haritanın bakış açısı. */
data class Camera(
    val center: LatLon,
    /** Kesirli yakınlaştırma (3..19). */
    val zoom: Double,
    /** Haritanın döndürülme açısı (derece); navigasyonda gidiş yönü yukarı. */
    val bearing: Double = 0.0,
)

/** Çizilecek ek katmanlar. */
data class MapOverlay(
    val route: List<LatLon> = emptyList(),
    /** Rota üzerinde geçilmiş kısım (soluk çizilir). */
    val travelledUntilIndex: Int = 0,
    val position: LatLon? = null,
    val heading: Float? = null,
    val destination: LatLon? = null,
    val borderPoints: List<Pair<LatLon, String>> = emptyList(),
)

/**
 * Harita karolarını, rotayı ve konumu bir Canvas'a çizer. Telefon ekranı (Compose) ve Android Auto
 * yüzeyi aynı çizimi kullanır.
 */
class MapRenderer(private val tiles: TileCache) {

    private val tilePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val bgPaint = Paint().apply { color = Color.rgb(226, 230, 234) }
    private val routeCasing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        color = Color.rgb(13, 71, 161)
    }
    private val routePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        color = Color.rgb(66, 133, 244)
    }
    private val travelledPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        color = Color.rgb(158, 158, 158)
    }
    private val posFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(25, 118, 210) }
    private val posStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 4f
    }
    private val accuracyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(40, 25, 118, 210) }
    private val destPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(211, 47, 47) }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 160, 0) }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(33, 33, 33); textSize = 30f; isFakeBoldText = true
    }
    private val labelBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(220, 255, 255, 255) }
    private val attribution = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(80, 80, 80); textSize = 22f }
    private val path = Path()

    /**
     * @param density ekran yoğunluğu (çizgi kalınlıkları için)
     */
    fun draw(canvas: Canvas, width: Int, height: Int, camera: Camera, overlay: MapOverlay, density: Float) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        val z = camera.zoom.coerceIn(3.0, 19.0)
        val zi = floor(z).toInt()
        val scale = 2.0.pow(z - zi) * TILE * density / 2f
        val cx = Geo.lonToTileX(camera.center.lon, zi.toDouble())
        val cy = Geo.latToTileY(camera.center.lat, zi.toDouble())
        val halfW = width / 2f
        val halfH = height / 2f

        canvas.save()
        canvas.rotate(-camera.bearing.toFloat(), halfW, halfH)

        // Döndürme sonrası köşeler açıkta kalmasın diye biraz geniş alan çiz.
        val radius = Math.hypot(halfW.toDouble(), halfH.toDouble()).toFloat()
        val tilesHalf = radius / scale + 1
        val n = 1 shl zi
        val x0 = floor(cx - tilesHalf).toInt()
        val x1 = ceil(cx + tilesHalf).toInt()
        val y0 = floor(cy - tilesHalf).toInt().coerceAtLeast(0)
        val y1 = ceil(cy + tilesHalf).toInt().coerceAtMost(n - 1)
        val dst = RectF()
        for (ty in y0..y1) {
            for (tx in x0..x1) {
                val wrapped = ((tx % n) + n) % n
                val left = halfW + ((tx - cx) * scale).toFloat()
                val top = halfH + ((ty - cy) * scale).toFloat()
                dst.set(left, top, left + scale.toFloat() + 0.5f, top + scale.toFloat() + 0.5f)
                val bmp = tiles.get(TileKey(zi, wrapped, ty))
                if (bmp != null) {
                    canvas.drawBitmap(bmp, null, dst, tilePaint)
                } else {
                    drawParentTile(canvas, zi, wrapped, ty, dst)
                }
            }
        }

        // Rota
        if (overlay.route.size >= 2) {
            val sw = 9f * density
            routeCasing.strokeWidth = sw + 3f * density
            routePaint.strokeWidth = sw
            travelledPaint.strokeWidth = sw
            val split = overlay.travelledUntilIndex.coerceIn(0, overlay.route.size - 1)
            buildPath(overlay.route, 0, split, camera, zi, cx, cy, scale, halfW, halfH)
            canvas.drawPath(path, travelledPaint)
            buildPath(overlay.route, split, overlay.route.size - 1, camera, zi, cx, cy, scale, halfW, halfH)
            canvas.drawPath(path, routeCasing)
            canvas.drawPath(path, routePaint)
        }

        // Sınır noktaları
        for ((p, name) in overlay.borderPoints) {
            val (px, py) = project(p, zi, cx, cy, scale, halfW, halfH)
            canvas.drawCircle(px, py, 8f * density, borderPaint)
            canvas.drawCircle(px, py, 8f * density, posStroke)
            drawLabel(canvas, name, px, py - 14f * density, density, camera.bearing)
        }

        // Hedef
        overlay.destination?.let {
            val (px, py) = project(it, zi, cx, cy, scale, halfW, halfH)
            canvas.drawCircle(px, py, 10f * density, destPaint)
            canvas.drawCircle(px, py, 10f * density, posStroke)
        }

        // Konum
        overlay.position?.let { pos ->
            val (px, py) = project(pos, zi, cx, cy, scale, halfW, halfH)
            canvas.drawCircle(px, py, 26f * density, accuracyPaint)
            val heading = overlay.heading
            if (heading != null) {
                drawArrow(canvas, px, py, heading - camera.bearing.toFloat(), 14f * density)
            } else {
                canvas.drawCircle(px, py, 9f * density, posFill)
                canvas.drawCircle(px, py, 9f * density, posStroke)
            }
        }
        canvas.restore()

        attribution.textSize = 11f * density
        val text = "© OpenStreetMap katkıcıları"
        canvas.drawRect(
            width - attribution.measureText(text) - 10f * density, height - 18f * density,
            width.toFloat(), height.toFloat(), labelBg,
        )
        canvas.drawText(text, width - attribution.measureText(text) - 5f * density, height - 5f * density, attribution)
    }

    /** Karo yoksa bir üst yakınlaştırmadaki karonun ilgili çeyreğini büyüterek çizer (boşluk kalmasın). */
    private fun drawParentTile(canvas: Canvas, z: Int, x: Int, y: Int, dst: RectF) {
        var pz = z - 1
        var px = x shr 1
        var py = y shr 1
        var sub = 1
        while (pz >= 3 && z - pz <= 4) {
            val bmp = tiles.peek(TileKey(pz, px, py))
            if (bmp != null) {
                val part = TILE / (1 shl (z - pz))
                val ox = (x - (px shl (z - pz))) * part
                val oy = (y - (py shl (z - pz))) * part
                canvas.drawBitmap(bmp, Rect(ox, oy, ox + part, oy + part), dst, tilePaint)
                return
            }
            px = px shr 1; py = py shr 1; pz--; sub++
        }
    }

    private fun project(
        p: LatLon, zi: Int, cx: Double, cy: Double, scale: Double, halfW: Float, halfH: Float,
    ): Pair<Float, Float> {
        val tx = Geo.lonToTileX(p.lon, zi.toDouble())
        val ty = Geo.latToTileY(p.lat, zi.toDouble())
        return (halfW + ((tx - cx) * scale)).toFloat() to (halfH + ((ty - cy) * scale)).toFloat()
    }

    private fun buildPath(
        pts: List<LatLon>, from: Int, to: Int, camera: Camera, zi: Int,
        cx: Double, cy: Double, scale: Double, halfW: Float, halfH: Float,
    ) {
        path.rewind()
        if (to <= from) return
        // Çok uzun rotalarda her noktayı çizmeye gerek yok: ekran çözünürlüğüne göre seyrelt.
        val step = ((to - from) / 4000).coerceAtLeast(1)
        var first = true
        var i = from
        while (i <= to) {
            val (x, y) = project(pts[i], zi, cx, cy, scale, halfW, halfH)
            if (first) { path.moveTo(x, y); first = false } else path.lineTo(x, y)
            if (i == to) break
            i = minOf(i + step, to)
        }
    }

    private fun drawArrow(canvas: Canvas, x: Float, y: Float, headingDeg: Float, size: Float) {
        val a = Math.toRadians(headingDeg.toDouble())
        fun pt(dx: Float, dy: Float): Pair<Float, Float> {
            val rx = (dx * cos(a) - dy * sin(a)).toFloat()
            val ry = (dx * sin(a) + dy * cos(a)).toFloat()
            return x + rx to y + ry
        }
        val tip = pt(0f, -size * 1.4f)
        val left = pt(-size, size)
        val right = pt(size, size)
        val back = pt(0f, size * 0.45f)
        val p = Path().apply {
            moveTo(tip.first, tip.second); lineTo(right.first, right.second)
            lineTo(back.first, back.second); lineTo(left.first, left.second); close()
        }
        posStroke.strokeWidth = 3f
        canvas.drawPath(p, posFill)
        canvas.drawPath(p, posStroke)
    }

    private fun drawLabel(canvas: Canvas, text: String, x: Float, y: Float, density: Float, bearing: Double) {
        labelPaint.textSize = 12f * density
        val w = labelPaint.measureText(text)
        canvas.save()
        canvas.rotate(bearing.toFloat(), x, y) // yazı dik dursun
        canvas.drawRoundRect(x - w / 2 - 6 * density, y - 16 * density, x + w / 2 + 6 * density, y + 4 * density, 6 * density, 6 * density, labelBg)
        canvas.drawText(text, x - w / 2, y, labelPaint)
        canvas.restore()
    }

    companion object {
        const val TILE = 256
    }
}
