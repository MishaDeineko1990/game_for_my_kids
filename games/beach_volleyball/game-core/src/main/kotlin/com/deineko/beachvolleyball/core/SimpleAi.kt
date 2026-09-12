package com.deineko.beachvolleyball.core

import kotlin.math.abs

/** A deliberately beatable "vs computer" opponent: chases the ball's x with no anticipation, and
 *  only jumps once the ball is close and coming down within reach -- a 3-5..8 year old should be
 *  able to consistently win points against it. */
object SimpleAi {
    private const val JUMP_RANGE_X = 0.16f
    private const val JUMP_TRIGGER_HEIGHT = 0.5f

    fun decide(blob: BlobState, ball: BallState): BlobInput {
        val dx = ball.x - blob.x
        val ballDescending = ball.vy < 0f
        val withinJumpRange = abs(dx) < JUMP_RANGE_X && ball.y < JUMP_TRIGGER_HEIGHT
        val jump = blob.y <= Court.GROUND_Y && ballDescending && withinJumpRange
        return BlobInput(targetX = ball.x, jump = jump)
    }
}
