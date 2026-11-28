package com.manhwatranslate.render

import com.manhwatranslate.core.layout.FontFamilyKind
import com.manhwatranslate.core.layout.FontWeightLevel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class ManhwaFontsTest {

    @Test
    fun `dialogue picks a real bold and a real italic file`() {
        val regular = ManhwaFonts.assetFor(FontFamilyKind.SANS, FontWeightLevel.REGULAR, italic = false)
        val bold = ManhwaFonts.assetFor(FontFamilyKind.SANS, FontWeightLevel.BOLD, italic = false)
        val italic = ManhwaFonts.assetFor(FontFamilyKind.SANS, FontWeightLevel.REGULAR, italic = true)
        val boldItalic = ManhwaFonts.assetFor(FontFamilyKind.SANS, FontWeightLevel.BLACK, italic = true)

        assertEquals("fonts/manhwa_sans_regular.ttf", regular)
        assertEquals("fonts/manhwa_sans_bold.ttf", bold)
        assertEquals("fonts/manhwa_sans_italic.ttf", italic)
        assertEquals("fonts/manhwa_sans_bold_italic.ttf", boldItalic)
    }

    @Test
    fun `every bundled family resolves to a face`() {
        for (family in ManhwaFonts.families) {
            for (weight in FontWeightLevel.entries) {
                for (italic in listOf(false, true)) {
                    val path = ManhwaFonts.assetFor(family, weight, italic)
                    assertTrue(path != null && path.startsWith("fonts/"), "$family/$weight/$italic -> $path")
                }
            }
        }
    }

    @Test
    fun `families keep their own lettering instead of collapsing into one`() {
        val sans = ManhwaFonts.assetFor(FontFamilyKind.SANS, heavy = false, italic = false)
        val serif = ManhwaFonts.assetFor(FontFamilyKind.SERIF, heavy = false, italic = false)
        val display = ManhwaFonts.assetFor(FontFamilyKind.CONDENSED, heavy = false, italic = false)

        assertNotEquals(sans, serif)
        assertNotEquals(sans, display)
        assertNotEquals(serif, display)
    }

    @Test
    fun `system families are left to the system`() {
        assertNull(ManhwaFonts.assetFor(FontFamilyKind.MONOSPACE, heavy = true, italic = false))
        assertNull(ManhwaFonts.assetFor(FontFamilyKind.CUSTOM, heavy = false, italic = false))
    }

    @Test
    fun `every referenced face exists in the app assets`() {
        val fontsDir = File("src/main/assets/fonts")
        assertTrue(fontsDir.isDirectory, "assets/fonts missing at ${fontsDir.absolutePath}")

        val referenced = ManhwaFonts.families
            .flatMap { family ->
                FontWeightLevel.entries.flatMap { weight ->
                    listOf(false, true).mapNotNull { italic -> ManhwaFonts.assetFor(family, weight, italic) }
                }
            }
            .toSet()

        for (path in referenced) {
            val file = File(fontsDir, path.substringAfterLast('/'))
            assertTrue(file.isFile && file.length() > 10_000, "missing or empty: $path")
            val signature = file.inputStream().use { input ->
                val head = ByteArray(4)
                input.read(head)
                head.joinToString("") { byte -> "%02x".format(byte) }
            }
            assertTrue(signature in SFNT_SIGNATURES, "$path is not a font file (starts with $signature)")
        }
    }

    private companion object {
        /** TrueType, OpenType with CFF outlines, the older Apple tag, and a font collection. */
        val SFNT_SIGNATURES = setOf("00010000", "4f54544f", "74727565", "74746366")
    }
}
