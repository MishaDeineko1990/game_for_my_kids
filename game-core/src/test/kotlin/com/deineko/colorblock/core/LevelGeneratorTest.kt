package com.deineko.colorblock.core

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Generates and validates the 100 shipped levels. This is the actual verification
 * loop for level design: every level must be reachable from its start state to a
 * fully-exited state, checked by exhaustive BFS in [LevelSolver], not eyeballed.
 *
 * Running this test also (re)renders `build/generated-levels.txt` — the literal
 * Kotlin source pasted into `Levels.kt`. Re-run and re-paste whenever the difficulty
 * curve in [LevelGenerator.difficultyFor] or the shape pool changes.
 */
class LevelGeneratorTest {

    @Test
    fun `all 100 levels generate and are solvable within a fair move range`() {
        val levels = (1..100).map { id ->
            val level = LevelGenerator.generate(id)
            assertTrue(level != null, "level $id: no solvable candidate found within attempt budget")
            level!!
        }

        levels.forEach { level ->
            val result = LevelSolver.solve(level)
            assertTrue(result.solvable, "level ${level.id} is not solvable on independent re-check")
        }

        File("build/generated-levels.txt").apply { parentFile?.mkdirs() }.writeText(renderKotlinSource(levels))
    }

    private fun renderKotlinSource(levels: List<LevelDef>): String = buildString {
        appendLine("val ALL_LEVELS: List<LevelDef> = listOf(")
        for (level in levels) {
            appendLine("    LevelDef(")
            appendLine("        id = ${level.id},")
            appendLine("        rows = ${level.rows},")
            appendLine("        cols = ${level.cols},")
            appendLine("        pieces = listOf(")
            for (p in level.pieces) {
                val cells = p.cells.joinToString(", ") { "Cell(${it.dr}, ${it.dc})" }
                appendLine(
                    "            PieceDef(id = ${p.id}, color = BlockColor.${p.color}, kind = PieceKind.${p.kind}, " +
                        "cells = listOf($cells), row = ${p.row}, col = ${p.col}),",
                )
            }
            appendLine("        ),")
            appendLine("        gates = listOf(")
            for (g in level.gates) {
                val lanes = g.lanes.sorted().joinToString(", ")
                appendLine("            GateDef(color = BlockColor.${g.color}, side = Side.${g.side}, lanes = setOf($lanes)),")
            }
            appendLine("        ),")
            appendLine("    ),")
        }
        appendLine(")")
    }
}
