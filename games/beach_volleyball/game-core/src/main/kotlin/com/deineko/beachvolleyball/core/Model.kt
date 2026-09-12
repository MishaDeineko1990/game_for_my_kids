package com.deineko.beachvolleyball.core

/** Side-view court, Blobby-Volley-style: `x` is horizontal position (0 = left wall, WIDTH = right
 *  wall, net in the middle), `y` is real height above the ground (0 = standing/ground level,
 *  gravity-affected) -- unlike the game's first top-down version, this `y` is an actual physical
 *  axis, not a visual-only cheat. */
object Court {
    const val WIDTH = 1f
    const val NET_X = WIDTH / 2f
    const val NET_HALF_WIDTH = 0.012f
    const val NET_HEIGHT = 0.26f
    const val GROUND_Y = 0f

    const val BALL_RADIUS = 0.035f
    const val BLOB_RADIUS = 0.09f

    const val GRAVITY = 2.4f
    const val JUMP_VELOCITY = 1.15f
    const val BLOB_MOVE_SPEED = 0.75f
    const val NET_RESTITUTION = 0.6f
    const val MIN_HIT_SPEED = 0.9f

    const val PLAYER_HOME_X = WIDTH * 0.25f
    const val OPPONENT_HOME_X = WIDTH * 0.75f
    const val SERVE_HEIGHT = 0.9f

    /** First to reach this wins outright -- ping-pong-style "game to eleven", no win-by-2 deuce
     *  rule, since the target players are 3-5..8 years old and a long deuce would just be
     *  frustrating. */
    const val WIN_SCORE = 11

    fun serveBall(side: Side): BallState {
        val x = if (side == Side.PLAYER) PLAYER_HOME_X else OPPONENT_HOME_X
        return BallState(x = x, y = SERVE_HEIGHT, vx = 0f, vy = 0f)
    }

    fun homeBlob(side: Side): BlobState {
        val x = if (side == Side.PLAYER) PLAYER_HOME_X else OPPONENT_HOME_X
        return BlobState(x = x, y = GROUND_Y, vy = 0f)
    }
}

enum class Side { PLAYER, OPPONENT }

/** Scales the whole simulation's time step -- lets "how fast everything moves" be a single knob
 *  in settings without touching gravity/speed constants. */
enum class BallSpeed(val timeScale: Float) {
    SLOW(0.6f),
    NORMAL(1f),
    FAST(1.4f),
}

data class BallState(val x: Float, val y: Float, val vx: Float, val vy: Float)

data class BlobState(val x: Float, val y: Float, val vy: Float) {
    fun mirroredX(): BlobState = copy(x = Court.WIDTH - x)
}

/** What a player (local input or a network peer) wants their blob to do this frame.
 *  `moveDirection` is -1 (toward the wall), 0, or 1 (toward the net). */
data class BlobInput(val moveDirection: Int, val jump: Boolean) {
    companion object {
        val NONE = BlobInput(moveDirection = 0, jump = false)
    }
}

data class MatchState(
    val ball: BallState,
    val player: BlobState,
    val opponent: BlobState,
    val playerScore: Int = 0,
    val opponentScore: Int = 0,
    val serving: Side = Side.PLAYER,
    val isFinished: Boolean = false,
) {
    /** Flips the match so the side that was "opponent" renders as "player" -- used by a joined
     *  (non-hosting) device, which always sees its own blob on its preferred side of its screen. */
    fun mirrored(): MatchState = copy(
        ball = ball.copy(x = Court.WIDTH - ball.x, vx = -ball.vx),
        player = opponent.mirroredX(),
        opponent = player.mirroredX(),
        playerScore = opponentScore,
        opponentScore = playerScore,
        serving = if (serving == Side.PLAYER) Side.OPPONENT else Side.PLAYER,
    )

    companion object {
        fun initial(serving: Side = Side.PLAYER): MatchState = MatchState(
            ball = Court.serveBall(serving),
            player = Court.homeBlob(Side.PLAYER),
            opponent = Court.homeBlob(Side.OPPONENT),
            serving = serving,
        )
    }
}
