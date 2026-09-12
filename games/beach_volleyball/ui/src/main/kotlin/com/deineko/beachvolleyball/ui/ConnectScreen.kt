package com.deineko.beachvolleyball.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.deineko.connect.ConnectionState
import com.deineko.connect.GameConnection
import com.deineko.connect.rememberNearbyPermissionLauncher

enum class ConnectRole { HOST, JOIN }

internal const val BEACH_VOLLEYBALL_GAME_ID = "beach_volleyball"

@Composable
fun ConnectScreen(role: ConnectRole, connection: GameConnection, onConnected: () -> Unit, onCancel: () -> Unit) {
    var permissionDenied by remember { mutableStateOf(false) }
    var connectionState by remember { mutableStateOf<ConnectionState>(ConnectionState.Idle) }

    val requestPermissions = rememberNearbyPermissionLauncher { granted ->
        if (granted) {
            when (role) {
                ConnectRole.HOST -> connection.host(BEACH_VOLLEYBALL_GAME_ID, "Гравець")
                ConnectRole.JOIN -> connection.join(BEACH_VOLLEYBALL_GAME_ID, "Гравець")
            }
        } else {
            permissionDenied = true
        }
    }

    LaunchedEffect(role) { requestPermissions() }

    LaunchedEffect(connection) {
        connection.state.collect { state ->
            connectionState = state
            if (state is ConnectionState.Connected) onConnected()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(BeachPalette.skyTop).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when {
            permissionDenied -> Text(
                text = "Потрібен дозвіл на Bluetooth, щоб знайти друга поруч",
                textAlign = TextAlign.Center,
            )
            else -> {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = when (connectionState) {
                        is ConnectionState.Hosting -> "Шукаємо друга поруч..."
                        is ConnectionState.Discovering -> "Шукаємо гру поруч..."
                        is ConnectionState.Failed -> (connectionState as ConnectionState.Failed).reason
                        else -> "Готуємось..."
                    },
                    textAlign = TextAlign.Center,
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        TextButton(onClick = { connection.stop(); onCancel() }) { Text("Скасувати") }
    }
}
