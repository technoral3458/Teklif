package com.teknoral.parametrik

import com.teknoral.parametrik.domain.logic.PanelEstimator
import org.junit.Assert.assertEquals
import org.junit.Test

class PanelEstimatorTest {

    @Test
    fun `adim kalinlik arti bosluk`() {
        assertEquals(24.0, PanelEstimator.pitch(18.0, 6.0), 1e-9)
    }

    @Test
    fun `panel sayisi formulu`() {
        // 1200 mm, 18 mm panel, 6 mm boşluk → floor((1200-18)/24)+1 = 50
        assertEquals(50, PanelEstimator.panelCount(1200.0, 18.0, 6.0))
    }

    @Test
    fun `en kalinliktan kucukse tek panel`() {
        assertEquals(1, PanelEstimator.panelCount(10.0, 18.0, 6.0))
    }

    @Test
    fun `gecersiz degerler sifir doner`() {
        assertEquals(0, PanelEstimator.panelCount(1200.0, 0.0, 6.0))
        assertEquals(0, PanelEstimator.panelCount(0.0, 18.0, 6.0))
    }

    @Test
    fun `yigin boyu`() {
        assertEquals(1194.0, PanelEstimator.stackLength(50, 18.0, 6.0), 1e-9)
    }
}
