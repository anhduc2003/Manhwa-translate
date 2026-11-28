package com.manhwatranslate.core.erase

import com.manhwatranslate.core.image.ArgbImage

/**
 * Rebuilds the background behind erased lettering with a trained model instead of a fitted surface.
 *
 * The pipeline hands it a region plus a margin of real surroundings, and the mask of pixels to
 * rebuild. A model that cannot run returns null and the eraser falls back to its own fill, so a page
 * never goes out with the lettering still on it.
 */
interface BackgroundFiller {
    fun fill(crop: ArgbImage, mask: TextMask): ArgbImage?
}
