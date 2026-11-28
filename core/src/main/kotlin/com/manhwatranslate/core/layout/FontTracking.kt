package com.manhwatranslate.core.layout

/**
 * Letter spacing that a face needs to sit like comic lettering.
 *
 * Every font arrives with its own natural advance width. Set at the same size, one face reads
 * tight and the next looks like it was typed with a space between every letter. Instead of a
 * fixed value per family, the advance is measured once and the difference from the lettering
 * target is applied as tracking.
 */
object FontTracking {

    /** Average advance of comic lettering, in em. */
    const val TARGET_ADVANCE_EM = 0.62f

    /** A sentence rather than an alphabet: one wide letter like M says nothing about a font. */
    private const val PROBE = "Đừng động vào cô ấy"
    private const val PROBE_SIZE = 100f

    /** Tracking in em that brings [font]'s advance onto the lettering target. */
    fun em(measurer: TextMeasurer, font: FontSpec): Float {
        val plain = font.copy(tracking = 0f)
        val width = measurer.measureWidth(PROBE, PROBE_SIZE, plain)
        if (width <= 0f) return 0f
        val advance = width / PROBE.length / PROBE_SIZE
        return (TARGET_ADVANCE_EM - advance).coerceIn(-0.10f, 0.03f)
    }
}
