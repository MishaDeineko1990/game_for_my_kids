package com.deineko.kidsgames.games

import androidx.compose.runtime.Composable
import com.deineko.beachvolleyball.BeachVolleyballApp
import com.deineko.colorblock.ColorBlockApp
import com.deineko.colorblock.core.ALL_LEVELS
import com.deineko.kidsgames.catalog.GameCatalogEntry

enum class AgeGroup(val label: String) {
    TODDLER("3-5"),
    KID("5-8"),
}

enum class GameCategory(val label: String) {
    PUZZLE("Головоломки"),
    SPORTS("Спорт"),
}

/** Static metadata for a game bundled in this build of the hub APK.
 *  Code always ships inside the hub app itself; only per-game content (levels, assets) is meant
 *  to be fetched/updated independently later, once a game's data moves out of Kotlin sources. */
data class GameInfo(
    val id: String,
    val title: String,
    val description: String,
    val levelCount: Int,
    val accent: Long,
    val ageGroup: AgeGroup,
    val category: GameCategory,
)

object GameRegistry {
    val games: List<GameInfo> = listOf(
        GameInfo(
            id = "color_block",
            title = "Color Block",
            description = "Виведи кольорові кубики за межі поля",
            levelCount = ALL_LEVELS.size,
            accent = 0xFF57C6B4,
            ageGroup = AgeGroup.TODDLER,
            category = GameCategory.PUZZLE,
        ),
        GameInfo(
            id = "beach_volleyball",
            title = "Пляжний волейбол",
            description = "Перебивайте м'ячик через сітку -- проти комп'ютера або друга",
            levelCount = 0,
            accent = 0xFFFF6F59,
            ageGroup = AgeGroup.KID,
            category = GameCategory.SPORTS,
        ),
    )

    /** Catalog entries whose id isn't bundled in this build -- a game that only appears once the
     *  hub itself is updated (games are never installed separately, see GameInfo's doc). Callers
     *  don't currently need this beyond confirming the update mechanism already covers it; kept
     *  here rather than in `catalog` so `catalog` never has to know what's actually bundled. */
    fun unknownGameIds(catalogGames: List<GameCatalogEntry>): List<String> =
        catalogGames.map { it.id }.filterNot { id -> games.any { it.id == id } }

    @Composable
    fun Launch(id: String, onExit: () -> Unit) {
        when (id) {
            "color_block" -> ColorBlockApp(onExitToHub = onExit)
            "beach_volleyball" -> BeachVolleyballApp(onExitToHub = onExit)
        }
    }
}
