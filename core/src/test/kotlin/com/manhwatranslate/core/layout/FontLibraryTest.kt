package com.manhwatranslate.core.layout

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class FontLibraryTest {

    @Test
    fun `the whole pack is listed`() {
        assertEquals(41, FontLibrary.faces.size)
        assertEquals(FontLibrary.faces.size, FontLibrary.faces.map { it.file }.toSet().size)
    }

    @Test
    fun `every listed face ships in the app assets`() {
        val dir = File("../app/src/main/assets/fonts/library")
        assertTrue(dir.isDirectory, "library missing at ${dir.absolutePath}")
        for (face in FontLibrary.faces) {
            val file = File(dir, face.file)
            assertTrue(file.isFile && file.length() > 10_000, "missing face: ${face.file}")
        }
    }

    @Test
    fun `a pick keeps the slant and finds the nearest weight`() {
        val bold = FontLibrary.closest(weight = 700, italic = false)
        val italic = FontLibrary.closest(weight = 400, italic = true)

        assertTrue(!bold.italic)
        assertEquals(700, bold.weight)
        assertTrue(italic.italic, "asked for an italic face, got ${italic.displayName}")
    }

    @Test
    fun `a face is found from the asset path the renderer uses`() {
        val face = FontLibrary.faces.first()
        assertNotNull(FontLibrary.faceAt(face.assetPath))
        assertEquals(face, FontLibrary.faceAt(face.assetPath))
    }
}
