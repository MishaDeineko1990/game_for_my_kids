package com.deineko.beachvolleyball.core

/** Ball position in normalized court units, plus a height (`z`) used only to arc over the net
 *  and to know when it has touched the ground -- there is no true 3D, `z` is a visual/scoring axis. */
data class BallState(
    val x: Float,
    val y: Float,
    val z: Float,
    val vx: Float,
    val vy: Float,
    val vz: Float,
)

enum class Side { PLAYER, OPPONENT }

/** Scales the whole simulation's time step -- lets a level of "how fast the ball flies" be a
 *  single knob in settings without touching gravity/velocity constants. */
enum class BallSpeed(val timeScale: Float) {
    SLOW(0.6f),
    NORMAL(1f),
    FAST(1.4f),
}

object Court {
    const val WIDTH = 1f
    const val LENGTH = 1.6f
    const val NET_Y = LENGTH / 2f
    const val NET_CLEAR_HEIGHT = 0.32f
    const val BALL_RADIUS = 0.035f
    const val PADDLE_RADIUS = 0.1f
    const val PADDLE_REACH_HEIGHT = 0.45f
    const val PADDLE_REACH_DEPTH = 0.08f
    const val PLAYER_BASELINE_Y = LENGTH - 0.12f
    const val OPPONENT_BASELINE_Y = 0.12f
    const val GRAVITY = 2.6f
    const val MIN_RETURN_VZ = 1.0f
    const val PADDLE_KICK = 0.9f
    /** First to reach this wins outright -- ping-pong-style "game to eleven", no win-by-2 deuce
     *  rule, since the target players are 3-5 years old and a long deuce would just be frustrating. */
    const val WIN_SCORE = 11

    fun serveBall(side: Side): BallState {
        val y = if (side == Side.PLAYER) PLAYER_BASELINE_Y else OPPONENT_BASELINE_Y
        val vy = if (side == Side.PLAYER) -0.5f else 0.5f
        return BallState(x = WIDTH / 2f, y = y, z = 0.5f, vx = 0f, vy = vy, vz = 1.1f)
    }
}

data class MatchState(
    val ball: BallState,
    val playerX: Float,
    val opponentX: Float,
    val playerScore: Int = 0,
    val opponentScore: Int = 0,
    val serving: Side = Side.PLAYER,
    val isFinished: Boolean = false,
) {
    /** Flips the match so the side that was "opponent" renders as "player" -- used by a joined
     *  (non-hosting) device, which always sees its own paddle at the bottom of its own screen. */
    fun mirrored(): MatchState = copy(
        ball = ball.copy(y = Court.LENGTH - ball.y, vy = -ball.vy),
        playerX = opponentX,
        opponentX = playerX,
        playerScore = opponentScore,
        opponentScore = playerScore,
        serving = if (serving == Side.PLAYER) Side.OPPONENT else Side.PLAYER,
    )

    companion object {
        fun initial(serving: Side = Side.PLAYER): MatchState = MatchState(
            ball = Court.serveBall(serving),
            playerX = Court.WIDTH / 2f,
            opponentX = Court.WIDTH / 2f,
            serving = serving,
        )
    }
}
