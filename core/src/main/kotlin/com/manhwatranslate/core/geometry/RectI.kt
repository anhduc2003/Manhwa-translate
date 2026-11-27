package com.manhwatranslate.core.geometry

import kotlinx.serialization.Serializable

/**
 * Integer rectangle with exclusive right/bottom edges, matching Android's Rect semantics.
 */
@Serializable
data class RectI(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val area: Long get() = width.toLong() * height.toLong()
    val centerX: Int get() = (left + right) / 2
    val centerY: Int get() = (top + bottom) / 2
    val isEmpty: Boolean get() = width <= 0 || height <= 0

    fun contains(x: Int, y: Int): Boolean = x >= left && x < right && y >= top && y < bottom

    fun contains(other: RectI): Boolean =
        other.left >= left && other.top >= top && other.right <= right && other.bottom <= bottom

    fun intersects(other: RectI): Boolean =
        left < other.right && other.left < right && top < other.bottom && other.top < bottom

    fun intersection(other: RectI): RectI? {
        val l = maxOf(left, other.left)
        val t = maxOf(top, other.top)
        val r = minOf(right, other.right)
        val b = minOf(bottom, other.bottom)
        return if (r <= l || b <= t) null else RectI(l, t, r, b)
    }

    fun union(other: RectI): RectI = RectI(
        minOf(left, other.left),
        minOf(top, other.top),
        maxOf(right, other.right),
        maxOf(bottom, other.bottom),
    )

    fun expanded(dx: Int, dy: Int = dx): RectI = RectI(left - dx, top - dy, right + dx, bottom + dy)

    fun translated(dx: Int, dy: Int): RectI = RectI(left + dx, top + dy, right + dx, bottom + dy)

    fun clamped(width: Int, height: Int): RectI? {
        val l = left.coerceIn(0, width)
        val t = top.coerceIn(0, height)
        val r = right.coerceIn(0, width)
        val b = bottom.coerceIn(0, height)
        return if (r <= l || b <= t) null else RectI(l, t, r, b)
    }

    fun iou(other: RectI): Float {
        val inter = intersection(other) ?: return 0f
        val interArea = inter.area.toFloat()
        val unionArea = area + other.area - inter.area
        return if (unionArea <= 0f) 0f else interArea / unionArea
    }

    /** Fraction of this rectangle's area covered by [other]. */
    fun coverageBy(other: RectI): Float {
        val inter = intersection(other) ?: return 0f
        return if (area <= 0L) 0f else inter.area.toFloat() / area.toFloat()
    }

    fun verticalGap(other: RectI): Int = when {
        bottom <= other.top -> other.top - bottom
        other.bottom <= top -> top - other.bottom
        else -> 0
    }

    fun horizontalOverlap(other: RectI): Int =
        maxOf(0, minOf(right, other.right) - maxOf(left, other.left))

    override fun toString(): String = "[$left,$top,$right,$bottom]"

}
