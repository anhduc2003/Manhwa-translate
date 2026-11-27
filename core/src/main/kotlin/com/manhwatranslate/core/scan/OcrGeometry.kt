package com.manhwatranslate.core.scan

import com.manhwatranslate.core.geometry.RectI

/**
 * Turns a box measured on a scaled copy of a tile back into page coordinates. The order
 * matters: the box is first divided by the scale it was measured at, then moved by the tile's
 * position on the page. Doing it the other way round shrinks the tile offset as well and puts
 * every region on the wrong part of a long page.
 */
object OcrGeometry {

    fun toPage(box: RectI, scale: Float, offsetX: Int, offsetY: Int): RectI {
        val safe = if (scale <= 0f) 1f else scale
        return RectI(
            (box.left / safe).toInt() + offsetX,
            (box.top / safe).toInt() + offsetY,
            (box.right / safe).toInt() + offsetX,
            (box.bottom / safe).toInt() + offsetY,
        )
    }
}
