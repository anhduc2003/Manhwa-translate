package com.manhwatranslate.core.scan

import com.manhwatranslate.core.geometry.RectI
import com.manhwatranslate.core.image.ArgbImage

/**
 * Finds lettering on a page without reading it. The pipeline uses it as a second pair of eyes:
 * whatever the OCR engine did not report is cropped out and offered to the engine again, which
 * picks up the small print and stylised sound effects a page-wide pass drops.
 */
interface TextDetector {
    /** Lettering proposals in [image] coordinates, in any order. */
    fun detect(image: ArgbImage): List<RectI>
}
