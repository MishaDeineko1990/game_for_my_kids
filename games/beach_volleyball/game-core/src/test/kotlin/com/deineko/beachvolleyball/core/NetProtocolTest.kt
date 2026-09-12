package com.deineko.beachvolleyball.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class NetProtocolTest {

    @Test
    fun `an input with a target x round-trips through encode and decode`() {
        val input = BlobInput(targetX = 0.42f, jump = true)
        val decoded = NetProtocol.decode(NetProtocol.encodeInput(input))
        val message = assertIs<NetProtocol.Message.InputUpdate>(decoded)
        assertEquals(input, message.input)
    }

    @Test
    fun `an input with no target x round-trips as null, not zero`() {
        val decoded = NetProtocol.decode(NetProtocol.encodeInput(BlobInput.NONE))
        val message = assertIs<NetProtocol.Message.InputUpdate>(decoded)
        assertEquals(null, message.input.targetX)
    }

    private fun sampleState() = MatchState(
        ball = BallState(x = 0.5f, y = 0.9f, vx = 0.1f, vy = -0.2f),
        player = BlobState(x = 0.3f, y = 0.1f, vy = 0.4f),
        opponent = BlobState(x = 0.7f, y = 0f, vy = 0f),
        playerScore = 3,
        opponentScore = 7,
        serving = Side.OPPONENT,
        isFinished = false,
    )

    @Test
    fun `state update round-trips through encode and decode`() {
        val state = sampleState()
        val decoded = NetProtocol.decode(NetProtocol.encodeState(state))
        val message = assertIs<NetProtocol.Message.StateUpdate>(decoded)
        assertEquals(state, message.state)
    }

    @Test
    fun `mirroring a state twice returns the original`() {
        val state = sampleState()
        assertEquals(state, state.mirrored().mirrored())
    }

    @Test
    fun `mirroring swaps which side is player vs opponent`() {
        val state = MatchState.initial(serving = Side.PLAYER).copy(playerScore = 2, opponentScore = 5)
        val mirrored = state.mirrored()
        assertEquals(state.opponentScore, mirrored.playerScore)
        assertEquals(state.playerScore, mirrored.opponentScore)
        assertEquals(Side.OPPONENT, mirrored.serving)
        assertEquals(Court.WIDTH - state.opponent.x, mirrored.player.x)
    }

    @Test
    fun `unknown message prefix decodes to null`() {
        assertEquals(null, NetProtocol.decode("X,garbage".toByteArray()))
    }
}
