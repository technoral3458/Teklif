package com.teknoral.parametrik.ui.viewer

import com.teknoral.parametrik.domain.model.Geometry

/**
 * Geometriyi bir kez yüzeylere açar ve düz dizilerde tutar; her karede yeniden
 * üretilmez. Köşeler global bir dizide toplanır, yüzeyler indeksle gösterir —
 * böylece her karede köşe başına tek bir döndürme hesabı yapılır.
 *
 * Her kontur için:
 *   front[k] = (p, u, v)      / kayıtta (u, p, v)
 *   back[k]  = (p + t, u, v)  / kayıtta (u, p + t, v)
 * Yan yüzeyler: [front[k], front[k+1], back[k+1], back[k]]
 * Kapaklar: front (olduğu gibi), back (ters çevrilmiş)
 */
class SurfaceSet private constructor(
    val vx: FloatArray,
    val vy: FloatArray,
    val vz: FloatArray,
    val faceStart: IntArray,
    val faceLen: IntArray,
    val facePanel: BooleanArray,
    val faceVerts: IntArray,
    val faceCount: Int,
    val bounds: FloatArray
) {
    val vertexCount: Int get() = vx.size

    companion object {

        /**
         * @param step kontur seyreltme adımı (1 = tam kalite, 2 = her 2. nokta)
         */
        fun build(geometry: Geometry, step: Int = 1): SurfaceSet {
            val loops = ArrayList<LoopPlan>(geometry.parts.sumOf { it.loops.size })
            var vertexTotal = 0
            var faceTotal = 0
            var faceVertTotal = 0

            for (part in geometry.parts) {
                for (loop in part.loops) {
                    val pointCount = loop.size / 2
                    val kept = keptCount(pointCount, step)
                    if (kept < 3) continue
                    loops += LoopPlan(part.orientation == 0, part.position, part.thickness, loop, kept, step)
                    vertexTotal += kept * 2
                    faceTotal += kept + 2
                    faceVertTotal += kept * 4 + kept * 2
                }
            }

            val vx = FloatArray(vertexTotal)
            val vy = FloatArray(vertexTotal)
            val vz = FloatArray(vertexTotal)
            val faceStart = IntArray(faceTotal)
            val faceLen = IntArray(faceTotal)
            val facePanel = BooleanArray(faceTotal)
            val faceVerts = IntArray(faceVertTotal)

            var vi = 0
            var fi = 0
            var fvi = 0
            var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE; var minZ = Float.MAX_VALUE
            var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE

            for (plan in loops) {
                val n = plan.kept
                val base = vi
                val source = plan.points
                val pointCount = source.size / 2

                val far = plan.position + plan.thickness
                // ön ve arka yüz köşeleri: index = k * step (kept sayesinde her zaman geçerli)
                for (side in 0..1) {
                    val depth = if (side == 0) plan.position else far
                    for (k in 0 until n) {
                        val index = (k * plan.step).coerceAtMost(pointCount - 1)
                        val u = source[index * 2]
                        val v = source[index * 2 + 1]
                        if (plan.isPanel) {
                            vx[vi] = depth; vy[vi] = u; vz[vi] = v
                        } else {
                            vx[vi] = u; vy[vi] = depth; vz[vi] = v
                        }
                        if (vx[vi] < minX) minX = vx[vi]
                        if (vx[vi] > maxX) maxX = vx[vi]
                        if (vy[vi] < minY) minY = vy[vi]
                        if (vy[vi] > maxY) maxY = vy[vi]
                        if (vz[vi] < minZ) minZ = vz[vi]
                        if (vz[vi] > maxZ) maxZ = vz[vi]
                        vi++
                    }
                }

                // yan yüzeyler
                for (edge in 0 until n) {
                    val next = (edge + 1) % n
                    faceStart[fi] = fvi
                    faceLen[fi] = 4
                    facePanel[fi] = plan.isPanel
                    faceVerts[fvi++] = base + edge
                    faceVerts[fvi++] = base + next
                    faceVerts[fvi++] = base + n + next
                    faceVerts[fvi++] = base + n + edge
                    fi++
                }
                // ön kapak
                faceStart[fi] = fvi
                faceLen[fi] = n
                facePanel[fi] = plan.isPanel
                for (c in 0 until n) faceVerts[fvi++] = base + c
                fi++
                // arka kapak (ters)
                faceStart[fi] = fvi
                faceLen[fi] = n
                facePanel[fi] = plan.isPanel
                for (c in n - 1 downTo 0) faceVerts[fvi++] = base + n + c
                fi++
            }

            val bb = geometry.bb ?: if (vertexTotal > 0) {
                floatArrayOf(minX, minY, minZ, maxX, maxY, maxZ)
            } else {
                floatArrayOf(0f, 0f, 0f, 1f, 1f, 1f)
            }

            return SurfaceSet(vx, vy, vz, faceStart, faceLen, facePanel, faceVerts, fi, bb)
        }

        private fun keptCount(pointCount: Int, step: Int): Int {
            if (step <= 1) return pointCount
            val kept = (pointCount + step - 1) / step
            return if (kept < 3) pointCount else kept
        }
    }

    private class LoopPlan(
        val isPanel: Boolean,
        val position: Float,
        val thickness: Float,
        val points: FloatArray,
        val kept: Int,
        requestedStep: Int
    ) {
        val step: Int = if (kept == points.size / 2) 1 else requestedStep
    }
}
