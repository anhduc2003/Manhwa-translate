package com.manhwatranslate.core.translate

import com.manhwatranslate.core.model.RegionTranslation
import com.manhwatranslate.core.model.TranslationStatus

/** True when this entry carries an actual translation rather than the source it was given. */
fun RegionTranslation.isRealTranslation(): Boolean =
    (status == TranslationStatus.DONE || status == TranslationStatus.EDITED) &&
        translatedText.isNotBlank()

/**
 * Combines a refreshed translation pass with the one it follows, region by region. A fresh
 * entry replaces the previous one only when it is a real translation, so a backend that failed
 * or echoed its input cannot turn a good translation back into the original lettering.
 */
fun mergeTranslations(
    previous: List<RegionTranslation>,
    fresh: List<RegionTranslation>,
): List<RegionTranslation> {
    val before = previous.associateBy { it.regionId }
    val merged = fresh.map { entry ->
        val old = before[entry.regionId]
        if (entry.isRealTranslation() || old == null || !old.isRealTranslation()) entry else old
    }
    val seen = fresh.mapTo(HashSet()) { it.regionId }
    return merged + previous.filter { it.regionId !in seen }
}
