package com.manhwatranslate.core.scan

import com.manhwatranslate.core.image.ArgbImage
import com.manhwatranslate.core.image.Colors
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ImageTilerTest {

    private fun page(height: Int) =
        ArgbImage.filled(120, height, Colors.opaque(255, 255, 255))

    @Test
    fun `short pages are a single tile`() {
        val tiles = ImageTiler.split(page(500), maxTileHeight = 1800)
        assertEquals(1, tiles.size)
        assertEquals(500, tiles.first().rect.height)
    }

    @Test
    fun `tall pages are split with overlap and reach the bottom`() {
        val tiles = ImageTiler.split(page(5000), maxTileHeight = 1800, overlap = 96)
        assertTrue(tiles.size >= 3)
        assertEquals(0, tiles.first().rect.top)
        assertEquals(5000, tiles.last().rect.bottom)
        assertEquals(1800, tiles.first().rect.height)
        for (index in 1 until tiles.size) {
            val previous = tiles[index - 1].rect
            val current = tiles[index].rect
            assertTrue(current.top < previous.bottom, "tiles must overlap")
            assertEquals(96, previous.bottom - current.top)
        }
    }

    @Test
    fun `tile images match their rectangles`() {
        val tiles = ImageTiler.split(page(3000), maxTileHeight = 1000, overlap = 50)
        tiles.forEach { tile ->
            assertEquals(tile.rect.width, tile.image.width)
            assertEquals(tile.rect.height, tile.image.height)
        }
    }

    @Test
    fun `pages exactly one tile tall are not split`() {
        val tiles = ImageTiler.split(page(1800), maxTileHeight = 1800)
        assertEquals(1, tiles.size)
    }

    @Test
    fun `overlap larger than half a tile is clamped`() {
        val tiles = ImageTiler.split(page(4000), maxTileHeight = 1000, overlap = 900)
        assertEquals(1000, tiles.first().rect.height)
        assertTrue(tiles.size >= 5)
    }
}
