package com.manhwatranslate.core.layout

import kotlinx.serialization.Serializable

enum class FontFamilyKind {
    SANS,
    SERIF,
    CONDENSED,
    MONOSPACE,
    CUSTOM,
}

/** Font the app should draw with. AUTO lets the estimator pick the weight from the artwork. */
enum class FontMode {
    AUTO,
    SANS,
    SERIF,
    CONDENSED,
    MONOSPACE,
    CUSTOM,
}

enum class FontWeightLevel(val androidWeight: Int) {
    THIN(300),
    REGULAR(400),
    MEDIUM(500),
    BOLD(700),
    BLACK(900),
}

@Serializable
data class FontSpec(
    val family: FontFamilyKind = FontFamilyKind.SANS,
    val weight: FontWeightLevel = FontWeightLevel.BOLD,
    val italic: Boolean = false,
    /**
     * Letter spacing in em, negative to pull the letters together. Comic lettering is set tight,
     * but every face ships its own natural advance, so the renderer asks the font and corrects it
     * rather than leaving lines loose or cramped depending on which face was picked.
     */
    val tracking: Float = 0f,
) {
    val isBold: Boolean get() = weight.androidWeight >= FontWeightLevel.BOLD.androidWeight

    companion object {
        val Default = FontSpec()
    }
}
