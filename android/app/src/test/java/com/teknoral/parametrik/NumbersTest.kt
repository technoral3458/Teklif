package com.teknoral.parametrik

import com.teknoral.parametrik.core.Numbers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NumbersTest {

    @Test
    fun `virgul ve nokta ayni sekilde cozulur`() {
        assertEquals(12.5, Numbers.parse("12,5")!!, 1e-9)
        assertEquals(12.5, Numbers.parse("12.5")!!, 1e-9)
        assertEquals(12.5, Numbers.parse(" 12 , 5 ")!!, 1e-9)
    }

    @Test
    fun `bos ve gecersiz girdi null`() {
        assertNull(Numbers.parse(""))
        assertNull(Numbers.parse("   "))
        assertNull(Numbers.parse(","))
        assertNull(Numbers.parse("abc"))
    }

    @Test
    fun `arayuz gosterimi virgullu`() {
        assertEquals("24,0", Numbers.format(24.0, 1))
        assertEquals("0,15", Numbers.format(0.15, 2))
    }

    @Test
    fun `sunucuya her zaman nokta gider`() {
        assertEquals("24", Numbers.toServer(24.0))
        assertEquals("0.15", Numbers.toServer(0.15))
        assertEquals("2100", Numbers.toServer(2100.0))
    }
}
