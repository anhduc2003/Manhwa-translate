package com.manhwatranslate.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.manhwatranslate.core.image.ArgbImage
import com.manhwatranslate.core.image.Colors
import com.manhwatranslate.core.layout.TextLayout
import com.manhwatranslate.core.render.PageCompositor
import com.manhwatranslate.core.render.RenderItem
import com.manhwatranslate.core.render.RenderStyle

/**
 * Draws translations onto the erased page. The page is uploaded into a Bitmap once, every
 * block is drawn on a single Canvas, then the pixels are copied back.
 */
class CanvasPageCompositor(private val measurer: AndroidTextMeasurer) : PageCompositor {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)

    override fun compose(page: ArgbImage, items: List<RenderItem>): ArgbImage {
        if (items.isEmpty()) return page
        val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(page.pixels, 0, page.width, 0, 0, page.width, page.height)
        val canvas = Canvas(bitmap)
        for (item in items) drawItem(canvas, page, item)
        bitmap.getPixels(page.pixels, 0, page.width, 0, 0, page.width, page.height)
        bitmap.recycle()
        return page
    }

    private fun drawItem(canvas: Canvas, page: ArgbImage, item: RenderItem) {
        val layout = item.layout
        val style = item.style
        if (layout.lines.isEmpty()) return

        val palette = paletteFor(page, layout, style)
        paint.reset()
        paint.isAntiAlias = true
        paint.isSubpixelText = true
        paint.typeface = measurer.typefaceFor(layout.font)
        paint.textSize = layout.textSizePx
        paint.letterSpacing = layout.font.tracking
        paint.textAlign = Paint.Align.LEFT

        val maxLineWidth = layout.lines.maxOfOrNull { paint.measureText(it) } ?: 0f
        val fitScale = layout.scaleToFit(maxLineWidth)
        if (fitScale < 0.999f) {
            canvas.save()
            canvas.scale(
                fitScale,
                fitScale,
                layout.box.centerX.toFloat(),
                layout.box.centerY.toFloat(),
            )
        }

        val strokeWidth = layout.textSizePx * style.strokeWidthFactor
        for ((index, line) in layout.lines.withIndex()) {
            if (line.isEmpty()) continue
            val baseline = layout.baselineFor(index)
            val width = paint.measureText(line)
            val left = layout.box.left + (layout.box.width - width) / 2f

            if (style.shadowEnabled && palette.shadow != Color.TRANSPARENT) {
                paint.style = Paint.Style.FILL
                paint.color = palette.text
                paint.setShadowLayer(
                    maxOf(1f, layout.textSizePx * 0.06f),
                    layout.textSizePx * 0.03f,
                    layout.textSizePx * 0.05f,
                    palette.shadow,
                )
                canvas.drawText(line, left, baseline, paint)
                paint.clearShadowLayer()
            }

            if (strokeWidth > 0.4f) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = strokeWidth * 2f
                paint.strokeJoin = Paint.Join.ROUND
                paint.strokeCap = Paint.Cap.ROUND
                paint.color = palette.stroke
                canvas.drawText(line, left, baseline, paint)
            }

            paint.style = Paint.Style.FILL
            paint.color = palette.text
            canvas.drawText(line, left, baseline, paint)
        }
        if (fitScale < 0.999f) canvas.restore()
    }

    private data class Palette(val text: Int, val stroke: Int, val shadow: Int)

    /**
     * Bubbles are usually white, but manhwa also uses black bubbles and dark panel captions.
     * Reading the page under the text box keeps the translated line legible in both cases.
     */
    private fun paletteFor(page: ArgbImage, layout: TextLayout, style: RenderStyle): Palette {
        if (!style.autoContrast) {
            return Palette(style.textColor, style.strokeColor, style.shadowColor)
        }
        var sum = 0f
        var count = 0
        val step = maxOf(1, minOf(page.width, page.height) / 400)
        var y = layout.box.top.coerceAtLeast(0)
        while (y < layout.box.bottom && y < page.height) {
            var x = layout.box.left.coerceAtLeast(0)
            while (x < layout.box.right && x < page.width) {
                sum += Colors.luminance(page.get(x, y))
                count++
                x += step
            }
            y += step
        }
        val mean = if (count == 0) 255f else sum / count
        return if (mean < 118f) {
            Palette(0xFFF6F6F6.toInt(), 0xE6000000.toInt(), 0x66000000)
        } else {
            Palette(
                style.textColor,
                if (Colors.isDark(style.textColor)) 0xFFFFFFFF.toInt() else 0xFF080808.toInt(),
                0x55000000,
            )
        }
    }
}
