package com.manhwatranslate.core.layout

/**
 * The lettering faces that ship with the app, with the measurements the renderer selects on.
 *
 * The pack is a Vietnamese comic lettering collection: forty faces that were never meant to be
 * used all at once, but that a reader may want to pick from. The weight and slant come from each
 * face's own name and metrics, and [advanceEm] is the average character advance measured over a
 * real Vietnamese sentence, which is what the renderer corrects the tracking against.
 */
data class FontFace(
    val file: String,
    val displayName: String,
    /** Weight of the face, 300 to 900, taken from the face's own name. */
    val weight: Int,
    val italic: Boolean,
    val advanceEm: Float,
) {
    val assetPath: String get() = "${FontLibrary.ASSET_DIR}/$file"
}

object FontLibrary {

    const val ASSET_DIR = "fonts/library"

    val faces: List<FontFace> = listOf(
        FontFace(file = "AnimeAce-Bold.ttf", displayName = "AnimeAce-Bold", weight = 700, italic = false, advanceEm = 0.799f),
        FontFace(file = "Avegaeance-Regular.ttf", displayName = "Avegaeance-Regular", weight = 400, italic = false, advanceEm = 0.345f),
        FontFace(file = "Belisa-Regular.ttf", displayName = "Belisa-Regular", weight = 400, italic = false, advanceEm = 0.247f),
        FontFace(file = "BlahBlahUC-Bold.ttf", displayName = "BlahBlahUC-Bold", weight = 700, italic = false, advanceEm = 0.805f),
        FontFace(file = "BlahBlahUC-BoldItalic.ttf", displayName = "BlahBlahUC-BoldItalic", weight = 700, italic = true, advanceEm = 0.788f),
        FontFace(file = "BlahBlahUC-Italic.ttf", displayName = "BlahBlahUC-Italic", weight = 400, italic = true, advanceEm = 0.699f),
        FontFace(file = "BlahBlahUC-Regular.ttf", displayName = "BlahBlahUC-Regular", weight = 400, italic = false, advanceEm = 0.674f),
        FontFace(file = "BlambotClassic-BoldItalic.ttf", displayName = "BlambotClassic-BoldItalic", weight = 700, italic = true, advanceEm = 0.687f),
        FontFace(file = "BlambotClassic-Italic.ttf", displayName = "BlambotClassic-Italic", weight = 400, italic = true, advanceEm = 0.596f),
        FontFace(file = "BlambotClassic-Regular.ttf", displayName = "BlambotClassic-Regular", weight = 400, italic = false, advanceEm = 0.596f),
        FontFace(file = "CCSpaghettiWesternSans.ttf", displayName = "CCSpaghettiWesternSans", weight = 400, italic = false, advanceEm = 0.411f),
        FontFace(file = "CaptainComic-Regular.otf", displayName = "CaptainComic-Regular", weight = 400, italic = false, advanceEm = 0.587f),
        FontFace(file = "CollectEmAll-Bold.ttf", displayName = "CollectEmAll-Bold", weight = 700, italic = false, advanceEm = 0.542f),
        FontFace(file = "CollectEmAll-BoldItalic.ttf", displayName = "CollectEmAll-BoldItalic", weight = 700, italic = true, advanceEm = 0.569f),
        FontFace(file = "CollectEmAll-Italic.ttf", displayName = "CollectEmAll-Italic", weight = 400, italic = true, advanceEm = 0.501f),
        FontFace(file = "CollectEmAll-Regular.ttf", displayName = "CollectEmAll-Regular", weight = 400, italic = false, advanceEm = 0.501f),
        FontFace(file = "FMAtlas-Regular.otf", displayName = "FMAtlas-Regular", weight = 400, italic = false, advanceEm = 0.317f),
        FontFace(file = "FrankBellamy-Regular.ttf", displayName = "FrankBellamy-Regular", weight = 400, italic = false, advanceEm = 0.625f),
        FontFace(file = "HandOfSeanPro-Regular.ttf", displayName = "HandOfSeanPro-Regular", weight = 400, italic = false, advanceEm = 0.352f),
        FontFace(file = "Heroid-Regular.ttf", displayName = "Heroid-Regular", weight = 400, italic = false, advanceEm = 0.715f),
        FontFace(file = "HometownHero-Regular.ttf", displayName = "HometownHero-Regular", weight = 400, italic = false, advanceEm = 0.544f),
        FontFace(file = "HundredWatt-Bold.ttf", displayName = "HundredWatt-Bold", weight = 700, italic = false, advanceEm = 0.508f),
        FontFace(file = "HundredWatt-BoldItalic.ttf", displayName = "HundredWatt-BoldItalic", weight = 700, italic = true, advanceEm = 0.507f),
        FontFace(file = "HundredWatt-Italic.ttf", displayName = "HundredWatt-Italic", weight = 400, italic = true, advanceEm = 0.431f),
        FontFace(file = "HundredWatt-Regular.ttf", displayName = "HundredWatt-Regular", weight = 400, italic = false, advanceEm = 0.431f),
        FontFace(file = "OutOfLine-Bold.ttf", displayName = "OutOfLine-Bold", weight = 700, italic = false, advanceEm = 0.643f),
        FontFace(file = "OutOfLine-BoldItalic.ttf", displayName = "OutOfLine-BoldItalic", weight = 700, italic = true, advanceEm = 0.641f),
        FontFace(file = "OutOfLine-Italic.ttf", displayName = "OutOfLine-Italic", weight = 400, italic = true, advanceEm = 0.52f),
        FontFace(file = "OutOfLine-Regular.ttf", displayName = "OutOfLine-Regular", weight = 400, italic = false, advanceEm = 0.52f),
        FontFace(file = "PiekosPro-BoldItalic.ttf", displayName = "PiekosPro-BoldItalic", weight = 700, italic = true, advanceEm = 0.516f),
        FontFace(file = "PiekosPro-Italic.ttf", displayName = "PiekosPro-Italic", weight = 400, italic = true, advanceEm = 0.463f),
        FontFace(file = "PiekosPro-Regular.ttf", displayName = "PiekosPro-Regular", weight = 400, italic = false, advanceEm = 0.438f),
        FontFace(file = "RangSun-Regular.ttf", displayName = "RangSun-Regular", weight = 400, italic = false, advanceEm = 0.3f),
        FontFace(file = "ReadyforAnythingTB-Bold.ttf", displayName = "ReadyforAnythingTB-Bold", weight = 700, italic = false, advanceEm = 0.623f),
        FontFace(file = "ReadyforAnythingTB-BoldItalic.ttf", displayName = "ReadyforAnythingTB-BoldItalic", weight = 700, italic = true, advanceEm = 0.623f),
        FontFace(file = "ReadyforAnythingTB-Italic.ttf", displayName = "ReadyforAnythingTB-Italic", weight = 300, italic = true, advanceEm = 0.506f),
        FontFace(file = "ReadyforAnythingTB-Regular.ttf", displayName = "ReadyforAnythingTB-Regular", weight = 300, italic = false, advanceEm = 0.506f),
        FontFace(file = "SkippySharp-Regular.ttf", displayName = "SkippySharp-Regular", weight = 400, italic = false, advanceEm = 0.313f),
        FontFace(file = "WebLettererP-Regular.ttf", displayName = "WebLettererP-Regular", weight = 400, italic = false, advanceEm = 0.601f),
        FontFace(file = "WhizBang-Roman.ttf", displayName = "WhizBang-Roman", weight = 400, italic = false, advanceEm = 0.758f),
        FontFace(file = "Zoinks-Regular.ttf", displayName = "Zoinks-Regular", weight = 400, italic = false, advanceEm = 0.397f),
    )

    private val byFile: Map<String, FontFace> = faces.associateBy { it.file }

    fun face(file: String): FontFace? = byFile[file]

    fun faceAt(assetPath: String): FontFace? =
        face(assetPath.substringAfterLast('/'))

    /**
     * The face closest to a measured style: same slant first, then the nearest weight. Ties go to
     * the earlier face in the list so the choice never flickers between runs.
     */
    fun closest(weight: Int, italic: Boolean): FontFace = faces.minWithOrNull(
        compareBy<FontFace> { if (it.italic == italic) 0 else 1 }
            .thenBy { kotlin.math.abs(it.weight - weight) }
            .thenBy { it.displayName },
    ) ?: error("the font pack is empty")
}
