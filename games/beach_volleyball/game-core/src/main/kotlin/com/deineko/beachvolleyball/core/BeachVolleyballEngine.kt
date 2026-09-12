package com.deineko.beachvolleyball.core

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

data class StepEvents(
    val playerHit: Boolean = false,
    val opponentHit: Boolean = false,
    val netBounce: Boolean = false,
    val scored: Boolean = false,
)

data class StepResult(val state: MatchState, val events: StepEvents)

/** Side-view volleyball physics: gravity on the ball and both blobs, arcade-style instant
 *  horizontal movement (no acceleration -- Blobby Volley's own feel), real circle-vs-circle
 *  collision between the ball and a blob (the bounce direction falls naturally out of the contact
 *  normal, no paddle-offset hack needed), a solid net, side walls and a ceiling the ball bounces
 *  off (so it can never leave the court), and ground-touch scoring. Pure function of the current
 *  state, elapsed time and both sides' inputs -- no I/O, no randomness. */
object BeachVolleyballEngine {

    fun step(
        state: MatchState,
        dtSeconds: Float,
        playerInput: BlobInput,
        opponentInput: BlobInput,
        speed: BallSpeed,
    ): StepResult {
        if (state.isFinished || dtSeconds <= 0f) return StepResult(state, StepEvents())
        val dt = dtSeconds * speed.timeScale

        val player = stepBlob(state.player, playerInput, dt, isPlayerSide = true)
        val opponent = stepBlob(state.opponent, opponentInput, dt, isPlayerSide = false)

        // A pending serve hovers motionless -- no gravity, no wall/ceiling/net bounce -- until the
        // serving side's jump makes contact with it below, at which point collide() launches it
        // exactly like any other hit and the match proceeds normally from there on.
        var ball = if (state.servePending) state.ball else stepBall(state.ball, dt)
        var netBounce = false
        if (!state.servePending) {
            ball = bounceOffWalls(ball)
            ball = bounceOffCeiling(ball)
            val netResult = bounceOffNet(ball, state.ball)
            if (netResult != null) {
                ball = netResult
                netBounce = true
            }
        }

        var playerHit = false
        var opponentHit = false
        if (!netBounce) {
            val afterPlayer = collide(ball, state.ball, player)
            playerHit = afterPlayer !== ball
            ball = afterPlayer
            val afterOpponent = collide(ball, state.ball, opponent)
            opponentHit = afterOpponent !== ball
            ball = afterOpponent
        }

        val servePending = state.servePending && !playerHit && !opponentHit

        if (!servePending && ball.y <= Court.GROUND_Y) {
            val playerSideLanded = ball.x < Court.NET_X
            val playerScore = state.playerScore + if (playerSideLanded) 0 else 1
            val opponentScore = state.opponentScore + if (playerSideLanded) 1 else 0
            val nextServing = if (playerSideLanded) Side.OPPONENT else Side.PLAYER
            val finished = playerScore >= Court.WIN_SCORE || opponentScore >= Court.WIN_SCORE
            val next = MatchState(
                ball = Court.serveBall(nextServing),
                player = Court.homeBlob(Side.PLAYER),
                opponent = Court.homeBlob(Side.OPPONENT),
                playerScore = playerScore,
                opponentScore = opponentScore,
                serving = nextServing,
                isFinished = finished,
                servePending = true,
            )
            return StepResult(next, StepEvents(playerHit, opponentHit, netBounce, scored = true))
        }

        return StepResult(
            state.copy(ball = ball, player = player, opponent = opponent, servePending = servePending),
            StepEvents(playerHit, opponentHit, netBounce),
        )
    }

    private fun stepBlob(blob: BlobState, input: BlobInput, dt: Float, isPlayerSide: Boolean): BlobState {
        val minX: Float
        val maxX: Float
        if (isPlayerSide) {
            minX = Court.BLOB_RADIUS
            maxX = Court.NET_X - Court.NET_HALF_WIDTH - Court.BLOB_RADIUS
        } else {
            minX = Court.NET_X + Court.NET_HALF_WIDTH + Court.BLOB_RADIUS
            maxX = Court.WIDTH - Court.BLOB_RADIUS
        }
        // Direct 1:1 mapping, no chase/catch-up: a dragging finger is already a continuous stream
        // of positions every frame, so tracking it exactly is both simpler and removes the lag a
        // capped-speed "chase the target" version read as glitchy on a real device.
        val x = (input.targetX ?: blob.x).coerceIn(minX, maxX)

        val grounded = blob.y <= Court.GROUND_Y
        val vy = if (grounded && input.jump) Court.JUMP_VELOCITY else blob.vy - Court.GRAVITY * dt
        var y = blob.y + vy * dt
        var finalVy = vy
        if (y <= Court.GROUND_Y) {
            y = Court.GROUND_Y
            finalVy = 0f
        }
        return BlobState(x = x, y = y, vy = finalVy)
    }

    private fun stepBall(ball: BallState, dt: Float): BallState {
        val x = ball.x + ball.vx * dt
        val y = ball.y + ball.vy * dt
        val vy = ball.vy - Court.GRAVITY * dt
        return ball.copy(x = x, y = y, vy = vy)
    }

    // Both guards below only reflect a ball that's actually moving INTO the boundary -- a ball
    // merely positioned past it (which can't happen in real play, only in a hand-built state) is
    // left alone rather than getting a spurious bounce, since it's presumably already headed back
    // the right way.
    private fun bounceOffWalls(ball: BallState): BallState {
        val minX = Court.BALL_RADIUS
        val maxX = Court.WIDTH - Court.BALL_RADIUS
        return when {
            ball.x < minX && ball.vx < 0f -> ball.copy(x = minX, vx = -ball.vx * Court.WALL_RESTITUTION)
            ball.x > maxX && ball.vx > 0f -> ball.copy(x = maxX, vx = -ball.vx * Court.WALL_RESTITUTION)
            else -> ball
        }
    }

    private fun bounceOffCeiling(ball: BallState): BallState {
        if (ball.y <= Court.CEILING_Y || ball.vy <= 0f) return ball
        return ball.copy(y = Court.CEILING_Y, vy = -ball.vy * Court.CEILING_RESTITUTION)
    }

    private fun bounceOffNet(ball: BallState, previous: BallState): BallState? {
        val netLeft = Court.NET_X - Court.NET_HALF_WIDTH - Court.BALL_RADIUS
        val netRight = Court.NET_X + Court.NET_HALF_WIDTH + Court.BALL_RADIUS
        if (ball.x !in netLeft..netRight || ball.y >= Court.NET_HEIGHT) return null
        val cameFromLeft = previous.x < Court.NET_X
        val clampedX = if (cameFromLeft) netLeft else netRight
        return ball.copy(x = clampedX, vx = -ball.vx * Court.NET_RESTITUTION)
    }

    private fun collide(ball: BallState, ballBefore: BallState, blob: BlobState): BallState {
        val dx = ball.x - blob.x
        val dy = ball.y - blob.y
        val dist = hypot(dx, dy)
        val minDist = Court.BALL_RADIUS + Court.BLOB_RADIUS
        if (dist >= minDist) return ball

        // Derive the contact normal from where the ball approached FROM this step, not its
        // (possibly already tunneled past the blob's center) post-move position -- a fast-falling
        // ball whose center ends up on the far side of the blob within one frame would otherwise
        // get pushed further through instead of bounced back.
        val fromDx = ballBefore.x - blob.x
        val fromDy = ballBefore.y - blob.y
        val fromDist = hypot(fromDx, fromDy)
        val nx: Float
        val ny: Float
        if (fromDist > 0.0001f) {
            nx = fromDx / fromDist
            ny = fromDy / fromDist
        } else if (dist > 0.0001f) {
            nx = dx / dist
            ny = dy / dist
        } else {
            nx = 0f
            ny = 1f
        }
        val correctedX = blob.x + nx * minDist
        val correctedY = blob.y + ny * minDist

        // Blobs only ever move by direct position update (no horizontal velocity of their own),
        // so the ball's velocity relative to the blob only differs from its absolute velocity
        // vertically -- reflect that relative velocity across the contact normal, then convert
        // back to absolute by adding the blob's vy (a jump adds real extra power to the return).
        val relVx = ball.vx
        val relVy = ball.vy - blob.vy
        val approachSpeed = relVx * nx + relVy * ny
        if (approachSpeed > 0f) return ball.copy(x = correctedX, y = correctedY)

        val bounceVx = relVx - 2 * approachSpeed * nx
        val bounceVy = relVy - 2 * approachSpeed * ny
        val speed = hypot(bounceVx, bounceVy).coerceIn(Court.MIN_HIT_SPEED, Court.MAX_HIT_SPEED)
        val angle = atan2(bounceVy, bounceVx)
        return ball.copy(
            x = correctedX,
            y = correctedY,
            vx = cos(angle) * speed,
            vy = sin(angle) * speed + blob.vy,
        )
    }
}
