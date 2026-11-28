package com.manhwatranslate.web

import android.content.Context
import com.manhwatranslate.core.filter.FilterSet
import com.manhwatranslate.core.filter.FilterListParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.GZIPInputStream

/**
 * Request blocking and page hygiene for manhwa sites. Three open-source lists ship inside the
 * APK, already distilled to hosts and CSS selectors by `tools/build_filters.py`:
 *
 * * EasyList — the main ad list, https://easylist.to (CC BY-SA 3.0).
 * * EasyPrivacy — trackers, https://easylist.to (CC BY-SA 3.0 / GPLv3).
 * * Peter Lowe's list — https://pgl.yoyo.org/adservers.
 *
 * The user can turn each one off in the reader's settings. A small built-in list covers the
 * reader sites' own ad slots and popups that the generic lists miss, and a script cleans ad
 * slots and cookie walls that load no request at all.
 */
class AdBlocker(private val assetReader: (String) -> String?) {

    companion object {
        const val ID_BUILTIN = "builtin"
        const val ID_EASYLIST = "easylist"
        const val ID_EASYPRIVACY = "easyprivacy"
        const val ID_PETER_LOWE = "peterlowe"

        /** Reads the bundled lists straight from the APK's assets, gzipped or not. */
        fun fromAssets(context: Context): AdBlocker = AdBlocker { path ->
            runCatching {
                context.assets.open(path).use { stream ->
                    val buffered = stream.buffered()
                    buffered.mark(2)
                    val magic = ByteArray(2)
                    val read = buffered.read(magic)
                    buffered.reset()
                    if (read == 2 && magic[0] == 0x1f.toByte() && magic[1] == 0x8b.toByte()) {
                        GZIPInputStream(buffered).bufferedReader(Charsets.UTF_8).readText()
                    } else {
                        buffered.reader(Charsets.UTF_8).readText()
                    }
                }
            }.getOrNull()
        }

        val SOURCES = listOf(
            SourceInfo(ID_BUILTIN, "Bộ lọc có sẵn", "Slot quảng cáo của các trang truyện phổ biến", 0),
            SourceInfo(ID_EASYLIST, "EasyList", "Bộ lọc quảng cáo lớn nhất, mã nguồn mở", 1),
            SourceInfo(ID_EASYPRIVACY, "EasyPrivacy", "Chặn mã theo dõi và phân tích", 2),
            SourceInfo(ID_PETER_LOWE, "Peter Lowe", "Danh sách máy chủ quảng cáo", 3),
        )
    }

    data class SourceInfo(val id: String, val label: String, val hint: String, val order: Int)

    private val builtinHosts = listOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com", "adservice.google.com",
        "amazon-adsystem.com", "adnxs.com", "adsrvr.org", "adform.net", "adroll.com",
        "pubmatic.com", "rubiconproject.com", "openx.net", "criteo.com", "criteo.net",
        "taboola.com", "outbrain.com", "zedo.com", "popads.net", "popcash.net",
        "propellerads.com", "propellerads.net", "onclickads.net", "exoclick.com", "exosrv.com",
        "juicyads.com", "trafficjunky.net", "adsterra.com", "adcash.com", "mgid.com",
        "revcontent.com", "sharethrough.com", "smartadserver.com", "yieldmo.com", "moatads.com",
        "scorecardresearch.com", "quantserve.com", "hotjar.com", "mouseflow.com",
        "mc.yandex.ru", "connect.facebook.net", "bannerads.com", "adspyglass.com",
        "google-analytics.com", "googletagmanager.com", "googletagservices.com",
        "tsyndicate.com", "hilltopads.net", "clickadu.com", "adskeeper.com",
    )

    val builtinFilter = FilterSet(
        id = ID_BUILTIN,
        label = "Bộ lọc có sẵn",
        blockedHosts = builtinHosts.toSet(),
        selectors = listOf(
            "ins.adsbygoogle", ".adsbygoogle", "[id^=\"google_ads\"]", "[id^=\"div-gpt-ad\"]",
            "[class*=\"ad-slot\"]", "[class*=\"advert\"]", "[data-ad-slot]", "[id*=\"taboola\"]",
            "[id*=\"outbrain\"]", ".OUTBRAIN", "[class*=\"sponsored\"]",
            "[data-ad]", "[data-ad-whitelist]", "[data-ad-unit]", "[data-ad-client]",
            "[data-advertisement]", "[data-ads]", "[class*=\"ai-top-bar\"]",
            "iframe[src*=\"doubleclick\"]", "iframe[src*=\"googlesyndication\"]",
            "iframe[src*=\"adservice\"]",
        ),
    )

    private val loaded = HashMap<String, FilterSet>()
    private val loading = AtomicBoolean(false)

    @Volatile
    private var ready = false

    @Volatile
    private var enabledSources: Set<String> = SOURCES.map { it.id }.toSet()

    val isReady: Boolean get() = ready

    fun setEnabledSources(ids: Set<String>) {
        enabledSources = ids
    }

    fun sourceCounts(): Map<String, Int> = SOURCES.associate { source ->
        source.id to when (source.id) {
            ID_BUILTIN -> builtinFilter.domainCount
            else -> loaded[source.id]?.domainCount ?: 0
        }
    }

    /** Loads the bundled lists once; parsing ~100k hosts takes a moment, so it runs off the UI thread. */
    suspend fun prepare() {
        if (ready || !loading.compareAndSet(false, true)) return
        withContext(Dispatchers.IO) {
            loaded[ID_BUILTIN] = builtinFilter
            loadList(ID_EASYLIST)
            loadList(ID_EASYPRIVACY)
            loadList(ID_PETER_LOWE)
            ready = true
        }
    }

    private fun loadList(id: String) {
        val domains = assetReader("filters/$id.domains.bin") ?: return
        val selectors = assetReader("filters/$id.selectors.bin")
        val (plain, scoped) = if (selectors == null) {
            emptyList<String>() to emptyList<FilterSet.ScopedSelector>()
        } else {
            FilterListParser.parseSelectors(selectors.lineSequence())
        }
        loaded[id] = FilterSet(
            id = id,
            label = SOURCES.firstOrNull { it.id == id }?.label ?: id,
            blockedHosts = FilterListParser.parseDomains(domains.lineSequence()),
            selectors = plain,
            scopedSelectors = scoped,
        )
    }

    fun shouldBlock(url: String): Boolean {
        val lower = url.lowercase()
        if (lower.startsWith("data:") || lower.startsWith("about:")) return false
        val host = hostOf(lower) ?: return false
        for (source in activeSources()) {
            if (source.blocksHost(host)) return true
        }
        return false
    }

    /** CSS for this page: the generic lists' selectors plus the site's own slot rules. */
    fun cssFor(url: String): String {
        val host = hostOf(url.lowercase()).orEmpty()
        val parts = ArrayList<String>()
        activeSources().forEach { source ->
            val css = source.cssFor(host)
            if (css.isNotBlank()) parts += css
        }
        parts += COOKIE_SELECTORS
        val joined = parts.joinToString(",").trim()
        if (joined.isEmpty()) return ""
        // A style element needs a rule, not a bare selector list: without the declaration block
        // the browser drops the whole sheet and every selector above hides nothing.
        return "$joined{display:none!important;}"
    }

    private fun activeSources(): List<FilterSet> {
        if (!loaded.containsKey(ID_BUILTIN)) loaded[ID_BUILTIN] = builtinFilter
        return SOURCES.mapNotNull { if (it.id in enabledSources) loaded[it.id] else null }
    }

    private fun hostOf(url: String): String? {
        val start = url.indexOf("://")
        val withoutScheme = if (start >= 0) url.substring(start + 3) else url
        val end = withoutScheme.indexOfFirst { it == '/' || it == '?' || it == ':' }
        val host = if (end >= 0) withoutScheme.substring(0, end) else withoutScheme
        return host.takeIf { it.isNotBlank() && it.contains('.') }
    }

    /** Cleanup script: ad frames, cookie walls, invisible overlay links and popups. */
    val pageScript: String = """
        (function () {
          if (window.__mtCleaner) { window.__mtCleaner(); return; }
          var AD_SELECTORS = [
            'ins.adsbygoogle', '.adsbygoogle', '[id^="google_ads"]', '[id^="div-gpt-ad"]',
            '[class*="ad-slot"]', '[class*="advert"]', '[data-ad-slot]', '[id*="taboola"]',
            '[id*="outbrain"]', '.OUTBRAIN', '[class*="sponsored"]',
            '[data-ad]', '[data-ad-whitelist]', '[data-ad-unit]', '[data-ad-client]',
            '[data-advertisement]', '[data-ads]', '[class*="ai-top-bar"]',
            'iframe[src*="doubleclick"]', 'iframe[src*="googlesyndication"]',
            'iframe[src*="adservice"]'
          ];
          var COOKIE_TEXT = ['cookie', 'chấp nhận', 'đồng ý', 'accept all', 'consent'];
          var removed = 0;

          function visible(el) {
            var style = window.getComputedStyle(el);
            if (style.display === 'none' || style.visibility === 'hidden') return false;
            var rect = el.getBoundingClientRect();
            return rect.width > 1 && rect.height > 1;
          }

          function clean() {
            AD_SELECTORS.forEach(function (selector) {
              try {
                document.querySelectorAll(selector).forEach(function (el) {
                  el.style.setProperty('display', 'none', 'important');
                  removed++;
                });
              } catch (error) {}
            });

            document.querySelectorAll('a').forEach(function (anchor) {
              var style = window.getComputedStyle(anchor);
              var rect = anchor.getBoundingClientRect();
              var transparent = style.opacity === '0' || style.visibility === 'hidden';
              var offscreen = rect.left < -50 || rect.top < -50;
              var overlay = rect.width > 120 && rect.height > 120 && rect.left <= 2 && rect.top <= 2;
              if ((transparent || offscreen) && anchor.querySelectorAll('img,div').length > 0) {
                anchor.remove();
                removed++;
              } else if (overlay && anchor.getAttribute('target') === '_blank') {
                anchor.remove();
                removed++;
              }
            });

            document.querySelectorAll('div,section,aside').forEach(function (el) {
              if (el.dataset.mtChecked) return;
              var text = (el.innerText || '').toLowerCase();
              if (text.length > 400) return;
              var hits = COOKIE_TEXT.filter(function (word) { return text.indexOf(word) >= 0; }).length;
              if (hits >= 2 && visible(el)) {
                el.style.setProperty('display', 'none', 'important');
                document.documentElement.style.setProperty('overflow', 'auto', 'important');
                el.dataset.mtChecked = '1';
                removed++;
              }
            });

            return removed;
          }

          window.__mtCleaner = function () {
            try { return clean(); } catch (error) { return 0; }
          };
          window.__mtCleaner();
          if (!window.__mtObserver) {
            window.__mtObserver = new MutationObserver(function () { window.__mtCleaner(); });
            window.__mtObserver.observe(document.documentElement, { childList: true, subtree: true });
          }
        })();
    """.trimIndent()

    /**
     * Finds the chapter images on the page and swaps in the translated ones. The app reads the
     * list from `__mtScan`, downloads and translates each image itself, then calls `__mtApply`
     * with the local URL of the result. Images are fetched natively rather than read from a
     * canvas because a canvas holding a cross-origin image is tainted and `toDataURL` throws.
     */
    val imageBridgeScript: String = """
        (function () {
          if (window.__mtReader) return 0;
          window.__mtReader = true;
          window.__mtImages = [];

          /**
           * A chapter page is a tall strip of artwork; the site's logo, its ad banners and the
           * reaction GIFs in the comments are not. Queuing those cost a download, a decode and a
           * full OCR pass each, and replacing an animated GIF with one still frame looked broken.
           */
          function mtWorthTranslating(img, src) {
            if (/\.gif(\?|$)/i.test(src)) return false;
            if (img.complete && img.naturalWidth === 0) return false;
            var rect = img.getBoundingClientRect();
            if (rect.width < 150 && rect.height < 150) return false;
            var nw = img.naturalWidth || 0;
            var nh = img.naturalHeight || 0;
            if (nw > 0 && nh > 0) {
              // Covers, comment pictures and ad slots are small boxes; a page is a tall one.
              if (rect.width * rect.height < 120000) return false;
              if (nw >= nh * 1.2 && nh <= 700) return false;
              return true;
            }
            // Not loaded yet: a lazy page still has its full-width box, a thumbnail does not.
            return rect.width >= 250;
          }

          window.__mtScan = function () {
            var found = [];
            var images = document.querySelectorAll('img');
            for (var i = 0; i < images.length; i++) {
              var img = images[i];
              if (img.__mtTranslated || img.__mtQueued) continue;
              var src = img.currentSrc || img.src || '';
              if (src.indexOf('data:') === 0 || src.indexOf('blob:') === 0) src = '';
              if (!src) {
                var lazy = img.getAttribute('data-src') ||
                  img.getAttribute('data-original') ||
                  img.getAttribute('data-lazy-src') ||
                  img.getAttribute('data-url');
                if (lazy && lazy.indexOf('http') === 0) src = lazy;
              }
              if (!src) continue;
              if (/\.svg(\?|$)/i.test(src)) continue;
              if (!mtWorthTranslating(img, src)) continue;
              var box = img.getBoundingClientRect();
              found.push({
                i: window.__mtImages.length,
                src: src,
                w: Math.round(box.width),
                h: Math.round(box.height)
              });
              img.__mtQueued = true;
              window.__mtImages.push(img);
            }
            return JSON.stringify(found);
          };
          window.__mtApply = function (index, url) {
            var img = window.__mtImages[index];
            if (!img) return false;
            if (img.__mtOriginal === undefined) img.__mtOriginal = img.currentSrc || img.src;
            img.removeAttribute('srcset');
            img.removeAttribute('sizes');
            img.removeAttribute('data-src');
            img.removeAttribute('data-original');
            img.removeAttribute('data-lazy-src');
            img.removeAttribute('data-url');
            img.style.transition = 'opacity .25s ease';
            img.style.opacity = '0.35';
            img.addEventListener('load', function () { img.style.opacity = '1'; }, { once: true });
            // A page whose bytes are gone must not leave a broken or half-faded frame: put the
            // site's own image back and let the next press try again.
            img.addEventListener('error', function () {
              if (img.__mtOriginal) img.src = img.__mtOriginal;
              img.__mtTranslated = false;
              img.__mtQueued = false;
              img.style.opacity = '1';
            }, { once: true });
            img.src = url;
            img.__mtTranslated = true;
            return true;
          };
          window.__mtFail = function (index) {
            var img = window.__mtImages[index];
            if (!img) return false;
            img.__mtQueued = false;
            return true;
          };
          window.__mtReset = function () {
            var restored = 0;
            for (var i = 0; i < window.__mtImages.length; i++) {
              var img = window.__mtImages[i];
              if (!img) continue;
              if (img.__mtOriginal) {
                img.src = img.__mtOriginal;
                restored++;
              }
              img.__mtTranslated = false;
              img.__mtQueued = false;
              img.style.opacity = '1';
            }
            window.__mtImages = [];
            return restored;
          };
        })();
    """.trimIndent()

    private val COOKIE_SELECTORS =
        "[class*=\"cookie-consent\"], [id*=\"cookie-consent\"], [class*=\"cookie-banner\"], " +
            "[id*=\"cookie-banner\"], [class*=\"cookie-notice\"], .cc-window, .fc-consent-root, " +
            "[class*=\"gdpr\"], [id*=\"gdpr\"]"
}
