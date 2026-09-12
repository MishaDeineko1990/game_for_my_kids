package com.deineko.connect

/** Lifecycle of a [GameConnection]. UI screens should render one state at a time. */
sealed class ConnectionState {
    data object Idle : ConnectionState()
    data object Hosting : ConnectionState()
    data object Discovering : ConnectionState()
    data class Connected(val peerName: String) : ConnectionState()
    data class Failed(val reason: String) : ConnectionState()
    data object Disconnected : ConnectionState()
}
