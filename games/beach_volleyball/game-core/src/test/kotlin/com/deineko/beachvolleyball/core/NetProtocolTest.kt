package com.deineko.beachvolleyball.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class NetProtocolTest {

    @Test
    fun `paddle update round-trips through encode and decode`() {
        val decoded = NetProtocol.decode(NetProtocol.encodePaddle(0.42f))
        val message = assertIs<NetProtocol.Message.PaddleUpdate>(decoded)
        assertEquals(0.42f, message.x)
    }

    @Test
    fun `state update round-trips through encode and decode`() {
        val state = MatchState(
            ball = BallState(x = 0.5f, y = 0.9f, z = 0.3f, vx = 0.1f, vy = -0.2f, vz = 1.5f),
            playerX = 0.6f,
            opponentX = 0.4f,
            playerScore = 3,
            opponentScore = 7,
            serving = Side.OPPONENT,
            isFinished = false,
        )
        val decoded = NetProtocol.decode(NetProtocol.encodeState(state))
        val message = assertIs<NetProtocol.Message.StateUpdate>(decoded)
        assertEquals(state, message.state)
    }

    @Test
    fun `mirroring a state twice returns the original`() {
        val state = MatchState(
            ball = BallState(x = 0.5f, y = 0.9f, z = 0.3f, vx = 0.1f, vy = -0.2f, vz = 1.5f),
            playerX = 0.6f,
            opponentX = 0.4f,
            playerScore = 3,
            opponentScore = 7,
            serving = Side.PLAYER,
        )
        assertEquals(state, state.mirrored().mirrored())
    }

    @Test
    fun `mirroring swaps which side is player vs opponent`() {
        val state = MatchState.initial(serving = Side.PLAYER).copy(playerScore = 2, opponentScore = 5)
        val mirrored = state.mirrored()
        assertEquals(state.opponentScore, mirrored.playerScore)
        assertEquals(state.playerScore, mirrored.opponentScore)
        assertEquals(Side.OPPONENT, mirrored.serving)
    }

    @Test
    fun `unknown message prefix decodes to null`() {
        assertEquals(null, NetProtocol.decode("X,garbage".toByteArray()))
    }
}
