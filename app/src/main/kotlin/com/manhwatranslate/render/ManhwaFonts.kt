package com.manhwatranslate.render

import com.manhwatranslate.core.layout.FontFamilyKind
import com.manhwatranslate.core.layout.FontWeightLevel

/**
 * The manhwa lettering set that ships inside the app.
 *
 * The renderer decides a family, a weight and a slant from the original artwork, and this maps
 * that decision onto a real lettering file. Without it the app drew Vietnamese translations in
 * the phone's system font, which is why the handwriting of the page disappeared after a
 * translation even though the style was estimated correctly.
 *
 * The files are the Vietnamese comic fonts from the "Font Truyện Tranh Teddy" pack, renamed to
 * the role they play here; see assets/fonts/CREDITS.md.
 */
object ManhwaFonts {

    const val ASSET_DIR = "fonts"

    private const val SANS_REGULAR = "manhwa_sans_regular.ttf"
    private const val SANS_BOLD = "manhwa_sans_bold.ttf"
    private const val SANS_ITALIC = "manhwa_sans_italic.ttf"
    private const val SANS_BOLD_ITALIC = "manhwa_sans_bold_italic.ttf"
    private const val SANS_BLACK = "manhwa_sans_black.ttf"
    private const val SERIF_REGULAR = "manhwa_serif_regular.ttf"
    private const val SERIF_ITALIC = "manhwa_serif_italic.ttf"
    private const val DISPLAY = "manhwa_display.ttf"

    /** Families that draw with the bundled set; the rest fall back to the system font. */
    val families: List<FontFamilyKind> = listOf(
        FontFamilyKind.SANS,
        FontFamilyKind.SERIF,
        FontFamilyKind.CONDENSED,
    )

    /** Asset path of the face for [family], or null when that family is not ours. */
    fun assetFor(
        family: FontFamilyKind,
        weight: FontWeightLevel,
        italic: Boolean,
    ): String? = when {
        family == FontFamilyKind.SANS && weight == FontWeightLevel.BLACK && !italic -> "$ASSET_DIR/$SANS_BLACK"
        else -> assetFor(family, weight.androidWeight >= FontWeightLevel.BOLD.androidWeight, italic)
    }

    /** Asset path of the face for [family], or null when that family is not ours. */
    fun assetFor(family: FontFamilyKind, heavy: Boolean, italic: Boolean): String? {
        val file = when (family) {
            FontFamilyKind.SANS -> when {
                heavy && italic -> SANS_BOLD_ITALIC
                heavy -> SANS_BOLD
                italic -> SANS_ITALIC
                else -> SANS_REGULAR
            }
            FontFamilyKind.SERIF -> if (italic) SERIF_ITALIC else SERIF_REGULAR
            FontFamilyKind.CONDENSED -> DISPLAY
            else -> return null
        }
        return "$ASSET_DIR/$file"
    }

    /** What the family looks like on a translated page, for the settings screen. */
    fun label(family: FontFamilyKind): String = when (family) {
        FontFamilyKind.SANS -> "Thoại"
        FontFamilyKind.SERIF -> "Kể chuyện"
        FontFamilyKind.CONDENSED -> "Hét / tiêu đề"
        else -> ""
    }

    /** The face the settings screen shows as a sample of [family]. */
    fun sampleAsset(family: FontFamilyKind): String? = assetFor(family, heavy = false, italic = false)
}
