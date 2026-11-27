package com.manhwatranslate.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween

/**
 * One set of timings and curves for the whole app, so a screen change, a card appearing and a
 * sheet opening feel like the same product moving. Entering content decelerates quickly; leaving
 * content accelerates, which is what makes a back gesture read as "going back".
 */
object Motion {
    /** A screen sliding in or out. */
    const val Screen = 320

    /** A block appearing inside a screen: a panel, a pill, a dialog. */
    const val Panel = 240

    /** A colour, a chevron, a toggle. */
    const val Fast = 160

    /** A list item fading as it is added or removed. */
    const val Item = 220

    /** Decelerating curve for anything that enters or moves. */
    val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** Standard curve for opacity and colour. */
    val Standard = FastOutSlowInEasing

    fun enter(durationMillis: Int = Panel) = tween<Float>(durationMillis, easing = Emphasized)

    fun fade(durationMillis: Int = Fast) = tween<Float>(durationMillis, easing = Standard)
}
