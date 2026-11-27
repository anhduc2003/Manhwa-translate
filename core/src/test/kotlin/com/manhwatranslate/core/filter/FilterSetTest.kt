package com.manhwatranslate.core.filter

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FilterSetTest {

    private val filters = FilterSet(
        id = "test",
        label = "Test",
        blockedHosts = setOf("doubleclick.net", "ads.example.com"),
        allowedHosts = setOf("good.ads.example.com"),
        selectors = listOf(".ad-banner"),
        scopedSelectors = listOf(FilterSet.ScopedSelector("example.com", ".sponsor")),
    )

    @Test
    fun `a blocked domain covers its subdomains`() {
        assertTrue(filters.blocksHost("doubleclick.net"))
        assertTrue(filters.blocksHost("ad.g.doubleclick.net"))
        assertTrue(filters.blocksHost("ADS.EXAMPLE.COM"))
    }

    @Test
    fun `an allowed domain wins over its parent rule`() {
        assertFalse(filters.blocksHost("good.ads.example.com"))
        assertTrue(filters.blocksHost("bad.ads.example.com"))
    }

    @Test
    fun `unrelated hosts pass`() {
        assertFalse(filters.blocksHost("manhwa.example.org"))
        assertFalse(filters.blocksHost("notdoubleclick.net"))
    }

    @Test
    fun `element hiding applies globally and per site`() {
        assertEquals(".ad-banner,.sponsor", filters.cssFor("example.com"))
        assertEquals(".ad-banner", filters.cssFor("other.org"))
    }

    @Test
    fun `parsing keeps hosts and scoped selectors apart`() {
        val domains = FilterListParser.parseDomains(sequenceOf("A.example.com", "! comment", "", "b.example.org"))
        assertEquals(setOf("a.example.com", "b.example.org"), domains)
        val (plain, scoped) = FilterListParser.parseSelectors(
            sequenceOf(".banner", "example.com|.sponsor", "#ad-slot"),
        )
        assertEquals(listOf(".banner", "#ad-slot"), plain)
        assertEquals(1, scoped.size)
        assertEquals("example.com", scoped.first().host)
    }
}
