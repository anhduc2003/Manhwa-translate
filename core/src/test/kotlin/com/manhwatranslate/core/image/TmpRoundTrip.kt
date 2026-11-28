package com.manhwatranslate.core.image

import com.manhwatranslate.core.image.ImageRotation
import com.manhwatranslate.core.image.rotated
import org.junit.jupiter.api.Test

class TmpRoundTrip {
    @Test
    fun check() {
        val w = 400; val h = 300
        val image = ArgbImage.filled(w, h, Colors.opaque(255, 255, 255))
        val markerX = 120; val markerY = 90
        image.set(markerX, markerY, Colors.opaque(255, 0, 0))
        for (angle in listOf(-24f, 24f)) {
            val rotation = ImageRotation.of(image, angle)
            val turned = image.rotated(rotation)
            var found: Pair<Int, Int>? = null
            for (y in 0 until turned.height) for (x in 0 until turned.width) {
                val p = turned.get(x, y)
                if (((p shr 16) and 0xFF) > 200 && ((p shr 8) and 0xFF) < 60) { found = x to y }
            }
            val (fx, fy) = found ?: (0 to 0)
            val back = rotation.toSource(fx.toDouble(), fy.toDouble())
            println("DEBUG angle=$angle marker=($markerX,$markerY) found=($fx,$fy) back=(${back.first.toInt()},${back.second.toInt()})")
        }
    }
}
