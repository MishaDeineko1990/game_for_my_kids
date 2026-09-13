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

    // Tuned down again after "the ball flies too fast/high" feedback: gravity, jump and hit speeds
    // moved together (so jump height keeps roughly the same feel) plus a hard MAX_HIT_SPEED cap so
    // no single hit -- however it was struck, even a jump-boosted spike -- can launch the ball as
    // violently as before. A real hard ceiling bounce (below) is what actually guarantees it can
    // never leave the top of the court, since jump velocity still stacks on top of a hit's speed.
    const val GRAVITY = 1.3f
    const val JUMP_VELOCITY = 0.85f
    const val NET_RESTITUTION = 0.55f
    const val WALL_RESTITUTION = 0.65f
    const val CEILING_RESTITUTION = 0.6f
    const val MIN_HIT_SPEED = 0.5f
    const val MAX_HIT_SPEED = 1.1f

    const val PLAYER_HOME_X = WIDTH * 0.25f
    const val OPPONENT_HOME_X = WIDTH * 0.75f

    // The serve no longer just drops from a height -- it hovers motionless until the serving side
    // jumps up and heads it into play, so kids get one clear "go hit it" moment instead of reacting
    // to an already-falling ball. Set above a grounded blob's own standing reach (which tops out
    // around BALL_RADIUS + BLOB_RADIUS =~ 0.125) but within a jump's reach, so a jump is required
    // but the timing window is forgiving.
    const val SERVE_HOVER_HEIGHT = 0.32f

    // Invisible ceiling the ball bounces off so an enthusiastic spike can never fly out the top of
    // the visible court. PLAY_AREA_HEIGHT is what the UI frames its camera to, with headroom above
    // the ceiling so a bounce there is still clearly visible.
    //
    // Kept low on purpose: the camera fits Court.WIDTH (1.0, always) to the screen's actual width
    // *and* PLAY_AREA_HEIGHT to its height, then uses whichever produces the smaller scale (see
    // MatchScreen's computeCourtMetrics) -- on a landscape phone that's almost always far wider
    // than it is tall (16:9 = 1.78 and up), so as long as PLAY_AREA_HEIGHT/WIDTH stays under that
    // ratio, the width is what binds and the court fills edge to edge with no side gutters. The
    // previous value (1.0) was tall enough that height bound instead on ordinary phones, leaving
    // the actual playable court a narrow strip in the middle of the screen -- reported as "play
    // only happens from the net to half the shown field".
    const val CEILING_Y = 0.42f
    const val PLAY_AREA_HEIGHT = 0.5f

    /** First to reach this wins outright -- ping-pong-style "game to eleven", no win-by-2 deuce
     *  rule, since the target players are 3-5..8 years old and a long deuce would just be
     *  frustrating. */
    const val WIN_SCORE = 11

    fun serveBall(side: Side): BallState {
        val x = if (side == Side.PLAYER) PLAYER_HOME_X else OPPONENT_HOME_X
        return BallState(x = x, y = SERVE_HOVER_HEIGHT, vx = 0f, vy = 0f)
    }

    fun homeBlob(side: Side): BlobState {
        val x = if (side == Side.PLAYER) PLAYER_HOME_X else OPPONENT_HOME_X
        return BlobState(x = x, y = GROUND_Y, vy = 0f)
    }
}

enum class Side { PLAYER, OPPONENT }

/** Scales the whole simulation's time step -- lets "how fast everything moves" be a single knob
 *  in settings without touching gravity/speed constants. Values are 20% lower than they used to
 *  be (again) after "the ball is still too fast" feedback -- this scales real-world seconds, so it
 *  slows the ball and both blobs' motion uniformly without changing trajectory shapes. */
enum class BallSpeed(val timeScale: Float) {
    SLOW(0.48f),
    NORMAL(0.8f),
    FAST(1.12f),
}

data class BallState(val x: Float, val y: Float, val vx: Float, val vy: Float)

data class BlobState(val x: Float, val y: Float, val vy: Float) {
    fun mirroredX(): BlobState = copy(x = Court.WIDTH - x)
}

/** What a player (local finger-drag input or a network peer) wants their blob to do this frame.
 *  `targetX` is an absolute court x the blob moves straight to this frame -- finger position IS
 *  blob position, no speed cap to "catch up" to. A capped chase-the-target version of this used to
 *  exist but read as laggy/unresponsive once tested on a real device, so movement is now a direct
 *  1:1 mapping (see [BeachVolleyballEngine]). `null` means no input yet, stay put. */
data class BlobInput(val targetX: Float?, val jump: Boolean) {
    /** Flips a court-x target to the other side -- a joining client always drags in "my blob is on
     *  the left" screen terms (it renders its own [MatchState.mirrored] blob there), but the host
     *  applies that input unmirrored to its actual OPPONENT-side blob, which lives on the right.
     *  Sending the raw target pins the client's blob uselessly against the net forever, since it
     *  always clamps to the near edge of the opponent's [BeachVolleyballEngine] movement range --
     *  the client must mirror it before it goes over the wire. */
    fun mirroredX(): BlobInput = copy(targetX = targetX?.let { Court.WIDTH - it })

    companion object {
        val NONE = BlobInput(targetX = null, jump = false)
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
    // Defaults to false so any state built directly (as most tests do) gets ordinary, always-
    // falling ball physics; only a fresh serve (`initial()` and the post-score reset in
    // BeachVolleyballEngine) explicitly opts into "hovering, wait for a hit".
    val servePending: Boolean = false,
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
            servePending = true,
        )
    }
}
