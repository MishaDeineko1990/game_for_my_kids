package com.deineko.beachvolleyball.core

/** Minimal text wire protocol for the host-authoritative link, sent over a
 *  `com.deineko.connect.GameConnection`. Plain comma-separated ASCII, not a binary format --
 *  payloads are tiny and infrequent, so readability while debugging matters more than a few bytes.
 *  The host runs physics and streams [Message.StateUpdate]; the joiner only ever sends its own
 *  desired [BlobInput] as [Message.InputUpdate] and renders whatever state it last received. */
object NetProtocol {
    sealed class Message {
        data class InputUpdate(val input: BlobInput) : Message()
        data class StateUpdate(val state: MatchState) : Message()
    }

    fun encodeInput(input: BlobInput): ByteArray = "I,${input.moveDirection},${input.jump}".toByteArray()

    fun encodeState(state: MatchState): ByteArray {
        val b = state.ball
        val p = state.player
        val o = state.opponent
        return listOf(
            "S", b.x, b.y, b.vx, b.vy,
            p.x, p.y, p.vy,
            o.x, o.y, o.vy,
            state.playerScore, state.opponentScore, state.serving.name, state.isFinished,
        ).joinToString(",").toByteArray()
    }

    fun decode(bytes: ByteArray): Message? = runCatching {
        val parts = String(bytes).split(",")
        when (parts[0]) {
            "I" -> Message.InputUpdate(BlobInput(moveDirection = parts[1].toInt(), jump = parts[2].toBoolean()))
            "S" -> Message.StateUpdate(
                MatchState(
                    ball = BallState(
                        x = parts[1].toFloat(),
                        y = parts[2].toFloat(),
                        vx = parts[3].toFloat(),
                        vy = parts[4].toFloat(),
                    ),
                    player = BlobState(x = parts[5].toFloat(), y = parts[6].toFloat(), vy = parts[7].toFloat()),
                    opponent = BlobState(x = parts[8].toFloat(), y = parts[9].toFloat(), vy = parts[10].toFloat()),
                    playerScore = parts[11].toInt(),
                    opponentScore = parts[12].toInt(),
                    serving = Side.valueOf(parts[13]),
                    isFinished = parts[14].toBoolean(),
                ),
            )
            else -> null
        }
    }.getOrNull()
}
