package com.deineko.beachvolleyball.core

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SimpleAiTest {

    private fun ballAt(x: Float, y: Float = 1f, vy: Float = 0f) = BallState(x = x, y = y, vx = 0f, vy = vy)
    private fun blobAt(x: Float, y: Float = Court.GROUND_Y) = BlobState(x = x, y = y, vy = 0f)

    @Test
    fun `tracks the ball's x position closely`() {
        val decision = SimpleAi.decide(blobAt(Court.OPPONENT_HOME_X), ballAt(0.7f))
        assertTrue(decision.targetX != null && kotlin.math.abs(decision.targetX - 0.7f) < 0.05f)
    }

    @Test
    fun `aims slightly off-center instead of dead-centered under the ball`() {
        // A hit dead-center under the ball bounces it straight up off the blob's head forever
        // (the real bug this was tuned to fix) -- the AI should always stand a little to the
        // ball's far side (away from the net) so its hits naturally angle back over the net.
        val decision = SimpleAi.decide(blobAt(Court.OPPONENT_HOME_X), ballAt(0.7f))
        assertTrue(decision.targetX!! > 0.7f, "should aim past the ball, not exactly onto it")
        assertTrue(decision.targetX - 0.7f < 0.16f, "the offset should still be small enough to track the ball")
    }

    @Test
    fun `jumps to serve a held ball even though it isn't descending`() {
        val decision = SimpleAi.decide(blobAt(0.7f), ballAt(0.7f, y = 0.2f, vy = 0f), servePending = true)
        assertTrue(decision.jump, "a pending serve never 'descends' -- servePending alone should trigger the jump")
    }

    @Test
    fun `jumps when a descending ball is low and close`() {
        val decision = SimpleAi.decide(blobAt(0.6f), ballAt(0.61f, y = 0.2f, vy = -0.5f))
        assertTrue(decision.jump)
    }

    @Test
    fun `does not jump at a ball that is still high up`() {
        val decision = SimpleAi.decide(blobAt(0.6f), ballAt(0.61f, y = 1.2f, vy = -0.5f))
        assertFalse(decision.jump)
    }

    @Test
    fun `does not jump while already airborne`() {
        val decision = SimpleAi.decide(blobAt(0.6f, y = 0.2f), ballAt(0.61f, y = 0.2f, vy = -0.5f))
        assertFalse(decision.jump, "a blob already in the air shouldn't be told to jump again")
    }

    @Test
    fun `does not jump at a ball moving upward`() {
        val decision = SimpleAi.decide(blobAt(0.6f), ballAt(0.61f, y = 0.2f, vy = 0.5f))
        assertFalse(decision.jump, "no need to jump at a ball that's rising away")
    }
}
