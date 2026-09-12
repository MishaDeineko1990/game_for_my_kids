package com.deineko.beachvolleyball.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BeachVolleyballEngineTest {

    private fun stillState(x: Float = Court.WIDTH / 2f, y: Float = Court.NET_Y, z: Float = 1f) = MatchState(
        ball = BallState(x = x, y = y, z = z, vx = 0f, vy = 0f, vz = 0f),
        playerX = Court.WIDTH / 2f,
        opponentX = Court.WIDTH / 2f,
    )

    @Test
    fun `gravity pulls the ball down over time`() {
        val first = BeachVolleyballEngine.step(stillState(z = 1f), dtSeconds = 0.1f, speed = BallSpeed.NORMAL)
        assertTrue(first.state.ball.vz < 0f, "vz should become negative under gravity")
        val second = BeachVolleyballEngine.step(first.state, dtSeconds = 0.1f, speed = BallSpeed.NORMAL)
        assertTrue(second.state.ball.z < first.state.ball.z, "z should decrease once vz is negative")
    }

    @Test
    fun `ball bounces off the left wall`() {
        val state = stillState(x = Court.BALL_RADIUS + 0.001f, z = 1f).let {
            it.copy(ball = it.ball.copy(vx = -0.5f))
        }
        val result = BeachVolleyballEngine.step(state, dtSeconds = 0.1f, speed = BallSpeed.NORMAL)
        assertTrue(result.state.ball.vx > 0f, "vx should reverse after hitting the left wall")
        assertTrue(result.state.ball.x >= Court.BALL_RADIUS, "ball should be clamped inside the court")
    }

    @Test
    fun `a low ball crossing the net bounces back without scoring`() {
        val state = stillState(y = Court.NET_Y - 0.01f, z = 0.1f).let {
            it.copy(ball = it.ball.copy(vy = 0.9f))
        }
        val result = BeachVolleyballEngine.step(state, dtSeconds = 0.05f, speed = BallSpeed.NORMAL)
        assertTrue(result.events.netFault, "a low crossing attempt should be flagged as a net fault")
        assertFalse(result.events.scored, "a net fault must not score a point")
        assertEquals(0, result.state.playerScore)
        assertEquals(0, result.state.opponentScore)
    }

    @Test
    fun `a high ball clears the net without bouncing`() {
        val state = stillState(y = Court.NET_Y - 0.01f, z = Court.NET_CLEAR_HEIGHT + 0.1f).let {
            it.copy(ball = it.ball.copy(vy = 0.9f))
        }
        val result = BeachVolleyballEngine.step(state, dtSeconds = 0.05f, speed = BallSpeed.NORMAL)
        assertFalse(result.events.netFault, "a ball above the net's clear height should not fault")
        assertTrue(result.state.ball.y > Court.NET_Y - 0.01f, "ball should have advanced across the net")
    }

    @Test
    fun `player paddle returns an approaching low ball`() {
        val state = stillState(y = Court.PLAYER_BASELINE_Y, z = 0.1f).let {
            it.copy(ball = it.ball.copy(vy = 0.6f, vz = -0.5f), playerX = Court.WIDTH / 2f)
        }
        val result = BeachVolleyballEngine.step(state, dtSeconds = 0.01f, speed = BallSpeed.NORMAL)
        assertTrue(result.events.playerHit, "ball within reach and low enough should be returned")
        assertTrue(result.state.ball.vy < 0f, "returned ball should now head back toward the opponent")
    }

    @Test
    fun `opponent paddle returns an approaching low ball`() {
        val state = stillState(y = Court.OPPONENT_BASELINE_Y, z = 0.1f).let {
            it.copy(ball = it.ball.copy(vy = -0.6f, vz = -0.5f), opponentX = Court.WIDTH / 2f)
        }
        val result = BeachVolleyballEngine.step(state, dtSeconds = 0.01f, speed = BallSpeed.NORMAL)
        assertTrue(result.events.opponentHit, "ball within reach and low enough should be returned")
        assertTrue(result.state.ball.vy > 0f, "returned ball should now head back toward the player")
    }

    @Test
    fun `ball landing on the player's side scores for the opponent`() {
        val state = stillState(y = Court.PLAYER_BASELINE_Y, z = 0.01f).let {
            it.copy(ball = it.ball.copy(vy = 0f, vz = -1f))
        }
        val result = BeachVolleyballEngine.step(state, dtSeconds = 0.02f, speed = BallSpeed.NORMAL)
        assertTrue(result.events.scored)
        assertEquals(1, result.state.opponentScore)
        assertEquals(0, result.state.playerScore)
        assertEquals(Side.OPPONENT, result.state.serving, "the side that won the rally serves next")
    }

    @Test
    fun `ball landing on the opponent's side scores for the player`() {
        val state = stillState(y = Court.OPPONENT_BASELINE_Y, z = 0.01f).let {
            it.copy(ball = it.ball.copy(vy = 0f, vz = -1f))
        }
        val result = BeachVolleyballEngine.step(state, dtSeconds = 0.02f, speed = BallSpeed.NORMAL)
        assertTrue(result.events.scored)
        assertEquals(1, result.state.playerScore)
        assertEquals(0, result.state.opponentScore)
    }

    @Test
    fun `match finishes once a side reaches the win score`() {
        var state = stillState(y = Court.OPPONENT_BASELINE_Y, z = 0.01f).copy(playerScore = Court.WIN_SCORE - 1)
        state = state.copy(ball = state.ball.copy(vy = 0f, vz = -1f))
        val result = BeachVolleyballEngine.step(state, dtSeconds = 0.02f, speed = BallSpeed.NORMAL)
        assertEquals(Court.WIN_SCORE, result.state.playerScore)
        assertTrue(result.state.isFinished)
    }

    @Test
    fun `a finished match no longer advances`() {
        val state = stillState().copy(isFinished = true)
        val result = BeachVolleyballEngine.step(state, dtSeconds = 1f, speed = BallSpeed.NORMAL)
        assertEquals(state, result.state)
    }

    @Test
    fun `slow speed advances the ball less per real second than fast speed`() {
        val falling = stillState(z = 1f).let { it.copy(ball = it.ball.copy(vz = -1f)) }
        val slow = BeachVolleyballEngine.step(falling, dtSeconds = 0.1f, speed = BallSpeed.SLOW)
        val fast = BeachVolleyballEngine.step(falling, dtSeconds = 0.1f, speed = BallSpeed.FAST)
        val slowDrop = 1f - slow.state.ball.z
        val fastDrop = 1f - fast.state.ball.z
        assertTrue(slowDrop < fastDrop, "SLOW should move the ball less per real second than FAST")
    }
}
