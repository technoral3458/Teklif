package com.teknoral.parametrik.domain.model

/**
 * Bir ekstrüzyon parçası.
 *
 * [orientation] 0 → panel: 2B noktalar (y, z), parça x ekseni boyunca
 * [position]..[position] + [thickness] arasına ekstrüde edilir.
 * [orientation] 1 → kayıt: 2B noktalar (x, z), parça y ekseni boyunca ekstrüde edilir.
 *
 * [loops] her biri düzleştirilmiş kapalı kontur: [u0, v0, u1, v1, ...]
 */
data class Part(
    val orientation: Int,
    val position: Float,
    val thickness: Float,
    val loops: List<FloatArray>
) {
    val isPanel: Boolean get() = orientation == 0
}

/** [bb] = [x0, y0, z0, x1, y1, z1] */
data class Geometry(
    val parts: List<Part>,
    val bb: FloatArray?,
    val panelCount: Int,
    val frameCount: Int
) {
    val isEmpty: Boolean get() = parts.isEmpty()

    companion object {
        val EMPTY = Geometry(emptyList(), null, 0, 0)
    }
}
