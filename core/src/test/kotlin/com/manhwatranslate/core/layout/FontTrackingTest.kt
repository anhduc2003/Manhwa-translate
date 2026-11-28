package com.manhwatranslate.core.layout

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Faces arrive with their own natural advance, so the same text size reads tight in one and
 * airy in another. These cases pin the correction that keeps a translated line lettered
 * instead of merely typed.
 */
class FontTrackingTest {

    private fun tracking(widthFactor: Float, weight: FontWeightLevel = FontWeightLevel.REGULAR): Float =
        FontTracking.em(
            FakeMonospaceMeasurer(widthFactor = widthFactor),
            FontSpec(weight = weight, tracking = 0f),
        )

    @Test
    fun `a wide face is pulled together`() {
        assertTrue(tracking(0.80f) < 0f)
    }

    @Test
    fun `a narrow face is opened up`() {
        assertTrue(tracking(0.45f) > 0f)
    }

    @Test
    fun `a face already on target is left alone`() {
        // The fake measurer's MEDIUM weight factor is exactly 1, so this advance is the target.
        assertEquals(0f, tracking(FontTracking.TARGET_ADVANCE_EM, FontWeightLevel.MEDIUM), 0.001f)
    }

    @Test
    fun `the correction is clamped so letters never collapse or float apart`() {
        assertEquals(-0.10f, tracking(2.0f), 0.001f)
        assertEquals(0.03f, tracking(0.10f), 0.001f)
    }

    @Test
    fun `the measurement follows the tracking it asks for`() {
        val measurer = FakeMonospaceMeasurer(widthFactor = 0.80f)
        val loose = measurer.measureWidth("Nhanh lên", 40f, FontSpec(weight = FontWeightLevel.REGULAR))
        val tight = measurer.measureWidth(
            "Nhanh lên",
            40f,
            FontSpec(weight = FontWeightLevel.REGULAR, tracking = -0.08f),
        )

        assertTrue(tight < loose, "tracking did not narrow the line: $tight vs $loose")
    }
}
