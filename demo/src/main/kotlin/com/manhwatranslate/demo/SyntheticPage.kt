package com.manhwatranslate.demo

import com.manhwatranslate.core.geometry.RectI
import com.manhwatranslate.core.model.TextLine
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.GradientPaint
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Ellipse2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import kotlin.random.Random

/**
 * Draws a manhwa-like page with known text boxes so the detection -> erase -> translate ->
 * render pipeline can be exercised end to end without a device or an OCR model.
 */
class SyntheticPage(private val measurer: AwtTextMeasurer) {

    private val BoldFont = com.manhwatranslate.core.layout.FontSpec(
        weight = com.manhwatranslate.core.layout.FontWeightLevel.BOLD,
    )

    data class Page(val image: BufferedImage, val texts: List<GroundTruthText>)

    data class GroundTruthText(
        val box: RectI,
        val lines: List<TextLine>,
        val text: String,
        val inverted: Boolean = false,
    )

    fun render(width: Int = 1000, height: Int = 1560): Page {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)

        drawBackdrop(graphics, width, height)

        val texts = ArrayList<GroundTruthText>()

        texts += caption(graphics, RectI(60, 40, 340, 108), "Chapter 12")

        texts += speechBubble(
            graphics = graphics,
            bounds = RectI(70, 150, 520, 430),
            lines = listOf("I never thought it would", "end like this."),
            fontSize = 30f,
        )

        texts += speechBubble(
            graphics = graphics,
            bounds = RectI(590, 170, 950, 400),
            lines = listOf("Give me the sword", "and I'll let you live."),
            fontSize = 26f,
        )

        texts += soundEffect(graphics, RectI(560, 470, 940, 660), "CRASH!")

        texts += speechBubble(
            graphics = graphics,
            bounds = RectI(60, 700, 540, 1010),
            lines = listOf("The gate has been closed for", "a hundred years.", "No one has come back alive."),
            fontSize = 24f,
            lineSpacing = 1.25f,
        )

        texts += darkBubble(
            graphics = graphics,
            bounds = RectI(600, 720, 950, 960),
            lines = listOf("You think you can", "run from me?"),
            fontSize = 26f,
        )

        texts += speechBubble(
            graphics = graphics,
            bounds = RectI(120, 1080, 420, 1210),
            lines = listOf("Psst… over here."),
            fontSize = 18f,
            compact = true,
        )

        texts += speechBubble(
            graphics = graphics,
            bounds = RectI(480, 1250, 950, 1520),
            lines = listOf("The sword you are holding", "belongs to the fallen king.", "Every soldier in this city", "is looking for it."),
            fontSize = 24f,
            lineSpacing = 1.3f,
        )

        graphics.dispose()
        return Page(image, texts)
    }

    private fun drawBackdrop(graphics: Graphics2D, width: Int, height: Int) {
        graphics.paint = GradientPaint(0f, 0f, Color(0xFB, 0xFB, 0xFB), 0f, height.toFloat(), Color(0xEC, 0xEC, 0xEE))
        graphics.fillRect(0, 0, width, height)

        val random = Random(7)
        for (index in 0 until 7) {
            val gray = 0xE0 + random.nextInt(0x14)
            graphics.color = Color(gray, gray, gray, 140)
            val w = 160 + random.nextInt(260)
            val h = 120 + random.nextInt(260)
            val x = random.nextInt(width - w)
            val y = random.nextInt(height - h)
            graphics.fill(Ellipse2D.Float(x.toFloat(), y.toFloat(), w.toFloat(), h.toFloat()))
        }

        graphics.color = Color(0xDD, 0xDD, 0xDF)
        graphics.stroke = BasicStroke(3f)
        for (index in 0 until 40) {
            val y = 1200 + index * 8
            graphics.drawLine(0, y, width, y - 90)
        }

        graphics.color = Color(0xC8, 0xC8, 0xCC, 180)
        graphics.fill(RoundRectangle2D.Float(70f, 640f, 860f, 12f, 12f, 12f))
    }

    private fun caption(graphics: Graphics2D, bounds: RectI, text: String): GroundTruthText {
        graphics.color = Color(0x1C, 0x1C, 0x22)
        graphics.fill(RoundRectangle2D.Float(
            bounds.left.toFloat(), bounds.top.toFloat(),
            bounds.width.toFloat(), bounds.height.toFloat(), 8f, 8f,
        ))
        val lines = drawTextLines(graphics, bounds, listOf(text), 26f, Color.WHITE)
        return groundTruth(lines, inverted = true)
    }

    private fun speechBubble(
        graphics: Graphics2D,
        bounds: RectI,
        lines: List<String>,
        fontSize: Float,
        lineSpacing: Float = 1.15f,
        compact: Boolean = false,
    ): GroundTruthText {
        graphics.color = Color.WHITE
        graphics.fill(Ellipse2D.Float(
            bounds.left.toFloat(), bounds.top.toFloat(),
            bounds.width.toFloat(), bounds.height.toFloat(),
        ))
        graphics.color = Color(0x1A, 0x1A, 0x1A)
        graphics.stroke = BasicStroke(if (compact) 3f else 5f)
        graphics.draw(Ellipse2D.Float(
            bounds.left.toFloat() + 2, bounds.top.toFloat() + 2,
            bounds.width.toFloat() - 4, bounds.height.toFloat() - 4,
        ))
        val inner = bounds.expanded(if (compact) -20 else -40)
        val textLines = drawTextLines(graphics, inner, lines, fontSize, Color(0x11, 0x11, 0x11), lineSpacing)
        drawTail(graphics, bounds)
        return groundTruth(textLines)
    }

    private fun darkBubble(
        graphics: Graphics2D,
        bounds: RectI,
        lines: List<String>,
        fontSize: Float,
    ): GroundTruthText {
        graphics.color = Color(0x14, 0x16, 0x1C)
        graphics.fill(RoundRectangle2D.Float(
            bounds.left.toFloat(), bounds.top.toFloat(),
            bounds.width.toFloat(), bounds.height.toFloat(), 40f, 40f,
        ))
        val inner = bounds.expanded(-30)
        val textLines = drawTextLines(graphics, inner, lines, fontSize, Color(0xF4, 0xF4, 0xF4), 1.2f)
        return groundTruth(textLines, inverted = true)
    }

    private fun soundEffect(graphics: Graphics2D, bounds: RectI, text: String): GroundTruthText {
        graphics.paint = GradientPaint(
            bounds.left.toFloat(), bounds.top.toFloat(), Color(0x55, 0x55, 0x5C),
            bounds.right.toFloat(), bounds.bottom.toFloat(), Color(0x1E, 0x1E, 0x24),
        )
        graphics.fillRect(bounds.left, bounds.top, bounds.width, bounds.height)
        val random = Random(3)
        graphics.color = Color(0x88, 0x88, 0x90, 120)
        graphics.stroke = BasicStroke(2f)
        for (index in 0 until 18) {
            val x = bounds.left + random.nextInt(bounds.width)
            graphics.drawLine(x, bounds.top, x + 40, bounds.bottom)
        }

        val font = measurer.font(72f, bold = true)
        graphics.font = font
        val metrics = font.getLineMetrics(text, graphics.fontRenderContext)
        val width = measurer.measureWidth(text, 72f, BoldFont)
        val x = bounds.centerX - width / 2f
        val baseline = bounds.centerY + (metrics.ascent - metrics.descent) / 2f
        val glyphs = font.createGlyphVector(graphics.fontRenderContext, text)
        val shape = glyphs.getOutline(x, baseline)
        graphics.color = Color.BLACK
        graphics.stroke = BasicStroke(9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        graphics.draw(shape)
        graphics.color = Color(0xFF, 0xF2, 0xC8)
        graphics.fill(shape)

        val line = TextLine(
            box = RectI(
                x.toInt() - 2,
                (baseline - metrics.ascent).toInt() - 2,
                (x + width).toInt() + 2,
                (baseline + metrics.descent).toInt() + 2,
            ),
            text = text,
        )
        return GroundTruthText(
            box = line.box.union(line.box),
            lines = listOf(line),
            text = text,
            inverted = true,
        )
    }

    private fun drawTextLines(
        graphics: Graphics2D,
        box: RectI,
        lines: List<String>,
        fontSize: Float,
        color: Color,
        lineSpacing: Float = 1.15f,
    ): List<TextLine> {
        val font = measurer.font(fontSize, bold = true)
        graphics.font = font
        val metrics = font.getLineMetrics("Ag", graphics.fontRenderContext)
        val lineHeight = measurer.lineHeight(fontSize, BoldFont) * lineSpacing
        val blockHeight = lineHeight * lines.size
        val top = box.centerY - blockHeight / 2f
        val result = ArrayList<TextLine>()
        for ((index, line) in lines.withIndex()) {
            val width = measurer.measureWidth(line, fontSize, BoldFont)
            val left = box.centerX - width / 2f
            val baseline = top + index * lineHeight + metrics.ascent
            graphics.color = color
            graphics.drawString(line, left, baseline)
            result += TextLine(
                box = RectI(
                    left.toInt(),
                    (baseline - metrics.ascent).toInt(),
                    (left + width).toInt() + 1,
                    (baseline + metrics.descent).toInt(),
                ),
                text = line,
            )
        }
        return result
    }

    private fun drawTail(graphics: Graphics2D, bounds: RectI) {
        graphics.color = Color.WHITE
        val tail = java.awt.Polygon(
            intArrayOf(bounds.centerX - 18, bounds.centerX + 26, bounds.centerX + 6),
            intArrayOf(bounds.bottom - 30, bounds.bottom - 6, bounds.bottom + 34),
            3,
        )
        graphics.fill(tail)
        graphics.color = Color(0x1A, 0x1A, 0x1A)
        graphics.stroke = BasicStroke(4f)
        graphics.drawLine(bounds.centerX + 4, bounds.bottom - 12, bounds.centerX + 6, bounds.bottom + 33)
    }

    private fun groundTruth(lines: List<TextLine>, inverted: Boolean = false): GroundTruthText {
        val box = lines.map { it.box }.reduce { acc, rect -> acc.union(rect) }.expanded(2)
        return GroundTruthText(
            box = box,
            lines = lines,
            text = lines.joinToString(" ") { it.text },
            inverted = inverted,
        )
    }

    fun fontFor(size: Float, bold: Boolean): Font = measurer.font(size, bold)
}
