package com.niblet.app.reader

import android.graphics.Bitmap
import android.graphics.Rect

object PanelDetector {

    data class Panel(val rect: Rect)

    private const val MIN_PANEL_SIZE = 40
    private const val MIN_GUTTER_THICKNESS = 6
    private const val COLOR_DELTA_SQ = 400
    private const val MAX_PANELS = 20

    fun detect(bmp: Bitmap): List<Panel> {
        val w = bmp.width
        val h = bmp.height
        if (w < 100 || h < 100) return listOf(Panel(Rect(0, 0, w, h)))

        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)

        // Find the "background" color: sample the border pixels, take median luminance
        val bg = estimateBackground(pixels, w, h)
        val dark = luminance(bg) < 100

        val rowGutter = BooleanArray(h) { y -> isGutterRow(pixels, w, h, y, bg, dark) }
        val colGutter = BooleanArray(w) { x -> isGutterCol(pixels, w, h, x, bg, dark) }

        val rowSplits = mergeAdjacent(contiguousRuns(rowGutter, MIN_GUTTER_THICKNESS), 8)
        val colSplits = mergeAdjacent(contiguousRuns(colGutter, MIN_GUTTER_THICKNESS), 8)

        // No gutters found at all -> whole page is one panel
        if (rowSplits.isEmpty() && colSplits.isEmpty()) {
            return listOf(Panel(Rect(0, 0, w, h)))
        }

        val yBands = toBands(rowSplits, h)
        val xBands = toBands(colSplits, w)

        val panels = mutableListOf<Panel>()
        for ((y1, y2) in yBands) for ((x1, x2) in xBands) {
            if (y2 - y1 >= MIN_PANEL_SIZE && x2 - x1 >= MIN_PANEL_SIZE) {
                panels.add(Panel(Rect(x1, y1, x2, y2)))
            }
        }

        // Too many small panels = false positive; fall back
        if (panels.size < 2 || panels.size > MAX_PANELS) {
            return listOf(Panel(Rect(0, 0, w, h)))
        }
        return panels
    }

    // -- helpers --

    private fun estimateBackground(pixels: IntArray, w: Int, h: Int): Int {
        // Sample the four borders (thick strips to smooth out noise)
        val samples = mutableListOf<Int>()
        val thick = (minOf(w, h) / 20).coerceIn(2, 20)
        for (x in 0 until w step 4) {
            for (t in 0 until thick) {
                samples.add(pixels[t * w + x])
                samples.add(pixels[(h - 1 - t) * w + x])
            }
        }
        for (y in 0 until h step 4) {
            for (t in 0 until thick) {
                samples.add(pixels[y * w + t])
                samples.add(pixels[y * w + (w - 1 - t)])
            }
        }
        // Bucket by coarse RGB to find the most common border color
        val buckets = HashMap<Int, Int>()
        for (c in samples) {
            val key = ((c shr 16) and 0xFF).coarsen() shl 16 or
                      ((c shr 8) and 0xFF).coarsen() shl 8 or
                      (c and 0xFF).coarsen()
            buckets[key] = (buckets[key] ?: 0) + 1
        }
        return buckets.maxByOrNull { it.value }?.key ?: 0xFFFFFF
    }

    private fun Int.coarsen(): Int = (this / 32) * 32

    private fun luminance(c: Int): Int {
        val r = (c shr 16) and 0xFF
        val g = (c shr 8) and 0xFF
        val b = c and 0xFF
        return (r * 299 + g * 587 + b * 114) / 1000
    }

    private fun isGutterRow(pixels: IntArray, w: Int, h: Int, y: Int, bg: Int, dark: Boolean): Boolean {
        // A row is gutter if >=85% of pixels are "background-like"
        var match = 0
        var total = 0
        for (x in 0 until w step 2) {
            val c = pixels[y * w + x]
            total++
            if (isBackgroundLike(c, bg, dark)) match++
        }
        return total > 0 && match.toFloat() / total >= 0.85f
    }

    private fun isGutterCol(pixels: IntArray, w: Int, h: Int, x: Int, bg: Int, dark: Boolean): Boolean {
        var match = 0
        var total = 0
        for (y in 0 until h step 2) {
            val c = pixels[y * w + x]
            total++
            if (isBackgroundLike(c, bg, dark)) match++
        }
        return total > 0 && match.toFloat() / total >= 0.85f
    }

    private fun isBackgroundLike(c: Int, bg: Int, dark: Boolean): Boolean {
        val dr = ((c shr 16) and 0xFF) - ((bg shr 16) and 0xFF)
        val dg = ((c shr 8) and 0xFF) - ((bg shr 8) and 0xFF)
        val db = (c and 0xFF) - (bg and 0xFF)
        return dr * dr + dg * dg + db * db < COLOR_DELTA_SQ
    }

    private fun contiguousRuns(flags: BooleanArray, minLen: Int): List<IntRange> {
        val out = mutableListOf<IntRange>()
        var start = -1
        for (i in flags.indices) {
            if (flags[i]) {
                if (start < 0) start = i
            } else {
                if (start >= 0) {
                    if (i - start >= minLen) out.add(start until i)
                    start = -1
                }
            }
        }
        if (start >= 0 && flags.size - start >= minLen) out.add(start until flags.size)
        return out
    }

    private fun mergeAdjacent(runs: List<IntRange>, maxGap: Int): List<IntRange> {
        if (runs.isEmpty()) return runs
        val out = mutableListOf<IntRange>()
        var current = runs[0]
        for (i in 1 until runs.size) {
            val next = runs[i]
            if (next.first - current.last <= maxGap) {
                current = current.first until next.last
            } else {
                out.add(current)
                current = next
            }
        }
        out.add(current)
        return out
    }

    private fun toBands(splits: List<IntRange>, size: Int): List<Pair<Int, Int>> {
        val out = mutableListOf<Pair<Int, Int>>()
        var cursor = 0
        for (s in splits) {
            if (s.first > cursor) out.add(cursor to s.first)
            cursor = s.last + 1
        }
        if (cursor < size) out.add(cursor to size)
        return out
    }
}
