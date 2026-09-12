package com.deineko.beachvolleyball.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.deineko.beachvolleyball.core.BallSpeed

@Composable
fun ModeSelectScreen(
    onPlayPc: () -> Unit,
    onHost: () -> Unit,
    onJoin: () -> Unit,
    onExitToHub: () -> Unit,
) {
    val context = LocalContext.current
    val settingsStore = remember { SettingsStore(context) }
    var speed by remember { mutableStateOf(settingsStore.ballSpeed()) }
    var showSettings by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(BeachPalette.skyTop),
    ) {
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight().padding(28.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "← Ігри",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.clickable(onClick = onExitToHub),
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Пляжний волейбол",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Перебивайте м'ячик через сітку!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Column(
            modifier = Modifier.weight(1f).fillMaxHeight().padding(28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Button(onClick = onPlayPc, modifier = Modifier.fillMaxWidth()) {
                Text("Проти комп'ютера")
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onHost, modifier = Modifier.fillMaxWidth()) {
                Text("Створити гру для друга")
            }
            Spacer(modifier = Modifier.height(12.dp))
            Button(onClick = onJoin, modifier = Modifier.fillMaxWidth()) {
                Text("Приєднатися до друга")
            }
            Spacer(modifier = Modifier.height(20.dp))
            OutlinedButton(onClick = { showSettings = true }, modifier = Modifier.fillMaxWidth()) {
                Text("⚙ Швидкість м'ячика")
            }
        }
    }

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            confirmButton = {
                TextButton(onClick = { showSettings = false }) { Text("Готово") }
            },
            title = { Text("Швидкість м'ячика") },
            text = {
                Column {
                    BallSpeed.entries.forEach { option ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    speed = option
                                    settingsStore.setBallSpeed(option)
                                }
                                .padding(vertical = 6.dp),
                        ) {
                            RadioButton(
                                selected = speed == option,
                                onClick = {
                                    speed = option
                                    settingsStore.setBallSpeed(option)
                                },
                            )
                            Text(speedLabel(option))
                        }
                    }
                }
            },
        )
    }
}

private fun speedLabel(speed: BallSpeed): String = when (speed) {
    BallSpeed.SLOW -> "Повільно (для малят)"
    BallSpeed.NORMAL -> "Звичайно"
    BallSpeed.FAST -> "Швидко"
}
