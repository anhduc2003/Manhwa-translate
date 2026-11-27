package com.manhwatranslate.core.image

/** Channel helpers, luminance math and color distance over packed ARGB ints. */
object Colors {
    const val ALPHA_SHIFT = 24
    const val RED_SHIFT = 16
    const val GREEN_SHIFT = 8
    const val BLUE_SHIFT = 0
    const val CHANNEL_MASK = 0xFF

    fun alpha(argb: Int): Int = (argb ushr ALPHA_SHIFT) and CHANNEL_MASK
    fun red(argb: Int): Int = (argb ushr RED_SHIFT) and CHANNEL_MASK
    fun green(argb: Int): Int = (argb ushr GREEN_SHIFT) and CHANNEL_MASK
    fun blue(argb: Int): Int = (argb ushr BLUE_SHIFT) and CHANNEL_MASK

    fun pack(a: Int, r: Int, g: Int, b: Int): Int =
        ((a and CHANNEL_MASK) shl ALPHA_SHIFT) or
            ((r and CHANNEL_MASK) shl RED_SHIFT) or
            ((g and CHANNEL_MASK) shl GREEN_SHIFT) or
            ((b and CHANNEL_MASK) shl BLUE_SHIFT)

    fun opaque(r: Int, g: Int, b: Int): Int = pack(0xFF, r, g, b)

    fun luminance(argb: Int): Float {
        val r = red(argb)
        val g = green(argb)
        val b = blue(argb)
        return 0.2126f * r + 0.7152f * g + 0.0722f * b
    }

    fun luminance(r: Int, g: Int, b: Int): Float = 0.2126f * r + 0.7152f * g + 0.0722f * b

    fun distance(a: Int, b: Int): Float {
        val dr = (red(a) - red(b)).toFloat()
        val dg = (green(a) - green(b)).toFloat()
        val db = (blue(a) - blue(b)).toFloat()
        return kotlin.math.sqrt(dr * dr + dg * dg + db * db)
    }

    fun distanceTo(r: Int, g: Int, b: Int, argb: Int): Float {
        val dr = r - red(argb)
        val dg = g - green(argb)
        val db = b - blue(argb)
        return kotlin.math.sqrt((dr * dr + dg * dg + db * db).toFloat())
    }

    /** Perceived brightness in 0..1, used to pick a readable text color for a background. */
    fun isDark(argb: Int): Boolean = luminance(argb) < 128f
}
