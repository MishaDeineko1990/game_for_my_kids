package com.deineko.kidsgames.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.deineko.kidsgames.catalog.Catalog
import com.deineko.kidsgames.catalog.CatalogRepository
import com.deineko.kidsgames.catalog.HubUpdateInfo
import com.deineko.kidsgames.games.AgeGroup
import com.deineko.kidsgames.games.GameCategory
import com.deineko.kidsgames.games.GameInfo
import com.deineko.kidsgames.games.GameRegistry
import com.deineko.kidsgames.installed.InstalledGamesStore
import com.deineko.kidsgames.update.UpdateManager
import com.deineko.kidsgames.update.UpdateState
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(onPlay: (String) -> Unit, onSettings: () -> Unit) {
    val context = LocalContext.current
    val installedStore = remember { InstalledGamesStore(context) }
    val updateManager = remember { UpdateManager(context) }
    val scope = rememberCoroutineScope()

    var catalog by remember { mutableStateOf<Catalog?>(null) }
    var updateState by remember { mutableStateOf<UpdateState>(UpdateState.Idle) }
    val installedState = remember {
        mutableStateMapOf<String, Boolean>().apply {
            GameRegistry.games.forEach { put(it.id, installedStore.isInstalled(it.id)) }
        }
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedAgeGroup by remember { mutableStateOf<AgeGroup?>(null) }
    var selectedCategory by remember { mutableStateOf<GameCategory?>(null) }

    LaunchedEffect(Unit) {
        val fetched = CatalogRepository.fetch()
        catalog = fetched
        updateState = updateManager.evaluate(fetched)
    }

    fun startHubUpdate(info: HubUpdateInfo) {
        scope.launch {
            updateState = UpdateState.Downloading(0)
            runCatching {
                val file = updateManager.download(info) { progress -> updateState = UpdateState.Downloading(progress) }
                updateState = UpdateState.ReadyToInstall(file)
                updateManager.promptInstall(file)
            }.onFailure {
                updateState = UpdateState.Failed(it.message ?: "Помилка завантаження")
            }
        }
    }

    val catalogVersions = catalog?.games?.associate { it.id to it.contentVersion } ?: emptyMap()

    val filteredGames = GameRegistry.games.filter { game ->
        (selectedAgeGroup == null || game.ageGroup == selectedAgeGroup) &&
            (selectedCategory == null || game.category == selectedCategory) &&
            (searchQuery.isBlank() || game.title.contains(searchQuery, ignoreCase = true))
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp, 28.dp, 20.dp, 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column {
                Text("Kids games", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    text = "Ігри для наших малят",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "⚙",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable(onClick = onSettings),
            )
        }

        when (val state = updateState) {
            is UpdateState.Available -> UpdateBanner(
                versionName = state.info.versionName,
                onInstall = { startHubUpdate(state.info) },
            )
            is UpdateState.Downloading -> LinearProgressIndicator(
                progress = { state.progress / 100f },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            )
            is UpdateState.Failed -> Text(
                text = state.message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            else -> Unit
        }

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Пошук гри...") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        )

        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = selectedAgeGroup == null,
                onClick = { selectedAgeGroup = null },
                label = { Text("Усі віки") },
            )
            AgeGroup.entries.forEach { age ->
                FilterChip(
                    selected = selectedAgeGroup == age,
                    onClick = { selectedAgeGroup = if (selectedAgeGroup == age) null else age },
                    label = { Text(age.label) },
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = selectedCategory == null,
                onClick = { selectedCategory = null },
                label = { Text("Усі види") },
            )
            GameCategory.entries.forEach { category ->
                FilterChip(
                    selected = selectedCategory == category,
                    onClick = { selectedCategory = if (selectedCategory == category) null else category },
                    label = { Text(category.label) },
                )
            }
        }

        if (filteredGames.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = "Нічого не знайдено",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(filteredGames) { game ->
                    val installed = installedState[game.id] ?: true
                    val updateAvailable = (catalogVersions[game.id] ?: game.bundledVersion) > game.bundledVersion
                    GameCard(
                        game = game,
                        installed = installed,
                        updateAvailable = updateAvailable,
                        onPlay = { onPlay(game.id) },
                        onInstall = {
                            installedStore.setInstalled(game.id, true)
                            installedState[game.id] = true
                        },
                        onUninstall = {
                            installedStore.setInstalled(game.id, false)
                            installedState[game.id] = false
                        },
                        onUpdate = { catalog?.hub?.let(::startHubUpdate) },
                    )
                }
            }
        }
    }
}

@Composable
private fun GameCard(
    game: GameInfo,
    installed: Boolean,
    updateAvailable: Boolean,
    onPlay: () -> Unit,
    onInstall: () -> Unit,
    onUninstall: () -> Unit,
    onUpdate: () -> Unit,
) {
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
                    .background(Color(game.accent))
                    .alpha(if (installed) 1f else 0.4f),
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f).alpha(if (installed) 1f else 0.5f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(game.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (!installed) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "видалено",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    text = game.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (game.levelCount > 0) {
                    Text(
                        text = "${game.levelCount} рівнів",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        val (primaryLabel, primaryAction) = when {
            installed && updateAvailable -> "Оновити" to onUpdate
            !installed -> "Встановити" to onInstall
            else -> "Грати" to onPlay
        }
        Button(onClick = primaryAction, modifier = Modifier.fillMaxWidth()) {
            Text(primaryLabel)
        }
        if (installed) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Видалити",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clickable(onClick = onUninstall),
            )
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
