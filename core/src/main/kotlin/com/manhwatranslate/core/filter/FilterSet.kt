package com.manhwatranslate.core.filter

/**
 * One distilled ad/tracker filter list: blocked hosts, hosts that must stay allowed, and CSS
 * selectors for hiding ad slots that load no request of their own.
 *
 * Every lookup walks the host's parent domains, so a rule for `doubleclick.net` also covers
 * `ad.doubleclick.net`.
 */
class FilterSet(
    val id: String,
    val label: String,
    val blockedHosts: Set<String>,
    val allowedHosts: Set<String> = emptySet(),
    val selectors: List<String> = emptyList(),
    val scopedSelectors: List<ScopedSelector> = emptyList(),
) {
    data class ScopedSelector(val host: String, val selector: String)

    val domainCount: Int get() = blockedHosts.size

    val selectorCount: Int get() = selectors.size + scopedSelectors.size

    fun blocksHost(host: String): Boolean {
        if (host.isBlank()) return false
        val clean = host.lowercase().trim('.')
        if (matchesAny(allowedHosts, clean)) return false
        return matchesAny(blockedHosts, clean)
    }

    /** CSS that hides this list's ad slots on [host]. */
    fun cssFor(host: String): String {
        if (host.isBlank()) return selectors.joinToString(",")
        val clean = host.lowercase().trim('.')
        val scoped = scopedSelectors.filter { entry ->
            clean == entry.host || clean.endsWith(".${entry.host}")
        }.map { it.selector }
        return (selectors + scoped).joinToString(",")
    }

    private fun matchesAny(hosts: Set<String>, host: String): Boolean {
        if (hosts.isEmpty()) return false
        var candidate = host
        while (true) {
            if (candidate in hosts) return true
            val dot = candidate.indexOf('.')
            if (dot < 0 || dot == candidate.length - 1) return false
            candidate = candidate.substring(dot + 1)
        }
    }
}

/** Reads the distilled list assets: one host per line, or `host|selector` for scoped hiding. */
object FilterListParser {

    fun parseDomains(lines: Sequence<String>): Set<String> {
        val out = HashSet<String>(8192)
        lines.forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("!")) return@forEach
            out += line.lowercase()
        }
        return out
    }

    fun parseSelectors(lines: Sequence<String>): Pair<List<String>, List<FilterSet.ScopedSelector>> {
        val plain = ArrayList<String>()
        val scoped = ArrayList<FilterSet.ScopedSelector>()
        lines.forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty()) return@forEach
            val bar = line.indexOf('|')
            if (bar <= 0 || bar == line.length - 1) {
                plain += line
            } else {
                val host = line.substring(0, bar).trim().lowercase().trim('.')
                val selector = line.substring(bar + 1).trim()
                if (host.isNotEmpty() && selector.isNotEmpty()) scoped += FilterSet.ScopedSelector(host, selector)
            }
        }
        return plain to scoped
    }
}
