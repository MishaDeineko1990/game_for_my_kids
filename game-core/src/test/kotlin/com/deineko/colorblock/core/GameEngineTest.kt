package com.deineko.colorblock.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private fun rail(id: Int, color: BlockColor, horizontal: Boolean, length: Int, row: Int, col: Int) = PieceDef(
    id = id,
    color = color,
    kind = PieceKind.RAIL,
    cells = (0 until length).map { if (horizontal) Cell(0, it) else Cell(it, 0) },
    row = row,
    col = col,
)

private fun free(id: Int, color: BlockColor, shape: List<Cell>, row: Int, col: Int) = PieceDef(
    id = id,
    color = color,
    kind = PieceKind.FREE,
    cells = shape,
    row = row,
    col = col,
)

private val TROMINO = listOf(Cell(0, 0), Cell(1, 0), Cell(1, 1))

class GameEngineTest {

    /** 3x3 board, one horizontal red rail (length 2) at row 1, col 0. A red gate sits on the RIGHT side at row 1. */
    private fun simpleLevel() = LevelDef(
        id = 1,
        rows = 3,
        cols = 3,
        pieces = listOf(rail(1, BlockColor.RED, horizontal = true, length = 2, row = 1, col = 0)),
        gates = listOf(GateDef(BlockColor.RED, Side.RIGHT, setOf(1))),
    )

    @Test
    fun `rail slides toward its matching gate and exits`() {
        val state = GameState.fromLevel(simpleLevel())
        val result = GameEngine.slideMax(state, pieceId = 1, direction = Direction.RIGHT)
        assertTrue(result.piece(1).exited)
        assertTrue(result.isWon)
    }

    @Test
    fun `rail cannot exit through a wall with no gate`() {
        val state = GameState.fromLevel(simpleLevel())
        val result = GameEngine.slideMax(state, pieceId = 1, direction = Direction.LEFT)
        assertFalse(result.piece(1).exited)
        assertEquals(0, result.piece(1).col)
    }

    @Test
    fun `a second piece blocks the path to the gate`() {
        // 4-wide board so the rail (length 2) has exactly one free step before the blocker (col 3).
        val level = simpleLevel().copy(
            cols = 4,
            pieces = simpleLevel().pieces + rail(2, BlockColor.BLUE, horizontal = false, length = 2, row = 0, col = 3),
        )
        val state = GameState.fromLevel(level)
        val result = GameEngine.slideMax(state, pieceId = 1, direction = Direction.RIGHT)
        assertFalse(result.piece(1).exited)
        assertEquals(1, result.piece(1).col)
    }

    @Test
    fun `wrong colored gate does not allow exit`() {
        val level = simpleLevel().copy(gates = listOf(GateDef(BlockColor.BLUE, Side.RIGHT, setOf(1))))
        val state = GameState.fromLevel(level)
        val result = GameEngine.slideMax(state, pieceId = 1, direction = Direction.RIGHT)
        assertFalse(result.piece(1).exited)
        assertEquals(1, result.piece(1).col)
    }

    @Test
    fun `nextStates is empty once every piece has exited`() {
        val state = GameState.fromLevel(simpleLevel())
        val exited = GameEngine.slideMax(state, pieceId = 1, direction = Direction.RIGHT)
        assertTrue(GameEngine.nextStates(exited).isEmpty())
    }

    @Test
    fun `moveBy can rest partway instead of sliding all the way`() {
        // 5-wide board: rail at col 0 has 3 free cells before the RIGHT gate at col 4.
        val level = simpleLevel().copy(cols = 5, gates = listOf(GateDef(BlockColor.RED, Side.RIGHT, setOf(1))))
        val state = GameState.fromLevel(level)
        val result = GameEngine.moveBy(state, pieceId = 1, direction = Direction.RIGHT, cells = 1)
        assertFalse(result.piece(1).exited)
        assertEquals(1, result.piece(1).col)
    }

    @Test
    fun `moveBy past the free run exits through a matching gate`() {
        val state = GameState.fromLevel(simpleLevel())
        val result = GameEngine.moveBy(state, pieceId = 1, direction = Direction.RIGHT, cells = 5)
        assertTrue(result.piece(1).exited)
    }

    @Test
    fun `moveBy past a wall with no gate clamps instead of exiting`() {
        val level = simpleLevel().copy(gates = emptyList())
        val state = GameState.fromLevel(level)
        val result = GameEngine.moveBy(state, pieceId = 1, direction = Direction.RIGHT, cells = 5)
        assertFalse(result.piece(1).exited)
        assertEquals(1, result.piece(1).col)
    }

    @Test
    fun `rail allowed directions match its axis`() {
        val horizontalPiece = PieceState(1, BlockColor.RED, PieceKind.RAIL, (0 until 2).map { Cell(0, it) }, 0, 0)
        val verticalPiece = PieceState(2, BlockColor.RED, PieceKind.RAIL, (0 until 2).map { Cell(it, 0) }, 0, 0)
        assertEquals(listOf(Direction.LEFT, Direction.RIGHT), GameEngine.allowedDirections(horizontalPiece))
        assertEquals(listOf(Direction.UP, Direction.DOWN), GameEngine.allowedDirections(verticalPiece))
    }

    @Test
    fun `free piece can move in all 4 directions when unobstructed`() {
        val level = LevelDef(1, rows = 6, cols = 6, pieces = listOf(free(1, BlockColor.GREEN, TROMINO, 2, 2)), gates = emptyList())
        val state = GameState.fromLevel(level)
        for (dir in Direction.entries) {
            val run = GameEngine.freeRun(state, pieceId = 1, direction = dir)
            assertTrue(run.steps > 0, "expected free movement in $dir, got steps=${run.steps}")
        }
    }

    @Test
    fun `free piece exit requires the gate to cover its full lane span`() {
        // tromino at row1/col0 occupies rows {1,2} (relative rows 0,1); a gate
        // covering only row 1 must NOT be enough to let it exit right.
        val level = LevelDef(1, rows = 4, cols = 3, pieces = listOf(free(1, BlockColor.GREEN, TROMINO, 1, 0)), gates = listOf(GateDef(BlockColor.GREEN, Side.RIGHT, setOf(1))))
        val state = GameState.fromLevel(level)
        val run = GameEngine.freeRun(state, pieceId = 1, direction = Direction.RIGHT)
        assertFalse(run.canExit, "gate only covers one of the two rows the piece touches")
    }

    @Test
    fun `free piece exits once the gate covers its full lane span`() {
        val level = LevelDef(1, rows = 4, cols = 3, pieces = listOf(free(1, BlockColor.GREEN, TROMINO, 1, 0)), gates = listOf(GateDef(BlockColor.GREEN, Side.RIGHT, setOf(1, 2))))
        val state = GameState.fromLevel(level)
        val result = GameEngine.slideMax(state, pieceId = 1, direction = Direction.RIGHT)
        assertTrue(result.piece(1).exited)
    }

    @Test
    fun `reachableAnchors finds an L-shaped detour around a blocker`() {
        val level = LevelDef(
            1, rows = 5, cols = 5,
            pieces = listOf(
                free(1, BlockColor.RED, listOf(Cell(0, 0)), 0, 0),
                rail(2, BlockColor.BLUE, horizontal = false, length = 1, row = 1, col = 0),
            ),
            gates = emptyList(),
        )
        val state = GameState.fromLevel(level)
        val reachable = GameEngine.reachableAnchors(state, pieceId = 1)
        assertTrue((1 to 1) in reachable, "expected a detour around the blocker at (1,0)")
        assertTrue((4 to 4) in reachable, "rest of the board should still be reachable via the detour")
    }

    @Test
    fun `reachableAnchors excludes a region cut off by a wall of pieces`() {
        val wall = (0..4).map { r -> rail(100 + r, BlockColor.BLUE, horizontal = true, length = 1, row = r, col = 2) }
        val level = LevelDef(1, rows = 5, cols = 5, pieces = listOf(free(1, BlockColor.RED, listOf(Cell(0, 0)), 0, 0)) + wall, gates = emptyList())
        val state = GameState.fromLevel(level)
        val reachable = GameEngine.reachableAnchors(state, pieceId = 1)
        assertTrue((4 to 1) in reachable)
        assertFalse((0 to 3) in reachable, "wall at column 2 should cut off the right side")
    }
}
