package com.deineko.kidsgames.games

import androidx.compose.runtime.Composable
import com.deineko.colorblock.ColorBlockApp
import com.deineko.colorblock.core.ALL_LEVELS

/** Static metadata for a game bundled in this build of the hub APK.
 *  Code always ships inside the hub app itself; only per-game content (levels, assets) is meant
 *  to be fetched/updated independently later, once a game's data moves out of Kotlin sources. */
data class GameInfo(
    val id: String,
    val title: String,
    val description: String,
    val levelCount: Int,
    val accent: Long,
)

object GameRegistry {
    val games: List<GameInfo> = listOf(
        GameInfo(
            id = "color_block",
            title = "Color Block",
            description = "Виведи кольорові кубики за межі поля",
            levelCount = ALL_LEVELS.size,
            accent = 0xFF57C6B4,
        ),
    )

    @Composable
    fun Launch(id: String, onExit: () -> Unit) {
        when (id) {
            "color_block" -> ColorBlockApp(onExitToHub = onExit)
        }
    }
}
