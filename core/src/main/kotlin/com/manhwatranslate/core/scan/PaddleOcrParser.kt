package com.manhwatranslate.core.scan

import com.manhwatranslate.core.geometry.RectI
import com.manhwatranslate.core.model.TextLine
import com.manhwatranslate.core.model.TextRegion
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * Reads a PaddleOCR answer into regions. Both shapes the project ships are accepted:
 *
 * * PaddleOCR 2.x Hub Serving — `results` holding per-image lists of
 *   `{text, confidence, text_region}`.
 * * PaddleOCR 3.x / PaddleX serving — one object with parallel `rec_texts`, `rec_scores`,
 *   `rec_polys` (or `rec_boxes`) arrays.
 *
 * Boxes come back in the coordinate space of the image that was sent, so the caller passes the
 * tile's place on the page and gets page coordinates ready for the rest of the pipeline.
 */
object PaddleOcrParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(raw: String, offsetX: Int = 0, offsetY: Int = 0): List<TextRegion> {
        val root = runCatching { json.parseToJsonElement(raw) }.getOrNull() ?: return emptyList()
        val regions = ArrayList<TextRegion>()
        walk(root, regions, offsetX, offsetY)
        return regions.mapIndexed { index, region -> region.copy(id = index) }
    }

    private fun walk(element: JsonElement, out: MutableList<TextRegion>, dx: Int, dy: Int) {
        when (element) {
            is JsonArray -> element.forEach { walk(it, out, dx, dy) }
            is JsonObject -> {
                when {
                    element["rec_texts"] != null -> readRecArrays(element, out, dx, dy)
                    element["text"] != null -> readSingleLine(element, out, dx, dy)
                    else -> {
                        element["results"]?.let { walk(it, out, dx, dy) }
                        element["result"]?.let { walk(it, out, dx, dy) }
                        element["data"]?.let { walk(it, out, dx, dy) }
                    }
                }
            }
            else -> Unit
        }
    }

    private fun readSingleLine(entry: JsonObject, out: MutableList<TextRegion>, dx: Int, dy: Int) {
        val text = entry["text"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (text.isBlank()) return
        val points = entry["text_region"]?.jsonArray ?: entry["points"]?.jsonArray
        val box = points?.let { boxOfPoints(it, dx, dy) } ?: return
        val confidence = entry["confidence"]?.jsonPrimitive?.doubleOrNull?.toFloat()
        out += region(box, text, confidence)
    }

    private fun readRecArrays(node: JsonObject, out: MutableList<TextRegion>, dx: Int, dy: Int) {
        val texts = node["rec_texts"]?.jsonArray ?: return
        val polygons = node["rec_polys"]?.jsonArray
        val boxes = node["rec_boxes"]?.jsonArray
        val scores = node["rec_scores"]?.jsonArray
        texts.forEachIndexed { index, textElement ->
            val text = textElement.jsonPrimitive.contentOrNull?.trim().orEmpty()
            if (text.isBlank()) return@forEachIndexed
            val polygon = polygons?.getOrNull(index) as? JsonArray
            val box = when {
                polygon != null -> boxOfPoints(polygon, dx, dy)
                else -> (boxes?.getOrNull(index) as? JsonArray)?.let { boxOfCorners(it, dx, dy) }
            } ?: return@forEachIndexed
            val score = (scores?.getOrNull(index) as? JsonPrimitive)?.doubleOrNull?.toFloat()
            out += region(box, text, score)
        }
    }

    private fun boxOfPoints(points: JsonArray, dx: Int, dy: Int): RectI? {
        var left = Int.MAX_VALUE
        var top = Int.MAX_VALUE
        var right = Int.MIN_VALUE
        var bottom = Int.MIN_VALUE
        for (point in points) {
            val pair = point as? JsonArray ?: continue
            val x = pair.getOrNull(0)?.jsonPrimitive?.doubleOrNull ?: continue
            val y = pair.getOrNull(1)?.jsonPrimitive?.doubleOrNull ?: continue
            left = minOf(left, x.toInt())
            top = minOf(top, y.toInt())
            right = maxOf(right, x.toInt())
            bottom = maxOf(bottom, y.toInt())
        }
        return rectOrNull(left, top, right, bottom, dx, dy)
    }

    private fun boxOfCorners(corners: JsonArray, dx: Int, dy: Int): RectI? {
        val x0 = corners.getOrNull(0)?.jsonPrimitive?.doubleOrNull ?: return null
        val y0 = corners.getOrNull(1)?.jsonPrimitive?.doubleOrNull ?: return null
        val x1 = corners.getOrNull(2)?.jsonPrimitive?.doubleOrNull ?: return null
        val y1 = corners.getOrNull(3)?.jsonPrimitive?.doubleOrNull ?: return null
        return rectOrNull(
            minOf(x0, x1).toInt(),
            minOf(y0, y1).toInt(),
            maxOf(x0, x1).toInt(),
            maxOf(y0, y1).toInt(),
            dx,
            dy,
        )
    }

    private fun rectOrNull(left: Int, top: Int, right: Int, bottom: Int, dx: Int, dy: Int): RectI? {
        if (left == Int.MAX_VALUE || right <= left || bottom <= top) return null
        return RectI(left + dx, top + dy, right + dx, bottom + dy)
    }

    private fun region(box: RectI, text: String, confidence: Float?): TextRegion = TextRegion(
        id = 0,
        box = box,
        lines = listOf(TextLine(box, text)),
        text = text,
        confidence = confidence,
    )
}
