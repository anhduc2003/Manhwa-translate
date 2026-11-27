package com.manhwatranslate.core.translate

import com.manhwatranslate.core.model.RegionTranslation
import com.manhwatranslate.core.model.TranslationStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TranslationMergeTest {

    private fun entry(id: Int, text: String, status: TranslationStatus) =
        RegionTranslation(regionId = id, sourceText = "원문$id", translatedText = text, status = status)

    @Test
    fun `a failed refinement keeps the earlier translation`() {
        val previous = entry(1, "xin chào", TranslationStatus.DONE)
        val fresh = entry(1, "원문1", TranslationStatus.FAILED)
        assertEquals(listOf(previous), mergeTranslations(listOf(previous), listOf(fresh)))
    }

    @Test
    fun `a fresh translation wins over a done one`() {
        val previous = entry(1, "cũ", TranslationStatus.DONE)
        val fresh = entry(1, "mới", TranslationStatus.DONE)
        assertEquals(listOf(fresh), mergeTranslations(listOf(previous), listOf(fresh)))
    }

    @Test
    fun `an edited fresh entry wins`() {
        val previous = entry(1, "cũ", TranslationStatus.DONE)
        val fresh = entry(1, "sửa", TranslationStatus.EDITED)
        assertEquals(listOf(fresh), mergeTranslations(listOf(previous), listOf(fresh)))
    }

    @Test
    fun `failed over failed takes the fresh entry`() {
        val previous = entry(1, "", TranslationStatus.FAILED)
        val fresh = entry(1, "원문1", TranslationStatus.FAILED)
        assertEquals(listOf(fresh), mergeTranslations(listOf(previous), listOf(fresh)))
    }

    @Test
    fun `a blank skipped entry keeps the earlier translation`() {
        val previous = entry(1, "xin chào", TranslationStatus.DONE)
        val fresh = entry(1, "", TranslationStatus.SKIPPED)
        assertEquals(listOf(previous), mergeTranslations(listOf(previous), listOf(fresh)))
    }

    @Test
    fun `a region only in the previous list is appended`() {
        val previous = listOf(entry(1, "một", TranslationStatus.DONE), entry(2, "hai", TranslationStatus.DONE))
        val fresh = listOf(entry(1, "một mới", TranslationStatus.DONE))
        assertEquals(listOf(fresh[0], previous[1]), mergeTranslations(previous, fresh))
    }

    @Test
    fun `fresh order and ids are preserved`() {
        val previous = listOf(entry(1, "một", TranslationStatus.DONE), entry(2, "hai", TranslationStatus.DONE))
        val fresh = listOf(entry(2, "원문2", TranslationStatus.FAILED), entry(1, "một mới", TranslationStatus.DONE))
        assertEquals(listOf(previous[1], fresh[1]), mergeTranslations(previous, fresh))
    }
}
