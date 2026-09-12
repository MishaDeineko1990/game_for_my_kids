package com.deineko.beachvolleyball.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SimpleAiTest {

    private fun ballAt(x: Float) = BallState(x = x, y = Court.NET_Y, z = 1f, vx = 0f, vy = 0f, vz = 0f)

    @Test
    fun `moves toward the ball's x position`() {
        val next = SimpleAi.nextOpponentX(currentX = 0.2f, ball = ballAt(0.8f), dtSeconds = 0.1f)
        assertTrue(next > 0.2f, "should move toward the ball")
        assertTrue(next < 0.8f, "should not teleport to the ball in one small step")
    }

    @Test
    fun `does not overshoot a small delta`() {
        val next = SimpleAi.nextOpponentX(currentX = 0.5f, ball = ballAt(0.505f), dtSeconds = 0.1f)
        assertEquals(0.5f, next, "a delta smaller than the dead zone should not move the paddle")
    }

    @Test
    fun `stays within the court bounds`() {
        val next = SimpleAi.nextOpponentX(currentX = 0.05f, ball = ballAt(0f), dtSeconds = 10f)
        assertTrue(next >= Court.PADDLE_RADIUS, "paddle center should never leave the playable court")
    }
}
