package com.deineko.colorblock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deineko.colorblock.progress.ProgressStore

private val WorldColors = listOf(
    Color(0xFF57C6B4), Color(0xFF4FA8D8), Color(0xFFE8B84B), Color(0xFFE38A4E), Color(0xFFA57BD1),
    Color(0xFF6FCFC0), Color(0xFF6FB8E0), Color(0xFFEDC46B), Color(0xFFE9A06E), Color(0xFFB595DA),
)

private fun worldColor(levelId: Int): Color = WorldColors[((levelId - 1) / 10).coerceIn(0, WorldColors.lastIndex)]

@Composable
fun LevelSelectScreen(totalLevels: Int, onLevelSelected: (Int) -> Unit) {
    val context = LocalContext.current
    val store = remember { ProgressStore(context) }
    var highestUnlocked by remember { mutableIntStateOf(store.highestUnlocked()) }

    // Re-check unlock state whenever this screen is (re)shown, e.g. after finishing a level.
    LaunchedEffect(Unit) {
        highestUnlocked = store.highestUnlocked()
    }

    Column(modifier = Modifier.fillMaxSize().padding(top = 24.dp)) {
        Text(
            text = "Color Block",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Обери рівень",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            textAlign = TextAlign.Center,
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(5),
            contentPadding = PaddingValues(20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(totalLevels) { index ->
                val levelId = index + 1
                val unlocked = levelId <= highestUnlocked
                LevelPeg(
                    levelId = levelId,
                    color = worldColor(levelId),
                    unlocked = unlocked,
                    onClick = { onLevelSelected(levelId) },
                )
            }
        }
    }
}

@Composable
private fun LevelPeg(levelId: Int, color: Color, unlocked: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(if (unlocked) color else MaterialTheme.colorScheme.outline)
            .clickable(enabled = unlocked, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (unlocked) {
            Text(text = levelId.toString(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        } else {
            Text(text = "🔒", fontSize = 12.sp)
        }
    }
}
