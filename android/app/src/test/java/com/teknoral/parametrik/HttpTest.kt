package com.teknoral.parametrik

import com.teknoral.parametrik.data.remote.Http
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpTest {

    @Test
    fun `is numarasi location icinden okunur`() {
        assertEquals(12L, Http.jobIdFrom("/parametric/12"))
        assertEquals(12L, Http.jobIdFrom("/parametric/12?msg=50%20panel%20hazirlandi."))
        assertNull(Http.jobIdFrom("/parametric?err=Hata"))
    }

    @Test
    fun `turkce hata mesaji cozulur`() {
        val location = "/parametric/5?err=%C3%96nce%20%27Dilimle%27%20butonuna%20bas%C4%B1n."
        assertEquals("Önce 'Dilimle' butonuna basın.", Http.queryValue(location, "err"))
    }

    @Test
    fun `msg parametresi okunur`() {
        assertEquals("50 panel hazırlandı.", Http.queryValue("/parametric/5?msg=50+panel+haz%C4%B1rland%C4%B1.", "msg"))
        assertNull(Http.queryValue("/parametric/5", "msg"))
    }

    @Test
    fun `login yonlendirmesi taninir`() {
        assertTrue(Http.isLoginRedirect("/login"))
        assertTrue(Http.isLoginRedirect("/login?next=/parametric"))
        assertFalse(Http.isLoginRedirect("/parametric/12"))
        assertFalse(Http.isLoginRedirect(null))
    }
}
