package com.deineko.colorblock.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Verifies the boundary tracing used to render a FREE piece as one rounded silhouette. */
class PolyominoOutlineTest {

    private fun shoelaceArea(points: List<OutlinePoint>): Double {
        var area = 0.0
        for (i in points.indices) {
            val a = points[i]
            val b = points[(i + 1) % points.size]
            area += a.row.toDouble() * b.col - b.row.toDouble() * a.col
        }
        return kotlin.math.abs(area) / 2.0
    }

    @Test
    fun `O piece (2x2 square) has 4 corners and area 4`() {
        val outline = PolyominoOutline.build(listOf(Cell(0, 0), Cell(0, 1), Cell(1, 0), Cell(1, 1)))
        assertEquals(4, outline.size)
        assertEquals(4.0, shoelaceArea(outline))
    }

    @Test
    fun `L-tromino has 6 corners and area 3`() {
        val outline = PolyominoOutline.build(listOf(Cell(0, 0), Cell(1, 0), Cell(1, 1)))
        assertEquals(6, outline.size)
        assertEquals(3.0, shoelaceArea(outline))
    }

    @Test
    fun `T-tetromino has 8 corners and area 4`() {
        val outline = PolyominoOutline.build(listOf(Cell(0, 0), Cell(0, 1), Cell(0, 2), Cell(1, 1)))
        assertEquals(8, outline.size)
        assertEquals(4.0, shoelaceArea(outline))
    }

    @Test
    fun `every consecutive corner pair is an axis-aligned edge`() {
        val shapes = listOf(
            listOf(Cell(0, 0), Cell(0, 1), Cell(1, 0), Cell(1, 1)),
            listOf(Cell(0, 0), Cell(1, 0), Cell(1, 1)),
            listOf(Cell(0, 0), Cell(0, 1), Cell(0, 2), Cell(1, 1)),
        )
        for (shape in shapes) {
            val outline = PolyominoOutline.build(shape)
            for (i in outline.indices) {
                val a = outline[i]
                val b = outline[(i + 1) % outline.size]
                assertTrue(a.row == b.row || a.col == b.col, "edge $a->$b is not axis-aligned")
            }
        }
    }
}
