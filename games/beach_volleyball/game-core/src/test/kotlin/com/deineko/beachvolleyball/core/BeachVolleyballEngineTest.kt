package com.deineko.beachvolleyball.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BeachVolleyballEngineTest {

    private fun stillState(
        ballX: Float = Court.PLAYER_HOME_X,
        ballY: Float = 1f,
        playerX: Float = Court.PLAYER_HOME_X,
        opponentX: Float = Court.OPPONENT_HOME_X,
    ) = MatchState(
        ball = BallState(x = ballX, y = ballY, vx = 0f, vy = 0f),
        player = BlobState(x = playerX, y = Court.GROUND_Y, vy = 0f),
        opponent = BlobState(x = opponentX, y = Court.GROUND_Y, vy = 0f),
    )

    private fun step(state: MatchState, dt: Float, player: BlobInput = BlobInput.NONE, opponent: BlobInput = BlobInput.NONE) =
        BeachVolleyballEngine.step(state, dt, player, opponent, BallSpeed.NORMAL)

    @Test
    fun `gravity pulls the ball down over time`() {
        val first = step(stillState(ballY = 1f), dt = 0.1f)
        assertTrue(first.state.ball.vy < 0f, "vy should become negative under gravity")
        val second = step(first.state, dt = 0.1f)
        assertTrue(second.state.ball.y < first.state.ball.y, "y should decrease once vy is negative")
    }

    @Test
    fun `jumping blob rises then gravity brings it back down`() {
        val jumped = step(stillState(), dt = 0.05f, player = BlobInput(0, jump = true))
        assertTrue(jumped.state.player.y > 0f, "a jump should lift the blob off the ground")
        assertTrue(jumped.state.player.vy > 0f)
    }

    @Test
    fun `a grounded blob cannot jump again mid-air without landing first`() {
        val airborne = step(stillState(), dt = 0.05f, player = BlobInput(0, jump = true)).state
        val stillJumping = step(airborne, dt = 0.05f, player = BlobInput(0, jump = true))
        // vy should keep decreasing under gravity, not reset to JUMP_VELOCITY, since the blob never landed.
        assertTrue(stillJumping.state.player.vy < airborne.player.vy)
    }

    @Test
    fun `moving toward the net is blocked at the net`() {
        val nearNet = stillState(playerX = Court.NET_X - Court.NET_HALF_WIDTH - Court.BLOB_RADIUS)
        val result = step(nearNet, dt = 1f, player = BlobInput(moveDirection = 1, jump = false))
        assertTrue(result.state.player.x <= Court.NET_X - Court.NET_HALF_WIDTH - Court.BLOB_RADIUS + 0.0001f)
    }

    @Test
    fun `a low ball crossing the net bounces back`() {
        val state = stillState(ballX = Court.NET_X - 0.01f, ballY = 0.05f).let {
            it.copy(ball = it.ball.copy(vx = 0.9f))
        }
        val result = step(state, dt = 0.05f)
        assertTrue(result.events.netBounce, "a low crossing attempt should bounce off the net")
        assertFalse(result.events.scored, "a net bounce must not score a point")
    }

    @Test
    fun `a high ball clears the net without bouncing`() {
        val state = stillState(ballX = Court.NET_X - 0.01f, ballY = Court.NET_HEIGHT + 0.1f).let {
            it.copy(ball = it.ball.copy(vx = 0.9f))
        }
        val result = step(state, dt = 0.05f)
        assertFalse(result.events.netBounce, "a ball above the net's height should not bounce")
    }

    @Test
    fun `a ball approaching the player's blob is returned`() {
        val state = stillState(ballX = Court.PLAYER_HOME_X, ballY = Court.BLOB_RADIUS).let {
            it.copy(ball = it.ball.copy(vy = -0.5f))
        }
        val result = step(state, dt = 0.02f)
        assertTrue(result.events.playerHit, "ball within reach of the player's blob should be returned")
        assertTrue(result.state.ball.vy > 0f, "returned ball should now head upward")
    }

    @Test
    fun `a ball approaching the opponent's blob is returned`() {
        val state = stillState(ballX = Court.OPPONENT_HOME_X, ballY = Court.BLOB_RADIUS).let {
            it.copy(ball = it.ball.copy(vy = -0.5f))
        }
        val result = step(state, dt = 0.02f)
        assertTrue(result.events.opponentHit, "ball within reach of the opponent's blob should be returned")
        assertTrue(result.state.ball.vy > 0f)
    }

    // Offset well clear of either blob's home x so the falling ball doesn't collide with a blob
    // standing at its home position on the way down -- these tests are about the ground/scoring
    // check, not the collision check.
    private val clearOfBlobsPlayerSideX = Court.PLAYER_HOME_X + 0.15f
    private val clearOfBlobsOpponentSideX = Court.OPPONENT_HOME_X - 0.15f

    @Test
    fun `ball touching ground on the player's side scores for the opponent`() {
        val state = stillState(ballX = clearOfBlobsPlayerSideX, ballY = 0.01f).let {
            it.copy(ball = it.ball.copy(vy = -1f))
        }
        val result = step(state, dt = 0.02f)
        assertTrue(result.events.scored)
        assertEquals(1, result.state.opponentScore)
        assertEquals(0, result.state.playerScore)
        assertEquals(Side.OPPONENT, result.state.serving, "the side that won the rally serves next")
    }

    @Test
    fun `ball touching ground on the opponent's side scores for the player`() {
        val state = stillState(ballX = clearOfBlobsOpponentSideX, ballY = 0.01f).let {
            it.copy(ball = it.ball.copy(vy = -1f))
        }
        val result = step(state, dt = 0.02f)
        assertTrue(result.events.scored)
        assertEquals(1, result.state.playerScore)
        assertEquals(0, result.state.opponentScore)
    }

    @Test
    fun `match finishes once a side reaches the win score`() {
        val state = stillState(ballX = clearOfBlobsOpponentSideX, ballY = 0.01f)
            .copy(playerScore = Court.WIN_SCORE - 1)
            .let { it.copy(ball = it.ball.copy(vy = -1f)) }
        val result = step(state, dt = 0.02f)
        assertEquals(Court.WIN_SCORE, result.state.playerScore)
        assertTrue(result.state.isFinished)
    }

    @Test
    fun `a finished match no longer advances`() {
        val state = stillState().copy(isFinished = true)
        val result = step(state, dt = 1f)
        assertEquals(state, result.state)
    }

    @Test
    fun `slow speed advances the ball less per real second than fast speed`() {
        val falling = stillState(ballY = 1f).let { it.copy(ball = it.ball.copy(vy = -1f)) }
        val slow = BeachVolleyballEngine.step(falling, 0.1f, BlobInput.NONE, BlobInput.NONE, BallSpeed.SLOW)
        val fast = BeachVolleyballEngine.step(falling, 0.1f, BlobInput.NONE, BlobInput.NONE, BallSpeed.FAST)
        val slowDrop = 1f - slow.state.ball.y
        val fastDrop = 1f - fast.state.ball.y
        assertTrue(slowDrop < fastDrop, "SLOW should move the ball less per real second than FAST")
    }
}
