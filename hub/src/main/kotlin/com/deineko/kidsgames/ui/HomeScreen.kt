package com.deineko.kidsgames.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.StartOffsetType
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
    var selectedGameId by rememberSaveable { mutableStateOf<String?>(null) }

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
    fun isInstalled(game: GameInfo) = installedState[game.id] ?: true
    fun isUpdateAvailable(game: GameInfo) = (catalogVersions[game.id] ?: game.bundledVersion) > game.bundledVersion

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
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 104.dp),
                contentPadding = PaddingValues(20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(filteredGames, key = { _, game -> game.id }) { index, game ->
                    GameTile(
                        game = game,
                        index = index,
                        installed = isInstalled(game),
                        updateAvailable = isUpdateAvailable(game),
                        onClick = { selectedGameId = game.id },
                    )
                }
            }
        }
    }

    val selectedGame = GameRegistry.games.find { it.id == selectedGameId }
    if (selectedGame != null) {
        GameDetailsSheet(
            game = selectedGame,
            installed = isInstalled(selectedGame),
            updateAvailable = isUpdateAvailable(selectedGame),
            onDismiss = { selectedGameId = null },
            // Clear the selection first: NavHost keeps this screen's saveable state on the back
            // stack, so a still-set id would reopen the sheet when the child returns from the game.
            onPlay = {
                selectedGameId = null
                onPlay(selectedGame.id)
            },
            onInstall = {
                installedStore.setInstalled(selectedGame.id, true)
                installedState[selectedGame.id] = true
            },
            onUninstall = {
                installedStore.setInstalled(selectedGame.id, false)
                installedState[selectedGame.id] = false
            },
            onUpdate = { catalog?.hub?.let(::startHubUpdate) },
        )
    }
}

/** One tile in the home grid: a floating, springy icon with the title underneath. Tapping it
 *  opens [GameDetailsSheet] via [onClick]; no description or buttons live on the tile itself. */
@Composable
private fun GameTile(
    game: GameInfo,
    index: Int,
    installed: Boolean,
    updateAvailable: Boolean,
    onClick: () -> Unit,
) {
    val density = LocalDensity.current
    val infiniteTransition = rememberInfiniteTransition(label = "tile-float")
    val floatOffsetDp by infiniteTransition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600 + (index % 5) * 180, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset(offsetMillis = index * 140, offsetType = StartOffsetType.FastForward),
        ),
        label = "tile-float-offset",
    )

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "tile-press-scale",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(4.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            GameIcon(
                game = game,
                installed = installed,
                size = 88.dp,
                cornerRadius = 22.dp,
                modifier = Modifier
                    .graphicsLayer {
                        translationY = with(density) { floatOffsetDp.dp.toPx() }
                        rotationZ = floatOffsetDp * 0.6f
                        scaleX = pressScale
                        scaleY = pressScale
                    }
                    .shadow(4.dp, RoundedCornerShape(22.dp), clip = false),
            )
            if (!installed) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                        .padding(horizontal = 6.dp, vertical = 1.dp),
                ) {
                    Text(
                        text = "видалено",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (updateAvailable) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "!",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onError,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = game.title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Bottom sheet with a game's full description and the same install/play/update actions the old
 *  list-item card used to show inline. Kept open across "Встановити"/"Видалити" so the button
 *  underneath updates live; closes itself after "Грати"/"Оновити" via the standard
 *  hide-then-notify [ModalBottomSheet] dismiss pattern. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GameDetailsSheet(
    game: GameInfo,
    installed: Boolean,
    updateAvailable: Boolean,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onInstall: () -> Unit,
    onUninstall: () -> Unit,
    onUpdate: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()

    fun dismiss() {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            if (!sheetState.isVisible) onDismiss()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            GameIcon(game = game, installed = installed, size = 120.dp, cornerRadius = 28.dp)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = game.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = game.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (game.levelCount > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${game.levelCount} рівнів",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(modifier = Modifier.height(20.dp))

            val (primaryLabel, primaryAction) = when {
                installed && updateAvailable -> "Оновити" to { onUpdate(); dismiss() }
                !installed -> "Встановити" to onInstall
                else -> "Грати" to onPlay
            }
            Button(onClick = primaryAction, modifier = Modifier.fillMaxWidth()) {
                Text(primaryLabel)
            }
            if (installed) {
                Spacer(modifier = Modifier.height(4.dp))
                TextButton(onClick = onUninstall, modifier = Modifier.fillMaxWidth()) {
                    Text("Видалити")
                }
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

/** Renders [game]'s icon if it has one, or a plain accent-colored square otherwise -- shared by
 *  [GameTile] (small, in the grid) and [GameDetailsSheet] (large, in the sheet). */
@Composable
private fun GameIcon(game: GameInfo, installed: Boolean, size: Dp, cornerRadius: Dp, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(cornerRadius)
    if (game.iconRes != null) {
        Image(
            painter = painterResource(game.iconRes),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(shape)
                .alpha(if (installed) 1f else 0.4f),
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(shape)
                .background(Color(game.accent))
                .alpha(if (installed) 1f else 0.4f),
        )
    }
}
