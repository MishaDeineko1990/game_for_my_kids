package com.deineko.beachvolleyball.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SimpleAiTest {

    private fun ballAt(x: Float, y: Float = 1f, vy: Float = 0f) = BallState(x = x, y = y, vx = 0f, vy = vy)
    private fun blobAt(x: Float, y: Float = Court.GROUND_Y) = BlobState(x = x, y = y, vy = 0f)

    @Test
    fun `always targets the ball's x position`() {
        val decision = SimpleAi.decide(blobAt(Court.OPPONENT_HOME_X), ballAt(Court.WIDTH - 0.05f))
        assertEquals(Court.WIDTH - 0.05f, decision.targetX)
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
