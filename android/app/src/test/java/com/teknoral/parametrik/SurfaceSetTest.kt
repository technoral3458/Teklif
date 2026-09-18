package com.teknoral.parametrik

import com.teknoral.parametrik.domain.model.Geometry
import com.teknoral.parametrik.domain.model.Part
import com.teknoral.parametrik.ui.viewer.SurfaceSet
import org.junit.Assert.assertEquals
import org.junit.Test

class SurfaceSetTest {

    /** Kare kontur: 4 nokta → 4 yan yüzey + 2 kapak = 6 yüzey, 8 köşe. */
    private fun squarePanel(): Geometry {
        val loop = floatArrayOf(0f, 0f, 100f, 0f, 100f, 100f, 0f, 100f)
        return Geometry(
            parts = listOf(Part(orientation = 0, position = 10f, thickness = 18f, loops = listOf(loop))),
            bb = floatArrayOf(10f, 0f, 0f, 28f, 100f, 100f),
            panelCount = 1,
            frameCount = 0
        )
    }

    @Test
    fun `yan yuzeyler ve kapaklar uretilir`() {
        val set = SurfaceSet.build(squarePanel())
        assertEquals(8, set.vertexCount)
        assertEquals(6, set.faceCount)
    }

    @Test
    fun `panel x ekseni boyunca ekstrude edilir`() {
        val set = SurfaceSet.build(squarePanel())
        // ilk 4 köşe ön yüz (x = p), sonraki 4 arka yüz (x = p + t)
        assertEquals(10f, set.vx[0], 1e-4f)
        assertEquals(28f, set.vx[4], 1e-4f)
        // 2B noktalar (y, z) olarak yerleşir
        assertEquals(100f, set.vy[1], 1e-4f)
        assertEquals(100f, set.vz[2], 1e-4f)
    }

    @Test
    fun `kayit y ekseni boyunca ekstrude edilir`() {
        val loop = floatArrayOf(0f, 0f, 50f, 0f, 50f, 50f)
        val geometry = Geometry(
            parts = listOf(Part(orientation = 1, position = 5f, thickness = 18f, loops = listOf(loop))),
            bb = null,
            panelCount = 0,
            frameCount = 1
        )
        val set = SurfaceSet.build(geometry)
        assertEquals(6, set.vertexCount)
        assertEquals(5f, set.vy[0], 1e-4f)
        assertEquals(23f, set.vy[3], 1e-4f)
        assertEquals(50f, set.vx[1], 1e-4f)
    }

    @Test
    fun `seyreltme kontur noktalarini azaltir`() {
        val points = FloatArray(40) { it.toFloat() }
        val geometry = Geometry(
            parts = listOf(Part(orientation = 0, position = 0f, thickness = 1f, loops = listOf(points))),
            bb = null,
            panelCount = 1,
            frameCount = 0
        )
        val full = SurfaceSet.build(geometry, step = 1)
        val lite = SurfaceSet.build(geometry, step = 2)
        assertEquals(40, full.vertexCount)
        assertEquals(20, lite.vertexCount)
    }
}
