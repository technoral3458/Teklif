package com.teknoral.parametrik.domain.logic

import kotlin.math.floor

/**
 * Sunucuya gitmeden panel sayısı tahmini.
 *
 *   pitch = thickness + gap
 *   panelSayısı = if (en < thickness) 1 else floor((en - thickness) / pitch) + 1
 */
object PanelEstimator {

    const val MAX_PANELS = 400

    fun pitch(thickness: Double, gap: Double): Double = thickness + gap

    fun panelCount(span: Double, thickness: Double, gap: Double): Int {
        if (thickness <= 0.0 || span <= 0.0) return 0
        val pitch = pitch(thickness, gap)
        if (pitch <= 0.0) return 1
        if (span < thickness) return 1
        return floor((span - thickness) / pitch).toInt() + 1
    }

    /** Panellerin kapladığı toplam boy. */
    fun stackLength(count: Int, thickness: Double, gap: Double): Double {
        if (count <= 0) return 0.0
        return count * thickness + (count - 1) * gap
    }
}
