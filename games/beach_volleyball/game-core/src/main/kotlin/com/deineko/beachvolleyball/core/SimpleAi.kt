package com.deineko.beachvolleyball.core

import kotlin.math.abs

/** A deliberately beatable "vs computer" opponent (always plays the right-hand/opponent side):
 *  chases the ball with no anticipation, and only jumps once the ball is close and either coming
 *  down within reach or waiting to be served -- a 3-5..8 year old should be able to consistently
 *  win points against it. */
object SimpleAi {
    private const val JUMP_RANGE_X = 0.16f
    private const val JUMP_TRIGGER_HEIGHT = 0.5f

    // Stand slightly to the ball's far side (away from the net) instead of dead-centered under it:
    // a perfectly centered hit bounces the ball straight up off the blob's head forever, because
    // the collision's contact normal is then purely vertical -- that was a real reported bug
    // ("computer bounces the ball vertically into its own head"). A small horizontal offset gives
    // every AI hit a natural angle back over the net toward the player.
    private const val AIM_OFFSET = Court.BLOB_RADIUS * 0.55f

    fun decide(blob: BlobState, ball: BallState, servePending: Boolean = false): BlobInput {
        val aimX = (ball.x + AIM_OFFSET).coerceAtMost(Court.WIDTH - Court.BLOB_RADIUS)
        val dx = ball.x - blob.x
        val withinJumpRange = abs(dx) < JUMP_RANGE_X && ball.y < JUMP_TRIGGER_HEIGHT
        // A pending serve hovers motionless (vy == 0), so it would never look "descending" --
        // treat "it's my serve and the ball is in range" as reason enough to jump and head it in.
        val ballApproaching = servePending || ball.vy < 0f
        val jump = blob.y <= Court.GROUND_Y && ballApproaching && withinJumpRange
        return BlobInput(targetX = aimX, jump = jump)
    }
}
