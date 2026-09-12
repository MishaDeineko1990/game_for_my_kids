package com.deineko.connect

import android.content.Context
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Wraps Google Nearby Connections (P2P_STAR: exactly one host, one joiner -- all a two-player
 * game needs) behind a tiny host/join/send/messages surface. Auto-accepts the first incoming
 * connection with no pairing-code confirmation screen, since the target players can't read one --
 * this is a "nearby + running the same game" trust model, not a security boundary, and is fine for
 * a casual local kids' game. Every game reuses this same class; `gameId` scopes discovery so two
 * different games advertising at once never see each other.
 */
class NearbyGameConnection(context: Context) : GameConnection {
    private val client: ConnectionsClient = Nearby.getConnectionsClient(context.applicationContext)
    private var connectedEndpointId: String? = null
    private var pendingPeerName: String = ""
    private var outgoingName: String = ""

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<ByteArray>(extraBufferCapacity = 32)
    override val messages: SharedFlow<ByteArray> = _messages.asSharedFlow()

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            payload.asBytes()?.let { _messages.tryEmit(it) }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) = Unit
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            pendingPeerName = info.endpointName
            client.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                connectedEndpointId = endpointId
                client.stopAdvertising()
                client.stopDiscovery()
                _state.value = ConnectionState.Connected(peerName = pendingPeerName)
            } else {
                _state.value = ConnectionState.Failed("Не вдалося з'єднатися")
            }
        }

        override fun onDisconnected(endpointId: String) {
            connectedEndpointId = null
            _state.value = ConnectionState.Disconnected
        }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            pendingPeerName = info.endpointName
            client.requestConnection(outgoingName, endpointId, connectionLifecycleCallback)
        }

        override fun onEndpointLost(endpointId: String) = Unit
    }

    override fun host(gameId: String, displayName: String) {
        outgoingName = displayName
        _state.value = ConnectionState.Hosting
        val options = AdvertisingOptions.Builder().setStrategy(Strategy.P2P_STAR).build()
        client.startAdvertising(displayName, serviceId(gameId), connectionLifecycleCallback, options)
            .addOnFailureListener { _state.value = ConnectionState.Failed(it.message ?: "Помилка хостингу") }
    }

    override fun join(gameId: String, displayName: String) {
        outgoingName = displayName
        _state.value = ConnectionState.Discovering
        val options = DiscoveryOptions.Builder().setStrategy(Strategy.P2P_STAR).build()
        client.startDiscovery(serviceId(gameId), endpointDiscoveryCallback, options)
            .addOnFailureListener { _state.value = ConnectionState.Failed(it.message ?: "Помилка пошуку") }
    }

    override fun send(bytes: ByteArray) {
        val endpointId = connectedEndpointId ?: return
        client.sendPayload(endpointId, Payload.fromBytes(bytes))
    }

    override fun stop() {
        client.stopAdvertising()
        client.stopDiscovery()
        client.stopAllEndpoints()
        connectedEndpointId = null
        _state.value = ConnectionState.Idle
    }

    private fun serviceId(gameId: String) = "com.deineko.kidsgames.$gameId"
}
