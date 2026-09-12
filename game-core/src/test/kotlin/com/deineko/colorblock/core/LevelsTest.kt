package com.deineko.colorblock.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Guards the shipped [ALL_LEVELS] list itself, independent of the generator. */
class LevelsTest {

    @Test
    fun `there are exactly 100 levels, numbered 1 through 100 in order`() {
        assertEquals(100, ALL_LEVELS.size)
        assertEquals((1..100).toList(), ALL_LEVELS.map { it.id })
    }

    @Test
    fun `every shipped level is solvable`() {
        for (level in ALL_LEVELS) {
            val result = LevelSolver.solve(level)
            assertTrue(result.solvable, "level ${level.id} is not solvable")
        }
    }

    @Test
    fun `every piece has at least one gate of its own color`() {
        for (level in ALL_LEVELS) {
            for (piece in level.pieces) {
                assertTrue(
                    level.gates.any { it.color == piece.color },
                    "level ${level.id}: piece ${piece.id} (${piece.color}) has no matching gate",
                )
            }
        }
    }
}
