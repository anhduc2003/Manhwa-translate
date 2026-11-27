package com.manhwatranslate.demo

import com.manhwatranslate.core.image.ArgbImage
import com.manhwatranslate.core.render.PageCompositor
import com.manhwatranslate.core.render.RenderItem
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.AffineTransform

/** Java2D twin of the Android compositor, used to verify the layout math off-device. */
class Java2DCompositor(private val measurer: AwtTextMeasurer) : PageCompositor {

    override fun compose(page: ArgbImage, items: List<RenderItem>): ArgbImage {
        val image = page.toBufferedImage()
        val graphics = image.createGraphics()
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
        for (item in items) draw(graphics, item)
        graphics.dispose()
        return image.toArgbImage()
    }

    private fun draw(graphics: Graphics2D, item: RenderItem) {
        val layout = item.layout
        val style = item.style
        val font = measurer.font(layout.textSizePx, layout.bold)
        val context = graphics.fontRenderContext
        val alpha = (style.opacity.coerceIn(0f, 1f) * 255).toInt()
        val filled = Color(style.textColor, false).let { Color(it.red, it.green, it.blue, alpha) }
        val strokeColor = Color(style.strokeColor, false)
            .let { Color(it.red, it.green, it.blue, (alpha * 0.95f).toInt()) }
        val outlineWidth = layout.textSizePx * style.strokeWidthFactor

        for ((index, line) in layout.lines.withIndex()) {
            if (line.isEmpty()) continue
            val baseline = layout.baselineFor(index)
            val glyphs = font.createGlyphVector(context, line)
            val width = glyphs.logicalBounds.width.toFloat()
            val left = layout.box.left + (layout.box.width - width) / 2f
            val shape = glyphs.getOutline(left, baseline)

            if (style.shadowEnabled) {
                val shadow = AffineTransform.getTranslateInstance(1.5, 2.0).createTransformedShape(shape)
                graphics.color = Color(0, 0, 0, 60)
                graphics.fill(shadow)
            }
            if (outlineWidth > 0.4f) {
                graphics.color = strokeColor
                graphics.stroke = BasicStroke(outlineWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                graphics.draw(shape)
            }
            graphics.color = filled
            graphics.fill(shape)
        }
    }
}
