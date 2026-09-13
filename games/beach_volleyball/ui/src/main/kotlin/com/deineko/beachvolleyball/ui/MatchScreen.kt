package com.deineko.beachvolleyball.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.deineko.beachvolleyball.core.BallState
import com.deineko.beachvolleyball.core.BeachVolleyballEngine
import com.deineko.beachvolleyball.core.BlobInput
import com.deineko.beachvolleyball.core.BlobState
import com.deineko.beachvolleyball.core.Court
import com.deineko.beachvolleyball.core.MatchState
import com.deineko.beachvolleyball.core.NetProtocol
import com.deineko.beachvolleyball.core.Side
import com.deineko.beachvolleyball.core.SimpleAi
import com.deineko.connect.GameConnection
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.isActive

sealed class MatchMode {
    data object VsPc : MatchMode()
    data object Host : MatchMode()
    data object Client : MatchMode()
}

private const val BOARD_SIDE_PADDING = 28f
private const val BOARD_BOTTOM_PADDING = 24f
private const val BOARD_TOP_PADDING = 32f
private const val COURT_VISUAL_HEIGHT = Court.PLAY_AREA_HEIGHT

private data class CourtMetrics(val originX: Float, val groundY: Float, val scale: Float) {
    fun screenX(courtX: Float) = originX + courtX * scale
    fun screenY(courtY: Float) = groundY - courtY * scale
    fun courtXFromScreenX(screenX: Float) = ((screenX - originX) / scale).coerceIn(0f, Court.WIDTH)
}

private fun computeCourtMetrics(canvasWidth: Float, canvasHeight: Float): CourtMetrics {
    val availW = (canvasWidth - BOARD_SIDE_PADDING * 2).coerceAtLeast(1f)
    val availH = (canvasHeight - BOARD_BOTTOM_PADDING - BOARD_TOP_PADDING).coerceAtLeast(1f)
    val scale = minOf(availW / Court.WIDTH, availH / COURT_VISUAL_HEIGHT)
    val courtW = Court.WIDTH * scale
    return CourtMetrics(
        originX = (canvasWidth - courtW) / 2f,
        groundY = canvasHeight - BOARD_BOTTOM_PADDING,
        scale = scale,
    )
}

@Composable
fun MatchScreen(mode: MatchMode, connection: GameConnection?, onExit: () -> Unit) {
    val context = LocalContext.current
    val settingsStore = remember { SettingsStore(context) }
    val speed = remember { settingsStore.ballSpeed() }
    val soundEngine = remember { SoundEngine() }
    val isAuthoritative = mode is MatchMode.VsPc || mode is MatchMode.Host

    var matchState by remember { mutableStateOf(MatchState.initial()) }
    var localTargetX by remember { mutableStateOf<Float?>(null) }
    var jumpPressed by remember { mutableStateOf(false) }
    var ballSpinDeg by remember { mutableFloatStateOf(0f) }
    var pulsePhase by remember { mutableFloatStateOf(0f) }
    var latestOpponentInput by remember { mutableStateOf(BlobInput.NONE) }

    val localInput = BlobInput(targetX = localTargetX, jump = jumpPressed)

    if (connection != null) {
        LaunchedEffect(connection) {
            connection.messages.collect { bytes ->
                when (val message = NetProtocol.decode(bytes)) {
                    is NetProtocol.Message.InputUpdate ->
                        if (mode is MatchMode.Host) latestOpponentInput = message.input
                    is NetProtocol.Message.StateUpdate ->
                        if (mode is MatchMode.Client) matchState = message.state.mirrored()
                    null -> Unit
                }
            }
        }
    }

    if (mode is MatchMode.Client) {
        LaunchedEffect(localInput) {
            // Mirror before sending -- see BlobInput.mirroredX(): a joining client's drag target is
            // computed in its own "my blob renders on the left" screen terms, but the host applies
            // received input directly to its unmirrored OPPONENT-side blob (the right side). Without
            // this, the client's target always falls in the host's PLAYER range and clamps to the
            // opponent range's near edge, pinning the client's blob against the net no matter where
            // they drag -- the real cause behind "controls broken in network play".
            connection?.send(NetProtocol.encodeInput(localInput.mirroredX()))
        }
    }

    LaunchedEffect(mode) {
        var lastFrame = withFrameNanos { it }
        while (isActive) {
            val now = withFrameNanos { it }
            val dt = ((now - lastFrame) / 1_000_000_000f).coerceIn(0f, 0.05f)
            lastFrame = now

            ballSpinDeg += (abs(matchState.ball.vx) + abs(matchState.ball.vy)) * dt * 220f
            pulsePhase += dt * 5f

            if (isAuthoritative) {
                // Read the live State values here, inside the loop, rather than closing over the
                // `localInput` computed at the top of this composable: this LaunchedEffect is
                // keyed on `mode`, which never changes during a match, so it launches once and
                // keeps running -- a value merely *read* at launch time would stay frozen at
                // whatever it was on the very first frame (no drag, no jump) forever, which is
                // exactly the "controls don't do anything" bug a real device caught.
                val frameInput = BlobInput(targetX = localTargetX, jump = jumpPressed)
                val opponentInput = if (mode is MatchMode.VsPc) {
                    SimpleAi.decide(matchState.opponent, matchState.ball, matchState.servePending)
                } else {
                    latestOpponentInput
                }
                val result = BeachVolleyballEngine.step(matchState, dt, frameInput, opponentInput, speed)
                matchState = result.state
                if (result.events.playerHit || result.events.opponentHit) soundEngine.playHit()
                if (result.events.scored) {
                    if (result.state.isFinished) soundEngine.playWin() else soundEngine.playScore()
                }
                if (mode is MatchMode.Host) connection?.send(NetProtocol.encodeState(result.state))
            }
        }
    }

    val renderState = matchState

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            localTargetX = computeCourtMetrics(size.width.toFloat(), size.height.toFloat())
                                .courtXFromScreenX(offset.x)
                        },
                        onDrag = { change, _ ->
                            // Consuming the change matters on some devices/OEM skins: an
                            // unconsumed pointer event can still be claimed by a system-level
                            // gesture recognizer (e.g. an edge-swipe back gesture), which cancels
                            // the drag mid-stream -- a plausible cause of "controls don't respond"
                            // reports that only showed up on some phones, not others.
                            change.consume()
                            localTargetX = computeCourtMetrics(size.width.toFloat(), size.height.toFloat())
                                .courtXFromScreenX(change.position.x)
                        },
                    )
                },
        ) {
            val metrics = computeCourtMetrics(size.width, size.height)
            drawBackground(size.width, size.height, metrics)
            drawCourt(metrics, size.width)
            drawBlob(metrics, renderState.opponent, BeachPalette.opponentBody)
            drawBlob(metrics, renderState.player, BeachPalette.playerBody)
            val pulseScale = if (renderState.servePending) 1f + 0.08f * sin(pulsePhase) else 1f
            drawBallWithShadow(metrics, renderState.ball, ballSpinDeg, pulseScale)
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0x99000000))
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "${renderState.playerScore}",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = BeachPalette.playerBody,
            )
            Text(
                text = ":",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Text(
                text = "${renderState.opponentScore}",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = BeachPalette.opponentBody,
            )
        }

        if (renderState.servePending && renderState.serving == Side.PLAYER) {
            Text(
                text = "Стрибни, щоб подати! ⤒",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = BeachPalette.playerBody,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 68.dp),
            )
        }

        Text(
            text = "✕ Вийти",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 20.dp, start = 20.dp)
                .clickable {
                    connection?.stop()
                    onExit()
                },
        )

        ControlButton(
            label = "⤒",
            pressed = jumpPressed,
            onPressChange = { jumpPressed = it },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            size = 72.dp,
        )

        if (renderState.isFinished) {
            ResultOverlay(
                playerWon = renderState.playerScore > renderState.opponentScore,
                canRematch = isAuthoritative,
                onRematch = { matchState = MatchState.initial() },
                onExit = {
                    connection?.stop()
                    onExit()
                },
            )
        }
    }
}

@Composable
private fun ControlButton(
    label: String,
    pressed: Boolean,
    onPressChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (pressed) BeachPalette.controlButtonPressed else BeachPalette.controlButton)
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    onPressChange(true)
                    tryAwaitRelease()
                    onPressChange(false)
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = BeachPalette.controlGlyph, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ResultOverlay(playerWon: Boolean, canRematch: Boolean, onRematch: () -> Unit, onExit: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(Color(0x99000000)), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = if (playerWon) "Перемога! 🎉" else "Ще спробуємо! 💪",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (canRematch) {
                    Button(onClick = onRematch) { Text("Ще раз") }
                } else {
                    Text(
                        text = "Очікуємо нову гру...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(onClick = onExit) { Text("До ігор") }
            }
        }
    }
}

private fun DrawScope.drawBackground(canvasWidth: Float, canvasHeight: Float, metrics: CourtMetrics) {
    val horizonY = metrics.groundY - (metrics.groundY) * 0.42f
    val seaTopY = horizonY
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(BeachPalette.skyTop, BeachPalette.skyHorizon),
            startY = 0f,
            endY = horizonY,
        ),
        topLeft = Offset(0f, 0f),
        size = Size(canvasWidth, horizonY),
    )
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(BeachPalette.seaFar, BeachPalette.seaNear),
            startY = seaTopY,
            endY = metrics.groundY,
        ),
        topLeft = Offset(0f, seaTopY),
        size = Size(canvasWidth, metrics.groundY - seaTopY),
    )
    val waveSteps = 3
    for (i in 1..waveSteps) {
        val y = seaTopY + (metrics.groundY - seaTopY) * (i / (waveSteps + 1f))
        drawLine(
            color = BeachPalette.seaWave,
            start = Offset(0f, y),
            end = Offset(canvasWidth, y),
            strokeWidth = 2f,
        )
    }

    val palmHeight = canvasHeight * 0.4f
    drawPalm(baseX = canvasWidth * 0.04f, baseY = metrics.groundY, height = palmHeight, mirrored = false)
    drawPalm(baseX = canvasWidth * 0.96f, baseY = metrics.groundY, height = palmHeight * 0.85f, mirrored = true)
}

private fun DrawScope.drawPalm(baseX: Float, baseY: Float, height: Float, mirrored: Boolean) {
    val dir = if (mirrored) -1f else 1f
    val topX = baseX + dir * height * 0.22f
    val topY = baseY - height
    drawLine(
        color = BeachPalette.palmTrunk,
        start = Offset(baseX, baseY),
        end = Offset(topX, topY),
        strokeWidth = height * 0.05f,
        cap = StrokeCap.Round,
    )
    // Fronds fan out from the trunk's top, angle measured from straight up (0deg) so the fan
    // is naturally symmetric regardless of which way the trunk itself leans.
    val frondLength = height * 0.5f
    val anglesFromVerticalDeg = listOf(-70f, -35f, 0f, 35f, 70f)
    for (angleDeg in anglesFromVerticalDeg) {
        val rad = Math.toRadians(angleDeg.toDouble())
        val dx = sin(rad).toFloat() * frondLength
        val dy = -cos(rad).toFloat() * frondLength
        drawLine(
            color = BeachPalette.palmLeaf,
            start = Offset(topX, topY),
            end = Offset(topX + dx, topY + dy),
            strokeWidth = height * 0.045f,
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawCourt(metrics: CourtMetrics, canvasWidth: Float) {
    drawRect(
        color = BeachPalette.sand,
        topLeft = Offset(0f, metrics.groundY),
        size = Size(canvasWidth, size.height - metrics.groundY),
    )
    drawLine(
        color = BeachPalette.sandLine,
        start = Offset(0f, metrics.groundY),
        end = Offset(canvasWidth, metrics.groundY),
        strokeWidth = 4f,
    )

    val netX = metrics.screenX(Court.NET_X)
    val netTopY = metrics.screenY(Court.NET_HEIGHT)
    drawRect(
        color = BeachPalette.netPost,
        topLeft = Offset(netX - 4f, netTopY - 10f),
        size = Size(8f, metrics.groundY - netTopY + 10f),
    )
    val meshSteps = 6
    for (i in 0..meshSteps) {
        val y = netTopY + ((metrics.groundY - netTopY) / meshSteps) * i
        drawLine(
            color = BeachPalette.netMesh,
            start = Offset(netX - 14f, y),
            end = Offset(netX + 14f, y),
            strokeWidth = 2f,
        )
    }
}

private fun DrawScope.drawBlob(metrics: CourtMetrics, blob: BlobState, color: Color) {
    val radius = Court.BLOB_RADIUS * metrics.scale
    val center = Offset(metrics.screenX(blob.x), metrics.screenY(blob.y) - radius)

    val shadowScale = 1f - (blob.y / Court.PLAY_AREA_HEIGHT).coerceIn(0f, 0.7f)
    drawOval(
        color = BeachPalette.shadow,
        topLeft = Offset(center.x - radius * shadowScale, metrics.groundY - radius * 0.3f * shadowScale),
        size = Size(radius * 2f * shadowScale, radius * 0.6f * shadowScale),
    )

    val stretch = (blob.vy / Court.JUMP_VELOCITY).coerceIn(-1f, 1f)
    val scaleY = 1f + stretch * 0.22f
    val scaleX = 1f - stretch * 0.14f
    withTransform({ scale(scaleX, scaleY, pivot = center) }) {
        drawCircle(color = color, radius = radius, center = center)
        val eyeDx = radius * 0.32f
        val eyeDy = -radius * 0.15f
        drawCircle(color = Color.Black, radius = radius * 0.1f, center = center + Offset(-eyeDx, eyeDy))
        drawCircle(color = Color.Black, radius = radius * 0.1f, center = center + Offset(eyeDx, eyeDy))
        drawArc(
            color = Color.Black,
            startAngle = 20f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(center.x - radius * 0.35f, center.y - radius * 0.05f),
            size = Size(radius * 0.7f, radius * 0.5f),
            style = Stroke(width = radius * 0.08f, cap = StrokeCap.Round),
        )

        // A small tied-off knot plus a curled string trailing below -- reads as a balloon rather
        // than a plain ball, per request.
        val knotWidth = radius * 0.32f
        val knotHeight = radius * 0.24f
        val knotTopY = center.y + radius * 0.94f
        val knotPath = Path().apply {
            moveTo(center.x - knotWidth / 2f, knotTopY)
            lineTo(center.x + knotWidth / 2f, knotTopY)
            lineTo(center.x, knotTopY + knotHeight)
            close()
        }
        drawPath(knotPath, color = color)

        val stringStart = Offset(center.x, knotTopY + knotHeight)
        val stringLength = radius * 1.5f
        val stringSway = radius * 0.4f
        val stringPath = Path().apply {
            moveTo(stringStart.x, stringStart.y)
            cubicTo(
                stringStart.x + stringSway, stringStart.y + stringLength * 0.35f,
                stringStart.x - stringSway, stringStart.y + stringLength * 0.7f,
                stringStart.x, stringStart.y + stringLength,
            )
        }
        drawPath(
            stringPath,
            color = BeachPalette.balloonString,
            style = Stroke(width = radius * 0.05f, cap = StrokeCap.Round),
        )
    }
}

private fun DrawScope.drawBallWithShadow(metrics: CourtMetrics, ball: BallState, spinDeg: Float, radiusScale: Float = 1f) {
    val radius = Court.BALL_RADIUS * metrics.scale * 1.3f * radiusScale
    val groundX = metrics.screenX(ball.x)
    val shadowScale = 1f - (ball.y / Court.PLAY_AREA_HEIGHT).coerceIn(0f, 0.7f)
    drawOval(
        color = BeachPalette.shadow,
        topLeft = Offset(groundX - radius * shadowScale, metrics.groundY - radius * 0.3f * shadowScale),
        size = Size(radius * 2f * shadowScale, radius * 0.6f * shadowScale),
    )

    val ballCenter = Offset(groundX, metrics.screenY(ball.y) - radius)
    withTransform({ rotate(spinDeg, pivot = ballCenter) }) {
        drawCircle(color = Color.White, radius = radius, center = ballCenter)
        drawArc(
            color = BeachPalette.ballStripeA,
            startAngle = -30f,
            sweepAngle = 55f,
            useCenter = true,
            topLeft = Offset(ballCenter.x - radius, ballCenter.y - radius),
            size = Size(radius * 2f, radius * 2f),
        )
        drawArc(
            color = BeachPalette.ballStripeB,
            startAngle = 90f,
            sweepAngle = 55f,
            useCenter = true,
            topLeft = Offset(ballCenter.x - radius, ballCenter.y - radius),
            size = Size(radius * 2f, radius * 2f),
        )
        drawArc(
            color = BeachPalette.ballStripeC,
            startAngle = 210f,
            sweepAngle = 55f,
            useCenter = true,
            topLeft = Offset(ballCenter.x - radius, ballCenter.y - radius),
            size = Size(radius * 2f, radius * 2f),
        )
    }
    drawCircle(
        color = Color(0x26000000),
        radius = radius,
        center = ballCenter,
        style = Stroke(width = radius * 0.08f),
    )
}
