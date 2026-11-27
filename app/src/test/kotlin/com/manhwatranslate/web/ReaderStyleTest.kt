package com.manhwatranslate.web

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReaderStyleTest {

    @Test
    fun `light sheet paints the paper background and ink text`() {
        val css = ReaderStyle.css(dark = false)

        assertTrue(css.contains("background:#FAF6F3!important"))
        assertTrue(css.contains("color:#221C1A!important"))
        assertFalse(css.contains("color-scheme:dark"))
    }

    @Test
    fun `dark sheet repaints the page instead of leaving it white`() {
        val css = ReaderStyle.css(dark = true)

        assertTrue(css.contains("background:#0D0A0D!important"))
        assertTrue(css.contains("color:#F4E9E4!important"))
        assertTrue(css.contains("color-scheme:dark!important"))
        assertTrue(css.contains("background-color:transparent!important"))
    }

    @Test
    fun `both sheets keep one reading column and rounded artwork`() {
        listOf(false, true).forEach { dark ->
            val css = ReaderStyle.css(dark)

            assertTrue(css.contains("max-width:780px!important"), "reading column missing (dark=$dark)")
            assertTrue(css.contains("border-radius:14px!important"), "image radius missing (dark=$dark)")
            assertTrue(css.contains("overflow-x:hidden!important"), "horizontal scroll not stopped")
        }
    }

    @Test
    fun `every rule is closed and no declaration is empty`() {
        listOf(false, true).forEach { dark ->
            val css = ReaderStyle.css(dark)

            assertEquals(0, css.count { it == '{' } - css.count { it == '}' }, "unbalanced braces")
            assertFalse(css.contains(";;"), "empty declaration")
            assertFalse(css.contains("{}"), "empty rule")
        }
    }
}
