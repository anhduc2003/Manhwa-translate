package com.manhwatranslate.web

/**
 * The stylesheet the reader injects into a page so the site looks like part of the app instead of
 * whatever the site shipped: the app's paper or ink background, its text colour, one centred
 * reading column and rounded artwork.
 *
 * These are the Compose palette values from `ui/theme/Theme.kt` written as CSS. They cannot be read
 * from Compose here because the stylesheet runs inside the WebView, so keep the two in step.
 */
object ReaderStyle {

    private const val PAPER = "#FAF6F3"
    private const val INK = "#221C1A"
    private const val INK_DARK = "#0D0A0D"
    private const val TEXT_DARK = "#F4E9E4"
    private const val TERRACOTTA = "#B4463B"
    private const val TERRACOTTA_LIGHT = "#F4A79C"
    private const val GOLD = "#E0A458"

    /** Text elements the dark sheet repaints so a site's own black-on-white never survives. */
    private const val TEXT_ELEMENTS =
        "p,span,li,td,th,h1,h2,h3,h4,h5,h6,blockquote,figcaption,strong,em,small,label"

    /** Containers that paint a white card behind the artwork on most manhwa sites. */
    private const val BLOCK_ELEMENTS =
        "div,article,section,main,header,footer,aside,nav,figure"

    fun css(dark: Boolean): String = buildString {
        append("html,body{background:").append(if (dark) INK_DARK else PAPER).append("!important;}")
        append("body{")
        append("margin:0 auto!important;")
        append("max-width:780px!important;")
        append("padding:14px 16px 48px!important;")
        append("font-family:-apple-system,Roboto,system-ui,sans-serif!important;")
        append("font-size:17px!important;")
        append("line-height:1.65!important;")
        append("color:").append(if (dark) TEXT_DARK else INK).append("!important;")
        append("overflow-x:hidden!important;")
        append("}")
        append("img,canvas,video,svg{max-width:100%!important;height:auto!important;")
        append("border-radius:14px!important;}")
        append("a{color:").append(if (dark) TERRACOTTA_LIGHT else TERRACOTTA).append("!important;")
        append("text-decoration:none!important;}")
        append("::selection{background:").append(GOLD).append("59!important;}")
        if (dark) {
            append("html{color-scheme:dark!important;}")
            append(TEXT_ELEMENTS).append("{color:").append(TEXT_DARK).append("!important;}")
            append(BLOCK_ELEMENTS).append("{background-color:transparent!important;}")
            append("img,canvas,video{opacity:.94;}")
        }
    }
}
