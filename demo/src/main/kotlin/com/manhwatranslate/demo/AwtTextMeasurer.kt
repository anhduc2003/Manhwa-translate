package com.manhwatranslate.demo

import com.manhwatranslate.core.layout.FontFamilyKind
import com.manhwatranslate.core.layout.FontSpec
import com.manhwatranslate.core.layout.FontWeightLevel
import com.manhwatranslate.core.layout.TextMeasurer
import java.awt.Font
import java.awt.font.FontRenderContext
import java.awt.font.LineMetrics
import java.awt.font.TextLayout

class AwtTextMeasurer(private val fontDir: java.io.File? = DEFAULT_FONT_DIR) : TextMeasurer {
    private val context = FontRenderContext(null, true, true)
    private val cache = HashMap<Long, Font>()
    private val assets = HashMap<String, Font?>()

    /** Loads the same files the app ships, so a preview here is a preview of the app. */
    private fun assetFont(path: String): Font? {
        val dir = fontDir ?: return null
        return assets.getOrPut(path) {
            val file = java.io.File(dir, path.substringAfterLast('/'))
            if (!file.isFile) return@getOrPut null
            runCatching {
                java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, file)
            }.getOrNull()
        }
    }

    companion object {
        val DEFAULT_FONT_DIR = java.io.File("app/src/main/assets/fonts")
    }

    fun font(sizePx: Float, spec: FontSpec): Font {
        val family = when (spec.family) {
            FontFamilyKind.SERIF -> Font.SERIF
            FontFamilyKind.MONOSPACE -> Font.MONOSPACED
            FontFamilyKind.CONDENSED -> Font.SANS_SERIF
            FontFamilyKind.CUSTOM -> Font.SANS_SERIF
            FontFamilyKind.SANS -> Font.SANS_SERIF
        }
        val style = when {
            spec.weight == FontWeightLevel.REGULAR || spec.weight == FontWeightLevel.THIN -> Font.PLAIN
            else -> Font.BOLD
        } or if (spec.italic) Font.ITALIC else Font.PLAIN
        val key = "$sizePx|${spec.family}|${spec.weight}|${spec.italic}|${spec.tracking}".hashCode().toLong()
        return cache.getOrPut(key) {
            val manhwa = com.manhwatranslate.core.layout.ManhwaFonts
                .assetFor(spec.family, spec.weight, spec.italic)
                ?.let { assetFont(it) }
            val base = manhwa?.deriveFont(sizePx) ?: Font(family, style, 12).deriveFont(sizePx)
            if (spec.tracking == 0f) {
                base
            } else {
                val attributes = java.util.HashMap<java.awt.font.TextAttribute, Any>(base.getAttributes())
                attributes[java.awt.font.TextAttribute.TRACKING] = spec.tracking
                base.deriveFont(attributes)
            }
        }
    }

    fun font(sizePx: Float, bold: Boolean): Font =
        font(sizePx, FontSpec(weight = if (bold) FontWeightLevel.BOLD else FontWeightLevel.REGULAR))

    override fun measureWidth(text: String, sizePx: Float, font: FontSpec): Float {
        if (text.isEmpty()) return 0f
        return TextLayout(text, font(sizePx, font), context).bounds.width.toFloat()
    }

    override fun lineHeight(sizePx: Float, font: FontSpec): Float {
        val metrics: LineMetrics = font(sizePx, font).getLineMetrics("Ag", context)
        return metrics.ascent + metrics.descent + metrics.leading
    }

    override fun breakIndex(text: String, sizePx: Float, font: FontSpec, maxWidth: Float): Int {
        if (text.isEmpty() || maxWidth <= 0f) return 1
        var low = 1
        var high = text.length
        var best = 1
        val awtFont = font(sizePx, font)
        while (low <= high) {
            val mid = (low + high) / 2
            val width = TextLayout(text.substring(0, mid), awtFont, context).bounds.width.toFloat()
            if (width <= maxWidth) {
                best = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return best
    }
}
