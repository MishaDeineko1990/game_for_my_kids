package com.deineko.colorblock.core

/** A flat, kid-friendly palette. `hex` is 0xRRGGBB, applied by the UI layer. */
enum class BlockColor(val hex: Long) {
    RED(0xE84C3D),
    YELLOW(0xF5C518),
    BLUE(0x2E86DE),
    GREEN(0x2ECC71),
    ORANGE(0xF39C12),
    PURPLE(0x9B59B6),
}

/**
 * RAIL pieces are straight bars constrained to one fixed axis (their own two
 * opposite directions, shown to the player as arrows). FREE pieces are polyomino
 * shapes (tetromino-like) that can move in any of the 4 cardinal directions and
 * are placed by drag-and-drop onto any on-board cell reachable by a valid path,
 * not just slid to the end of a line -- see [GameEngine.reachableAnchors].
 */
enum class PieceKind { RAIL, FREE }

enum class Side { TOP, BOTTOM, LEFT, RIGHT }

enum class Direction(val dr: Int, val dc: Int, val side: Side) {
    UP(-1, 0, Side.TOP),
    DOWN(1, 0, Side.BOTTOM),
    LEFT(0, -1, Side.LEFT),
    RIGHT(0, 1, Side.RIGHT),
}

/** A cell offset relative to a piece's anchor (its `row`/`col`), non-negative, normalized so the shape's own top-left is (0,0). */
data class Cell(val dr: Int, val dc: Int)

/**
 * Fixed definition of a piece as authored in a level: color, kind, shape, and
 * starting anchor cell. Immutable -- runtime position lives in [PieceState].
 */
data class PieceDef(
    val id: Int,
    val color: BlockColor,
    val kind: PieceKind,
    val cells: List<Cell>,
    val row: Int,
    val col: Int,
)

/**
 * An opening on the board edge that only lets a matching-color piece slide off
 * through it. `lanes` is the set of rows (for LEFT/RIGHT sides) or columns (for
 * TOP/BOTTOM sides) the opening covers -- a single-cell rail needs one lane; a
 * multi-cell FREE piece needs the gate to cover every lane it touches on that
 * edge, not just one of them (see [GameEngine.freeRun]).
 */
data class GateDef(
    val color: BlockColor,
    val side: Side,
    val lanes: Set<Int>,
)

data class LevelDef(
    val id: Int,
    val rows: Int,
    val cols: Int,
    val pieces: List<PieceDef>,
    val gates: List<GateDef>,
)

/** Runtime position of one piece. `exited` is true once it has fully left the board. */
data class PieceState(
    val id: Int,
    val color: BlockColor,
    val kind: PieceKind,
    val cells: List<Cell>,
    val row: Int,
    val col: Int,
    val exited: Boolean = false,
)

data class GameState(
    val level: LevelDef,
    val pieces: List<PieceState>,
) {
    val isWon: Boolean get() = pieces.all { it.exited }

    fun piece(id: Int): PieceState = pieces.first { it.id == id }

    companion object {
        fun fromLevel(level: LevelDef): GameState = GameState(
            level = level,
            pieces = level.pieces.map {
                PieceState(id = it.id, color = it.color, kind = it.kind, cells = it.cells, row = it.row, col = it.col)
            },
        )
    }
}
