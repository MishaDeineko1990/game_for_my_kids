package com.deineko.kidsgames

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.deineko.kidsgames.games.GameRegistry
import com.deineko.kidsgames.ui.HomeScreen

private const val ROUTE_HOME = "home"
private const val ROUTE_GAME = "play/{gameId}"

@Composable
fun HubApp() {
    val navController = rememberNavController()
    Surface(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        NavHost(navController = navController, startDestination = ROUTE_HOME) {
            composable(ROUTE_HOME) {
                HomeScreen(onPlay = { gameId -> navController.navigate("play/$gameId") })
            }
            composable(ROUTE_GAME) { backStackEntry ->
                val gameId = backStackEntry.arguments?.getString("gameId")
                if (gameId != null) {
                    GameRegistry.Launch(id = gameId, onExit = { navController.popBackStack() })
                }
            }
        }
    }
}
