package com.manhwatranslate.web

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AdBlockerTest {

    private val blocker = AdBlocker({ null })

    @Test
    fun `the built-in list blocks known ad hosts and their subdomains`() {
        assertTrue(blocker.builtinFilter.blocksHost("pagead2.googlesyndication.com"))
        assertTrue(blocker.builtinFilter.blocksHost("securepubads.g.doubleclick.net"))
        assertTrue(blocker.builtinFilter.blocksHost("cdn.taboola.com"))
        assertTrue(blocker.builtinFilter.blocksHost("s.adsterra.com"))
        assertTrue(blocker.builtinFilter.blocksHost("www.google-analytics.com"))
    }

    @Test
    fun `chapter images and ordinary assets pass`() {
        assertFalse(blocker.shouldBlock("https://cdn.mangasite.com/chapters/12/001.jpg"))
        assertFalse(blocker.shouldBlock("https://cdn.mangasite.com/assets/style.css"))
        assertFalse(blocker.shouldBlock("https://mangasite.com/chapter/12/"))
        assertFalse(blocker.shouldBlock("data:image/png;base64,AAAA"))
        assertFalse(blocker.shouldBlock("about:blank"))
    }

    @Test
    fun `blocking can be limited to the built-in list`() {
        blocker.setEnabledSources(setOf(AdBlocker.ID_BUILTIN))
        assertTrue(blocker.shouldBlock("https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js"))
        blocker.setEnabledSources(emptySet())
        assertFalse(blocker.shouldBlock("https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js"))
    }

    @Test
    fun `the cleaning script targets ads, cookie walls and hidden links`() {
        val script = AdBlocker({ null }).pageScript
        assertTrue(script.contains("adsbygoogle"))
        assertTrue(script.contains("cookie"))
        assertTrue(script.contains("MutationObserver"))
        assertTrue(script.contains("getBoundingClientRect"))
    }

    @Test
    fun `the image bridge exposes the calls the page needs`() {
        val script = AdBlocker({ null }).imageBridgeScript
        assertTrue(script.contains("__mtApply"))
        assertTrue(script.contains("__mtScanImages"))
        assertTrue(script.contains("ManhwaTranslate.submit"))
    }

    @Test
    fun `every shipped source has a label and an id`() {
        assertTrue(AdBlocker.SOURCES.size >= 4)
        assertTrue(AdBlocker.SOURCES.all { it.label.isNotBlank() && it.id.isNotBlank() })
    }
}
