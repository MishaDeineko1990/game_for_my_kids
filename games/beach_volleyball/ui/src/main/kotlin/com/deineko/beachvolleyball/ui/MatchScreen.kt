package com.deineko.beachvolleyball.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.deineko.beachvolleyball.core.BallState
import com.deineko.beachvolleyball.core.BeachVolleyballEngine
import com.deineko.beachvolleyball.core.Court
import com.deineko.beachvolleyball.core.MatchState
import com.deineko.beachvolleyball.core.NetProtocol
import com.deineko.beachvolleyball.core.SimpleAi
import com.deineko.connect.GameConnection
import kotlin.math.abs
import kotlinx.coroutines.isActive

sealed class MatchMode {
    data object VsPc : MatchMode()
    data object Host : MatchMode()
    data object Client : MatchMode()
}

private const val HEIGHT_SCALE = 0.55f
private const val BOARD_PADDING = 32f

private data class CourtMetrics(val originX: Float, val originY: Float, val width: Float, val height: Float) {
    fun screenX(courtY: Float) = originX + (courtY / Court.LENGTH) * width
    fun screenY(courtX: Float) = originY + (courtX / Court.WIDTH) * height
    fun courtXFromScreenY(screenY: Float): Float =
        (((screenY - originY) / height) * Court.WIDTH).coerceIn(Court.PADDLE_RADIUS, Court.WIDTH - Court.PADDLE_RADIUS)
}

private fun computeCourtMetrics(canvasWidth: Float, canvasHeight: Float): CourtMetrics {
    val availW = (canvasWidth - BOARD_PADDING * 2).coerceAtLeast(1f)
    val availH = (canvasHeight - BOARD_PADDING * 2).coerceAtLeast(1f)
    val scale = minOf(availW / Court.LENGTH, availH / Court.WIDTH)
    val courtW = Court.LENGTH * scale
    val courtH = Court.WIDTH * scale
    return CourtMetrics(
        originX = (canvasWidth - courtW) / 2f,
        originY = (canvasHeight - courtH) / 2f,
        width = courtW,
        height = courtH,
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
    var localPaddle by remember { mutableFloatStateOf(Court.WIDTH / 2f) }
    var playerPulse by remember { mutableFloatStateOf(0f) }
    var opponentPulse by remember { mutableFloatStateOf(0f) }
    var ballSpinDeg by remember { mutableFloatStateOf(0f) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    if (connection != null) {
        LaunchedEffect(connection) {
            connection.messages.collect { bytes ->
                when (val message = NetProtocol.decode(bytes)) {
                    is NetProtocol.Message.PaddleUpdate ->
                        if (mode is MatchMode.Host) matchState = matchState.copy(opponentX = message.x)
                    is NetProtocol.Message.StateUpdate ->
                        if (mode is MatchMode.Client) matchState = message.state.mirrored()
                    null -> Unit
                }
            }
        }
    }

    if (mode is MatchMode.Client) {
        LaunchedEffect(localPaddle) {
            connection?.send(NetProtocol.encodePaddle(localPaddle))
        }
    }

    LaunchedEffect(mode) {
        var lastFrame = withFrameNanos { it }
        while (isActive) {
            val now = withFrameNanos { it }
            val dt = ((now - lastFrame) / 1_000_000_000f).coerceIn(0f, 0.05f)
            lastFrame = now

            ballSpinDeg += (abs(matchState.ball.vx) + abs(matchState.ball.vy)) * dt * 220f
            playerPulse = (playerPulse - dt * 3f).coerceAtLeast(0f)
            opponentPulse = (opponentPulse - dt * 3f).coerceAtLeast(0f)

            if (isAuthoritative) {
                val opponentX = if (mode is MatchMode.VsPc) {
                    SimpleAi.nextOpponentX(matchState.opponentX, matchState.ball, dt)
                } else {
                    matchState.opponentX
                }
                val withInputs = matchState.copy(playerX = localPaddle, opponentX = opponentX)
                val result = BeachVolleyballEngine.step(withInputs, dt, speed)
                matchState = result.state
                if (result.events.playerHit) playerPulse = 1f
                if (result.events.opponentHit) opponentPulse = 1f
                if (result.events.playerHit || result.events.opponentHit) soundEngine.playHit()
                if (result.events.scored) {
                    if (result.state.isFinished) soundEngine.playWin() else soundEngine.playScore()
                }
                if (mode is MatchMode.Host) connection?.send(NetProtocol.encodeState(result.state))
            }
        }
    }

    val renderState = if (mode is MatchMode.Client) matchState.copy(playerX = localPaddle) else matchState

    Box(modifier = Modifier.fillMaxSize().background(BeachPalette.skyBottom)) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { canvasSize = it }
                .pointerInput(canvasSize) {
                    val metrics = computeCourtMetrics(canvasSize.width.toFloat(), canvasSize.height.toFloat())
                    detectDragGestures(
                        onDragStart = { offset -> localPaddle = metrics.courtXFromScreenY(offset.y) },
                        onDrag = { change, _ -> localPaddle = metrics.courtXFromScreenY(change.position.y) },
                    )
                },
        ) {
            val metrics = computeCourtMetrics(size.width, size.height)
            drawCourt(metrics)
            drawCharacter(
                center = Offset(metrics.screenX(Court.OPPONENT_BASELINE_Y), metrics.screenY(renderState.opponentX)),
                radius = metrics.height * Court.PADDLE_RADIUS,
                color = BeachPalette.opponentBody,
                pulse = opponentPulse,
            )
            drawCharacter(
                center = Offset(metrics.screenX(Court.PLAYER_BASELINE_Y), metrics.screenY(renderState.playerX)),
                radius = metrics.height * Court.PADDLE_RADIUS,
                color = BeachPalette.playerBody,
                pulse = playerPulse,
            )
            drawBallWithShadow(metrics, renderState.ball, ballSpinDeg)
        }

        Row(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "${renderState.opponentScore}",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = BeachPalette.opponentBody,
            )
            Text(
                text = "${renderState.playerScore}",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = BeachPalette.playerBody,
            )
        }

        Text(
            text = "✕ Вийти",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 20.dp)
                .clickable {
                    connection?.stop()
                    onExit()
                },
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

private fun DrawScope.drawCourt(metrics: CourtMetrics) {
    drawRect(
        color = BeachPalette.sand,
        topLeft = Offset(metrics.originX, metrics.originY),
        size = Size(metrics.width, metrics.height),
    )
    val netX = metrics.screenX(Court.NET_Y)
    drawRect(
        color = BeachPalette.sandLine,
        topLeft = Offset(netX - metrics.width * 0.004f, metrics.originY),
        size = Size(metrics.width * 0.008f, metrics.height),
    )
    drawRect(
        color = BeachPalette.netPost,
        topLeft = Offset(netX - 4f, metrics.originY - 14f),
        size = Size(8f, metrics.height + 28f),
    )
    val meshSteps = 8
    for (i in 0..meshSteps) {
        val y = metrics.originY + (metrics.height / meshSteps) * i
        drawLine(
            color = BeachPalette.netMesh,
            start = Offset(netX - 14f, y),
            end = Offset(netX + 14f, y),
            strokeWidth = 2f,
        )
    }
}

private fun DrawScope.drawCharacter(center: Offset, radius: Float, color: Color, pulse: Float) {
    val scaleX = 1f + pulse * 0.22f
    val scaleY = 1f - pulse * 0.22f
    withTransform({ scale(scaleX, scaleY, pivot = center) }) {
        drawCircle(color = Color(0x33000000), radius = radius * 0.9f, center = center + Offset(0f, radius * 0.55f))
        drawCircle(color = color, radius = radius, center = center)
        val eyeDx = radius * 0.32f
        val eyeDy = -radius * 0.18f
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
    }
}

private fun DrawScope.drawBallWithShadow(metrics: CourtMetrics, ball: BallState, spinDeg: Float) {
    val groundX = metrics.screenX(ball.y)
    val groundY = metrics.screenY(ball.x)
    val radius = metrics.height * Court.BALL_RADIUS * 1.4f
    val shadowScale = 1f - (ball.z / 1.4f).coerceIn(0f, 0.65f)
    drawOval(
        color = BeachPalette.shadow,
        topLeft = Offset(groundX - radius * shadowScale, groundY - radius * 0.35f * shadowScale),
        size = Size(radius * 2f * shadowScale, radius * 0.7f * shadowScale),
    )

    val liftPx = ball.z * metrics.height * HEIGHT_SCALE
    val ballCenter = Offset(groundX, groundY - liftPx)
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
