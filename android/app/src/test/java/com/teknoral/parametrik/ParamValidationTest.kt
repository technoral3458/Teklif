package com.teknoral.parametrik

import com.teknoral.parametrik.domain.logic.ParamValidation
import com.teknoral.parametrik.domain.model.ImageSliceParams
import com.teknoral.parametrik.domain.model.MeshSliceParams
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParamValidationTest {

    @Test
    fun `kalinlik sifirsa gecersiz`() {
        val result = ParamValidation.validate(MeshSliceParams(thickness = 0.0))
        assertFalse(result.isValid)
    }

    @Test
    fun `taban derinligi en derin noktadan kucuk olmali`() {
        val invalid = ParamValidation.validate(ImageSliceParams(imgDepth = 100.0, minDepth = 120.0))
        assertFalse(invalid.isValid)

        val valid = ParamValidation.validate(ImageSliceParams(imgDepth = 100.0, minDepth = 40.0))
        assertTrue(valid.isValid)
    }

    @Test
    fun `400 panelden fazlasi uyari verir`() {
        val result = ParamValidation.validate(
            ImageSliceParams(imgW = 20000.0, imgH = 800.0, thickness = 18.0, gap = 6.0, orient = "v")
        )
        assertTrue(result.isValid)
        assertTrue(result.warnings.any { it.contains("400") })
    }

    @Test
    fun `checkbox alanlari kapaliyken gonderilmez`() {
        val fields = ImageSliceParams(invert = false, normalize = false).toFields()
        assertFalse(fields.containsKey("invert"))
        assertFalse(fields.containsKey("normalize"))

        val open = ImageSliceParams(invert = true, normalize = true).toFields()
        assertTrue(open["invert"] == "1")
        assertTrue(open["normalize"] == "1")
    }
}
