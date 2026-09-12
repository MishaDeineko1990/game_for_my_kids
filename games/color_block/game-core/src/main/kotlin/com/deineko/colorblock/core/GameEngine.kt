package com.deineko.colorblock.core

/**
 * Pure game rules: no Android/UI dependency so this can be unit-tested on a plain
 * JVM and reused by the solver. All positions are integer cell coordinates; the UI
 * layer turns finger drags into calls here.
 */
object GameEngine {

    private fun cellsOf(piece: PieceState): List<Pair<Int, Int>> =
        piece.cells.map { (piece.row + it.dr) to (piece.col + it.dc) }

    /** Cells currently occupied by on-board pieces other than [excludingId]. */
    private fun occupiedCells(state: GameState, excludingId: Int): Set<Pair<Int, Int>> {
        val cells = mutableSetOf<Pair<Int, Int>>()
        for (p in state.pieces) {
            if (p.id == excludingId || p.exited) continue
            cells += cellsOf(p)
        }
        return cells
    }

    /**
     * Directions a piece may be driven in. RAIL pieces are restricted to their own
     * fixed axis (derived from their shape being one row or one column tall) --
     * that restriction is a game rule shown to the player as arrows, not a
     * geometric necessity. FREE pieces may move in any of the 4 directions.
     */
    fun allowedDirections(piece: PieceState): List<Direction> = when (piece.kind) {
        PieceKind.FREE -> Direction.entries
        PieceKind.RAIL -> {
            val rows = piece.cells.map { it.dr }.toSet()
            if (rows.size == 1) listOf(Direction.LEFT, Direction.RIGHT) else listOf(Direction.UP, Direction.DOWN)
        }
    }

    /** Distinct rows (LEFT/RIGHT) or cols (TOP/BOTTOM) the piece occupies, in board coordinates -- the width a gate on that side must cover. Public so the UI can locate the matching [GateDef] for a piece that's exiting (e.g. to place an effect at its rectangle). */
    fun laneSpan(piece: PieceState, side: Side): Set<Int> {
        val abs = cellsOf(piece)
        return if (side == Side.LEFT || side == Side.RIGHT) abs.map { it.first }.toSet() else abs.map { it.second }.toSet()
    }

    private fun isFlush(cellsAbs: List<Pair<Int, Int>>, side: Side, level: LevelDef): Boolean = when (side) {
        Side.LEFT -> cellsAbs.minOf { it.second } == 0
        Side.RIGHT -> cellsAbs.maxOf { it.second } == level.cols - 1
        Side.TOP -> cellsAbs.minOf { it.first } == 0
        Side.BOTTOM -> cellsAbs.maxOf { it.first } == level.rows - 1
    }

    private fun gateAllows(level: LevelDef, piece: PieceState, side: Side): Boolean {
        val required = laneSpan(piece, side)
        return level.gates.any { it.color == piece.color && it.side == side && required.all { l -> l in it.lanes } }
    }

    /**
     * How far [pieceId] can move toward [direction] before hitting another piece
     * or a wall ([steps], in cells), and whether going further than that is
     * allowed because it would exit the board through a matching, wide-enough
     * gate ([canExit]).
     */
    data class RunResult(val steps: Int, val canExit: Boolean)

    fun freeRun(state: GameState, pieceId: Int, direction: Direction): RunResult {
        val piece = state.piece(pieceId)
        check(!piece.exited) { "piece $pieceId already exited" }
        val occupied = occupiedCells(state, pieceId)
        var steps = 0
        while (true) {
            val moved = piece.cells.map { (piece.row + it.dr + direction.dr * (steps + 1)) to (piece.col + it.dc + direction.dc * (steps + 1)) }
            val outOfBounds = moved.any { it.first !in 0 until state.level.rows || it.second !in 0 until state.level.cols }
            if (outOfBounds) {
                val atSteps = piece.copy(row = piece.row + direction.dr * steps, col = piece.col + direction.dc * steps)
                val flush = isFlush(cellsOf(atSteps), direction.side, state.level)
                val canExit = flush && gateAllows(state.level, atSteps, direction.side)
                return RunResult(steps, canExit)
            }
            if (moved.any { it in occupied }) return RunResult(steps, canExit = false)
            steps++
        }
    }

    private fun moved(piece: PieceState, direction: Direction, steps: Int): PieceState = piece.copy(
        row = piece.row + direction.dr * steps,
        col = piece.col + direction.dc * steps,
    )

    /**
     * Slide [pieceId] as far as possible toward [direction]: either to the
     * wall/next piece (a normal move), or fully off the board if it reaches a
     * matching gate (the piece becomes [PieceState.exited]). Returns the same
     * state unchanged if the piece cannot move at all in that direction. This is
     * the move granularity [nextStates]/[LevelSolver] search over; real touch
     * input additionally allows resting partway ([moveBy]) or, for FREE pieces,
     * dropping onto any reachable on-board cell ([reachableAnchors]) -- both are
     * strictly more permissive than slideMax, so a level solved via slideMax
     * moves alone remains solvable under the richer input model.
     */
    fun slideMax(state: GameState, pieceId: Int, direction: Direction): GameState {
        val run = freeRun(state, pieceId, direction)
        if (run.steps == 0 && !run.canExit) return state
        val newPieces = state.pieces.map {
            if (it.id != pieceId) it else if (run.canExit) it.copy(exited = true) else moved(it, direction, run.steps)
        }
        return state.copy(pieces = newPieces)
    }

    /**
     * Moves [pieceId] by up to [cells] toward [direction] (clamped to what
     * [freeRun] allows); exits instead of stopping at the wall if [cells]
     * overshoots a free run on a side where [RunResult.canExit] is true. This is
     * the primitive a RAIL piece's drag commits through -- the child may release
     * partway rather than always sliding to the end.
     */
    fun moveBy(state: GameState, pieceId: Int, direction: Direction, cells: Int): GameState {
        if (cells <= 0) return state
        val run = freeRun(state, pieceId, direction)
        val newPieces = state.pieces.map {
            if (it.id != pieceId) {
                it
            } else if (cells > run.steps && run.canExit) {
                it.copy(exited = true)
            } else {
                moved(it, direction, minOf(cells, run.steps))
            }
        }
        return state.copy(pieces = newPieces)
    }

    /**
     * On-board anchor cells reachable by sliding a FREE piece one cell at a time
     * (4-directionally), never overlapping another piece or leaving the board --
     * "a path exists to there", not just "is that one cell empty". A drag commits
     * to a target only if it is in this set; otherwise the piece springs back to
     * where it started.
     */
    fun reachableAnchors(state: GameState, pieceId: Int): Set<Pair<Int, Int>> {
        val piece = state.piece(pieceId)
        val occupied = occupiedCells(state, pieceId)
        fun fits(row: Int, col: Int): Boolean = piece.cells.all {
            val r = row + it.dr
            val c = col + it.dc
            r in 0 until state.level.rows && c in 0 until state.level.cols && (r to c) !in occupied
        }
        val visited = mutableSetOf(piece.row to piece.col)
        val queue = ArrayDeque<Pair<Int, Int>>()
        queue.add(piece.row to piece.col)
        val dirs = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
        while (queue.isNotEmpty()) {
            val (r, c) = queue.removeFirst()
            for ((dr, dc) in dirs) {
                val nr = r + dr
                val nc = c + dc
                if ((nr to nc) in visited || !fits(nr, nc)) continue
                visited += nr to nc
                queue.add(nr to nc)
            }
        }
        return visited
    }

    /** All distinct states reachable from [state] in exactly one maximal slide of one piece. */
    fun nextStates(state: GameState): List<GameState> {
        val results = mutableListOf<GameState>()
        for (p in state.pieces) {
            if (p.exited) continue
            for (dir in allowedDirections(p)) {
                val next = slideMax(state, p.id, dir)
                if (next != state) results += next
            }
        }
        return results
    }
}
