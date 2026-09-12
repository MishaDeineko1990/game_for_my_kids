package com.deineko.kidsgames.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.deineko.kidsgames.catalog.Catalog
import com.deineko.kidsgames.catalog.CatalogRepository
import com.deineko.kidsgames.games.GameInfo
import com.deineko.kidsgames.games.GameRegistry
import com.deineko.kidsgames.installed.InstalledGamesStore
import com.deineko.kidsgames.update.UpdateManager
import com.deineko.kidsgames.update.UpdateState
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(onPlay: (String) -> Unit) {
    val context = LocalContext.current
    val installedStore = remember { InstalledGamesStore(context) }
    val updateManager = remember { UpdateManager(context) }
    val scope = rememberCoroutineScope()

    var catalog by remember { mutableStateOf<Catalog?>(null) }
    var updateState by remember { mutableStateOf<UpdateState>(UpdateState.UpToDate) }
    val installedState = remember {
        mutableStateMapOf<String, Boolean>().apply {
            GameRegistry.games.forEach { put(it.id, installedStore.isInstalled(it.id)) }
        }
    }

    LaunchedEffect(Unit) {
        catalog = CatalogRepository.fetch()
        val hubInfo = catalog?.hub
        if (hubInfo != null && updateManager.isNewer(hubInfo)) {
            updateState = UpdateState.Available(hubInfo)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp, 28.dp, 20.dp, 8.dp)) {
            Text("Kids games", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                text = "Ігри для наших малят",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        when (val state = updateState) {
            is UpdateState.Available -> UpdateBanner(
                versionName = state.info.versionName,
                onInstall = {
                    scope.launch {
                        updateState = UpdateState.Downloading(0)
                        runCatching {
                            val file = updateManager.download(state.info) { progress ->
                                updateState = UpdateState.Downloading(progress)
                            }
                            updateState = UpdateState.ReadyToInstall(file)
                            updateManager.promptInstall(file)
                        }.onFailure {
                            updateState = UpdateState.Failed(it.message ?: "Помилка завантаження")
                        }
                    }
                },
            )
            is UpdateState.Downloading -> LinearProgressIndicator(
                progress = { state.progress / 100f },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            )
            is UpdateState.Failed -> Text(
                text = state.message,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            else -> Unit
        }

        LazyColumn(
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(GameRegistry.games) { game ->
                val installed = installedState[game.id] ?: true
                GameCard(
                    game = game,
                    installed = installed,
                    onPlay = { onPlay(game.id) },
                    onToggleInstalled = {
                        val next = !installed
                        installedStore.setInstalled(game.id, next)
                        installedState[game.id] = next
                    },
                )
            }
        }
    }
}

@Composable
private fun GameCard(game: GameInfo, installed: Boolean, onPlay: () -> Unit, onToggleInstalled: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(game.accent)),
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(game.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    text = game.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "${game.levelCount} рівнів",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onPlay, enabled = installed, modifier = Modifier.weight(1f)) {
                Text("Грати")
            }
            OutlinedButton(onClick = onToggleInstalled, modifier = Modifier.weight(1f)) {
                Text(if (installed) "Видалити" else "Встановити")
            }
        }
    }
}

@Composable
private fun UpdateBanner(versionName: String, onInstall: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(14.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Доступне оновлення $versionName",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = "Натисніть, щоб завантажити й встановити",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        Button(onClick = onInstall) { Text("Оновити") }
    }
}
