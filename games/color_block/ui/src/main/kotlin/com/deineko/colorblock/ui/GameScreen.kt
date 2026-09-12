package com.deineko.colorblock.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.deineko.colorblock.core.ALL_LEVELS
import com.deineko.colorblock.core.BlockColor
import com.deineko.colorblock.core.Cell
import com.deineko.colorblock.core.Direction
import com.deineko.colorblock.core.GameEngine
import com.deineko.colorblock.core.GameState
import com.deineko.colorblock.core.GateDef
import com.deineko.colorblock.core.LevelDef
import com.deineko.colorblock.core.PieceDef
import com.deineko.colorblock.core.PieceKind
import com.deineko.colorblock.core.PieceState
import com.deineko.colorblock.core.PolyominoOutline
import com.deineko.colorblock.core.Side
import com.deineko.colorblock.progress.ProgressStore
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

private const val EXIT_OVERDRAG = 1.6f

// ============================================================================
// Runtime piece state
// ============================================================================

/** game-core's [PieceDef] plus the mutable fields the UI animates. `row`/`col`
 * are the committed logical position; `animRow`/`animCol` are what's actually
 * drawn, which differs from the committed position while a drag or a settle/exit
 * animation is in flight. */
private class PieceRuntime(def: PieceDef) {
    val id = def.id
    val color: BlockColor = def.color
    val kind: PieceKind = def.kind
    val cells: List<Cell> = def.cells
    var row by mutableIntStateOf(def.row)
    var col by mutableIntStateOf(def.col)
    var exited by mutableStateOf(false)
    var animRow by mutableFloatStateOf(def.row.toFloat())
    var animCol by mutableFloatStateOf(def.col.toFloat())
    var alpha by mutableFloatStateOf(1f)
    val outline = if (kind == PieceKind.FREE) PolyominoOutline.build(cells) else emptyList()
    val boundingWidth = cells.maxOf { it.dc } + 1
    val boundingHeight = cells.maxOf { it.dr } + 1

    fun toPieceState() = PieceState(id, color, kind, cells, row, col, exited)
}

private class GameSession(val level: LevelDef) {
    val pieces = level.pieces.map { PieceRuntime(it) }
    var moves by mutableIntStateOf(0)
    var won by mutableStateOf(false)

    fun toGameState() = GameState(level, pieces.map { it.toPieceState() })
    fun piece(id: Int) = pieces.first { it.id == id }
    fun isWon() = pieces.all { it.exited }
}

private enum class ActivePhase { DRAGGING, SETTLING, EXITING }

private sealed class DragInfo(val pieceId: Int) {
    class Rail(pieceId: Int, val negDir: Direction, val posDir: Direction, val negRun: GameEngine.RunResult, val posRun: GameEngine.RunResult) : DragInfo(pieceId) {
        var pxOffset = 0f
        var offsetCells = 0f
    }
    class Free(pieceId: Int, val startRow: Int, val startCol: Int, val reachable: Set<Pair<Int, Int>>) : DragInfo(pieceId) {
        var pxDx = 0f
        var pxDy = 0f
        var liveRow = startRow.toFloat()
        var liveCol = startCol.toFloat()
        var ghost: Pair<Int, Int>? = null
        var exitDirection: Direction? = null
    }
}

private class ShredBit(var x: Float, var y: Float, var vx: Float, var vy: Float, var rot: Float, val vr: Float, val w: Float, val h: Float, val color: Color, val life: Float) {
    var age = 0f
}

private class ConfettiBit(var x: Float, var y: Float, var vx: Float, var vy: Float, var rot: Float, val vr: Float, val size: Float, val color: Color)

// ============================================================================
// Screen
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(levelId: Int, onBack: () -> Unit, onNavigateToLevel: (Int) -> Unit) {
    val context = LocalContext.current
    val progressStore = remember { ProgressStore(context) }
    val levelDef = remember(levelId) { ALL_LEVELS.first { it.id == levelId } }
    var session by remember(levelId) { mutableStateOf(GameSession(levelDef)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Рівень $levelId/${ALL_LEVELS.size}   •   Ходів: ${session.moves}") },
                navigationIcon = { IconButton(onClick = onBack) { Text("‹", style = MaterialTheme.typography.headlineSmall) } },
                actions = { IconButton(onClick = { session = GameSession(levelDef) }) { Text("↺") } },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                GameBoard(session = session, onWon = { progressStore.markCompleted(levelId, ALL_LEVELS.size) })
            }
        }
    }

    if (session.won) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("🎉 Рівень пройдено!") },
            text = { Text("За ${session.moves} ${moveWord(session.moves)}") },
            confirmButton = {
                Button(onClick = { if (levelId < ALL_LEVELS.size) onNavigateToLevel(levelId + 1) else onBack() }) {
                    Text(if (levelId < ALL_LEVELS.size) "Наступний рівень" else "До списку рівнів")
                }
            },
            dismissButton = { TextButton(onClick = { session = GameSession(levelDef) }) { Text("Ще раз") } },
        )
    }
}

private fun moveWord(n: Int): String {
    val mod10 = n % 10
    val mod100 = n % 100
    return when {
        mod10 == 1 && mod100 != 11 -> "хід"
        mod10 in 2..4 && mod100 !in 12..14 -> "ходи"
        else -> "ходів"
    }
}

// ============================================================================
// Board canvas: rendering + gestures + juice (shredder strips, confetti)
// ============================================================================

@Composable
private fun GameBoard(session: GameSession, onWon: () -> Unit) {
    val density = LocalDensity.current
    val marginPx = with(density) { 16.dp.toPx() }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val scope = rememberCoroutineScope()
    val shredBits = remember { mutableStateListOf<ShredBit>() }
    val confetti = remember { mutableStateListOf<ConfettiBit>() }
    var frameTick by remember { mutableIntStateOf(0) }
    var activePieceId by remember { mutableStateOf<Int?>(null) }
    var activePhase by remember { mutableStateOf<ActivePhase?>(null) }
    var dragInfo by remember { mutableStateOf<DragInfo?>(null) }

    LaunchedEffect(Unit) {
        while (isActive) {
            withFrameMillis { }
            var changed = false
            if (shredBits.isNotEmpty()) {
                for (b in shredBits) {
                    b.vy += 0.14f; b.x += b.vx; b.y += b.vy; b.rot += b.vr; b.age += 16.7f
                }
                shredBits.removeAll { it.age >= it.life }
                changed = true
            }
            if (confetti.isNotEmpty()) {
                for (c in confetti) {
                    c.vy += 0.18f; c.x += c.vx; c.y += c.vy; c.rot += c.vr
                }
                confetti.removeAll { it.y > canvasSize.height + 20f }
                changed = true
            }
            if (changed) frameTick++
        }
    }

    fun metrics() = computeMetrics(canvasSize.width.toFloat(), canvasSize.height.toFloat(), marginPx, session.level.rows, session.level.cols)

    fun hitTest(offset: Offset): PieceRuntime? {
        val m = metrics()
        if (m.cellPx <= 0f) return null
        val col = ((offset.x - m.boardLeft) / m.cellPx).let { if (it < 0f) -1 else it.toInt() }
        val row = ((offset.y - m.boardTop) / m.cellPx).let { if (it < 0f) -1 else it.toInt() }
        if (row !in 0 until session.level.rows || col !in 0 until session.level.cols) return null
        return session.pieces.firstOrNull { piece -> !piece.exited && piece.cells.any { piece.row + it.dr == row && piece.col + it.dc == col } }
    }

    fun findExitGate(piece: PieceRuntime, direction: Direction): GateDef? {
        val required = GameEngine.laneSpan(piece.toPieceState(), direction.side)
        return session.level.gates.firstOrNull { it.color == piece.color && it.side == direction.side && required.all { l -> l in it.lanes } }
    }

    fun spawnConfetti() {
        val colors = BlockColor.entries.map { it.toComposeColor() }
        val cx = canvasSize.width / 2f
        repeat(40) {
            confetti += ConfettiBit(
                x = cx + (Random.nextFloat() - 0.5f) * 40f,
                y = canvasSize.height * 0.3f,
                vx = (Random.nextFloat() - 0.5f) * 4.5f,
                vy = -Random.nextFloat() * 5f - 2f,
                rot = Random.nextFloat() * 3.14f,
                vr = (Random.nextFloat() - 0.5f) * 0.3f,
                size = 5f + Random.nextFloat() * 4f,
                color = colors.random(),
            )
        }
    }

    fun spawnShredBurst(gate: GateDef?, direction: Direction, color: Color, cellPx: Float) {
        if (gate == null) return
        val m = metrics()
        val lo = gate.lanes.min()
        val hi = gate.lanes.max()
        val span = (hi - lo + 1) * cellPx
        val laneStart = if (direction == Direction.LEFT || direction == Direction.RIGHT) m.boardTop + lo * cellPx else m.boardLeft + lo * cellPx
        val thick = cellPx * 0.42f
        val (rx, ry, rw, rh) = when (direction.side) {
            Side.LEFT -> listOf(m.boardLeft - thick * 0.55f, laneStart + cellPx * 0.16f, thick * 0.8f, span - cellPx * 0.32f)
            Side.RIGHT -> listOf(m.boardLeft + m.boardW - thick * 0.25f, laneStart + cellPx * 0.16f, thick * 0.8f, span - cellPx * 0.32f)
            Side.TOP -> listOf(laneStart + cellPx * 0.16f, m.boardTop - thick * 0.55f, span - cellPx * 0.32f, thick * 0.8f)
            Side.BOTTOM -> listOf(laneStart + cellPx * 0.16f, m.boardTop + m.boardH - thick * 0.25f, span - cellPx * 0.32f, thick * 0.8f)
        }
        val horizontal = direction == Direction.LEFT || direction == Direction.RIGHT
        val sign = if (direction == Direction.RIGHT || direction == Direction.DOWN) 1f else -1f
        repeat(2) {
            val px = rx + Random.nextFloat() * rw
            val py = ry + Random.nextFloat() * rh
            val speed = 1.5f + Random.nextFloat() * 2.5f
            val vx = if (horizontal) sign * speed else (Random.nextFloat() - 0.5f) * 1.5f
            val vy = if (!horizontal) sign * speed else (Random.nextFloat() - 0.5f) * 1.5f + 0.6f
            shredBits += ShredBit(
                x = px, y = py, vx = vx, vy = vy,
                rot = (Random.nextFloat() - 0.5f) * 0.4f, vr = (Random.nextFloat() - 0.5f) * 0.25f,
                w = if (horizontal) cellPx * 0.75f else cellPx * 0.16f,
                h = if (horizontal) cellPx * 0.16f else cellPx * 0.75f,
                color = if (Random.nextBoolean()) color.lighten(0.15f) else color.darken(0.1f),
                life = 420f + Random.nextFloat() * 260f,
            )
        }
    }

    suspend fun runExit(piece: PieceRuntime, direction: Direction) {
        activePieceId = piece.id
        activePhase = ActivePhase.EXITING
        val gate = findExitGate(piece, direction)
        val horizontal = direction == Direction.LEFT || direction == Direction.RIGHT
        val span = if (horizontal) piece.boundingWidth else piece.boundingHeight
        val sign = if (direction == Direction.RIGHT || direction == Direction.DOWN) 1f else -1f
        val from = if (horizontal) piece.animCol else piece.animRow
        val to = from + sign * (span + 3f)
        val cellPx = metrics().cellPx
        var lastSpawnT = -1f
        animate(0f, 1f, animationSpec = tween(380, easing = LinearEasing)) { t, _ ->
            val eased = t * t * t
            val pos = from + (to - from) * eased
            if (horizontal) piece.animCol = pos else piece.animRow = pos
            piece.alpha = if (t < 0.75f) 1f else (1f - (t - 0.75f) / 0.25f).coerceIn(0f, 1f)
            if (t in 0.05f..0.95f && t - lastSpawnT > 0.05f) {
                lastSpawnT = t
                spawnShredBurst(gate, direction, piece.color.toComposeColor(), cellPx)
            }
        }
        piece.exited = true
        activePieceId = null
        activePhase = null
        if (session.isWon()) {
            session.won = true
            spawnConfetti()
            onWon()
        }
    }

    suspend fun runRailSettle(piece: PieceRuntime, horizontal: Boolean, from: Float, target: Int) {
        activePieceId = piece.id
        activePhase = ActivePhase.SETTLING
        animate(from, target.toFloat(), animationSpec = tween(160, easing = FastOutSlowInEasing)) { v, _ ->
            if (horizontal) piece.animCol = v else piece.animRow = v
        }
        if (horizontal) { piece.col = target; piece.animCol = target.toFloat() } else { piece.row = target; piece.animRow = target.toFloat() }
        activePieceId = null
        activePhase = null
    }

    suspend fun runFreeSettle(piece: PieceRuntime, fromRow: Float, fromCol: Float, toRow: Int, toCol: Int) {
        activePieceId = piece.id
        activePhase = ActivePhase.SETTLING
        val dur = if (fromRow.roundToInt() == toRow && fromCol.roundToInt() == toCol) 220 else 180
        coroutineScope {
            launch { animate(fromRow, toRow.toFloat(), animationSpec = tween(dur, easing = FastOutSlowInEasing)) { v, _ -> piece.animRow = v } }
            launch { animate(fromCol, toCol.toFloat(), animationSpec = tween(dur, easing = FastOutSlowInEasing)) { v, _ -> piece.animCol = v } }
        }
        piece.row = toRow; piece.col = toCol
        piece.animRow = toRow.toFloat(); piece.animCol = toCol.toFloat()
        activePieceId = null
        activePhase = null
    }

    val gestureModifier = Modifier.pointerInput(session.level.id) {
        detectDragGestures(
            onDragStart = { offset ->
                if (activePieceId != null) return@detectDragGestures
                val piece = hitTest(offset) ?: return@detectDragGestures
                val state = session.toGameState()
                dragInfo = if (piece.kind == PieceKind.RAIL) {
                    val dirs = GameEngine.allowedDirections(piece.toPieceState())
                    val negDir = dirs[0]
                    val posDir = dirs[1]
                    DragInfo.Rail(piece.id, negDir, posDir, GameEngine.freeRun(state, piece.id, negDir), GameEngine.freeRun(state, piece.id, posDir))
                } else {
                    DragInfo.Free(piece.id, piece.row, piece.col, GameEngine.reachableAnchors(state, piece.id))
                }
                activePieceId = piece.id
                activePhase = ActivePhase.DRAGGING
            },
            onDrag = { change, dragAmount ->
                val info = dragInfo ?: return@detectDragGestures
                change.consume()
                val m = metrics()
                if (m.cellPx <= 0f) return@detectDragGestures
                val piece = session.piece(info.pieceId)
                when (info) {
                    is DragInfo.Rail -> {
                        val horizontal = info.negDir == Direction.LEFT
                        info.pxOffset += if (horizontal) dragAmount.x else dragAmount.y
                        val maxPos = info.posRun.steps + if (info.posRun.canExit) EXIT_OVERDRAG else 0f
                        val maxNeg = info.negRun.steps + if (info.negRun.canExit) EXIT_OVERDRAG else 0f
                        info.offsetCells = (info.pxOffset / m.cellPx).coerceIn(-maxNeg, maxPos)
                        if (horizontal) piece.animCol = piece.col + info.offsetCells else piece.animRow = piece.row + info.offsetCells
                    }
                    is DragInfo.Free -> {
                        info.pxDx += dragAmount.x
                        info.pxDy += dragAmount.y
                        val dRow = info.pxDy / m.cellPx
                        val dCol = info.pxDx / m.cellPx
                        info.liveRow = info.startRow + dRow
                        info.liveCol = info.startCol + dCol
                        piece.animRow = info.liveRow
                        piece.animCol = info.liveCol
                        val candRow = info.liveRow.roundToInt()
                        val candCol = info.liveCol.roundToInt()
                        if ((candRow to candCol) in info.reachable) {
                            info.ghost = candRow to candCol
                            info.exitDirection = null
                        } else {
                            info.ghost = null
                            val horizontalDominant = abs(dCol) > abs(dRow)
                            val direction = if (horizontalDominant) (if (dCol > 0) Direction.RIGHT else Direction.LEFT) else (if (dRow > 0) Direction.DOWN else Direction.UP)
                            val run = GameEngine.freeRun(session.toGameState(), info.pieceId, direction)
                            val magnitude = if (horizontalDominant) abs(dCol) else abs(dRow)
                            info.exitDirection = if (magnitude > run.steps + 0.02f && run.canExit) direction else null
                        }
                    }
                }
            },
            onDragEnd = {
                val info = dragInfo ?: return@detectDragGestures
                dragInfo = null
                val piece = session.piece(info.pieceId)
                when (info) {
                    is DragInfo.Rail -> {
                        val horizontal = info.negDir == Direction.LEFT
                        val exitDir = when {
                            info.offsetCells > info.posRun.steps + 0.02f && info.posRun.canExit -> info.posDir
                            info.offsetCells < -info.negRun.steps - 0.02f && info.negRun.canExit -> info.negDir
                            else -> null
                        }
                        if (exitDir != null) {
                            session.moves++
                            scope.launch { runExit(piece, exitDir) }
                        } else {
                            val rounded = info.offsetCells.roundToInt().coerceIn(-info.negRun.steps, info.posRun.steps)
                            if (rounded != 0) session.moves++
                            val target = (if (horizontal) piece.col else piece.row) + rounded
                            val from = if (horizontal) piece.animCol else piece.animRow
                            scope.launch { runRailSettle(piece, horizontal, from, target) }
                        }
                    }
                    is DragInfo.Free -> {
                        val exitDir = info.exitDirection
                        if (exitDir != null) {
                            session.moves++
                            scope.launch { runExit(piece, exitDir) }
                        } else {
                            val target = info.ghost ?: (info.startRow to info.startCol)
                            if (target.first != info.startRow || target.second != info.startCol) session.moves++
                            scope.launch { runFreeSettle(piece, info.liveRow, info.liveCol, target.first, target.second) }
                        }
                    }
                }
            },
            onDragCancel = {
                val info = dragInfo
                dragInfo = null
                if (info != null) {
                    val piece = session.piece(info.pieceId)
                    when (info) {
                        is DragInfo.Rail -> {
                            val horizontal = info.negDir == Direction.LEFT
                            val from = if (horizontal) piece.animCol else piece.animRow
                            scope.launch { runRailSettle(piece, horizontal, from, if (horizontal) piece.col else piece.row) }
                        }
                        is DragInfo.Free -> scope.launch { runFreeSettle(piece, info.liveRow, info.liveCol, info.startRow, info.startCol) }
                    }
                }
            },
        )
    }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .onSizeChanged { canvasSize = it }
            .then(gestureModifier),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            @Suppress("UNUSED_EXPRESSION")
            frameTick // read to subscribe this draw phase to the particle-physics ticks above
            val m = computeMetrics(size.width, size.height, marginPx, session.level.rows, session.level.cols)
            drawBoard(session.level, m)

            for (piece in session.pieces) {
                if (piece.exited || piece.id == activePieceId) continue
                drawPiece(piece, m)
            }

            val active = activePieceId?.let { id -> session.pieces.firstOrNull { it.id == id } }
            if (active != null) {
                if (activePhase == ActivePhase.EXITING) {
                    clipPath(boardClipPath(m)) { drawPiece(active, m) }
                } else {
                    drawPiece(active, m)
                    val freeGhost = (dragInfo as? DragInfo.Free)?.takeIf { it.pieceId == active.id }?.ghost
                    if (activePhase == ActivePhase.DRAGGING && freeGhost != null) {
                        drawGhost(active, freeGhost.first, freeGhost.second, m)
                    }
                }
            }

            for (b in shredBits) drawShredBit(b)
            for (c in confetti) drawConfettiBit(c)
        }
    }
}

// ============================================================================
// Geometry
// ============================================================================

private data class BoardMetrics(val cellPx: Float, val boardLeft: Float, val boardTop: Float, val boardW: Float, val boardH: Float)

private fun computeMetrics(canvasW: Float, canvasH: Float, marginPx: Float, rows: Int, cols: Int): BoardMetrics {
    if (canvasW <= 0f || canvasH <= 0f) return BoardMetrics(0f, 0f, 0f, 0f, 0f)
    val availW = canvasW - 2 * marginPx
    val availH = canvasH - 2 * marginPx
    val cellPx = min(availW / cols, availH / rows)
    val boardW = cellPx * cols
    val boardH = cellPx * rows
    return BoardMetrics(cellPx, (canvasW - boardW) / 2f, (canvasH - boardH) / 2f, boardW, boardH)
}

private fun boardClipPath(m: BoardMetrics): Path = Path().apply {
    addRoundRect(RoundRect(m.boardLeft, m.boardTop, m.boardLeft + m.boardW, m.boardTop + m.boardH, 14f, 14f))
}

// ============================================================================
// Drawing
// ============================================================================

private fun DrawScope.drawBoard(level: LevelDef, m: BoardMetrics) {
    val baseplate = Color(0xFFE8DFC8)
    val stud = Color(0xFFD8CBA9)
    drawRoundRect(color = baseplate, topLeft = Offset(m.boardLeft, m.boardTop), size = androidx.compose.ui.geometry.Size(m.boardW, m.boardH), cornerRadius = androidx.compose.ui.geometry.CornerRadius(14f, 14f))
    for (r in 0 until level.rows) {
        for (c in 0 until level.cols) {
            drawCircle(stud, radius = m.cellPx * 0.1f, center = Offset(m.boardLeft + (c + 0.5f) * m.cellPx, m.boardTop + (r + 0.5f) * m.cellPx))
        }
    }
    val gridColor = Color.Black.copy(alpha = 0.07f)
    for (i in 1 until level.cols) {
        val x = m.boardLeft + i * m.cellPx
        drawLine(gridColor, Offset(x, m.boardTop), Offset(x, m.boardTop + m.boardH), strokeWidth = 1f)
    }
    for (i in 1 until level.rows) {
        val y = m.boardTop + i * m.cellPx
        drawLine(gridColor, Offset(m.boardLeft, y), Offset(m.boardLeft + m.boardW, y), strokeWidth = 1f)
    }
    for (gate in level.gates) drawGate(gate, m)
    drawRoundRect(
        color = Color(0xFFEAE0C8),
        topLeft = Offset(m.boardLeft, m.boardTop),
        size = androidx.compose.ui.geometry.Size(m.boardW, m.boardH),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(14f, 14f),
        style = Stroke(width = 2.5f),
    )
}

private fun DrawScope.drawGate(gate: GateDef, m: BoardMetrics) {
    val lo = gate.lanes.min()
    val hi = gate.lanes.max()
    val span = (hi - lo + 1) * m.cellPx
    val laneStart = if (gate.side == Side.LEFT || gate.side == Side.RIGHT) m.boardTop + lo * m.cellPx else m.boardLeft + lo * m.cellPx
    val thick = m.cellPx * 0.42f
    val (x, y, w, h) = when (gate.side) {
        Side.LEFT -> Rect4(m.boardLeft - thick * 0.55f, laneStart + m.cellPx * 0.16f, thick * 0.8f, span - m.cellPx * 0.32f)
        Side.RIGHT -> Rect4(m.boardLeft + m.boardW - thick * 0.25f, laneStart + m.cellPx * 0.16f, thick * 0.8f, span - m.cellPx * 0.32f)
        Side.TOP -> Rect4(laneStart + m.cellPx * 0.16f, m.boardTop - thick * 0.55f, span - m.cellPx * 0.32f, thick * 0.8f)
        Side.BOTTOM -> Rect4(laneStart + m.cellPx * 0.16f, m.boardTop + m.boardH - thick * 0.25f, span - m.cellPx * 0.32f, thick * 0.8f)
    }
    drawRoundRect(gate.color.toComposeColor(), topLeft = Offset(x, y), size = androidx.compose.ui.geometry.Size(w, h), cornerRadius = androidx.compose.ui.geometry.CornerRadius(min(w, h) * 0.4f))
}

private data class Rect4(val x: Float, val y: Float, val w: Float, val h: Float)

private fun DrawScope.drawPiece(piece: PieceRuntime, m: BoardMetrics) {
    if (piece.kind == PieceKind.RAIL) drawRailPiece(piece, m) else drawFreePiece(piece, m)
}

private fun DrawScope.drawRailPiece(piece: PieceRuntime, m: BoardMetrics) {
    val horizontal = piece.cells.map { it.dr }.toSet().size == 1
    val len = piece.cells.size
    val row = piece.animRow
    val col = piece.animCol
    val w = if (horizontal) len * m.cellPx else m.cellPx
    val h = if (horizontal) m.cellPx else len * m.cellPx
    val x = m.boardLeft + col * m.cellPx + m.cellPx * 0.07f
    val y = m.boardTop + row * m.cellPx + m.cellPx * 0.07f
    val bw = w - m.cellPx * 0.14f
    val bh = h - m.cellPx * 0.14f
    val hex = piece.color.toComposeColor()
    val radius = m.cellPx * 0.24f

    withAlpha(piece.alpha) {
        drawRoundRect(Color.Black.copy(alpha = 0.2f), topLeft = Offset(x, y + m.cellPx * 0.05f), size = androidx.compose.ui.geometry.Size(bw, bh), cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius))
        drawRoundRect(
            brush = androidx.compose.ui.graphics.Brush.verticalGradient(listOf(hex.lighten(0.22f), hex), startY = y, endY = y + bh),
            topLeft = Offset(x, y), size = androidx.compose.ui.geometry.Size(bw, bh), cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius),
        )
        drawRoundRect(hex.darken(0.25f), topLeft = Offset(x, y), size = androidx.compose.ui.geometry.Size(bw, bh), cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius), style = Stroke(1.5f))

        for (i in 0 until len) {
            val scx = if (horizontal) x + (i + 0.5f) * (bw / len) else x + bw / 2f
            val scy = if (horizontal) y + bh / 2f else y + (i + 0.5f) * (bh / len)
            drawStud(scx, scy, m.cellPx * 0.15f, hex)
        }

        val s = min(bw, bh) * 0.3f
        if (horizontal) {
            drawTriangle(Offset(x + s * 0.9f, y + bh / 2f), s, TriangleDir.LEFT, hex)
            drawTriangle(Offset(x + bw - s * 0.9f, y + bh / 2f), s, TriangleDir.RIGHT, hex)
        } else {
            drawTriangle(Offset(x + bw / 2f, y + s * 0.9f), s, TriangleDir.UP, hex)
            drawTriangle(Offset(x + bw / 2f, y + bh - s * 0.9f), s, TriangleDir.DOWN, hex)
        }
    }
}

private fun DrawScope.drawFreePiece(piece: PieceRuntime, m: BoardMetrics) {
    val hex = piece.color.toComposeColor()
    val pts = piece.outline.map { pt -> Offset(m.boardLeft + (piece.animCol + pt.col) * m.cellPx, m.boardTop + (piece.animRow + pt.row) * m.cellPx) }
    val radius = m.cellPx * 0.24f

    withAlpha(piece.alpha) {
        drawRoundedPolygon(pts.map { Offset(it.x, it.y + m.cellPx * 0.05f) }, radius, Color.Black.copy(alpha = 0.2f))
        val minY = pts.minOf { it.y }
        val maxY = pts.maxOf { it.y }
        drawRoundedPolygon(pts, radius, brush = androidx.compose.ui.graphics.Brush.verticalGradient(listOf(hex.lighten(0.22f), hex), startY = minY, endY = maxY))
        drawRoundedPolygon(pts, radius, color = hex.darken(0.25f), stroke = Stroke(1.5f))

        for (cell in piece.cells) {
            val scx = m.boardLeft + (piece.animCol + cell.dc + 0.5f) * m.cellPx
            val scy = m.boardTop + (piece.animRow + cell.dr + 0.5f) * m.cellPx
            drawStud(scx, scy, m.cellPx * 0.15f, hex)
        }

        val cx = pts.map { it.x }.average().toFloat()
        val cy = pts.map { it.y }.average().toFloat()
        val gs = m.cellPx * 0.24f
        drawTriangle(Offset(cx - gs * 1.1f, cy), gs * 0.75f, TriangleDir.LEFT, hex)
        drawTriangle(Offset(cx + gs * 1.1f, cy), gs * 0.75f, TriangleDir.RIGHT, hex)
        drawTriangle(Offset(cx, cy - gs * 1.1f), gs * 0.75f, TriangleDir.UP, hex)
        drawTriangle(Offset(cx, cy + gs * 1.1f), gs * 0.75f, TriangleDir.DOWN, hex)
    }
}

private fun DrawScope.drawGhost(piece: PieceRuntime, row: Int, col: Int, m: BoardMetrics) {
    val hex = piece.color.toComposeColor()
    val pts = piece.outline.map { pt -> Offset(m.boardLeft + (col + pt.col) * m.cellPx, m.boardTop + (row + pt.row) * m.cellPx) }
    val radius = m.cellPx * 0.24f
    drawRoundedPolygon(pts, radius, color = hex.lighten(0.5f), alpha = 0.4f)
    drawRoundedPolygon(pts, radius, color = hex, alpha = 0.7f, stroke = Stroke(width = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(m.cellPx * 0.12f, m.cellPx * 0.08f))))
}

private inline fun DrawScope.withAlpha(alpha: Float, block: DrawScope.() -> Unit) {
    if (alpha >= 0.999f) {
        block()
    } else {
        val paint = androidx.compose.ui.graphics.Paint().apply { this.alpha = alpha }
        drawIntoCanvas { canvas ->
            canvas.saveLayer(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height), paint)
            block()
            canvas.restore()
        }
    }
}

private fun DrawScope.drawRoundedPolygon(points: List<Offset>, radius: Float, color: Color = Color.Unspecified, brush: androidx.compose.ui.graphics.Brush? = null, alpha: Float = 1f, stroke: Stroke? = null) {
    val path = roundedPolygonPath(points, radius)
    if (stroke != null) {
        if (brush != null) drawPath(path, brush = brush, alpha = alpha, style = stroke) else drawPath(path, color = color, alpha = alpha, style = stroke)
    } else {
        if (brush != null) drawPath(path, brush = brush, alpha = alpha) else drawPath(path, color = color, alpha = alpha)
    }
}

private fun roundedPolygonPath(points: List<Offset>, radius: Float): Path {
    val n = points.size
    val path = Path()
    for (i in 0 until n) {
        val prev = points[(i - 1 + n) % n]
        val cur = points[i]
        val next = points[(i + 1) % n]
        val toPrev = Offset(prev.x - cur.x, prev.y - cur.y)
        val toNext = Offset(next.x - cur.x, next.y - cur.y)
        val lenPrev = hypot(toPrev.x, toPrev.y).takeIf { it > 0f } ?: 1f
        val lenNext = hypot(toNext.x, toNext.y).takeIf { it > 0f } ?: 1f
        val r = min(radius, min(lenPrev * 0.5f, lenNext * 0.5f))
        val p1 = Offset(cur.x + toPrev.x / lenPrev * r, cur.y + toPrev.y / lenPrev * r)
        val p2 = Offset(cur.x + toNext.x / lenNext * r, cur.y + toNext.y / lenNext * r)
        if (i == 0) path.moveTo(p1.x, p1.y) else path.lineTo(p1.x, p1.y)
        path.quadraticTo(cur.x, cur.y, p2.x, p2.y)
    }
    path.close()
    return path
}

private fun hypot(x: Float, y: Float): Float = kotlin.math.sqrt(x * x + y * y)

private fun DrawScope.drawStud(cx: Float, cy: Float, r: Float, hex: Color) {
    drawCircle(hex.lighten(0.32f), radius = r, center = Offset(cx, cy))
    drawCircle(hex.darken(0.2f), radius = r, center = Offset(cx, cy), style = Stroke(1f))
    drawCircle(Color.White.copy(alpha = 0.4f), radius = r * 0.4f, center = Offset(cx - r * 0.3f, cy - r * 0.32f))
}

private enum class TriangleDir { LEFT, RIGHT, UP, DOWN }

private fun DrawScope.drawTriangle(center: Offset, size: Float, dir: TriangleDir, pieceHex: Color) {
    val path = Path()
    when (dir) {
        TriangleDir.LEFT -> {
            path.moveTo(center.x - size * 0.5f, center.y)
            path.lineTo(center.x + size * 0.35f, center.y - size * 0.4f)
            path.lineTo(center.x + size * 0.35f, center.y + size * 0.4f)
        }
        TriangleDir.RIGHT -> {
            path.moveTo(center.x + size * 0.5f, center.y)
            path.lineTo(center.x - size * 0.35f, center.y - size * 0.4f)
            path.lineTo(center.x - size * 0.35f, center.y + size * 0.4f)
        }
        TriangleDir.UP -> {
            path.moveTo(center.x, center.y - size * 0.5f)
            path.lineTo(center.x - size * 0.4f, center.y + size * 0.35f)
            path.lineTo(center.x + size * 0.4f, center.y + size * 0.35f)
        }
        TriangleDir.DOWN -> {
            path.moveTo(center.x, center.y + size * 0.5f)
            path.lineTo(center.x - size * 0.4f, center.y - size * 0.35f)
            path.lineTo(center.x + size * 0.4f, center.y - size * 0.35f)
        }
    }
    path.close()
    drawPath(path, color = Color.White.copy(alpha = 0.85f))
    drawPath(path, color = pieceHex.darken(0.3f), style = Stroke(1f))
}

private fun DrawScope.drawShredBit(b: ShredBit) {
    val alpha = (1f - b.age / b.life).coerceIn(0f, 1f)
    rotate(degrees = b.rot * 57.2958f, pivot = Offset(b.x, b.y)) {
        drawRoundRect(
            color = b.color.copy(alpha = alpha * b.color.alpha),
            topLeft = Offset(b.x - b.w / 2f, b.y - b.h / 2f),
            size = androidx.compose.ui.geometry.Size(b.w, b.h),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(min(b.w, b.h) * 0.4f),
        )
    }
}

private fun DrawScope.drawConfettiBit(c: ConfettiBit) {
    rotate(degrees = c.rot * 57.2958f, pivot = Offset(c.x, c.y)) {
        drawRect(color = c.color, topLeft = Offset(c.x - c.size / 2f, c.y - c.size / 2f), size = androidx.compose.ui.geometry.Size(c.size, c.size))
    }
}
