package com.manhwatranslate.web

/**
 * Blocklist and page hygiene rules for manhwa reader sites. Requests to ad and tracker hosts
 * never leave the WebView; a script then cleans what is already in the DOM: ad slots, cookie
 * walls, invisible overlay links that open another site, and popup windows.
 */
object AdBlocker {

    private val blockedHostSuffixes = listOf(
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "google-analytics.com",
        "googletagmanager.com",
        "googletagservices.com",
        "adservice.google.com",
        "adsystem.com",
        "amazon-adsystem.com",
        "adnxs.com",
        "adsrvr.org",
        "adform.net",
        "adroll.com",
        "adcolony.com",
        "applovin.com",
        "unityads.unity3d.com",
        "pubmatic.com",
        "rubiconproject.com",
        "openx.net",
        "criteo.com",
        "criteo.net",
        "taboola.com",
        "outbrain.com",
        "zedo.com",
        "popads.net",
        "popcash.net",
        "propellerads.com",
        "propellerads.net",
        "onclickads.net",
        "exoclick.com",
        "exosrv.com",
        "juicyads.com",
        "trafficjunky.net",
        "adsterra.com",
        "adcash.com",
        "revenuehits.com",
        "mgid.com",
        "revcontent.com",
        "sharethrough.com",
        "smartadserver.com",
        "yieldmo.com",
        "moatads.com",
        "scorecardresearch.com",
        "quantserve.com",
        "hotjar.com",
        "mouseflow.com",
        "yandex.ru/metrika",
        "mc.yandex.ru",
        "facebook.net/en_US/fbevents",
        "connect.facebook.net",
        "histats.com",
        "statcounter.com",
        "clarity.ms",
    )

    private val blockedPathPatterns = listOf(
        Regex("""/ads?[/._-]""", RegexOption.IGNORE_CASE),
        Regex("""/advert""", RegexOption.IGNORE_CASE),
        Regex("""/banners?/""", RegexOption.IGNORE_CASE),
        Regex("""/popunder""", RegexOption.IGNORE_CASE),
        Regex("""/interstitial""", RegexOption.IGNORE_CASE),
        Regex("""[?&]ad_?id=""", RegexOption.IGNORE_CASE),
        Regex("""[?&]utm_(source|medium|campaign)=""", RegexOption.IGNORE_CASE),
        Regex("""/pagead/""", RegexOption.IGNORE_CASE),
        Regex("""/prebid""", RegexOption.IGNORE_CASE),
        Regex("""/gpt\.js""", RegexOption.IGNORE_CASE),
    )

    private val blockedExtensions = listOf(".gif?ad", "ad.js", "ads.js", "adframe.js")

    fun shouldBlock(url: String): Boolean {
        val lower = url.lowercase()
        if (lower.startsWith("data:") || lower.startsWith("about:")) return false
        for (suffix in blockedHostSuffixes) {
            if (lower.contains(suffix)) return true
        }
        for (pattern in blockedPathPatterns) {
            if (pattern.containsMatchIn(lower)) return true
        }
        for (extension in blockedExtensions) {
            if (lower.endsWith(extension)) return true
        }
        return false
    }

    /** CSS that hides ad furniture before scripts run, so nothing flashes on screen. */
    val pageCss: String = """
        ins.adsbygoogle, .adsbygoogle, [id^="google_ads"], [id^="div-gpt-ad"],
        [class*="ad-slot"], [class*="ads-"], [class*="advert"], [data-ad-slot],
        [id*="taboola"], [id*="outbrain"], .OUTBRAIN, [class*="sponsored"],
        iframe[src*="doubleclick"], iframe[src*="googlesyndication"],
        [class*="cookie-consent"], [id*="cookie-consent"], [class*="cookie-banner"],
        [id*="cookie-banner"], [class*="cookie-notice"], .cc-window, .fc-consent-root,
        [class*="gdpr"], [id*="gdpr"] { display: none !important; visibility: hidden !important; }
    """.trimIndent()

    /**
     * Runs after every page so ads injected later are also removed. It also drops the invisible
     * full-screen anchors that only exist to send the reader to another site.
     */
    val pageScript: String = """
        (function () {
          if (window.__mtCleaner) { window.__mtCleaner(); return; }
          var AD_SELECTORS = [
            'ins.adsbygoogle', '.adsbygoogle', '[id^="google_ads"]', '[id^="div-gpt-ad"]',
            '[class*="ad-slot"]', '[class*="advert"]', '[data-ad-slot]', '[id*="taboola"]',
            '[id*="outbrain"]', '.OUTBRAIN', '[class*="sponsored"]',
            'iframe[src*="doubleclick"]', 'iframe[src*="googlesyndication"]',
            'iframe[src*="adservice"]', '[class*="cookie-consent"]', '[id*="cookie-consent"]',
            '[class*="cookie-banner"]', '[id*="cookie-banner"]', '[class*="cookie-notice"]',
            '.cc-window', '.fc-consent-root', '[class*="gdpr"]'
          ];
          var COOKIE_TEXT = ['cookie', 'chấp nhận', 'đồng ý', 'accept all', 'consent'];
          var removed = 0;

          function visible(el) {
            var style = window.getComputedStyle(el);
            if (style.display === 'none' || style.visibility === 'hidden') return false;
            var rect = el.getBoundingClientRect();
            return rect.width > 1 && rect.height > 1;
          }

          function kill(el) {
            if (!el || !el.parentNode) return;
            el.parentNode.removeChild(el);
            removed++;
          }

          function cleanSelectors() {
            AD_SELECTORS.forEach(function (sel) {
              try {
                document.querySelectorAll(sel).forEach(kill);
              } catch (e) {}
            });
          }

          function cleanOverlays() {
            var vw = window.innerWidth, vh = window.innerHeight;
            var area = vw * vh;
            document.querySelectorAll('a, div, iframe, ins').forEach(function (el) {
              if (!visible(el)) return;
              var style = window.getComputedStyle(el);
              var rect = el.getBoundingClientRect();
              var covers = (rect.width * rect.height) > area * 0.55;
              var floating = style.position === 'fixed' || style.position === 'absolute';
              var invisible = parseFloat(style.opacity || '1') < 0.08;
              var tiny = rect.width < 8 || rect.height < 8;
              var zIndex = parseInt(style.zIndex || '0', 10);
              var tag = el.tagName.toLowerCase();
              if (tiny && (tag === 'a' || tag === 'div')) { kill(el); return; }
              if (covers && floating && (invisible || zIndex > 900)) {
                if (tag === 'a' || tag === 'ins' || tag === 'iframe') kill(el);
              }
            });
          }

          function cleanCookieWalls() {
            document.querySelectorAll('div, section, aside').forEach(function (el) {
              if (!visible(el)) return;
              var style = window.getComputedStyle(el);
              if (style.position !== 'fixed' && style.position !== 'sticky') return;
              var text = (el.innerText || '').toLowerCase();
              if (text.length > 400) return;
              var hits = COOKIE_TEXT.filter(function (word) { return text.indexOf(word) >= 0; }).length;
              if (hits >= 2) {
                kill(el);
                document.body.style.overflow = 'auto';
              }
            });
          }

          function cleanHiddenLinks() {
            document.querySelectorAll('a[target="_blank"]').forEach(function (link) {
              var rect = link.getBoundingClientRect();
              var style = window.getComputedStyle(link);
              var transparent = parseFloat(style.opacity || '1') < 0.05;
              if (transparent && rect.width > 200 && rect.height > 200) kill(link);
            });
            document.querySelectorAll('img[onclick]').forEach(function (img) {
              img.removeAttribute('onclick');
            });
          }

          window.addEventListener('click', function (event) {
            var target = event.target;
            if (target && target.tagName === 'A') {
              var href = target.getAttribute('href') || '';
              if (href.indexOf('javascript:') === 0) event.preventDefault();
            }
          }, true);

          window.open = function () { return null; };

          window.__mtCleaner = function () {
            cleanSelectors();
            cleanOverlays();
            cleanCookieWalls();
            cleanHiddenLinks();
            return removed;
          };
          window.__mtCleaner();
          new MutationObserver(function () {
            if (window.__mtPending) return;
            window.__mtPending = true;
            setTimeout(function () { window.__mtPending = false; window.__mtCleaner(); }, 400);
          }).observe(document.documentElement, { childList: true, subtree: true });
        })();
    """.trimIndent()

    /**
     * Finds the large images of a reader page and ships them to the app, then swaps each one for
     * the translated page once the app hands back a local URL.
     */
    val imageBridgeScript: String = """
        (function () {
          if (window.__mtImagesInstalled) return 'ready';
          window.__mtImagesInstalled = true;
          window.__mtApply = function (index, url) {
            var img = document.querySelector('img[data-mt-index="' + index + '"]');
            if (!img) return;
            img.removeAttribute('srcset');
            img.src = url;
            img.style.height = 'auto';
            img.style.maxWidth = '100%';
            var parent = img.parentElement;
            if (parent) parent.style.height = 'auto';
          };
          window.__mtScanImages = function () {
            var all = Array.prototype.slice.call(document.querySelectorAll('img'));
            var candidates = all.filter(function (img) {
              if (img.dataset.mtIndex !== undefined) return false;
              var rect = img.getBoundingClientRect();
              var source = img.currentSrc || img.src || '';
              return img.naturalWidth >= 300 && rect.width >= 120 && source.indexOf('data:') !== 0;
            });
            candidates.slice(0, 30).forEach(function (img, index) {
              img.dataset.mtIndex = String(index);
              var source = img.currentSrc || img.src;
              fetch(source, { credentials: 'include' })
                .then(function (response) { return response.blob(); })
                .then(function (blob) {
                  return new Promise(function (resolve, reject) {
                    var reader = new FileReader();
                    reader.onload = function () { resolve(reader.result); };
                    reader.onerror = reject;
                    reader.readAsDataURL(blob);
                  });
                })
                .then(function (dataUrl) {
                  AndroidImages.submit(index, dataUrl, img.naturalWidth, img.naturalHeight);
                })
                .catch(function (error) {
                  AndroidImages.failed(index, String(error));
                });
            });
            return candidates.length;
          };
          return 'ok';
        })();
    """.trimIndent()
}
