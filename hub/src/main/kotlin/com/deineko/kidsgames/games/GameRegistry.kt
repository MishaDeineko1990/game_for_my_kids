package com.deineko.kidsgames.games

import androidx.compose.runtime.Composable
import com.deineko.beachvolleyball.BeachVolleyballApp
import com.deineko.colorblock.ColorBlockApp
import com.deineko.colorblock.core.ALL_LEVELS

enum class AgeGroup(val label: String) {
    TODDLER("3-5"),
    KID("5-8"),
}

enum class GameCategory(val label: String) {
    PUZZLE("Головоломки"),
    SPORTS("Спорт"),
}

/** Static metadata for a game bundled in this build of the hub APK. Code always ships inside the
 *  hub app itself (games are never separately installable units); [bundledVersion] is this
 *  build's version of that game's code, compared against the catalog's `contentVersion` for the
 *  same id to decide whether this game's card should offer "Оновити" instead of "Грати". Since a
 *  game's code only ever changes via a new hub build, tapping that "Оновити" triggers the same
 *  whole-app update as the hub's own update banner -- there is deliberately no separate per-game
 *  download path yet (see the hub-store-architecture decision for what that would need). */
data class GameInfo(
    val id: String,
    val title: String,
    val description: String,
    val levelCount: Int,
    val accent: Long,
    val ageGroup: AgeGroup,
    val category: GameCategory,
    val bundledVersion: Int,
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
            bundledVersion = 1,
        ),
        GameInfo(
            id = "beach_volleyball",
            title = "Пляжний волейбол",
            description = "Стрибай і відбивай м'яч через сітку -- проти комп'ютера або друга",
            levelCount = 0,
            accent = 0xFFFF6F59,
            ageGroup = AgeGroup.KID,
            category = GameCategory.SPORTS,
            bundledVersion = 2,
        ),
    )

    @Composable
    fun Launch(id: String, onExit: () -> Unit) {
        when (id) {
            "color_block" -> ColorBlockApp(onExitToHub = onExit)
            "beach_volleyball" -> BeachVolleyballApp(onExitToHub = onExit)
        }
    }
}
