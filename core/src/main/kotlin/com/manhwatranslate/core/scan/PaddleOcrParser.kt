package com.manhwatranslate.core.scan

import com.manhwatranslate.core.geometry.RectI
import com.manhwatranslate.core.model.TextLine
import com.manhwatranslate.core.model.TextRegion
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Reads a PaddleOCR server answer into regions. The Hub Serving endpoint wraps its lines in
 * different envelopes depending on the model export (`results` holding a list of per-image
 * lists, `results` holding `{data: [...]}`, or a bare list), so all three are accepted and the
 * boxes are moved from tile space to page space.
 */
object PaddleOcrParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(raw: String, offsetX: Int = 0, offsetY: Int = 0): List<TextRegion> {
        val root = runCatching { json.parseToJsonElement(raw) }.getOrNull() ?: return emptyList()
        val lines = collectLines(root)
        return lines.mapIndexedNotNull { index, element ->
            val entry = element as? JsonObject ?: return@mapIndexedNotNull null
            val text = entry["text"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            if (text.isBlank()) return@mapIndexedNotNull null
            val box = boxOf(entry, offsetX, offsetY) ?: return@mapIndexedNotNull null
            val confidence = entry["confidence"]?.jsonPrimitive?.doubleOrNull?.toFloat()
            TextRegion(
                id = index,
                box = box,
                lines = listOf(TextLine(box, text)),
                text = text,
                confidence = confidence,
            )
        }
    }

    private fun collectLines(root: kotlinx.serialization.json.JsonElement): List<kotlinx.serialization.json.JsonElement> {
        val out = ArrayList<kotlinx.serialization.json.JsonElement>()
        fun walk(element: kotlinx.serialization.json.JsonElement) {
            when (element) {
                is JsonArray -> element.forEach { child ->
                    if (child is JsonObject && child["text"] != null) out += child else walk(child)
                }
                is JsonObject -> {
                    if (element["text"] != null) {
                        out += element
                    } else {
                        element["results"]?.let { walk(it) }
                            ?: element["data"]?.let { walk(it) }
                            ?: element["result"]?.let { walk(it) }
                    }
                }
                else -> Unit
            }
        }
        walk(root)
        return out
    }

    private fun boxOf(entry: JsonObject, offsetX: Int, offsetY: Int): RectI? {
        val region = entry["text_region"]?.jsonArray ?: entry["points"]?.jsonArray ?: return null
        var left = Int.MAX_VALUE
        var top = Int.MAX_VALUE
        var right = Int.MIN_VALUE
        var bottom = Int.MIN_VALUE
        for (point in region) {
            val pair = point as? JsonArray ?: continue
            val x = pair.getOrNull(0)?.jsonPrimitive?.doubleOrNull ?: continue
            val y = pair.getOrNull(1)?.jsonPrimitive?.doubleOrNull ?: continue
            left = minOf(left, x.toInt())
            top = minOf(top, y.toInt())
            right = maxOf(right, x.toInt())
            bottom = maxOf(bottom, y.toInt())
        }
        if (left == Int.MAX_VALUE || right <= left || bottom <= top) return null
        return RectI(left + offsetX, top + offsetY, right + offsetX, bottom + offsetY)
    }
}
