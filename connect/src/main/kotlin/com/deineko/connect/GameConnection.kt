package com.deineko.connect

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Game-agnostic two-device link. Every game that supports a local "play with a friend" mode
 * reuses this same interface and just picks its own [host]/[join] `gameId`; the wire format of
 * the bytes exchanged over [send]/[messages] is entirely up to each game.
 */
interface GameConnection {
    val state: StateFlow<ConnectionState>
    val messages: SharedFlow<ByteArray>

    /** Advertises this device as a host for [gameId] and auto-accepts the first peer that joins. */
    fun host(gameId: String, displayName: String)

    /** Searches for a device hosting [gameId] and auto-connects to the first one found. */
    fun join(gameId: String, displayName: String)

    fun send(bytes: ByteArray)

    fun stop()
}
