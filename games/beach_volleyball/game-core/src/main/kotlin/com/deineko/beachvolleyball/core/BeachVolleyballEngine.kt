package com.deineko.beachvolleyball.core

import kotlin.math.abs

/** Which gameplay events happened during one [BeachVolleyballEngine.step] call -- lets the UI
 *  trigger sounds/animations on the exact frame something happened, instead of diffing states. */
data class StepEvents(
    val playerHit: Boolean = false,
    val opponentHit: Boolean = false,
    val netFault: Boolean = false,
    val scored: Boolean = false,
)

data class StepResult(val state: MatchState, val events: StepEvents)

/** All ball physics: gravity, wall/net bounces, paddle hits, and scoring. Pure function of the
 *  current state plus elapsed time -- no I/O, no randomness, fully unit-testable. */
object BeachVolleyballEngine {

    fun step(state: MatchState, dtSeconds: Float, speed: BallSpeed): StepResult {
        if (state.isFinished || dtSeconds <= 0f) return StepResult(state, StepEvents())
        val dt = dtSeconds * speed.timeScale
        val previous = state.ball

        var x = previous.x + previous.vx * dt
        var y = previous.y + previous.vy * dt
        val z = previous.z + previous.vz * dt
        var vx = previous.vx
        var vy = previous.vy
        val vz = previous.vz - Court.GRAVITY * dt

        if (x < Court.BALL_RADIUS) {
            x = Court.BALL_RADIUS
            vx = -vx
        } else if (x > Court.WIDTH - Court.BALL_RADIUS) {
            x = Court.WIDTH - Court.BALL_RADIUS
            vx = -vx
        }

        val crossedNet = (previous.y - Court.NET_Y) * (y - Court.NET_Y) < 0f
        var netFault = false
        if (crossedNet && z < Court.NET_CLEAR_HEIGHT) {
            y = if (previous.y < Court.NET_Y) Court.NET_Y - 0.01f else Court.NET_Y + 0.01f
            vy = -vy
            netFault = true
        }

        var ball = BallState(x = x, y = y, z = z, vx = vx, vy = vy, vz = vz)
        var playerHit = false
        var opponentHit = false
        if (!netFault) {
            val afterPlayer = tryPlayerHit(ball, state.playerX)
            playerHit = afterPlayer !== ball
            ball = afterPlayer
            val afterOpponent = tryOpponentHit(ball, state.opponentX)
            opponentHit = afterOpponent !== ball
            ball = afterOpponent
        }

        if (ball.z <= 0f) {
            val playerSideLanded = ball.y > Court.NET_Y
            val playerScore = state.playerScore + if (playerSideLanded) 0 else 1
            val opponentScore = state.opponentScore + if (playerSideLanded) 1 else 0
            val nextServing = if (playerSideLanded) Side.OPPONENT else Side.PLAYER
            val finished = playerScore >= Court.WIN_SCORE || opponentScore >= Court.WIN_SCORE
            val next = state.copy(
                ball = Court.serveBall(nextServing),
                playerScore = playerScore,
                opponentScore = opponentScore,
                serving = nextServing,
                isFinished = finished,
            )
            return StepResult(next, StepEvents(playerHit, opponentHit, netFault, scored = true))
        }

        return StepResult(state.copy(ball = ball), StepEvents(playerHit, opponentHit, netFault))
    }

    private fun tryPlayerHit(ball: BallState, paddleX: Float): BallState {
        val approaching = ball.vy > 0f
        val nearBaseline = abs(ball.y - Court.PLAYER_BASELINE_Y) <= Court.PADDLE_REACH_DEPTH
        val withinReach = abs(ball.x - paddleX) <= Court.PADDLE_RADIUS + Court.BALL_RADIUS
        val lowEnough = ball.z <= Court.PADDLE_REACH_HEIGHT
        if (!(approaching && nearBaseline && withinReach && lowEnough)) return ball

        val offset = ((ball.x - paddleX) / Court.PADDLE_RADIUS).coerceIn(-1f, 1f)
        return ball.copy(
            vy = -maxOf(abs(ball.vy), 0.5f),
            vz = Court.MIN_RETURN_VZ,
            vx = ball.vx + offset * Court.PADDLE_KICK,
        )
    }

    private fun tryOpponentHit(ball: BallState, paddleX: Float): BallState {
        val approaching = ball.vy < 0f
        val nearBaseline = abs(ball.y - Court.OPPONENT_BASELINE_Y) <= Court.PADDLE_REACH_DEPTH
        val withinReach = abs(ball.x - paddleX) <= Court.PADDLE_RADIUS + Court.BALL_RADIUS
        val lowEnough = ball.z <= Court.PADDLE_REACH_HEIGHT
        if (!(approaching && nearBaseline && withinReach && lowEnough)) return ball

        val offset = ((ball.x - paddleX) / Court.PADDLE_RADIUS).coerceIn(-1f, 1f)
        return ball.copy(
            vy = maxOf(abs(ball.vy), 0.5f),
            vz = Court.MIN_RETURN_VZ,
            vx = ball.vx + offset * Court.PADDLE_KICK,
        )
    }
}
