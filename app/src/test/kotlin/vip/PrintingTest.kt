package vip

import kotlin.test.Test
import kotlin.test.assertEquals

class PrintingTest {
    @Test
    fun `control characters, newlines and the escape byte become a single space`() {
        assertEquals("a ::error b [31mc d", "a\r\n::error b\u001B[31mc\td".printable())
        assertEquals("plain", "plain".printable())
    }
}
