package com.manhwatranslate.core.image

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ImagePlannerTest {

    private val budget = 8_000_000

    @Test
    fun `a page just over budget is not subsampled`() {
        val plan = planImage(720, 13718, budget)
        assertEquals(1, plan.sample)
        assertEquals(720, plan.width)
        assertEquals(13718, plan.height)
        assertEquals(minOf(720L * 13718, budget.toLong()).toInt(), plan.pixels)
        assertTrue(plan.pixels <= budget)
        val scale = Math.sqrt(plan.pixels.toDouble() / (720.0 * 13718.0))
        assertTrue(720 * scale >= 600, "a smooth scale keeps most of the width, not the old 360")
    }

    @Test
    fun `a tall strip within twice the budget scales to the budget`() {
        val plan = planImage(1080, 14419, budget)
        assertEquals(1, plan.sample)
        assertEquals(budget, plan.pixels)
    }

    @Test
    fun `a page past twice the budget samples once then scales`() {
        val plan = planImage(2000, 12000, budget)
        assertEquals(2, plan.sample)
        assertEquals(1000, plan.width)
        assertEquals(6000, plan.height)
        assertEquals(minOf(6_000_000, budget), plan.pixels)
    }

    @Test
    fun `a page of exactly the budget is left alone`() {
        val plan = planImage(2000, 4000, budget)
        assertEquals(1, plan.sample)
        assertEquals(budget, plan.pixels)
        assertEquals(2000, plan.width)
        assertEquals(4000, plan.height)
    }

    @Test
    fun `a page under budget keeps its size`() {
        val plan = planImage(800, 1200, budget)
        assertEquals(1, plan.sample)
        assertEquals(960_000, plan.pixels)
        assertEquals(800, plan.width)
        assertEquals(1200, plan.height)
    }

    @Test
    fun `the product of a huge page does not overflow`() {
        val plan = planImage(Int.MAX_VALUE, Int.MAX_VALUE, budget)
        assertTrue(plan.sample > 1)
        assertTrue(plan.pixels in 1..budget)
        assertTrue(plan.width > 0 && plan.height > 0)
    }
}
