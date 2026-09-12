package com.deineko.colorblock.core

/** A corner point in cell-grid coordinates (not pixels) -- the UI scales these to a canvas. */
data class OutlinePoint(val row: Int, val col: Int)

/**
 * Traces the outer boundary of a simply-connected polyomino [shape] into a
 * simplified corner loop, in clockwise order, with straight-run intermediate
 * points removed (only real turns kept). Used to draw a FREE piece as one
 * rounded silhouette instead of a grid of separate unit squares.
 */
object PolyominoOutline {

    fun build(shape: List<Cell>): List<OutlinePoint> {
        val cellSet = shape.map { it.dr to it.dc }.toSet()
        fun has(r: Int, c: Int) = (r to c) in cellSet

        val edges = mutableMapOf<Pair<Int, Int>, Pair<Int, Int>>()
        for ((dr, dc) in shape.map { it.dr to it.dc }) {
            if (!has(dr - 1, dc)) edges[dr to dc] = dr to (dc + 1)
            if (!has(dr, dc + 1)) edges[dr to (dc + 1)] = (dr + 1) to (dc + 1)
            if (!has(dr + 1, dc)) edges[(dr + 1) to (dc + 1)] = (dr + 1) to dc
            if (!has(dr, dc - 1)) edges[(dr + 1) to dc] = dr to dc
        }

        val start = edges.keys.first()
        val loopRaw = mutableListOf(start)
        var current = start
        while (true) {
            val next = edges.getValue(current)
            if (next == start) break
            loopRaw += next
            current = next
        }

        val n = loopRaw.size
        val simplified = mutableListOf<Pair<Int, Int>>()
        for (i in 0 until n) {
            val prev = loopRaw[(i - 1 + n) % n]
            val cur = loopRaw[i]
            val next = loopRaw[(i + 1) % n]
            val d1 = (cur.first - prev.first) to (cur.second - prev.second)
            val d2 = (next.first - cur.first) to (next.second - cur.second)
            if (d1 != d2) simplified += cur
        }
        return simplified.map { OutlinePoint(it.first, it.second) }
    }
}
