package com.deineko.colorblock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.deineko.colorblock.core.ALL_LEVELS
import com.deineko.colorblock.ui.GameScreen
import com.deineko.colorblock.ui.LevelSelectScreen

private const val ROUTE_LEVELS = "levels"
private const val ROUTE_GAME = "game/{levelId}"

@Composable
fun ColorBlockApp(onExitToHub: () -> Unit = {}) {
    val navController = rememberNavController()
    Surface(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        NavHost(navController = navController, startDestination = ROUTE_LEVELS) {
            composable(ROUTE_LEVELS) {
                LevelSelectScreen(
                    totalLevels = ALL_LEVELS.size,
                    onLevelSelected = { levelId -> navController.navigate("game/$levelId") },
                    onExitToHub = onExitToHub,
                )
            }
            composable(
                route = ROUTE_GAME,
                arguments = listOf(navArgument("levelId") { type = NavType.IntType }),
            ) { backStackEntry ->
                val levelId = backStackEntry.arguments?.getInt("levelId") ?: 1
                GameScreen(
                    levelId = levelId,
                    onBack = { navController.popBackStack() },
                    onNavigateToLevel = { nextId ->
                        navController.navigate("game/$nextId") {
                            popUpTo(ROUTE_LEVELS)
                        }
                    },
                )
            }
        }
    }
}
