package com.deineko.colorblock.core

/**
 * Breadth-first search over the maximal-slide move graph. Used to prove a level is
 * solvable (and to find the shortest solution length) before it ships — moves here
 * are "slide a piece as far as it goes in one of its allowed directions" (2 for a
 * RAIL piece, 4 for a FREE piece), the same granularity [GameEngine.nextStates]
 * produces, which keeps branching factor small enough for exhaustive search on
 * these board sizes (<=8x8, <=10 pieces). Real touch input is strictly more
 * permissive than this move set (partial rests, FREE pieces dropping onto any
 * reachable cell), so a level proven solvable here stays solvable under it.
 */
object LevelSolver {

    data class Result(val solvable: Boolean, val minMoves: Int?, val statesExplored: Int)

    fun solve(level: LevelDef, maxStates: Int = 300_000): Result {
        val start = GameState.fromLevel(level)
        if (start.isWon) return Result(true, 0, 1)

        val visited = HashSet<GameState>()
        visited += start
        var frontier = listOf(start)
        var depth = 0

        while (frontier.isNotEmpty()) {
            if (visited.size > maxStates) return Result(false, null, visited.size)
            depth++
            val next = mutableListOf<GameState>()
            for (state in frontier) {
                for (candidate in GameEngine.nextStates(state)) {
                    if (visited.add(candidate)) {
                        if (candidate.isWon) return Result(true, depth, visited.size)
                        next += candidate
                    }
                }
            }
            frontier = next
        }
        return Result(false, null, visited.size)
    }
}
