package com.manhwatranslate.demo

import com.manhwatranslate.core.image.ArgbImage
import java.awt.image.BufferedImage

fun BufferedImage.toArgbImage(): ArgbImage {
    val pixels = IntArray(width * height)
    getRGB(0, 0, width, height, pixels, 0, width)
    return ArgbImage(width, height, pixels)
}

fun ArgbImage.toBufferedImage(): BufferedImage {
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    image.setRGB(0, 0, width, height, pixels, 0, width)
    return image
}
