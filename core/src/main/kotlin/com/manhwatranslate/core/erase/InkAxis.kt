package com.manhwatranslate.core.erase

import com.manhwatranslate.core.image.Colors
import kotlin.math.sqrt

/**
 * Tells the lettering of the estimated ink colour from artwork that merely happens to sit inside
 * the text box.
 *
 * "Close to the paper" says nothing about a red sparkle in a white bubble: the sparkle is far from
 * the paper, so a tolerance on distance alone erased it along with the letters. What separates the
 * two is direction, not distance. A letter pixel lies on the line from the paper colour to the ink
 * colour, because it is that ink, or a blend of it with the paper at an anti-aliased edge. A
 * coloured decoration lies off that line, so it keeps a large residual once its share along the
 * line is removed, and the patch leaves it standing.
 */
internal object InkAxis {

    /** Ink and paper closer than this in each channel are the same colour; nothing to tell apart. */
    private const val MIN_AXIS_LENGTH = 26f

    /**
     * How far off the paper-to-ink line a pixel may sit and still count as lettering, in channel
     * levels. Anti-aliased blends sit on the line; a decoration of another hue sits far off it.
     */
    const val DEFAULT_RESIDUAL_LIMIT = 54f

    /** Share of the way from the paper to the ink at which a pixel starts counting as lettering. */
    private const val MIN_BLEND = 0.32f

    fun isInkLike(
        pixel: Int,
        paper: Int,
        ink: Int,
        residualLimit: Float = DEFAULT_RESIDUAL_LIMIT,
    ): Boolean {
        val axisR = (Colors.red(ink) - Colors.red(paper)).toFloat()
        val axisG = (Colors.green(ink) - Colors.green(paper)).toFloat()
        val axisB = (Colors.blue(ink) - Colors.blue(paper)).toFloat()
        val lengthSquared = axisR * axisR + axisG * axisG + axisB * axisB
        if (lengthSquared < MIN_AXIS_LENGTH * MIN_AXIS_LENGTH) return false
        val vectorR = (Colors.red(pixel) - Colors.red(paper)).toFloat()
        val vectorG = (Colors.green(pixel) - Colors.green(paper)).toFloat()
        val vectorB = (Colors.blue(pixel) - Colors.blue(paper)).toFloat()
        val blend = (vectorR * axisR + vectorG * axisG + vectorB * axisB) / lengthSquared
        if (blend < MIN_BLEND) return false
        val residualR = vectorR - blend * axisR
        val residualG = vectorG - blend * axisG
        val residualB = vectorB - blend * axisB
        return sqrt(residualR * residualR + residualG * residualG + residualB * residualB) <= residualLimit
    }
}
