package com.manhwatranslate.core.layout

import com.manhwatranslate.core.geometry.RectI
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TextLayoutEngineTest {

    private val measurer = FakeMonospaceMeasurer()
    private val engine = TextLayoutEngine(measurer)

    @Test
    fun `short text keeps a large font size inside a big bubble`() {
        val layout = engine.layout("Hi there", RectI(0, 0, 400, 300), LayoutStyle(maxTextSizePx = 60f))
        assertTrue(layout.fits)
        assertTrue(layout.textSizePx > 30f, "expected a large size, got ${layout.textSizePx}")
        assertEquals(1, layout.lines.size)
    }

    @Test
    fun `long text shrinks until it fits`() {
        val box = RectI(0, 0, 200, 90)
        val short = engine.layout("Hello", box, LayoutStyle(maxTextSizePx = 48f))
        val long = engine.layout(
            "This sentence is much longer than the bubble and must wrap across several lines",
            box,
            LayoutStyle(maxTextSizePx = 48f),
        )
        assertTrue(short.textSizePx > long.textSizePx)
        assertTrue(long.lines.size > 1)
    }

    @Test
    fun `no line exceeds the available width`() {
        val box = RectI(10, 20, 310, 260)
        val text = "Tôi chưa từng nghĩ mọi chuyện lại kết thúc như thế này cả"
        val layout = engine.layout(text, box, LayoutStyle(maxTextSizePx = 70f))
        val limit = layout.availableWidth
        for (line in layout.lines) {
            val width = measurer.measureWidth(line, layout.textSizePx, layout.font)
            assertTrue(width <= limit + 0.5f, "line '$line' is ${width}px > $limit")
        }
    }

    @Test
    fun `block height stays inside the bubble`() {
        val box = RectI(0, 0, 260, 120)
        val layout = engine.layout(
            "Cánh cổng này đã đóng suốt trăm năm rồi. Chưa ai quay về còn sống cả.",
            box,
            LayoutStyle(maxTextSizePx = 64f),
        )
        assertTrue(layout.blockHeightPx <= box.height, "block ${layout.blockHeightPx} > ${box.height}")
    }

    @Test
    fun `cjk text wraps without spaces`() {
        val layout = engine.layout(
            "この門は百年も閉ざされている",
            RectI(0, 0, 120, 200),
            LayoutStyle(maxTextSizePx = 40f),
        )
        assertTrue(layout.lines.size >= 2)
        assertTrue(layout.lines.all { it.isNotEmpty() })
    }

    @Test
    fun `a single huge word is broken by characters`() {
        val layout = engine.layout(
            "Supercalifragilisticexpialidocious",
            RectI(0, 0, 80, 200),
            LayoutStyle(maxTextSizePx = 40f),
        )
        assertTrue(layout.lines.size >= 2)
        val limit = layout.availableWidth
        layout.lines.forEach { line ->
            assertTrue(measurer.measureWidth(line, layout.textSizePx, layout.font) <= limit + 0.5f)
        }
    }

    @Test
    fun `text that can never fit is reported as not fitting`() {
        val layout = engine.layout(
            "word ".repeat(120),
            RectI(0, 0, 40, 40),
            LayoutStyle(maxTextSizePx = 40f, minTextSizePx = 20f, maxLines = 3),
        )
        assertFalse(layout.fits)
        assertTrue(layout.lines.size > 3)
    }

    @Test
    fun `blank text produces an empty layout`() {
        val layout = engine.layout("   ", RectI(0, 0, 100, 100))
        assertTrue(layout.lines.isEmpty())
        assertEquals(0f, layout.blockHeightPx)
    }

    @Test
    fun `baselines are evenly spaced and start inside the box`() {
        val box = RectI(0, 100, 300, 400)
        val layout = engine.layout("one two three four five six seven eight nine ten", box, LayoutStyle(maxTextSizePx = 40f))
        assertTrue(layout.lines.size > 2)
        val spacing = layout.baselineFor(1) - layout.baselineFor(0)
        assertEquals(layout.lineHeightPx, spacing, 0.01f)
        assertTrue(layout.baselineFor(0) > box.top)
        assertTrue(layout.baselineFor(layout.lines.lastIndex) < box.bottom)
    }

    @Test
    fun `a long translation never spills out of a small box`() {
        val layout = engine.layout(
            "Tôi đã không nghĩ rằng bước bước trên phanh trong giữa nói chuyện lại kết thúc như thế này",
            RectI(0, 0, 190, 90),
            LayoutStyle(maxTextSizePx = 34f, minTextSizePx = 11f, preferredLines = 2),
        )
        assertTrue(layout.fits, "layout should fit, size ${layout.textSizePx} lines ${layout.lines.size}")
        assertTrue(layout.blockHeightPx <= 90f, "block ${layout.blockHeightPx} exceeds the box")
        for (line in layout.lines) {
            assertTrue(
                measurer.measureWidth(line, layout.textSizePx, layout.font) <= layout.box.width,
                "line '$line' is wider than the box",
            )
        }
    }

    @Test
    fun `the shape of the original lettering is respected`() {
        val text = "Tôi đã không nghĩ rằng bước bước trên phanh trong."
        val wide = engine.layout(text, RectI(0, 0, 420, 120), LayoutStyle(maxTextSizePx = 40f, preferredLines = 2))
        val tall = engine.layout(text, RectI(0, 0, 420, 240), LayoutStyle(maxTextSizePx = 40f, preferredLines = 4))
        assertTrue(wide.lines.size <= 3, "two original lines gave ${wide.lines.size} lines")
        assertTrue(wide.textSizePx >= tall.textSizePx * 0.5f)
    }

    @Test
    fun `the outline is reserved so stroked text stays inside`() {
        val text = "Ngươi nghĩ mình có thể chạy thoát khỏi ta sao"
        val layout = engine.layout(
            text,
            RectI(0, 0, 240, 100),
            LayoutStyle(maxTextSizePx = 32f, strokeWidthFactor = 0.09f),
        )
        val widest = layout.lines.maxOf { measurer.measureWidth(it, layout.textSizePx, layout.font) }
        assertTrue(widest + layout.textSizePx * 0.09f * 2f <= layout.box.width, "outline spills past the box")
    }
}
