package com.deineko.colorblock.core

import kotlin.random.Random

/**
 * Places non-overlapping RAIL bars and FREE polyominoes at random cells and gives
 * each one a same-color gate on a side it can reach without changing its other
 * axis. This does NOT guarantee solvability by itself -- a piece placed later can
 * block an earlier piece's only route to its gate. [generate] compensates by
 * running [LevelSolver] on every candidate and only returning one that is
 * actually solvable within a sane move-count window; candidates that fail are
 * discarded and a new random layout is tried instead.
 */
object LevelGenerator {

    // Fixed-orientation polyomino pool for FREE pieces -- no in-game rotation (see
    // Model.kt doc on PieceKind): the generator instead samples pre-rotated
    // variants so 100 levels don't all show the same handful of silhouettes.
    private val TROMINOES: List<List<Cell>> = listOf(
        listOf(Cell(0, 0), Cell(1, 0), Cell(1, 1)),
        listOf(Cell(0, 1), Cell(1, 0), Cell(1, 1)),
        listOf(Cell(0, 0), Cell(0, 1), Cell(1, 1)),
        listOf(Cell(0, 0), Cell(0, 1), Cell(1, 0)),
    )
    private val TETROMINOES: List<List<Cell>> = listOf(
        listOf(Cell(0, 0), Cell(0, 1), Cell(1, 0), Cell(1, 1)), // O
        listOf(Cell(0, 0), Cell(0, 1), Cell(0, 2), Cell(1, 1)), // T down
        listOf(Cell(1, 0), Cell(1, 1), Cell(1, 2), Cell(0, 1)), // T up
        listOf(Cell(0, 1), Cell(0, 2), Cell(1, 0), Cell(1, 1)), // S
        listOf(Cell(0, 0), Cell(0, 1), Cell(1, 1), Cell(1, 2)), // Z
        listOf(Cell(0, 0), Cell(1, 0), Cell(2, 0), Cell(2, 1)), // L
        listOf(Cell(0, 1), Cell(1, 1), Cell(2, 1), Cell(2, 0)), // J
        listOf(Cell(0, 0), Cell(1, 0), Cell(1, 1), Cell(2, 1)), // S vertical
        listOf(Cell(0, 1), Cell(1, 0), Cell(1, 1), Cell(2, 0)), // Z vertical
    )

    data class Difficulty(
        val rows: Int,
        val cols: Int,
        val numRail: Int,
        val numFree: Int,
        val colorCount: Int,
        val allowRailLength3: Boolean,
        val freePool: List<List<Cell>>,
    )

    /**
     * Difficulty curve across 10 worlds of 10 levels each (100 total). Board size
     * caps at 7x7 and total piece count at ~7 -- FREE pieces move in 4 directions
     * instead of a RAIL's 2, so the solver's branching factor (and therefore BFS
     * cost) grows much faster per FREE piece than per RAIL piece. An earlier
     * attempt at 8x8 boards with ~10 pieces (4-5 of them FREE) got killed after
     * one late-game level ran for 13+ minutes without finishing; these caps keep
     * every level's generation+solve well under a second.
     */
    fun difficultyFor(levelId: Int): Difficulty {
        require(levelId in 1..100)
        val posInWorld = (levelId - 1) % 10
        return when ((levelId - 1) / 10) {
            0 -> Difficulty(4, 4, numRail = 1 + posInWorld / 4, numFree = 0, colorCount = 2, allowRailLength3 = false, freePool = emptyList())
            1 -> Difficulty(5, 5, numRail = 2 + posInWorld / 3, numFree = 0, colorCount = 3, allowRailLength3 = true, freePool = emptyList())
            2 -> Difficulty(5, 5, numRail = 2, numFree = 1, colorCount = 3, allowRailLength3 = true, freePool = TROMINOES)
            3 -> Difficulty(5, 6, numRail = 2, numFree = 1 + posInWorld / 6, colorCount = 4, allowRailLength3 = true, freePool = TROMINOES)
            4 -> Difficulty(6, 6, numRail = 2, numFree = 2, colorCount = 4, allowRailLength3 = true, freePool = TROMINOES + TETROMINOES)
            5 -> Difficulty(6, 6, numRail = 2 + posInWorld / 6, numFree = 2, colorCount = 5, allowRailLength3 = true, freePool = TROMINOES + TETROMINOES)
            6 -> Difficulty(6, 6, numRail = 3, numFree = 2, colorCount = 5, allowRailLength3 = true, freePool = TETROMINOES)
            7 -> Difficulty(7, 7, numRail = 3, numFree = 2, colorCount = 5, allowRailLength3 = true, freePool = TETROMINOES)
            8 -> Difficulty(7, 7, numRail = 3, numFree = 2 + posInWorld / 6, colorCount = 6, allowRailLength3 = true, freePool = TETROMINOES)
            else -> Difficulty(7, 7, numRail = 3 + posInWorld / 6, numFree = 3, colorCount = 6, allowRailLength3 = true, freePool = TETROMINOES)
        }
    }

    /**
     * Tries seeds until it finds a layout solvable within [minMoves]..[maxMoves]
     * (too easy or absurdly long both make bad levels for this age group).
     * Returns null if nothing worked within [attempts] -- callers should widen the
     * move bounds rather than ship a level that never converged.
     */
    fun generate(
        levelId: Int,
        attempts: Int = 800,
        minMoves: Int = 1,
        maxMoves: Int = 18,
    ): LevelDef? {
        val difficulty = difficultyFor(levelId)
        repeat(attempts) { attempt ->
            val seed = levelId * 1_000_003L + attempt
            val level = buildCandidate(levelId, difficulty, Random(seed)) ?: return@repeat
            // A smaller budget here than LevelSolver's default: rejecting a bad
            // candidate fast matters far more during search than proving one
            // candidate unsolvable exhaustively -- there are hundreds more seeds
            // to try. The final shipped level still gets the full-budget check in
            // LevelsTest, by which point a solution is already known to exist.
            val result = LevelSolver.solve(level, maxStates = 60_000)
            if (result.solvable && result.minMoves != null && result.minMoves in minMoves..maxMoves) {
                return level
            }
        }
        return null
    }

    private fun shapeBounds(shape: List<Cell>): Pair<Int, Int> {
        val h = shape.maxOf { it.dr } + 1
        val w = shape.maxOf { it.dc } + 1
        return h to w
    }

    private fun cellsFree(occupied: Array<BooleanArray>, shape: List<Cell>, row: Int, col: Int): Boolean =
        shape.all { !occupied[row + it.dr][col + it.dc] }

    private fun markCells(occupied: Array<BooleanArray>, shape: List<Cell>, row: Int, col: Int) {
        for (cell in shape) occupied[row + cell.dr][col + cell.dc] = true
    }

    private fun laneSpanForPlacement(shape: List<Cell>, row: Int, col: Int, side: Side): Set<Int> {
        val abs = shape.map { (row + it.dr) to (col + it.dc) }
        return if (side == Side.LEFT || side == Side.RIGHT) abs.map { it.first }.toSet() else abs.map { it.second }.toSet()
    }

    private fun lanesConflict(gates: List<GateDef>, side: Side, lanes: Set<Int>, color: BlockColor): Boolean =
        gates.any { it.side == side && it.color != color && it.lanes.any { lane -> lane in lanes } }

    private fun buildCandidate(levelId: Int, d: Difficulty, random: Random): LevelDef? {
        val occupied = Array(d.rows) { BooleanArray(d.cols) }
        val pieces = mutableListOf<PieceDef>()
        val gates = mutableListOf<GateDef>()
        val colors = BlockColor.entries.take(d.colorCount)
        var nextId = 1

        fun tryPlaceRail(): Boolean {
            val horizontal = random.nextBoolean()
            val length = if (d.allowRailLength3 && random.nextInt(3) == 0) 3 else 2
            val shape = if (horizontal) (0 until length).map { Cell(0, it) } else (0 until length).map { Cell(it, 0) }
            val (h, w) = shapeBounds(shape)
            if (h > d.rows || w > d.cols) return false
            val row = random.nextInt(d.rows - h + 1)
            val col = random.nextInt(d.cols - w + 1)
            if (!cellsFree(occupied, shape, row, col)) return false

            val color = colors[random.nextInt(colors.size)]
            val side = if (horizontal) (if (random.nextBoolean()) Side.LEFT else Side.RIGHT) else (if (random.nextBoolean()) Side.TOP else Side.BOTTOM)
            val lanes = laneSpanForPlacement(shape, row, col, side)
            if (lanesConflict(gates, side, lanes, color)) return false

            markCells(occupied, shape, row, col)
            pieces += PieceDef(nextId++, color, PieceKind.RAIL, shape, row, col)
            gates += GateDef(color, side, lanes)
            return true
        }

        fun tryPlaceFree(): Boolean {
            if (d.freePool.isEmpty()) return false
            val shape = d.freePool[random.nextInt(d.freePool.size)]
            val (h, w) = shapeBounds(shape)
            if (h > d.rows || w > d.cols) return false
            val row = random.nextInt(d.rows - h + 1)
            val col = random.nextInt(d.cols - w + 1)
            if (!cellsFree(occupied, shape, row, col)) return false

            val color = colors[random.nextInt(colors.size)]
            val side = Side.entries[random.nextInt(4)]
            val lanes = laneSpanForPlacement(shape, row, col, side)
            if (lanesConflict(gates, side, lanes, color)) return false

            markCells(occupied, shape, row, col)
            pieces += PieceDef(nextId++, color, PieceKind.FREE, shape, row, col)
            gates += GateDef(color, side, lanes)
            return true
        }

        var railPlaced = 0
        var freePlaced = 0
        var safety = 0
        val budget = (d.numRail + d.numFree) * 60
        while ((railPlaced < d.numRail || freePlaced < d.numFree) && safety < budget) {
            safety++
            if (railPlaced < d.numRail && tryPlaceRail()) railPlaced++
            else if (freePlaced < d.numFree && tryPlaceFree()) freePlaced++
        }
        if (railPlaced < d.numRail || freePlaced < d.numFree) return null

        return LevelDef(levelId, d.rows, d.cols, pieces, gates)
    }
}
