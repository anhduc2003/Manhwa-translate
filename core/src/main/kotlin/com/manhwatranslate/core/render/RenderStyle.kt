package com.manhwatranslate.core.render

import com.manhwatranslate.core.image.ArgbImage
import com.manhwatranslate.core.layout.FontMode
import com.manhwatranslate.core.layout.FontWeightLevel
import com.manhwatranslate.core.layout.TextLayout

/** How translated text is drawn. */
data class RenderStyle(
    val fontMode: FontMode = FontMode.AUTO,
    val fontWeightOverride: FontWeightLevel? = null,
    val autoSizeFromOriginal: Boolean = true,
    val autoContrast: Boolean = true,
    val textColor: Int = 0xFF101010.toInt(),
    val strokeColor: Int = 0xFFFFFFFF.toInt(),
    val strokeWidthFactor: Float = 0.09f,
    val shadowEnabled: Boolean = true,
    val shadowColor: Int = 0x55000000,
    val opacity: Float = 1f,
    val textSizeScale: Float = 1f,
    val minTextSizePx: Float = 11f,
    val maxTextSizePx: Float = 96f,
    val lineSpacing: Float = 1.08f,
    val maxLines: Int = 8,
)

/** One laid-out block waiting to be drawn. */
data class RenderItem(
    val layout: TextLayout,
    val style: RenderStyle,
    /** Turning of the original lettering, so the translation is drawn along the same line. */
    val rotationDegrees: Float = 0f,
)

/**
 * Draws every translated block onto a page. Implemented per platform so the page bitmap is
 * only converted once: Android draws on a `Canvas`, the demo harness with Java2D.
 */
interface PageCompositor {
    fun compose(page: ArgbImage, items: List<RenderItem>): ArgbImage
}
