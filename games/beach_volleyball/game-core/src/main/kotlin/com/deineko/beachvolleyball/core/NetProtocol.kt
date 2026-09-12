package com.deineko.beachvolleyball.core

/** Minimal text wire protocol for the host-authoritative link, sent over a
 *  `com.deineko.connect.GameConnection`. Plain comma-separated ASCII, not a binary format --
 *  payloads are tiny and infrequent, so readability while debugging matters more than a few bytes.
 *  The host runs physics and streams [Message.StateUpdate]; the joiner only ever sends its own
 *  paddle position as [Message.PaddleUpdate] and renders whatever state it last received. */
object NetProtocol {
    sealed class Message {
        data class PaddleUpdate(val x: Float) : Message()
        data class StateUpdate(val state: MatchState) : Message()
    }

    fun encodePaddle(x: Float): ByteArray = "P,$x".toByteArray()

    fun encodeState(state: MatchState): ByteArray {
        val b = state.ball
        return listOf(
            "S", b.x, b.y, b.z, b.vx, b.vy, b.vz,
            state.playerX, state.opponentX, state.playerScore, state.opponentScore,
            state.serving.name, state.isFinished,
        ).joinToString(",").toByteArray()
    }

    fun decode(bytes: ByteArray): Message? = runCatching {
        val parts = String(bytes).split(",")
        when (parts[0]) {
            "P" -> Message.PaddleUpdate(parts[1].toFloat())
            "S" -> Message.StateUpdate(
                MatchState(
                    ball = BallState(
                        x = parts[1].toFloat(),
                        y = parts[2].toFloat(),
                        z = parts[3].toFloat(),
                        vx = parts[4].toFloat(),
                        vy = parts[5].toFloat(),
                        vz = parts[6].toFloat(),
                    ),
                    playerX = parts[7].toFloat(),
                    opponentX = parts[8].toFloat(),
                    playerScore = parts[9].toInt(),
                    opponentScore = parts[10].toInt(),
                    serving = Side.valueOf(parts[11]),
                    isFinished = parts[12].toBoolean(),
                ),
            )
            else -> null
        }
    }.getOrNull()
}
