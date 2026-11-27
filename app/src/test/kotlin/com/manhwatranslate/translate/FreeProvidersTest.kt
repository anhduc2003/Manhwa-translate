package com.manhwatranslate.translate

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FreeProvidersTest {

    private fun bytes(text: String): Int = text.toByteArray(Charsets.UTF_8).size

    @Test
    fun `a short line stays one piece`() {
        assertEquals(listOf("안녕하세요"), splitToFit("안녕하세요", 480))
    }

    @Test
    fun `a long line is split under the byte limit`() {
        val text = (1..80).joinToString(" ") { "문장$it" }
        val pieces = splitToFit(text, 120)
        assertTrue(pieces.size > 1)
        assertTrue(pieces.all { bytes(it) <= 120 }, "every piece fits the endpoint's limit")
        assertEquals(text, pieces.joinToString(" "))
    }

    @Test
    fun `a single word longer than the limit is split by character`() {
        val text = "가".repeat(200)
        val pieces = splitToFit(text, 60)
        assertTrue(pieces.size > 1)
        assertTrue(pieces.all { bytes(it) <= 60 })
        assertEquals(text, pieces.joinToString(""))
    }
}
