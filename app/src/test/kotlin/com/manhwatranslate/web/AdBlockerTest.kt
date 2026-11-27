package com.manhwatranslate.web

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AdBlockerTest {

    @Test
    fun `blocks known ad and tracker hosts`() {
        assertTrue(AdBlocker.shouldBlock("https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js"))
        assertTrue(AdBlocker.shouldBlock("https://securepubads.g.doubleclick.net/tag/js/gpt.js"))
        assertTrue(AdBlocker.shouldBlock("https://cdn.taboola.com/libtrc/loader.js"))
        assertTrue(AdBlocker.shouldBlock("https://s.adsterra.com/banner/123.gif"))
        assertTrue(AdBlocker.shouldBlock("https://www.google-analytics.com/collect"))
    }

    @Test
    fun `blocks ad shaped paths on normal hosts`() {
        assertTrue(AdBlocker.shouldBlock("https://cdn.example.com/ads/banner-300x250.png"))
        assertTrue(AdBlocker.shouldBlock("https://site.example.com/popunder.js"))
        assertTrue(AdBlocker.shouldBlock("https://site.example.com/pagead/show.html"))
    }

    @Test
    fun `keeps chapter images and page assets`() {
        assertFalse(AdBlocker.shouldBlock("https://cdn.mangasite.com/chapters/12/001.jpg"))
        assertFalse(AdBlocker.shouldBlock("https://cdn.mangasite.com/assets/style.css"))
        assertFalse(AdBlocker.shouldBlock("https://mangasite.com/chapter/12/"))
        assertFalse(AdBlocker.shouldBlock("data:image/png;base64,AAAA"))
        assertFalse(AdBlocker.shouldBlock("about:blank"))
    }

    @Test
    fun `the cleaning script targets ads, cookie walls and hidden links`() {
        val script = AdBlocker.pageScript
        assertTrue(script.contains("adsbygoogle"))
        assertTrue(script.contains("cookie"))
        assertTrue(script.contains("window.open"))
        assertTrue(script.contains("MutationObserver"))
        assertTrue(AdBlocker.pageCss.contains("display: none"))
    }

    @Test
    fun `the image bridge exposes the calls the page needs`() {
        val script = AdBlocker.imageBridgeScript
        assertTrue(script.contains("__mtApply"))
        assertTrue(script.contains("__mtScanImages"))
        assertTrue(script.contains("AndroidImages.submit"))
    }
}
